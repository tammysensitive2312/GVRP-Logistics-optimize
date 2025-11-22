/**
 * Fleet Form Component
 * Manages fleet setup form and vehicle cards
 */

import { AppState } from '../../core/state.js';
import { Router } from '../../core/router.js';
import { Toast } from '../../utils/toast.js';
import { Loading } from '../../utils/loading.js';
import { Validator } from '../../utils/validation.js';
import { DOMHelpers } from '../../utils/dom-helpers.js';
import { VehicleCard } from './vehicle-card.js';

export class FleetForm {
    static #formElement = null;
    static #vehiclesContainer = null;
    static #depotsInfoBox = null;

    /**
     * Initialize fleet form
     */
    static init() {
        this.#formElement = document.getElementById('fleet-form');
        this.#vehiclesContainer = document.getElementById('vehicles-container');
        this.#depotsInfoBox = document.getElementById('depots-info');

        if (!this.#formElement) {
            console.warn('Fleet form not found');
            return;
        }

        // Attach submit handler
        this.#formElement.addEventListener('submit', (e) => {
            this.handleSubmit(e);
        });

        // Listen to vehicle capacity changes
        document.addEventListener('vehicle-capacity-changed', () => {
            this.updateSummary();
        });
    }

    /**
     * Load available depots and display
     */
    static async loadDepots() {
        Loading.show('Đang tải depots...');

        try {
            const depots = await getDepots();

            if (!depots || depots.length === 0) {
                Toast.error('Không tìm thấy depot nào. Vui lòng tạo depot trước.');
                Router.goTo(Router.SCREENS.DEPOT_SETUP);
                return;
            }

            // Store in state
            AppState.setAvailableDepots(depots);

            // Display depot info
            this.#displayDepotInfo(depots);

            // Add first vehicle by default if none exists
            if (AppState.vehicleCount === 0) {
                this.addVehicle();
            }

        } catch (error) {
            console.error('Failed to load depots:', error);
            Toast.error('Không thể tải danh sách depot');
        } finally {
            Loading.hide();
        }
    }

    /**
     * Display depot info
     * @private
     */
    static #displayDepotInfo(depots) {
        if (!this.#depotsInfoBox) return;

        const listContainer = document.getElementById('depots-list-info');
        if (!listContainer) return;

        this.#depotsInfoBox.style.display = 'block';
        listContainer.innerHTML = '';

        depots.forEach(depot => {
            const item = document.createElement('div');
            item.className = 'depot-info-item';
            item.innerHTML = `
        <strong>${depot.name}</strong> - ${depot.address}
      `;
            listContainer.appendChild(item);
        });
    }

    /**
     * Add a new vehicle card
     */
    static addVehicle() {
        AppState.incrementVehicleCount();

        const vehicleNumber = AppState.vehicleCount;
        const depots = AppState.availableDepots;

        const vehicleCard = VehicleCard.create(vehicleNumber, depots);
        this.#vehiclesContainer.appendChild(vehicleCard);

        this.updateSummary();

        // Scroll to new card
        vehicleCard.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }

    /**
     * Update fleet summary
     */
    static updateSummary() {
        const vehicles = VehicleCard.getAll();
        let totalCapacity = 0;

        vehicles.forEach(vehicle => {
            const capacityInput = vehicle.querySelector('input[name="capacity"]');
            if (capacityInput && capacityInput.value) {
                totalCapacity += parseInt(capacityInput.value) || 0;
            }
        });

        DOMHelpers.setText('total-vehicles', vehicles.length);
        DOMHelpers.setText('total-capacity', totalCapacity + ' kg');
    }

    /**
     * Handle form submission
     */
    static async handleSubmit(event) {
        event.preventDefault();

        const fleetName = DOMHelpers.getValue('fleet-name').trim();

        if (!fleetName) {
            Toast.error('Vui lòng nhập tên đội xe');
            return;
        }

        // Collect vehicle data
        const vehicles = [];
        const vehicleCards = VehicleCard.getAll();
        const errors = [];

        vehicleCards.forEach((card, index) => {
            const vehicleData = VehicleCard.getData(card);

            // Validate
            const vehicleErrors = Validator.validateVehicle(vehicleData, index + 1);
            if (vehicleErrors.length > 0) {
                errors.push(...vehicleErrors);
            }

            vehicles.push(vehicleData);
        });

        // Check for validation errors
        if (errors.length > 0) {
            Toast.error(errors[0]);
            return;
        }

        if (vehicles.length === 0) {
            Toast.error('Vui lòng thêm ít nhất 1 xe');
            return;
        }

        const fleetData = {
            fleet_name: fleetName,
            vehicles
        };

        Loading.show('Đang lưu đội xe...');

        try {
            // Call API
            await createFleet(fleetData);

            Toast.success('Đội xe đã được tạo thành công!');

            // Navigate to main screen
            Router.goTo(Router.SCREENS.MAIN);

        } catch (error) {
            console.error('Failed to create fleet:', error);
            Toast.error('Không thể tạo đội xe. Vui lòng thử lại.');
        } finally {
            Loading.hide();
        }
    }

    /**
     * Reset form
     */
    static reset() {
        if (this.#formElement) {
            this.#formElement.reset();
        }

        // Clear all vehicle cards
        VehicleCard.clearAll();

        // Reset vehicle count
        AppState.setVehicleCount(0);

        // Add first vehicle
        this.addVehicle();

        this.updateSummary();

        Toast.info('Form đã được reset');
    }

    /**
     * Validate form (without submitting)
     * @returns {boolean}
     */
    static validate() {
        const fleetName = DOMHelpers.getValue('fleet-name').trim();

        if (!fleetName) {
            Toast.error('Vui lòng nhập tên đội xe');
            return false;
        }

        const vehicles = [];
        const vehicleCards = VehicleCard.getAll();
        const errors = [];

        vehicleCards.forEach((card, index) => {
            const vehicleData = VehicleCard.getData(card);
            const vehicleErrors = Validator.validateVehicle(vehicleData, index + 1);

            if (vehicleErrors.length > 0) {
                errors.push(...vehicleErrors);
            }

            vehicles.push(vehicleData);
        });

        if (errors.length > 0) {
            Toast.error(errors[0]);
            return false;
        }

        if (vehicles.length === 0) {
            Toast.error('Vui lòng thêm ít nhất 1 xe');
            return false;
        }

        return true;
    }

    /**
     * Pre-fill form with data
     * @param {Object} data - Fleet data
     */
    static fill(data) {
        if (data.fleet_name) {
            DOMHelpers.setValue('fleet-name', data.fleet_name);
        }

        if (data.vehicles && data.vehicles.length > 0) {
            // Clear existing vehicles
            VehicleCard.clearAll();
            AppState.setVehicleCount(0);

            // Add vehicles from data
            data.vehicles.forEach(vehicleData => {
                this.addVehicle();

                const cards = VehicleCard.getAll();
                const lastCard = cards[cards.length - 1];

                VehicleCard.fill(lastCard, vehicleData);
            });

            this.updateSummary();
        }
    }

    /**
     * Get current form values
     * @returns {Object}
     */
    static getValues() {
        const vehicles = [];
        const vehicleCards = VehicleCard.getAll();

        vehicleCards.forEach(card => {
            vehicles.push(VehicleCard.getData(card));
        });

        return {
            fleet_name: DOMHelpers.getValue('fleet-name').trim(),
            vehicles
        };
    }

    /**
     * Check if form has unsaved changes
     * @returns {boolean}
     */
    static hasUnsavedChanges() {
        const values = this.getValues();
        return !!(values.fleet_name || values.vehicles.length > 0);
    }
}

// Export for global access
if (typeof window !== 'undefined') {
    window.FleetForm = FleetForm;
}

// Backward compatibility
window.addVehicle = () => {
    FleetForm.addVehicle();
};

window.updateFleetSummary = () => {
    FleetForm.updateSummary();
};

window.handleFleetSubmit = (event) => {
    FleetForm.handleSubmit(event);
};

window.loadDepotsForFleet = async () => {
    await FleetForm.loadDepots();
};