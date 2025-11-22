/**
 * Import Modal Component
 * Manages import orders modal and file handling
 */

import { Toast } from '../../utils/toast.js';
import { Loading } from '../../utils/loading.js';
import { Validator } from '../../utils/validation.js';
import { DOMHelpers } from '../../utils/dom-helpers.js';

export class ImportModal {
    static #modal = null;
    static #form = null;
    static #currentMethod = 'file';

    /**
     * Initialize import modal
     */
    static init() {
        this.#modal = document.getElementById('modal-import');
        this.#form = document.getElementById('import-form');

        if (!this.#modal || !this.#form) {
            console.warn('Import modal elements not found');
            return;
        }

        // Initialize file drop zone
        this.#initFileDropZone();

        // Initialize method toggle
        this.#initMethodToggle();
    }

    /**
     * Initialize file drop zone
     * @private
     */
    static #initFileDropZone() {
        const dropZone = document.getElementById('file-drop-zone');
        const fileInput = document.getElementById('import-file');

        if (!dropZone || !fileInput) return;

        // Click to select file
        dropZone.addEventListener('click', () => {
            fileInput.click();
        });

        // Drag and drop
        dropZone.addEventListener('dragover', (e) => {
            e.preventDefault();
            dropZone.style.borderColor = '#4A90E2';
        });

        dropZone.addEventListener('dragleave', () => {
            dropZone.style.borderColor = '#E5E5E5';
        });

        dropZone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropZone.style.borderColor = '#E5E5E5';

