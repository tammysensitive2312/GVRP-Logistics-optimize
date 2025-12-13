/**
 * Vehicle Type Form Component
 * Manages vehicle type setup form
 */

import { Router } from '../../core/router.js';
import { Toast } from '../../utils/toast.js';
import { Loading } from '../../utils/loading.js';
import { Validator } from '../../utils/validation.js';
import { DOMHelpers } from '../../utils/dom-helpers.js';

export class VehicleTypeForm {
    static #formElement = null;
    static #typesContainer = null;

    /**
     * Initialize vehicle type form
     */
    static init() {
        this.#formElement = document.getElementById('vehicle-type-form');
        this.#typesContainer = document.getElementById('vehicle-types-list');

        if (!this.#formElement) {
            console.warn('Vehicle type form not found');
            return;
        }

        // Load existing vehicle types
        this.loadVehicleTypes();

        // Attach submit handler
        this.#formElement.addEventListener('submit', (e) => {
            this.handleSubmit(e);
        });
    }

    /**
     * Load and display existing vehicle types
     */
    static async loadVehicleTypes() {
        Loading.show('Đang tải loại xe...');

        try {
            const vehicleTypes = await getVehicleTypes();

            if (!vehicleTypes || vehicleTypes.length === 0) {
                this.#displayEmptyState();
            } else {
                this.#displayVehicleTypes(vehicleTypes);
            }

        } catch (error) {
            console.error('Failed to load vehicle types:', error);
            Toast.error('Không thể tải danh sách loại xe');
            this.#displayEmptyState();
        } finally {
            Loading.hide();
        }
    }

    /**
     * Display vehicle types list
     * @private
     */
    static #displayVehicleTypes(types) {
        if (!this.#typesContainer) return;

        this.#typesContainer.innerHTML = '';

        types.forEach(type => {
            const card = this.#createVehicleTypeCard(type);
            this.#typesContainer.appendChild(card);
        });
    }

    /**
     * Create vehicle type card
     * @private
     */
    static #createVehicleTypeCard(type) {
        const card = document.createElement('div');
        card.className = 'vehicle-type-card';
        card.innerHTML = `
            <div class="type-header">
                <h4>🚚 ${type.name}</h4>
            </div>
            <div class="type-details">
                <div class="detail-row">
                    <span class="label">Tải trọng:</span>
                    <span class="value">${type.capacity} kg</span>
                </div>
                <div class="detail-row">
                    <span class="label">Chi phí cố định:</span>
                    <span class="value">${this.#formatCurrency(type.fixed_cost)}</span>
                </div>
                <div class="detail-row">
                    <span class="label">Chi phí/km:</span>
                    <span class="value">${this.#formatCurrency(type.cost_per_km)}</span>
                </div>
                <div class="detail-row">
                    <span class="label">Chi phí/giờ:</span>
                    <span class="value">${this.#formatCurrency(type.cost_per_hour)}</span>
                </div>
                ${type.max_distance ? `
                <div class="detail-row">
                    <span class="label">Quãng đường tối đa:</span>
                    <span class="value">${type.max_distance} km</span>
                </div>` : ''}
                ${type.max_duration ? `
                <div class="detail-row">
                    <span class="label">Thời gian tối đa:</span>
                    <span class="value">${type.max_duration} phút</span>
                </div>` : ''}
            </div>
        `;
        return card;
    }

    /**
     * Display empty state
     * @private
     */
    static #displayEmptyState() {
        if (!this.#typesContainer) return;

        this.#typesContainer.innerHTML = `
            <div class="empty-state">
                <div class="empty-icon">🚚</div>
                <div class="empty-text">Chưa có loại xe nào</div>
                <div class="empty-hint">Thêm loại xe đầu tiên bằng form bên dưới</div>
            </div>
        `;
    }

    /**
     * Format currency
     * @private
     */
    static #formatCurrency(amount) {
        return new Intl.NumberFormat('vi-VN', {
            style: 'currency',
            currency: 'VND'
        }).format(amount);
    }

    /**
     * Handle form submission
     */
    static async handleSubmit(event) {
        event.preventDefault();

        const formData = this.#getFormData();

        // Validate
        const validation = Validator.validateVehicleType(formData);
        if (!validation.isValid) {
            Toast.error(validation.errors[0]);
            return;
        }

        Loading.show('Đang tạo loại xe...');

        try {
            // Prepare vehicle features
            const vehicleFeatures = {};

            if (formData.emissionFactor) {
                vehicleFeatures.emission_factor = parseFloat(formData.emissionFactor);
            }

            // Prepare request data
            const requestData = {
                type_name: formData.typeName,
                vehicle_features: vehicleFeatures,
                capacity: parseInt(formData.capacity),
                fixed_cost: parseFloat(formData.fixedCost),
                cost_per_km: parseFloat(formData.costPerKm),
                cost_per_hour: parseFloat(formData.costPerHour)
            };

            // Optional fields
            if (formData.maxDistance) {
                requestData.max_distance = parseFloat(formData.maxDistance);
            }
            if (formData.maxDuration) {
                requestData.max_duration = parseFloat(formData.maxDuration);
            }

            // Call API
            await createVehicleType(requestData);

            Toast.success('Loại xe đã được tạo thành công!');

            // Reset form and reload list
            this.reset();
            await this.loadVehicleTypes();

        } catch (error) {
            console.error('Failed to create vehicle type:', error);
            Toast.error('Không thể tạo loại xe. Vui lòng thử lại.');
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
            typeName: DOMHelpers.getValue('vtype-name').trim(),
            capacity: DOMHelpers.getValue('vtype-capacity'),
            fixedCost: DOMHelpers.getValue('vtype-fixed-cost'),
            costPerKm: DOMHelpers.getValue('vtype-cost-per-km'),
            costPerHour: DOMHelpers.getValue('vtype-cost-per-hour'),
            maxDistance: DOMHelpers.getValue('vtype-max-distance'),
            maxDuration: DOMHelpers.getValue('vtype-max-duration'),
            emissionFactor: DOMHelpers.getValue('vtype-emission-factor')
        };
    }

    /**
     * Reset form
     */
    static reset() {
        if (this.#formElement) {
            this.#formElement.reset();
        }
    }

    /**
     * Check if we have vehicle types
     */
    static async hasVehicleTypes() {
        try {
            const types = await getVehicleTypes();
            return types && types.length > 0;
        } catch (error) {
            return false;
        }
    }

    /**
     * Continue to next step (Fleet Setup)
     */
    static continueToFleet() {
        Router.goTo(Router.SCREENS.FLEET_SETUP);
    }
}

if (typeof window !== 'undefined') {
    window.VehicleTypeForm = VehicleTypeForm;
}

window.handleVehicleTypeSubmit = (event) => {
    VehicleTypeForm.handleSubmit(event);
};

window.continueToFleet = () => {
    VehicleTypeForm.continueToFleet();
};