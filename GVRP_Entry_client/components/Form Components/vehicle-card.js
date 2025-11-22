/**
 * Vehicle Card Component
 * Creates and manages individual vehicle cards in fleet form
 */

import { AppState } from '../../core/state.js';
import { Toast } from '../../utils/toast.js';
import { DOMHelpers } from '../../utils/dom-helpers.js';

export class VehicleCard {
    /**
     * Create a new vehicle card
     * @param {number} vehicleNumber - Vehicle number/ID
     * @param {Array} depots - Available depots
     * @returns {HTMLElement}
     */
    static create(vehicleNumber, depots = []) {
        const card = document.createElement('div');
        card.className = 'vehicle-card';
        card.id = `vehicle-${vehicleNumber}`;
        card.dataset.vehicleNumber = vehicleNumber;

        card.innerHTML = this.#buildCardHTML(vehicleNumber, depots);

        return card;
    }

    /**
     * Build card HTML
     * @private
     */
    static #buildCardHTML(vehicleNumber, depots) {
        return `
      <div class="vehicle-header">
        <span class="vehicle-number">Xe #${vehicleNumber}</span>
        <button type="button" class="btn-remove-vehicle" 
                onclick="VehicleCard.remove(${vehicleNumber})">
          ✕ Xóa
        </button>
      </div>
      <div class="vehicle-fields">
        <div class="form-group">
          <label>Biển số <span class="required">*</span></label>
          <input type="text" name="vehicleLicensePlate" 
                 placeholder="29A-12345" required />
        </div>
        <div class="form-group">
          <label>Loại xe</label>
          <input type="text" name="vehicleFeature" 
                 placeholder="Xe tải nhỏ" />
        </div>
        <div class="form-group">
          <label>Tải trọng (kg) <span class="required">*</span></label>
          <input type="number" name="capacity" 
                 placeholder="500" min="1" required 
                 onchange="VehicleCard.onCapacityChange()" />
        </div>
        <div class="form-group">
          <label>Chi phí cố định (VND)</label>
          <input type="number" name="fixedCost" 
                 placeholder="100000" min="0" step="1000" />
        </div>
        <div class="form-group">
          <label>Chi phí/km (VND)</label>
          <input type="number" name="costPerKm" 
                 placeholder="5000" min="0" step="100" />
        </div>
        <div class="form-group">
          <label>Chi phí/giờ (VND)</label>
          <input type="number" name="costPerHour" 
                 placeholder="50000" min="0" step="1000" />
        </div>
        <div class="form-group">
          <label>Quãng đường tối đa (km)</label>
          <input type="number" name="maxDistance" 
                 placeholder="100" min="0" step="1" />
        </div>
        <div class="form-group">
          <label>Thời gian tối đa (giờ)</label>
          <input type="number" name="maxDuration" 
                 placeholder="8" min="0" step="0.5" />
        </div>
        <div class="form-group">
          <label>Điểm xuất phát <span class="required">*</span></label>
          <select name="startDepotId" required>
            ${this.#buildDepotOptions(depots)}
          </select>
        </div>
        <div class="form-group">
          <label>Điểm kết thúc <span class="required">*</span></label>
          <select name="endDepotId" required>
            ${this.#buildDepotOptions(depots)}
          </select>
        </div>
      </div>
    `;
    }

    /**
     * Build depot options HTML
     * @private
     */
    static #buildDepotOptions(depots) {
        if (!depots || depots.length === 0) {
            return '<option value="">No depots available</option>';
        }

        let html = '<option value="">-- Chọn depot --</option>';

        depots.forEach(depot => {
            html += `<option value="${depot.id}">${depot.name}</option>`;
        });

        return html;
    }

    /**
     * Remove a vehicle card
     * @param {number} vehicleNumber
     */
    static remove(vehicleNumber) {
        if (AppState.vehicleCount <= 1) {
            Toast.error('Phải có ít nhất 1 xe');
            return;
        }

        const card = document.getElementById(`vehicle-${vehicleNumber}`);
        if (card) {
            // Confirm deletion
            if (confirm(`Xóa xe #${vehicleNumber}?`)) {
                card.remove();
                AppState.decrementVehicleCount();

                // Trigger summary update
                this.onCapacityChange();

                Toast.success(`Đã xóa xe #${vehicleNumber}`);
            }
        }
    }

    /**
     * Get data from a vehicle card
     * @param {HTMLElement} card - Vehicle card element
     * @returns {Object}
     */
    static getData(card) {
        const startDepotId = parseInt(card.querySelector('select[name="startDepotId"]').value);
        const endDepotId = parseInt(card.querySelector('select[name="endDepotId"]').value);

        return {
            vehicle_license_plate: card.querySelector('input[name="vehicleLicensePlate"]').value.trim(),
            vehicle_feature: card.querySelector('input[name="vehicleFeature"]').value.trim(),
            capacity: parseInt(card.querySelector('input[name="capacity"]').value) || 0,
            fixed_cost: parseFloat(card.querySelector('input[name="fixedCost"]').value) || 0,
            cost_per_km: parseFloat(card.querySelector('input[name="costPerKm"]').value) || 0,
            cost_per_hour: parseFloat(card.querySelector('input[name="costPerHour"]').value) || 0,
            max_distance: parseFloat(card.querySelector('input[name="maxDistance"]').value) || null,
            max_duration: parseFloat(card.querySelector('input[name="maxDuration"]').value) || null,
            start_depot_id: startDepotId || null,
            end_depot_id: endDepotId || null
        };
    }

    /**
     * Validate vehicle card data
     * @param {Object} vehicleData
     * @param {number} index - Vehicle number for error messages
     * @returns {Array<string>} - Array of error messages
     */
    static validate(vehicleData, index) {
        return Validator.validateVehicle(vehicleData, index);
    }

    /**
     * Fill vehicle card with data
     * @param {HTMLElement} card
     * @param {Object} data
     */
    static fill(card, data) {
        if (data.vehicle_license_plate) {
            card.querySelector('input[name="vehicleLicensePlate"]').value = data.vehicle_license_plate;
        }
        if (data.vehicle_feature) {
            card.querySelector('input[name="vehicleFeature"]').value = data.vehicle_feature;
        }
        if (data.capacity) {
            card.querySelector('input[name="capacity"]').value = data.capacity;
        }
        if (data.fixed_cost) {
            card.querySelector('input[name="fixedCost"]').value = data.fixed_cost;
        }
        if (data.cost_per_km) {
            card.querySelector('input[name="costPerKm"]').value = data.cost_per_km;
        }
        if (data.cost_per_hour) {
            card.querySelector('input[name="costPerHour"]').value = data.cost_per_hour;
        }
        if (data.max_distance) {
            card.querySelector('input[name="maxDistance"]').value = data.max_distance;
        }
        if (data.max_duration) {
            card.querySelector('input[name="maxDuration"]').value = data.max_duration;
        }
        if (data.start_depot_id) {
            card.querySelector('select[name="startDepotId"]').value = data.start_depot_id;
        }
        if (data.end_depot_id) {
            card.querySelector('select[name="endDepotId"]').value = data.end_depot_id;
        }
    }

    /**
     * Handle capacity change (triggers fleet summary update)
     */
    static onCapacityChange() {
        // Dispatch event for FleetForm to listen
        const event = new CustomEvent('vehicle-capacity-changed');
        document.dispatchEvent(event);
    }

    /**
     * Get all vehicle cards
     * @returns {NodeList}
     */
    static getAll() {
        return document.querySelectorAll('.vehicle-card');
    }

    /**
     * Get vehicle count
     * @returns {number}
     */
    static getCount() {
        return this.getAll().length;
    }

    /**
     * Clear all vehicle cards
     */
    static clearAll() {
        const cards = this.getAll();
        cards.forEach(card => card.remove());
    }
}

// Export for global access
if (typeof window !== 'undefined') {
    window.VehicleCard = VehicleCard;
}

// Backward compatibility
window.removeVehicle = (id) => {
    VehicleCard.remove(id);
};