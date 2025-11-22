/**
 * VRP System - Main Application Logic
 * Manages screens, forms, and business logic
 */

import { Sidebar } from './components/UI Components/sidebar.js';
import { ResizableDivider } from './components/UI Components/resizable-divider.js';
import { OrdersTable } from './components/UI Components/orders-table.js';
import { OrdersFilters } from './components/UI Components/orders-filters.js';

import { DepotForm } from './components/Form Components/depot-form.js';
import { VehicleCard } from './components/Form Components/vehicle-card.js';
import { FleetForm } from './components/Form Components/fleet-form.js';
import { ImportModal } from './components/Form Components/import-modal.js';

import { DepotMap } from './components/Map Components/depot-map.js';
import { MainMap } from './components/Map Components/main-map.js';

import { AppState } from './core/state.js';
import { Router } from './core/router.js';

import { Toast } from './utils/toast.js';
import { Loading } from './utils/loading.js';
import { Validator } from './utils/validation.js';
import { DOMHelpers } from './utils/dom-helpers.js';

// Depot Setup screen
Router.onScreenActivated(Router.SCREENS.DEPOT_SETUP, () => {
    setTimeout(() => DepotMap.init(), 100);
});

// Fleet Setup screen
Router.onScreenActivated(Router.SCREENS.FLEET_SETUP, async () => {
    await FleetForm.loadDepots();
});

// Main screen
Router.onScreenActivated(Router.SCREENS.MAIN, async () => {
    setTimeout(() => {
        MainMap.init();
        loadMainScreenData();
    }, 100);
});

AppState.subscribe('vehicleCount', (newCount) => {
    updateFleetSummary();
});

document.addEventListener('DOMContentLoaded', async function() {
    console.log('VRP System initializing...');

    // Check authentication
    if (!requireAuth()) {
        return;
    }

    // Display user info in navbar
    displayUserInfo();

    // Initialize app with screen restoration
    await initializeApp();

    // Initialize event listeners
    initEventListeners();

    console.log('VRP System ready!');
});

/**
 * Display user info in navbar
 */
function displayUserInfo() {
    const user = getCurrentUser();
    const branch = getCurrentBranch();

    if (user && branch) {
        // Update brand name with branch
        const brandName = document.querySelector('.brand-name');
        if (brandName) {
            brandName.textContent = `VRP System™ - ${branch.name}`;
        }

        // Update user info
        const userName = document.querySelector('.user-name');
        if (userName) {
            userName.textContent = user.fullName || user.username;
        }
    }
}

/**
 * Handle logout
 */
function handleLogout() {
    if (confirm('Bạn có chắc chắn muốn đăng xuất?')) {
        // Clear saved state
        AppState.clearLocalStorage();

        // Logout
        logout();
    }
}

/**
 * Initialize app with screen restoration
 */
async function initializeApp() {
    // Load saved state (including last screen)
    AppState.loadFromLocalStorage();

    // Check setup status
    const setupStatus = await checkSetupStatus();

    if (!setupStatus.complete) {
        // Setup incomplete - go to setup screens
        if (!setupStatus.hasDepots) {
            Router.goTo(Router.SCREENS.DEPOT_SETUP);
        } else if (!setupStatus.hasFleet) {
            Router.goTo(Router.SCREENS.FLEET_SETUP);
        }
    } else {
        // Setup complete - restore last screen
        restoreLastScreen();
    }
}

/**
 * Check if setup is complete
 */
async function checkSetupStatus() {
    try {
        const [depots, fleet] = await Promise.all([
            getDepots(),
            getFleet()
        ]);

        const totalVehicleCount = fleet ? fleet.reduce((sum, currentFleet) => sum + currentFleet.vehicle_count, 0) : 0;
        const hasDepots = depots && depots.length > 0;
        const hasFleet = totalVehicleCount > 0;

        return {
            complete: hasDepots && hasFleet,
            hasDepots,
            hasFleet,
            depots,
            fleet
        };
    } catch (error) {
        console.error('Setup check failed:', error);
        return {
            complete: false,
            hasDepots: false,
            hasFleet: false
        };
    }
}

/**
 * Restore last screen user was on
 */
function restoreLastScreen() {
    const lastScreen = AppState.getLastScreen();

    // Valid screens to restore
    const validScreens = [
        Router.SCREENS.DEPOT_SETUP,
        Router.SCREENS.FLEET_SETUP,
        Router.SCREENS.MAIN
    ];

    if (lastScreen && validScreens.includes(lastScreen)) {
        Router.goTo(lastScreen);
    } else {
        Router.goTo(Router.SCREENS.MAIN);
    }
}

/**
 * Initialize all event listeners
 */
function initEventListeners() {
    DepotForm.init();
    FleetForm.init();
    ImportModal.init();

    Sidebar.init();
    ResizableDivider.init();
    OrdersTable.init();
    OrdersFilters.init();
}


/**
 * Handle screen activation
 */
function onScreenActivated(screenId) {
    switch(screenId) {
        case 'screen-depot-setup':
            setTimeout(() => initDepotSetupMap(), 100);
            break;
        case 'screen-fleet-setup':
            loadDepotsForFleet();
            break;
        case 'screen-main':
            setTimeout(() => {
                initMainMap();
                loadMainScreenData();
            }, 100);
            break;
    }
}

/**
 * Load data for main screen
 */
async function loadMainScreenData() {
    Loading.show();

    try {
        const depots = await getDepots();
        if (depots && depots.length > 0) {
            MainMap.loadDepots(depots);
            Sidebar.updateDepotsList(depots);
            AppState.setAvailableDepots(depots);
        }

        const today = new Date().toISOString().split('T')[0];
        AppState.setFilterDate(today);
        await loadOrders();

    } catch (error) {
        console.error('Failed to load main screen data:', error);
    } finally {
        Loading.hide();
    }
}

/**
 * Load orders from API
 */
async function loadOrders() {
    const date = AppState.filters.date;
    Loading.show();

    try {
        const apiResponse = await getOrders(date);
        const orders = apiResponse.content || [];
        AppState.setOrders(orders);

        MainMap.loadOrders(AppState.filteredOrders);

        OrdersTable.render();

        Sidebar.updateStatsCards();

    } catch (error) {
        console.error('Failed to load orders:', error);
        AppState.setOrders([]);
        OrdersTable.render();
    } finally {
        Loading.hide();
    }
}

// Placeholder functions for incomplete features
function openAddOrderModal() {
    Toast.success('Add Order feature - Coming soon!');
}

function exportOrders() {
    Toast.success('Export feature - Coming soon!');
}

function deleteSelectedOrders() {
    if (AppState.selectedOrdersCount === 0) {
        Toast.error('No orders selected');
        return;
    }
    Toast.success(`Delete ${selectedOrders.size} orders - Coming soon!`);
}

function bulkEditOrders() {
    Toast.success('Bulk Edit feature - Coming soon!');
}

function viewOrderDetails(orderId) {
    Toast.success(`View order #${orderId} - Coming soon!`);
}

function previousPage() {
    Toast.success('Pagination - Coming soon!');
}

function nextPage() {
    Toast.success('Pagination - Coming soon!');
}

window.exportOrders = exportOrders;
window.previousPage = previousPage;
window.nextPage = nextPage;
window.loadOrders = loadOrders;
window.openAddOrderModal = openAddOrderModal;
window.viewOrderDetails = viewOrderDetails;

console.log('App.js loaded successfully!');