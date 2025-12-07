/**
 * Orders Filters Component
 * Manages order filtering logic and UI
 */

import { AppState } from '../../core/state.js';
import { DOMHelpers } from '../../utils/dom-helpers.js';

export class OrdersFilters {
    static #filterElements = {
        date: null,
        status: null,
        priority: null,
        search: null
    };

    /**
     * Initialize filters
     */
    static init() {
        this.#filterElements.date = document.getElementById('filter-date');
        this.#filterElements.status = document.getElementById('filter-status');
        this.#filterElements.priority = document.getElementById('filter-priority');
        this.#filterElements.search = document.getElementById('filter-search');

        // Set initial values from state
        this.#restoreFiltersFromState();

        // Attach event listeners
        this.#attachEventListeners();
    }

    /**
     * Restore filter values from state
     * @private
     */
    static #restoreFiltersFromState() {
        const filters = AppState.filters;

        if (this.#filterElements.date) {
            this.#filterElements.date.value = filters.date;
        }
        if (this.#filterElements.status) {
            this.#filterElements.status.value = filters.status;
        }
        if (this.#filterElements.priority) {
            this.#filterElements.priority.value = filters.priority;
        }
        if (this.#filterElements.search) {
            this.#filterElements.search.value = filters.search;
        }
    }

    /**
     * Attach event listeners
     * @private
     */
    static #attachEventListeners() {
        // Date change
        if (this.#filterElements.date) {
            this.#filterElements.date.addEventListener('change', () => {
                this.onDateChange();
            });
        }

        // Status change
        if (this.#filterElements.status) {
            this.#filterElements.status.addEventListener('change', () => {
                this.apply();
            });
        }

        // Priority change
        if (this.#filterElements.priority) {
            this.#filterElements.priority.addEventListener('change', () => {
                this.apply();
            });
        }

        // Search input (with debounce)
        if (this.#filterElements.search) {
            let debounceTimer;
            this.#filterElements.search.addEventListener('input', () => {
                clearTimeout(debounceTimer);
                debounceTimer = setTimeout(() => {
                    this.apply();
                }, 300); // 300ms debounce
            });
        }
    }

    /**
     * Apply all filters
     */
    static apply() {
        // Get current filter values
        const filters = {
            status: this.#filterElements.status?.value || '',
            priority: this.#filterElements.priority?.value || '',
            search: this.#filterElements.search?.value || ''
        };

        // Update state
        AppState.setFilters(filters);

        // Trigger filter application
        this.#notifyFiltersChanged();
    }

    /**
     * Clear all filters (except date)
     */
    static clear() {
        // Clear UI
        if (this.#filterElements.status) {
            this.#filterElements.status.value = '';
        }
        if (this.#filterElements.priority) {
            this.#filterElements.priority.value = '';
        }
        if (this.#filterElements.search) {
            this.#filterElements.search.value = '';
        }

        // Clear state
        AppState.clearFilters();

        // Trigger update
        this.#notifyFiltersChanged();
    }

    /**
     * Handle date change (requires reloading orders)
     */
    static onDateChange() {
        const date = this.#filterElements.date?.value;

        if (date) {
            AppState.setFilterDate(date);

            // Trigger orders reload
            if (typeof window.loadOrders === 'function') {
                window.loadOrders();
            }
        }
    }

    /**
     * Filter by status (from stats card click)
     * @param {string} status - Status to filter ('SCHEDULED', 'COMPLETED', etc.)
     */
    static filterByStatus(status) {
        if (this.#filterElements.status) {
            if (status === 'ALL') {
                this.#filterElements.status.value = '';
            } else {
                this.#filterElements.status.value = status;
            }
        }

        AppState.setFilterStatus(status === 'ALL' ? '' : status);
        this.#notifyFiltersChanged();
    }

    /**
     * Set specific filter value
     * @param {string} filterName - 'status', 'priority', or 'search'
     * @param {string} value
     */
    static setFilter(filterName, value) {
        if (this.#filterElements[filterName]) {
            this.#filterElements[filterName].value = value;
        }

        const filters = {};
        filters[filterName] = value;
        AppState.setFilters(filters);

        this.#notifyFiltersChanged();
    }

    /**
     * Get current filter values
     * @returns {Object}
     */
    static getFilters() {
        return {
            date: this.#filterElements.date?.value || '',
            status: this.#filterElements.status?.value || '',
            priority: this.#filterElements.priority?.value || '',
            search: this.#filterElements.search?.value || ''
        };
    }

    /**
     * Notify that filters have changed
     * @private
     */
    static #notifyFiltersChanged() {
        // Dispatch custom event for other components to listen
        const event = new CustomEvent('filters-changed', {
            detail: this.getFilters()
        });
        document.dispatchEvent(event);

        if (typeof MainMap !== 'undefined') {
            MainMap.loadOrders(AppState.filteredOrders);
        }
    }

    /**
     * Check if any filters are active
     * @returns {boolean}
     */
    static hasActiveFilters() {
        const filters = this.getFilters();
        return !!(filters.status || filters.priority || filters.search);
    }

    /**
     * Get filter summary text
     * @returns {string}
     */
    static getFilterSummary() {
        const filters = this.getFilters();
        const parts = [];

        if (filters.status) {
            parts.push(`Status: ${filters.status}`);
        }
        if (filters.priority) {
            parts.push(`Priority: ${filters.priority}`);
        }
        if (filters.search) {
            parts.push(`Search: "${filters.search}"`);
        }

        return parts.length > 0 ? parts.join(', ') : 'No filters applied';
    }
}

// Export for global access
if (typeof window !== 'undefined') {
    window.OrdersFilters = OrdersFilters;
}

// Backward compatibility functions
window.applyFilters = () => {
    OrdersFilters.apply();
};

window.clearFilters = () => {
    OrdersFilters.clear();
};

window.filterOrdersByStatus = (status) => {
    OrdersFilters.filterByStatus(status);
};