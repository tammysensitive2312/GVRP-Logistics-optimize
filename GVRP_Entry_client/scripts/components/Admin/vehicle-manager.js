import {Toast} from "../../utils/toast.js";
import {Loading} from "../../utils/loading.js";

export class VehicleManager {
    static #vehicles = [];
    static #filteredVehicles = [];

    static async load() {
        Loading.show('Loading vehicles...');
        try {
            const response = await getVehicle(0, 1000);
            this.#vehicles = response.content || [];
            this.#filteredVehicles = [...this.#vehicles];
            this.render();
        } catch (error) {
            console.error('Failed to load vehicles:', error);
            Toast.error('Failed to load vehicles');
        } finally {
            Loading.hide();
        }
    }

    static render() {
        const container = document.getElementById('vehicles-list');

        if (!this.#filteredVehicles || this.#filteredVehicles.length === 0) {
            container.innerHTML = `
                <div class="empty-state" style="grid-column: 1/-1;">
                    <div class="empty-icon">🚗</div>
                    <div class="empty-text">No vehicles found</div>
                    <div class="empty-hint">Add your first vehicle to get started</div>
                </div>
            `;
            return;
        }

        container.innerHTML = this.#filteredVehicles.map(vehicle => `
            <div class="item-card">
                <div class="card-header">
                    <div class="card-title">
                        🚗 ${vehicle.vehicle_license_plate}
                    </div>
                </div>
                <div class="card-details">
                    <div class="detail-row">
                        <span class="detail-label">Type:</span>
                        <span class="detail-value">${vehicle.vehicle_type_name || 'N/A'}</span>
                    </div>
                    <div class="detail-row">
                        <span class="detail-label">Status:</span>
                        <span class="detail-value">${vehicle.status || 'AVAILABLE'}</span>
                    </div>
                </div>
            </div>
        `).join('');
    }

    static search(query) {
        if (!query) {
            this.#filteredVehicles = [...this.#vehicles];
        } else {
            const lowerQuery = query.toLowerCase();
            this.#filteredVehicles = this.#vehicles.filter(v =>
                v.vehicle_license_plate.toLowerCase().includes(lowerQuery)
            );
        }
        this.render();
    }

    static openAddModal() {
        Toast.info('Vehicle management coming soon!');
    }
}

if (typeof window !== 'undefined') {
    window.VehicleManager = VehicleManager;
}
