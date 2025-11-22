/**
 * Sidebar Component
 * Manages sidebar visibility, depot list, and fleet info display
 */

import { AppState } from '../core/state.js';
import { DOMHelpers } from '../utils/dom-helpers.js';

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

        // Restore collapsed state
        if (AppState.sidebarCollapsed) {
            this.collapse();
        }

        // Listen to state changes
        AppState.subscribe('sidebarCollapsed', (collapsed) => {
            if (collapsed) {
                this.collapse();
            } else {
                this.expand();
            }
        });
    }

    /**
     * Toggle sidebar visibility
     */
    static toggle() {
        if (!this.#sidebarElement) return;

        const isCollapsed = this.#sidebarElement.classList.contains('collapsed');

        if (isCollapsed) {
            this.expand();
        } else {
            this.collapse();
        }
    }

    /**
     * Collapse sidebar
     */
    static collapse() {
        if (!this.#sidebarElement) return;

        this.#sidebarElement.classList.add('collapsed');
        AppState.setSidebarCollapsed(true);

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
     * Expand sidebar
     */
    static expand() {
        if (!this.#sidebarElement) return;

        this.#sidebarElement.classList.remove('collapsed');
        AppState.setSidebarCollapsed(false);

        // Update button
        const btn = this.#sidebarElement.querySelector('.btn-collapse');
        if (btn) {
            btn.innerHTML = '◄ Hide Sidebar';
            btn.title = 'Hide Sidebar';
        }

        // Invalidate map size after animation
        this.#invalidateMapAfterDelay();
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
        if (!container) return;

        container.innerHTML = '';

        if (!depots || depots.length === 0) {
            container.innerHTML = '<div class="empty-message">No depots available</div>';
            return;
        }

        depots.forEach(depot => {
            const depotItem = this.#createDepotItem(depot);
            container.appendChild(depotItem);
        });
    }

    /**
     * Update fleet info
     * @param {Object} fleetStats - Fleet statistics
     */
    static updateFleetInfo(fleetStats = null) {
        const stats = fleetStats || AppState.fleetStats;

        DOMHelpers.setText('fleet-total', `${stats.available} / ${stats.total} vehicles`);
        DOMHelpers.setText('fleet-in-use', `${stats.inUse} vehicles`);
        DOMHelpers.setText('fleet-capacity', `${stats.totalCapacity} kg`);

        const availablePercent = stats.totalCapacity > 0
            ? Math.round((stats.availableCapacity / stats.totalCapacity) * 100)
            : 0;

        DOMHelpers.setText('fleet-available', `${stats.availableCapacity} kg (${availablePercent}%)`);
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
        // Call global map function (from map.js)
        if (typeof centerMapToDepot === 'function') {
            centerMapToDepot(lat, lng);
        } else {
            console.warn('centerMapToDepot function not found');
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

// Export for global access
if (typeof window !== 'undefined') {
    window.Sidebar = Sidebar;
}

// Backward compatibility
window.toggleSidebar = () => Sidebar.toggle();