            const files = e.dataTransfer.files;
            if (files.length > 0) {
                fileInput.files = files;
                this.handleFileSelect({ target: { files } });
            }
        });

        // File input change
        fileInput.addEventListener('change', (e) => {
            this.handleFileSelect(e);
        });
    }

    /**
     * Initialize method toggle
     * @private
     */
    static #initMethodToggle() {
        const methodRadios = document.querySelectorAll('input[name="import-method"]');

        methodRadios.forEach(radio => {
            radio.addEventListener('change', () => {
                this.toggleMethod();
            });
        });
    }

    /**
     * Open modal
     */
    static open() {
        if (!this.#modal) return;

        this.#modal.classList.add('active');

        // Set default date to today
        const today = new Date().toISOString().split('T')[0];
        DOMHelpers.setValue('import-delivery-date', today);

        // Reset to file method
        this.#currentMethod = 'file';
        const fileRadio = document.querySelector('input[name="import-method"][value="file"]');
        if (fileRadio) {
            fileRadio.checked = true;
        }
        this.toggleMethod();
    }

    /**
     * Close modal
     */
    static close() {
        if (!this.#modal) return;

        this.#modal.classList.remove('active');

        // Reset form
        this.reset();
    }

    /**
     * Toggle import method
     */
    static toggleMethod() {
        const method = document.querySelector('input[name="import-method"]:checked')?.value || 'file';
        this.#currentMethod = method;

        const fileSection = document.getElementById('import-file-section');
        const textSection = document.getElementById('import-text-section');

        if (method === 'file') {
            fileSection.style.display = 'block';
            textSection.style.display = 'none';
        } else {
            fileSection.style.display = 'none';
            textSection.style.display = 'block';
        }
    }

    /**
     * Handle file selection
     */
    static handleFileSelect(event) {
        const file = event.target.files[0];
        if (!file) return;

        // Validate file
        const validation = Validator.validateFile(file, {
            maxSize: 10 * 1024 * 1024, // 10MB
            allowedExtensions: ['.csv', '.xlsx']
        });

        if (!validation.isValid) {
            Toast.error(validation.error);
            this.removeFile();
            return;
        }

        // Show file info
        document.querySelector('.upload-content').style.display = 'none';
        const fileSelected = document.getElementById('file-selected');
        fileSelected.style.display = 'flex';
        document.getElementById('file-name').textContent = file.name;
    }

    /**
     * Remove selected file
     */
    static removeFile() {
        DOMHelpers.setValue('import-file', '');
        document.querySelector('.upload-content').style.display = 'block';
        document.getElementById('file-selected').style.display = 'none';
    }

    /**
     * Submit import
     */
    static async submit() {
        const method = this.#currentMethod;
        const deliveryDate = DOMHelpers.getValue('import-delivery-date');
        const serviceTime = DOMHelpers.getValue('import-service-time');
        const overwrite = document.getElementById('import-overwrite').checked;

        // Validate
        const validation = Validator.validateImport({
            method,
            deliveryDate,
            serviceTime: parseFloat(serviceTime),
            file: method === 'file' ? document.getElementById('import-file').files[0] : null,
            textData: method === 'text' ? DOMHelpers.getValue('import-text') : null
        });

        if (!validation.isValid) {
            Toast.error(validation.errors[0]);
            return;
        }

        // Build FormData
        const formData = new FormData();

        if (method === 'file') {
            const file = document.getElementById('import-file').files[0];
            if (!file) {
                Toast.error('Vui lòng chọn file');
                return;
            }
            formData.append('file', file);
        } else {
            const textData = DOMHelpers.getValue('import-text').trim();
            if (!textData) {
                Toast.error('Vui lòng nhập dữ liệu');
                return;
            }
            formData.append('textData', textData);
        }

        formData.append('deliveryDate', deliveryDate);
        formData.append('serviceTime', serviceTime);
        formData.append('overwriteExisting', overwrite);

        Loading.show('Đang import orders...');

        try {
            // Call API
            const result = await importOrders(formData);

            if (result.success) {
                Toast.success(`Đã import ${result.importedCount} orders thành công!`);
                this.close();

                // Reload orders
                if (typeof window.loadOrders === 'function') {
                    await window.loadOrders();
                }
            } else {
                // Partial success
                Toast.error(`Import ${result.importedCount} orders. ${result.errors.length} lỗi.`);
            }

        } catch (error) {
            console.error('Import failed:', error);
            Toast.error('Import thất bại. Vui lòng thử lại.');
        } finally {
            Loading.hide();
        }
    }

    /**
     * Download template file
     */
    static downloadTemplate() {
        const csvContent =
            'orderCode,customerName,customerPhone,address,latitude,longitude,demand,serviceTime,timeWindowStart,timeWindowEnd,priority,deliveryNotes\n' +
            'ORD001,John Doe,0901234567,123 Main St,21.028511,105.804817,50.5,5,08:00,12:00,1,Handle with care\n' +
            'ORD002,Jane Smith,0912345678,456 Second Ave,21.030000,105.810000,30.0,10,09:00,13:00,2,\n';

        const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'order_template.csv';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);

        Toast.success('Template đã được tải xuống');
    }

    /**
     * Reset form
     */
    static reset() {
        if (this.#form) {
            this.#form.reset();
        }

        // Reset file selection
        this.removeFile();

        // Reset method to file
        this.#currentMethod = 'file';
        this.toggleMethod();
    }

    /**
     * Get form values
     * @returns {Object}
     */
    static getValues() {
        return {
            method: this.#currentMethod,
            deliveryDate: DOMHelpers.getValue('import-delivery-date'),
            serviceTime: DOMHelpers.getValue('import-service-time'),
            overwrite: document.getElementById('import-overwrite').checked,
            file: this.#currentMethod === 'file'
                ? document.getElementById('import-file').files[0]
                : null,
            textData: this.#currentMethod === 'text'
                ? DOMHelpers.getValue('import-text')
                : null
        };
    }
}

// Export for global access
if (typeof window !== 'undefined') {
    window.ImportModal = ImportModal;
}

// Backward compatibility
window.openImportModal = () => {
    ImportModal.open();
};

window.closeImportModal = () => {
    ImportModal.close();
};

window.toggleImportMethod = () => {
    ImportModal.toggleMethod();
};

window.handleFileSelect = (event) => {
    ImportModal.handleFileSelect(event);
};

window.removeFile = () => {
    ImportModal.removeFile();
};

window.submitImport = async () => {
    await ImportModal.submit();
};

window.downloadTemplate = () => {
    ImportModal.downloadTemplate();
};