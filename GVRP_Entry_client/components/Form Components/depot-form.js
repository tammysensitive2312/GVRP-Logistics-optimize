/**
 * Depot Form Component
 * Manages depot setup form and map interaction
 */

import { Router } from '../../core/router.js';
import { Toast } from '../../utils/toast.js';
import { Loading } from '../../utils/loading.js';
import { Validator } from '../../utils/validation.js';
import { DOMHelpers } from '../../utils/dom-helpers.js';

export class DepotForm {
    static #formElement = null;
    static #mapInstance = null;
    static #markerInstance = null;

    /**
     * Initialize depot form
     */
    static init() {
        this.#formElement = document.getElementById('depot-form');

        if (!this.#formElement) {
            console.warn('Depot form not found');
            return;
        }

        // Attach submit handler
        this.#formElement.addEventListener('submit', (e) => {
            this.handleSubmit(e);
        });
    }

    /**
     * Set map instance 
     * @param {Object} map - Leaflet map instance
     */
    static setMap(map) {
        this.#mapInstance = map;
    }

    /**
     * Set marker instance 
     * @param {Object} marker - Leaflet marker instance
     */
    static setMarker(marker) {
        this.#markerInstance = marker;
    }

    /**
     * Handle form submission
     */
    static async handleSubmit(event) {
        event.preventDefault();

        const formData = this.#getFormData();

        // Validate
        const validation = Validator.validateDepot(formData);
        if (!validation.isValid) {
            Toast.error(validation.errors[0]);
            return;
        }

        // Show loading
        Loading.show('Đang lưu depot...');

        try {
            // Call API (from api.js)
            await createDepot(formData);

            Toast.success('Depot đã được tạo thành công!');

            // Navigate to fleet setup
            Router.goTo(Router.SCREENS.FLEET_SETUP);

        } catch (error) {
            console.error('Failed to create depot:', error);
            Toast.error('Không thể tạo depot. Vui lòng thử lại.');
        } finally {
            Loading.hide();
        }
    }

    /**
     * Get form data
     * @private
     * @returns {Object}
     */
    static #getFormData() {
        return {
            name: DOMHelpers.getValue('depot-name').trim(),
            address: DOMHelpers.getValue('depot-address').trim(),
            latitude: parseFloat(DOMHelpers.getValue('depot-lat')),
            longitude: parseFloat(DOMHelpers.getValue('depot-lng'))
        };
    }

    /**
     * Update location fields from map click
     * @param {number} lat - Latitude
     * @param {number} lng - Longitude
     * @param {string} address - Reverse geocoded address
     */
    static updateLocation(lat, lng, address = '') {
        DOMHelpers.setValue('depot-lat', lat.toFixed(6));
        DOMHelpers.setValue('depot-lng', lng.toFixed(6));

        if (address) {
            DOMHelpers.setValue('depot-address', address);
        } else {
            DOMHelpers.setValue('depot-address', `${lat.toFixed(6)}, ${lng.toFixed(6)}`);
        }
    }

    /**
     * Reset form
     */
    static reset() {
        if (this.#formElement) {
            this.#formElement.reset();
        }

        // Remove marker from map
        if (this.#markerInstance && this.#mapInstance) {
            this.#mapInstance.removeLayer(this.#markerInstance);
            this.#markerInstance = null;
        }

        Toast.info('Form đã được reset');
    }

    /**
     * Validate form (without submitting)
     * @returns {boolean}
     */
    static validate() {
        const formData = this.#getFormData();
        const validation = Validator.validateDepot(formData);

        if (!validation.isValid) {
            Toast.error(validation.errors[0]);
            return false;
        }

        return true;
    }

    /**
     * Pre-fill form with data
     * @param {Object} data - Depot data
     */
    static fill(data) {
        if (data.name) {
            DOMHelpers.setValue('depot-name', data.name);
        }
        if (data.address) {
            DOMHelpers.setValue('depot-address', data.address);
        }
        if (data.latitude) {
            DOMHelpers.setValue('depot-lat', data.latitude);
        }
        if (data.longitude) {
            DOMHelpers.setValue('depot-lng', data.longitude);
        }
    }

    /**
     * Get current form values (without validation)
     * @returns {Object}
     */
    static getValues() {
        return this.#getFormData();
    }

    /**
     * Check if form has unsaved changes
     * @returns {boolean}
     */
    static hasUnsavedChanges() {
        const values = this.#getFormData();
        return !!(values.name || values.address || values.latitude || values.longitude);
    }
}

// Export for global access
if (typeof window !== 'undefined') {
    window.DepotForm = DepotForm;
}

// Backward compatibility
window.handleDepotSubmit = (event) => {
    DepotForm.handleSubmit(event);
};

window.resetDepotForm = () => {
    DepotForm.reset();
};