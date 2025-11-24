/**
 * Centralized State Management
 * Single source of truth for application state
 */

export class AppState {
    // Private state
    static #state = {
        // Navigation
        currentScreen: 'screen-main',

        // User & Auth
        currentUser: null,
        currentBranch: null,

        // Depot & Fleet
        availableDepots: [],
        fleetInfo: null,

        // Vehicles
        allVehicles: [],
        vehicleCount: 0,
        selectedVehicles: new Set(),

        // Orders
        allOrders: [],
        filteredOrders: [],
        selectedOrders: new Set(),

        // Filters
        filters: {
            date: new Date().toISOString().split('T')[0],
            status: '',
            priority: '',
            search: ''
        },

        // UI State
        sidebarCollapsed: false,
        loading: false
    };

    // State change listeners
    static #listeners = new Map();

    /**
     * Get entire state (for debugging)
     * @returns {Object}
     */
    static getState() {
        return { ...this.#state };
    }

    // ============================================
    // GETTERS
    // ============================================

    static get currentScreen() {
        return this.#state.currentScreen;
    }

    static get currentUser() {
        return this.#state.currentUser;
    }

    static get currentBranch() {
        return this.#state.currentBranch;
    }

    static get availableDepots() {
        return [...this.#state.availableDepots];
    }

    static get fleetInfo() {
        return this.#state.fleetInfo;
    }

    static get allVehicles() {
        return [...this.#state.allVehicles];
    }

    static get selectedVehicles() {
        return new Set(this.#state.selectedVehicles);
    }

    static get vehicleCount() {
        return this.#state.vehicleCount;
    }

    static get allOrders() {
        return [...this.#state.allOrders];
    }

    static get filteredOrders() {
        return [...this.#state.filteredOrders];
    }

    static get selectedOrders() {
        return new Set(this.#state.selectedOrders);
    }

    static get selectedOrdersCount() {
        return this.#state.selectedOrders.size;
    }

    static get filters() {
        return { ...this.#state.filters };
    }

    static get sidebarCollapsed() {
        return this.#state.sidebarCollapsed;
    }

    static get loading() {
        return this.#state.loading;
    }

    // ============================================
    // SETTERS (with state change notifications)
    // ============================================

    static setCurrentScreen(screen) {
        const oldScreen = this.#state.currentScreen;
        this.#state.currentScreen = screen;
        this.#notify('currentScreen', screen, oldScreen);
    }

    static setCurrentUser(user) {
        this.#state.currentUser = user;
        this.#notify('currentUser', user);
    }

    static setCurrentBranch(branch) {
        this.#state.currentBranch = branch;
        this.#notify('currentBranch', branch);
    }

    static setAvailableDepots(depots) {
        this.#state.availableDepots = depots || [];
        this.#notify('availableDepots', this.#state.availableDepots);
    }

    static setFleetInfo(fleet) {
        this.#state.fleetInfo = fleet;
        this.#notify('fleetInfo', fleet);
    }

    static setAllVehicles(vehicles) {
        this.#state.allVehicles = vehicles || [];
        this.#notify('allVehicles', this.#state.allVehicles);
    }

    static selectVehicle(vehicleId) {
        this.#state.selectedVehicles.add(vehicleId);
        this.#notify('selectedVehicles', this.selectedVehicles);
    }

    static deselectVehicle(vehicleId) {
        this.#state.selectedVehicles.delete(vehicleId);
        this.#notify('selectedVehicles', this.selectedVehicles);
    }

    static toggleVehicleSelection(vehicleId) {
        if (this.#state.selectedVehicles.has(vehicleId)) {
            this.deselectVehicle(vehicleId);
        } else {
            this.selectVehicle(vehicleId);
        }
    }

    static isVehicleSelected(vehicleId) {
        return this.#state.selectedVehicles.has(vehicleId);
    }

    static deselectAllVehicles() {
        if (this.#state.selectedVehicles.size > 0) {
            this.#state.selectedVehicles.clear();
            this.#notify('selectedVehicles', this.selectedVehicles);
        }
    }

    static setVehicleCount(count) {
        this.#state.vehicleCount = count;
        this.#notify('vehicleCount', count);
    }

    static incrementVehicleCount() {
        this.#state.vehicleCount++;
        this.#notify('vehicleCount', this.#state.vehicleCount);
    }

    static decrementVehicleCount() {
        if (this.#state.vehicleCount > 0) {
            this.#state.vehicleCount--;
            this.#notify('vehicleCount', this.#state.vehicleCount);
        }
    }

    static setOrders(orders) {
        this.#state.allOrders = orders || [];
        this.#state.filteredOrders = [...this.#state.allOrders];
        this.#notify('allOrders', this.#state.allOrders);
    }

    static setFilteredOrders(orders) {
        this.#state.filteredOrders = orders || [];
        this.#notify('filteredOrders', this.#state.filteredOrders);
    }

    static setFilters(filters) {
        this.#state.filters = { ...this.#state.filters, ...filters };
        this.#notify('filters', this.#state.filters);
    }

    static setSidebarCollapsed(collapsed) {
        this.#state.sidebarCollapsed = collapsed;
        this.#notify('sidebarCollapsed', collapsed);
    }

    static setLoading(loading) {
        this.#state.loading = loading;
        this.#notify('loading', loading);
    }

    // ============================================
    // ORDER SELECTION METHODS
    // ============================================

    static selectOrder(orderId) {
        this.#state.selectedOrders.add(orderId);
        this.#notify('selectedOrders', this.selectedOrders);
    }

    static deselectOrder(orderId) {
        this.#state.selectedOrders.delete(orderId);
        this.#notify('selectedOrders', this.selectedOrders);
    }

    static toggleOrderSelection(orderId) {
        if (this.#state.selectedOrders.has(orderId)) {
            this.deselectOrder(orderId);
        } else {
            this.selectOrder(orderId);
        }
    }

    static selectAllOrders() {
        const oldValue = new Set(this.#state.selectedOrders);

        this.#state.filteredOrders.forEach(order => {
            this.#state.selectedOrders.add(order.id);
        });
        this.#notify('selectedOrders', this.selectedOrders, oldValue);
    }

    static deselectAllOrders() {
        const oldValue = new Set(this.#state.selectedOrders);

        this.#state.selectedOrders.clear();
        this.#notify('selectedOrders', this.selectedOrders, oldValue);
    }

    static isOrderSelected(orderId) {
        return this.#state.selectedOrders.has(orderId);
    }

    // ============================================
    // FILTER METHODS
    // ============================================

    static applyFilters() {
        const { status, priority, search } = this.#state.filters;

        this.#state.filteredOrders = this.#state.allOrders.filter(order => {
            // Status filter
            if (status && order.status !== status) {
                return false;
            }

            // Priority filter
            if (priority) {
                if (priority === 'high' && (!order.priority || order.priority > 3)) return false;
                if (priority === 'medium' && (!order.priority || order.priority < 4 || order.priority > 6)) return false;
                if (priority === 'low' && (!order.priority || order.priority < 7)) return false;
            }

            // Search filter
            if (search) {
                const searchLower = search.toLowerCase();
                const searchableText = `${order.orderCode} ${order.customerName} ${order.address}`.toLowerCase();
                if (!searchableText.includes(searchLower)) {
                    return false;
                }
            }

            return true;
        });

        this.#notify('filteredOrders', this.#state.filteredOrders);
    }

    static clearFilters() {
        this.#state.filters = {
            date: this.#state.filters.date, // Keep date
            status: '',
            priority: '',
            search: ''
        };
        this.#state.filteredOrders = [...this.#state.allOrders];
        this.#notify('filters', this.#state.filters);
        this.#notify('filteredOrders', this.#state.filteredOrders);
    }

    static setFilterDate(date) {
        this.#state.filters.date = date;
        this.#notify('filters', this.#state.filters);
    }

    static setFilterStatus(status) {
        this.#state.filters.status = status;
        this.applyFilters();
    }

    static setFilterPriority(priority) {
        this.#state.filters.priority = priority;
        this.applyFilters();
    }

    static setFilterSearch(search) {
        this.#state.filters.search = search;
        this.applyFilters();
    }

    // ============================================
    // COMPUTED PROPERTIES
    // ============================================

    static get orderStats() {
        return {
            total: this.#state.allOrders.length,
            scheduled: this.#state.allOrders.filter(o => o.status === 'SCHEDULED').length,
            onRoute: this.#state.allOrders.filter(o => o.status === 'ON_ROUTE').length,
            completed: this.#state.allOrders.filter(o => o.status === 'COMPLETED').length,
            failed: this.#state.allOrders.filter(o => o.status === 'FAILED').length
        };
    }

    static get fleetStats() {
        if (!this.#state.fleetInfo) {
            return {
                total: 0,
                available: 0,
                inUse: 0,
                totalCapacity: 0,
                availableCapacity: 0
            };
        }

        // Calculate from fleet info
        const vehicles = this.#state.fleetInfo.vehicles || [];
        const totalCapacity = vehicles.reduce((sum, v) => sum + (v.capacity || 0), 0);

        return {
            total: vehicles.length,
            available: vehicles.length, // TODO: Calculate from active routes
            inUse: 0, // TODO: Calculate from active routes
            totalCapacity,
            availableCapacity: totalCapacity // TODO: Subtract used capacity
        };
    }

    // ============================================
    // STATE PERSISTENCE (LocalStorage)
    // ============================================

    static saveToLocalStorage() {
        try {
            const stateToSave = {
                currentScreen: this.#state.currentScreen,
                filters: this.#state.filters,
                sidebarCollapsed: this.#state.sidebarCollapsed
            };
            localStorage.setItem('vrp_app_state', JSON.stringify(stateToSave));
        } catch (error) {
            console.warn('Failed to save state to localStorage:', error);
        }
    }

    static loadFromLocalStorage() {
        try {
            const saved = localStorage.getItem('vrp_app_state');
            if (saved) {
                const parsed = JSON.parse(saved);

                // Restore last screen
                if (parsed.currentScreen) {
                    this.#state.currentScreen = parsed.currentScreen;
                }

                if (parsed.filters) {
                    this.#state.filters = { ...this.#state.filters, ...parsed.filters };
                }
                if (parsed.sidebarCollapsed !== undefined) {
                    this.#state.sidebarCollapsed = parsed.sidebarCollapsed;
                }
            }
        } catch (error) {
            console.warn('Failed to load state from localStorage:', error);
        }
    }

    /**
     * Get last saved screen from localStorage
     * @returns {string|null}
     */
    static getLastScreen() {
        try {
            const saved = localStorage.getItem('vrp_app_state');
            if (saved) {
                const parsed = JSON.parse(saved);
                return parsed.currentScreen || null;
            }
        } catch (error) {
            console.warn('Failed to get last screen:', error);
        }
        return null;
    }

    static clearLocalStorage() {
        try {
            localStorage.removeItem('vrp_app_state');
        } catch (error) {
            console.warn('Failed to clear localStorage:', error);
        }
    }

    // ============================================
    // STATE LISTENERS (Observer Pattern)
    // ============================================

    /**
     * Subscribe to state changes
     * @param {string} key - State key to watch (e.g., 'selectedOrders')
     * @param {Function} callback - Callback function (newValue, oldValue) => {}
     * @returns {Function} Unsubscribe function
     */
    static subscribe(key, callback) {
        if (!this.#listeners.has(key)) {
            this.#listeners.set(key, []);
        }
        this.#listeners.get(key).push(callback);

        // Return unsubscribe function
        return () => {
            const callbacks = this.#listeners.get(key);
            const index = callbacks.indexOf(callback);
            if (index > -1) {
                callbacks.splice(index, 1);
            }
        };
    }

    /**
     * Notify listeners of state change
     * @private
     */
    static #notify(key, newValue, oldValue) {
        const callbacks = this.#listeners.get(key);
        if (callbacks) {
            callbacks.forEach(callback => {
                try {
                    callback(newValue, oldValue);
                } catch (error) {
                    console.error(`Error in state listener for "${key}":`, error);
                }
            });
        }

        // Auto-save certain state changes
        if (['currentScreen', 'filters', 'sidebarCollapsed'].includes(key)) {
            this.saveToLocalStorage();
        }
    }

    // ============================================
    // RESET METHODS
    // ============================================

    static resetOrders() {
        this.#state.allOrders = [];
        this.#state.filteredOrders = [];
        this.#state.selectedOrders.clear();
        this.#notify('allOrders', []);
        this.#notify('filteredOrders', []);
        this.#notify('selectedOrders', new Set());
    }

    static resetFilters() {
        this.clearFilters();
    }

    static resetAll() {
        this.#state = {
            currentScreen: 'screen-depot-setup',
            currentUser: null,
            currentBranch: null,
            availableDepots: [],
            fleetInfo: null,
            vehicleCount: 0,
            allOrders: [],
            filteredOrders: [],
            selectedOrders: new Set(),
            filters: {
                date: new Date().toISOString().split('T')[0],
                status: '',
                priority: '',
                search: ''
            },
            sidebarCollapsed: false,
            loading: false
        };
        this.clearLocalStorage();
        this.#notify('reset', true);
    }
}

// For debugging in console
if (typeof window !== 'undefined') {
    window.AppState = AppState;
}