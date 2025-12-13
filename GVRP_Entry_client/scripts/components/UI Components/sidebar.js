/**
 * Sidebar Component
 * Manages sidebar visibility, depot list, and fleet info display
 */

import { AppState } from '../../core/state.js';
import { DOMHelpers } from '../../utils/dom-helpers.js';

export class Sidebar {
    static #sidebarElement = null;

    /**
     * Initialize sidebar
     */
    static init() {
        this.#sidebarElement = DOMHelpers.getElement('main-sidebar');

        if (!this.#sidebarElement) {
            console.warn('Sidebar element not found');
            return;
        }

        AppState.subscribe('sidebarCollapsed', (collapsed) => {
            if (collapsed) {
                this.collapse();
            } else {
                this.expand();
            }
        });

        if (AppState.sidebarCollapsed) {
            this.collapse();
        } else {
            this.expand();
        }
    }

    /**
     * Toggle sidebar visibility
     */
    static toggle() {
        const currentState = AppState.sidebarCollapsed;
        AppState.setSidebarCollapsed(!currentState);
    }

    static collapse() {
        if (!this.#sidebarElement) return;

        this.#sidebarElement.classList.add('collapsed');
        // Update button
        const btn = this.#sidebarElement.querySelector('.btn-collapse');
        if (btn) {
            btn.innerHTML = '►';
            btn.title = 'Show Sidebar';
        }

        // Invalidate map size after animation
        this.#invalidateMapAfterDelay();
    }

    /**
     * Expand sidebar UI
     * SỬA: Chỉ update DOM/CSS, XÓA dòng setSidebarCollapsed
     */
    static expand() {
        if (!this.#sidebarElement) return;

        this.#sidebarElement.classList.remove('collapsed');
        // Update button
        const btn = this.#sidebarElement.querySelector('.btn-collapse');
        if (btn) {
            btn.innerHTML = '◄ Hide Sidebar';
            btn.title = 'Hide Sidebar';
        }

        // Invalidate map size after animation
        this.#invalidateMapAfterDelay();
    }

    static toggleSection(contentId, headerElement) {
        const content = document.getElementById(contentId);
        const icon = headerElement.querySelector('.toggle-icon');

        if (content) {
            const isExpanded = content.classList.contains('expanded');
            if (isExpanded) {
                content.classList.remove('expanded');
                if(icon) icon.style.transform = 'rotate(-90deg)';
            } else {
                content.classList.add('expanded');
                if(icon) icon.style.transform = 'rotate(0deg)';
            }
        }
    }

    /**
     * Check if sidebar is collapsed
     */
    static isCollapsed() {
        return this.#sidebarElement?.classList.contains('collapsed') || false;
    }

    /**
     * Update depots list
     * @param {Array} depots - Array of depot objects
     */
    static updateDepotsList(depots) {
        const container = document.getElementById('depots-list');
        const countLabel = document.getElementById('depot-count');
        if (!container) return;

        if(countLabel) countLabel.textContent = depots?.length || 0;
        container.innerHTML = '';

        if (!depots || depots.length === 0) {
            container.innerHTML = '<div class="empty-message">No depots</div>';
            return;
        }

        depots.forEach(depot => {
            const div = document.createElement('div');
            div.className = 'depot-item-compact';
            div.title = depot.address;
            div.onclick = () => Sidebar.centerMapOnDepot(depot.latitude, depot.longitude);

            div.innerHTML = `
                <span>📍 ${depot.name}</span>
                <span style="font-size: 10px; opacity: 0.5">➜</span>
            `;
            container.appendChild(div);
        });
    }

    static toggleVehicleSelection(vehicleId, isChecked) {
        const id = Number(vehicleId);
        if (isChecked) {
            AppState.selectVehicle(id);
        } else {
            AppState.deselectVehicle(id);
        }

        // Router.updatePlanRoutesButton();
    }

    /**
     * Update Vehicles List
     */
    static updateVehiclesList(vehicles) {
        const container = document.getElementById('vehicles-list');
        const countLabel = document.getElementById('vehicle-count');
        if (!container) return;

        // Get data from AppState if not passed in
        const list = vehicles || (AppState.allVehicles ? AppState.allVehicles : []);

        if(countLabel) countLabel.textContent = list.length || 0;
        container.innerHTML = '';

        if (list.length === 0) {
            container.innerHTML = '<div class="empty-message">No vehicles</div>';
            return;
        }

        list.forEach(vehicle => {
            const statusClass = vehicle.status === 'AVAILABLE' ? 'available' : 'in-use';
            const isSelected = AppState.isVehicleSelected(vehicle.id);

            const div = document.createElement('div');
            div.className = 'vehicle-item-compact';


            div.innerHTML = `
            <div style="display:flex; align-items:center; flex: 1;">
                
                <input 
                    type="checkbox" 
                    id="vehicle-${vehicle.id}" 
                    style="margin-right: 8px;"
                    onchange="Sidebar.toggleVehicleSelection(${vehicle.id}, this.checked)"
                    ${isSelected ? 'checked' : ''}
                />
                <span class="status-dot ${statusClass}"></span>
                <span class="vehicle-plate">${vehicle.vehicle_license_plate}</span>
            </div>
            <div style="font-size: 11px; color: #666">
                ${vehicle.vehicle_type_name}
            </div>
            `;
            container.appendChild(div);
        });
    }

    /**
     * Update stats cards
     * @param {Object} orderStats - Order statistics
     */
    static updateStatsCards(orderStats = null) {
        const stats = orderStats || AppState.orderStats;

        const statsCards = document.querySelectorAll('.stat-card');
        if (statsCards.length >= 3) {
            statsCards[0].querySelector('.stat-number').textContent = stats.scheduled;
            statsCards[1].querySelector('.stat-number').textContent = stats.completed;
            statsCards[2].querySelector('.stat-number').textContent = stats.total;
        }

        // Update 4th card if exists (routes)
        if (statsCards.length >= 4) {
            // TODO: Get active routes count
            statsCards[3].querySelector('.stat-number').textContent = '0';
        }
    }

    /**
     * Create depot item element
     * @private
     */
    static #createDepotItem(depot) {
        const depotItem = document.createElement('div');
        depotItem.className = 'depot-item';
        depotItem.innerHTML = `
      <div class="depot-header">
        <span class="depot-icon">📍</span>
        <span class="depot-name">${depot.name}</span>
      </div>
      <div class="depot-details">
        <div class="depot-address">${depot.address}</div>
        <div class="depot-coords">Lat: ${depot.latitude.toFixed(6)}, Lng: ${depot.longitude.toFixed(6)}</div>
        <button class="btn btn-sm btn-text" onclick="Sidebar.centerMapOnDepot(${depot.latitude}, ${depot.longitude})">
          📍 View on Map
        </button>
      </div>
    `;
        return depotItem;
    }

    /**
     * Center map on depot location
     * @param {number} lat
     * @param {number} lng
     */
    static centerMapOnDepot(lat, lng) {
        if (typeof MainMap !== 'undefined') {
            MainMap.centerTo(lat, lng);
        } else {
            console.warn('MainMap not available');
        }
    }

    /**
     * Invalidate map size after animation
     * @private
     */
    static #invalidateMapAfterDelay() {
        setTimeout(() => {
            if (typeof mainMap !== 'undefined' && mainMap) {
                mainMap.invalidateSize();
            }
        }, 300);
    }
}

if (typeof window !== 'undefined') {
    window.Sidebar = Sidebar;
}

window.toggleSidebar = () => Sidebar.toggle();