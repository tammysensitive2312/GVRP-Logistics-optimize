import {Toast} from "../../utils/toast.js";
import {Loading} from "../../utils/loading.js";
import {AdminSettings} from "../screens/admin-settings.js";

export class VehicleTypeManager {
    static #vehicleTypes = [];
    static #filteredTypes = [];
    static #currentEditId = null;
    static #modal = null;
    static #form = null;

    static init() {
        this.#modal = document.getElementById('vehicle-type-modal');
        this.#form = document.getElementById('vehicle-type-form');

        if (this.#form) {
            this.#form.addEventListener('submit', (e) => this.handleSubmit(e));
        }
    }

    static async load() {
        Loading.show('Loading vehicle types...');
        try {
            this.#vehicleTypes = await getVehicleTypes();
            this.#filteredTypes = [...this.#vehicleTypes];
            this.render();
        } catch (error) {
            console.error('Failed to load vehicle types:', error);
            Toast.error('Failed to load vehicle types');
        } finally {
            Loading.hide();
        }
    }

    static render() {
        const container = document.getElementById('vehicle-types-list');

        if (!this.#filteredTypes || this.#filteredTypes.length === 0) {
            container.innerHTML = `
                <div class="empty-state" style="grid-column: 1/-1;">
                    <div class="empty-icon">🚚</div>
                    <div class="empty-text">No vehicle types found</div>
                    <div class="empty-hint">Add your first vehicle type to get started</div>
                    <button class="btn btn-primary" onclick="VehicleTypeManager.openAddModal()">
                        + Add Vehicle Type
                    </button>
                </div>
            `;
            return;
        }

        container.innerHTML = this.#filteredTypes.map(type => `
            <div class="item-card">
                <div class="card-header">
                    <div class="card-title">
                        🚚 ${type.name}
                    </div>
                    <div class="card-actions">
                        <button class="btn-icon" onclick="VehicleTypeManager.edit(${type.id})" title="Edit">
                            ✏️
                        </button>
                        <button class="btn-icon danger" onclick="VehicleTypeManager.delete(${type.id})" title="Delete">
                            🗑️
                        </button>
                    </div>
                </div>
                <div class="card-details">
                    <div class="detail-row">
                        <span class="detail-label">Capacity:</span>
                        <span class="detail-value">${type.capacity} kg</span>
                    </div>
                    <div class="detail-row">
                        <span class="detail-label">Fixed Cost:</span>
                        <span class="detail-value">${this.#formatCurrency(type.fixed_cost)}</span>
                    </div>
                    <div class="detail-row">
                        <span class="detail-label">Cost/km:</span>
                        <span class="detail-value">${this.#formatCurrency(type.cost_per_km)}</span>
                    </div>
                    <div class="detail-row">
                        <span class="detail-label">Cost/hour:</span>
                        <span class="detail-value">${this.#formatCurrency(type.cost_per_hour)}</span>
                    </div>
                    ${type.max_distance ? `
                    <div class="detail-row">
                        <span class="detail-label">Max Distance:</span>
                        <span class="detail-value">${type.max_distance} km</span>
                    </div>
                    ` : ''}
                </div>
            </div>
        `).join('');
    }

    static search(query) {
        if (!query) {
            this.#filteredTypes = [...this.#vehicleTypes];
        } else {
            const lowerQuery = query.toLowerCase();
            this.#filteredTypes = this.#vehicleTypes.filter(type =>
                type.name.toLowerCase().includes(lowerQuery)
            );
        }
        this.render();
    }

    static openAddModal() {
        this.#currentEditId = null;
        document.getElementById('vehicle-type-modal-title').textContent = 'Add Vehicle Type';
        this.#form?.reset();
        this.#modal?.classList.add('active');
    }

    static edit(typeId) {
        const type = this.#vehicleTypes.find(t => t.id === typeId);
        if (!type) return;

        this.#currentEditId = typeId;
        document.getElementById('vehicle-type-modal-title').textContent = 'Edit Vehicle Type';

        document.getElementById('vtype-name').value = type.name;
        document.getElementById('vtype-capacity').value = type.capacity;
        document.getElementById('vtype-emission-factor').value = type.vehicle_features?.emission_factor || '';
        document.getElementById('vtype-fixed-cost').value = type.fixed_cost;
        document.getElementById('vtype-cost-per-km').value = type.cost_per_km;
        document.getElementById('vtype-cost-per-hour').value = type.cost_per_hour;
        document.getElementById('vtype-max-distance').value = type.max_distance || '';
        document.getElementById('vtype-max-duration').value = type.max_duration || '';

        this.#modal?.classList.add('active');
    }

    static async delete(typeId) {
        if (!confirm('Are you sure you want to delete this vehicle type?')) {
            return;
        }

        Loading.show('Deleting...');
        try {
            await deleteVehicleType(typeId);
            Toast.success('Vehicle type deleted successfully!');
            await this.load();
            await AdminSettings.loadCounts();
        } catch (error) {
            console.error('Failed to delete:', error);
            Toast.error('Failed to delete vehicle type');
        } finally {
            Loading.hide();
        }
    }

    static closeModal() {
        this.#modal?.classList.remove('active');
        this.#currentEditId = null;
    }

    static async handleSubmit(e) {
        e.preventDefault();

        const formData = {
            type_name: document.getElementById('vtype-name').value.trim(),
            capacity: parseInt(document.getElementById('vtype-capacity').value),
            fixed_cost: parseFloat(document.getElementById('vtype-fixed-cost').value),
            cost_per_km: parseFloat(document.getElementById('vtype-cost-per-km').value),
            cost_per_hour: parseFloat(document.getElementById('vtype-cost-per-hour').value),
            vehicle_features: {}
        };

        const emissionFactor = document.getElementById('vtype-emission-factor').value;
        if (emissionFactor) {
            formData.vehicle_features.emission_factor = parseFloat(emissionFactor);
        }

        const maxDistance = document.getElementById('vtype-max-distance').value;
        if (maxDistance) formData.max_distance = parseFloat(maxDistance);

        const maxDuration = document.getElementById('vtype-max-duration').value;
        if (maxDuration) formData.max_duration = parseFloat(maxDuration);

        Loading.show('Saving...');
        try {
            if (this.#currentEditId) {
                await updateVehicleType(this.#currentEditId, formData);
                Toast.success('Vehicle type updated!');
            } else {
                await createVehicleType(formData);
                Toast.success('Vehicle type created!');
            }

            this.closeModal();
            await this.load();
            await AdminSettings.loadCounts();
        } catch (error) {
            console.error('Failed to save:', error);
            Toast.error('Failed to save vehicle type');
        } finally {
            Loading.hide();
        }
    }

    static #formatCurrency(amount) {
        return new Intl.NumberFormat('vi-VN', {
            style: 'currency',
            currency: 'VND'
        }).format(amount);
    }
}

if (typeof window !== 'undefined') {
    window.VehicleTypeManager = VehicleTypeManager;
}
