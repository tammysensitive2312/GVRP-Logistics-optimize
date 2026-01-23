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
        const vehicles = this.#filteredVehicles || [];
        const container = document.getElementById('admin-vehicles-list');

        if (vehicles.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <div class="empty-icon">🚗</div>
                    <div class="empty-text">No vehicles found</div>
                    <div class="empty-hint">Let's add a new car to get started</div>
                </div>
            `;
            return;
        }

        container.innerHTML = `
            <div class="table-responsive">
                <table class="data-table" style="width: 100%; border-collapse: collapse;">
                    <thead>
                        <tr>
                            <th>#</th>
                            <th>Vehicle License Plate</th>
                            <th>Type</th>
                            <th>Status</th>
                            <th class="text-center">Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${vehicles.map((vehicle, index) => `
                            <tr>
                                <td>${index + 1}</td>
                                <td>
                                    <strong>${vehicle.vehicle_license_plate}</strong>
                                </td>
                                <td>${vehicle.vehicle_type_name || '<span class="text-muted">N/A</span>'}</td>
                                <td>
                                    ${this.#renderStatusBadge(vehicle.status)}
                                </td>
                                <td class="text-center">
                                    <div class="action-buttons" style="justify-content: center;">
                                        <button class="btn-icon" onclick="VehicleManager.openEdit(${vehicle.id})" title="Edit">
                                            ✏️
                                        </button>
                                        <button class="btn-icon danger" onclick="VehicleManager.delete(${vehicle.id})" title="Delete">
                                            🗑️
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            </div>
        `;
    }

    static #renderStatusBadge(status) {
        const s = status || 'AVAILABLE';
        let colorClass = 'badge-success';

        if (s === 'ON_ROUTE' || s === 'BUSY') colorClass = 'badge-warning'; // Vàng
        if (s === 'MAINTENANCE' || s === 'FAILED') colorClass = 'badge-danger'; // Đỏ

        return `<span class="badge ${colorClass}" style="padding: 4px 8px; border-radius: 4px; font-size: 12px;">${s}</span>`;
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
