/**
 * Edit Order Modal Component
 * Handles editing order details
 */

import { AppState } from '../../core/state.js';
import { Toast } from '../../utils/toast.js';
import { Loading } from '../../utils/loading.js';
import { Validator } from '../../utils/validation.js';
import { DOMHelpers } from '../../utils/dom-helpers.js';

export class EditOrderModal {
    static #modal = null;
    static #form = null;
    static #currentOrderId = null;
    static #currentOrder = null;

    /**
     * Initialize edit order modal
     */
    static init() {
        this.#modal = document.getElementById('modal-edit-order');
        this.#form = document.getElementById('edit-order-form');

        if (!this.#modal || !this.#form) {
            console.warn('Edit order modal elements not found');
            return;
        }

        // Attach form submit handler
        this.#form.addEventListener('submit', (e) => {
            this.handleSubmit(e);
        });
    }

    /**
     * Open modal with order data
     * @param {number} orderId
     */
    static async open(orderId) {
        if (!this.#modal) return;

        Loading.show('Đang tải thông tin đơn hàng...');

        try {
            // Get order from state or fetch from API
            let order = AppState.allOrders.find(o => o.id === orderId);

            if (!order) {
                // Fetch from API if not in state
                order = await this.#fetchOrder(orderId);
            }

            if (!order) {
                Toast.error('Không tìm thấy đơn hàng');
                return;
            }

            this.#currentOrderId = orderId;
            this.#currentOrder = order;

            // Fill form with order data
            this.#fillForm(order);

            // Show modal
            this.#modal.classList.add('active');

        } catch (error) {
            console.error('Failed to load order:', error);
            Toast.error('Không thể tải thông tin đơn hàng');
        } finally {
            Loading.hide();
        }
    }

    /**
     * Fetch order from API
     * @private
     */
    static async #fetchOrder(orderId) {
        try {
            // Call API from api.js
            if (typeof getOrderById === 'function') {
                return await getOrderById(orderId);
            }
            return null;
        } catch (error) {
            console.error('Failed to fetch order:', error);
            return null;
        }
    }

    /**
     * Fill form with order data
     * @private
     */
    static #fillForm(order) {
        // Basic info
        DOMHelpers.setValue('edit-order-code', order.orderCode || order.order_code || '');
        DOMHelpers.setValue('edit-customer-name', order.customerName || order.customer_name || '');
        DOMHelpers.setValue('edit-customer-phone', order.customerPhone || order.customer_phone || '');
        DOMHelpers.setValue('edit-address', order.address || '');

        // Location
        DOMHelpers.setValue('edit-latitude', order.latitude || '');
        DOMHelpers.setValue('edit-longitude', order.longitude || '');

        // Order details
        DOMHelpers.setValue('edit-demand', order.demand || '');
        DOMHelpers.setValue('edit-service-time', order.serviceTime || order.service_time || 5);

        // Time windows
        DOMHelpers.setValue('edit-time-window-start', order.timeWindowStart || order.time_window_start || '');
        DOMHelpers.setValue('edit-time-window-end', order.timeWindowEnd || order.time_window_end || '');

        // Priority & Notes
        DOMHelpers.setValue('edit-priority', order.priority || 5);
        DOMHelpers.setValue('edit-delivery-notes', order.deliveryNotes || order.delivery_notes || '');

        // Status
        const statusSelect = document.getElementById('edit-status');
        if (statusSelect) {
            statusSelect.value = order.status || 'SCHEDULED';
        }

        // Delivery date (if available)
        if (order.deliveryDate || order.delivery_date) {
            DOMHelpers.setValue('edit-delivery-date', order.deliveryDate || order.delivery_date);
        }

        // Update modal title
        const modalTitle = document.querySelector('#modal-edit-order .modal-title');
        if (modalTitle) {
            modalTitle.textContent = `Edit Order: ${order.orderCode || order.order_code}`;
        }
    }

    /**
     * Close modal
     */
    static close(skipConfirm = false) {
        if (!this.#modal) return;

        if (!skipConfirm && this.#hasUnsavedChanges()) {
            if (!confirm('Bạn có thay đổi chưa lưu. Bạn có chắc muốn đóng?')) {
                return;
            }
        }

        this.#modal.classList.remove('active');
        this.#reset();
    }

    /**
     * Handle form submission
     */
    static async handleSubmit(event) {
        event.preventDefault();

        const formData = this.#getFormData();

        // Validate
        const validation = this.#validate(formData);
        if (!validation.isValid) {
            Toast.error(validation.errors[0]);
            return;
        }

        Loading.show('Đang cập nhật đơn hàng...');

        try {
            // Prepare update data (OrderInputDTO format)
            const updateData = {
                order_code: formData.orderCode,
                customer_name: formData.customerName,
                customer_phone: formData.customerPhone,
                address: formData.address,
                latitude: parseFloat(formData.latitude),
                longitude: parseFloat(formData.longitude),
                demand: parseFloat(formData.demand),
                service_time: parseInt(formData.serviceTime),
                time_window_start: formData.timeWindowStart || null,
                time_window_end: formData.timeWindowEnd || null,
                priority: parseInt(formData.priority),
                delivery_notes: formData.deliveryNotes
            };

            // Call API
            const updatedOrder = await updateOrder(
                this.#currentOrderId,
                updateData,
                formData.deliveryDate || null
            );

            Toast.success('Đã cập nhật đơn hàng thành công!');

            // Update state
            this.#updateOrderInState(updatedOrder);

            // Close modal
            this.close(true);

            // Reload orders table and map
            if (typeof OrdersTable !== 'undefined') {
                OrdersTable.render();
            }
            if (typeof MainMap !== 'undefined') {
                MainMap.loadOrders(AppState.filteredOrders);
            }

        } catch (error) {
            console.error('Failed to update order:', error);
            Toast.error('Không thể cập nhật đơn hàng. Vui lòng thử lại.');
        } finally {
            Loading.hide();
        }
    }

    /**
     * Get form data
     * @private
     */
    static #getFormData() {
        return {
            orderCode: DOMHelpers.getValue('edit-order-code').trim(),
            customerName: DOMHelpers.getValue('edit-customer-name').trim(),
            customerPhone: DOMHelpers.getValue('edit-customer-phone').trim(),
            address: DOMHelpers.getValue('edit-address').trim(),
            latitude: DOMHelpers.getValue('edit-latitude'),
            longitude: DOMHelpers.getValue('edit-longitude'),
            demand: DOMHelpers.getValue('edit-demand'),
            serviceTime: DOMHelpers.getValue('edit-service-time'),
            timeWindowStart: DOMHelpers.getValue('edit-time-window-start'),
            timeWindowEnd: DOMHelpers.getValue('edit-time-window-end'),
            priority: DOMHelpers.getValue('edit-priority'),
            deliveryNotes: DOMHelpers.getValue('edit-delivery-notes'),
            deliveryDate: DOMHelpers.getValue('edit-delivery-date'),
            status: DOMHelpers.getValue('edit-status')
        };
    }

    /**
     * Validate form data
     * @private
     */
    static #validate(data) {
        const errors = [];

        if (!data.orderCode) {
            errors.push('Vui lòng nhập mã đơn hàng');
        }

        if (!data.customerName) {
            errors.push('Vui lòng nhập tên khách hàng');
        }

        if (!data.address) {
            errors.push('Vui lòng nhập địa chỉ');
        }

        if (!data.latitude || !data.longitude) {
            errors.push('Vui lòng nhập tọa độ');
        }

        if (!Validator.validateCoordinates(parseFloat(data.latitude), parseFloat(data.longitude))) {
            errors.push('Tọa độ không hợp lệ');
        }

        if (!data.demand || parseFloat(data.demand) <= 0) {
            errors.push('Khối lượng phải lớn hơn 0');
        }

        if (data.customerPhone && !Validator.validatePhone(data.customerPhone)) {
            errors.push('Số điện thoại không hợp lệ');
        }

        if (data.timeWindowStart && data.timeWindowEnd) {
            if (data.timeWindowStart >= data.timeWindowEnd) {
                errors.push('Thời gian bắt đầu phải trước thời gian kết thúc');
            }
        }

        return {
            isValid: errors.length === 0,
            errors
        };
    }

    /**
     * Update order in state
     * @private
     */
    static #updateOrderInState(updatedOrder) {
        const index = AppState.allOrders.findIndex(o => o.id === updatedOrder.id);

        if (index !== -1) {
            // Update in allOrders
            const newAllOrders = [...AppState.allOrders];
            newAllOrders[index] = updatedOrder;
            AppState.setOrders(newAllOrders);
        }
    }

    /**
     * Check if form has unsaved changes
     * @private
     */
    static #hasUnsavedChanges() {
        if (!this.#currentOrder) return false;

        const currentData = this.#getFormData();

        return (
            currentData.customerName !== (this.#currentOrder.customerName || this.#currentOrder.customer_name) ||
            currentData.customerPhone !== (this.#currentOrder.customerPhone || this.#currentOrder.customer_phone) ||
            currentData.address !== this.#currentOrder.address ||
            currentData.demand !== String(this.#currentOrder.demand) ||
            currentData.deliveryNotes !== (this.#currentOrder.deliveryNotes || this.#currentOrder.delivery_notes || '')
        );
    }

    /**
     * Reset modal state
     * @private
     */
    static #reset() {
        if (this.#form) {
            this.#form.reset();
        }
        this.#currentOrderId = null;
        this.#currentOrder = null;
    }

    /**
     * Open location picker (integrate with map)
     */
    static openLocationPicker() {
        Toast.info('Location picker - Coming soon! (Integrate with map component)');
        // TODO: Open a map modal to select location
        // Similar to depot setup map
    }

    /**
     * Get current order being edited
     */
    static getCurrentOrder() {
        return this.#currentOrder;
    }

    /**
     * Get current order ID
     */
    static getCurrentOrderId() {
        return this.#currentOrderId;
    }
}

// Export for global access
if (typeof window !== 'undefined') {
    window.EditOrderModal = EditOrderModal;
}

// Backward compatibility
window.editOrderDetails = (orderId) => {
    EditOrderModal.open(orderId);
};