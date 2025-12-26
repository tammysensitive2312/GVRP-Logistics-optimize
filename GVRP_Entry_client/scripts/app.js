/**
 * VRP System - Main Application Logic
 * Manages screens, forms, and business logic
 */

import { Sidebar } from './components/UI Components/sidebar.js';
import { Navbar } from "./components/UI Components/navbar.js";
import { ResizableDivider } from './components/UI Components/resizable-divider.js';
import { OrdersTable } from './components/UI Components/orders-table.js';
import { OrdersFilters } from './components/UI Components/orders-filters.js';

import { BackgroundJobModal } from "./components/Form Components/background-job-modal.js";
import { JobMonitoringModal } from "./components/Form Components/job-monitoring-modal.js";
import { RoutePlanningModal } from "./components/Form Components/route-planning-modal.js";
import { DepotForm } from './components/Form Components/depot-form.js';
import { VehicleCard } from './components/Form Components/vehicle-card.js';
import { VehicleTypeForm } from './components/Form Components/vehicle-type-form.js';
import { FleetForm } from './components/Form Components/fleet-form.js';
import { ImportModal } from './components/Form Components/import-modal.js';
import { EditOrderModal } from './components/Form Components/edit-order-modal.js';

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

Router.onScreenActivated(Router.SCREENS.VEHICLE_TYPE_SETUP, () => {
    setTimeout(() => {
        VehicleTypeForm.init();
        VehicleTypeForm.loadVehicleTypes();
    }, 100);
});

// Fleet Setup screen
Router.onScreenActivated(Router.SCREENS.FLEET_SETUP, async () => {
    await FleetForm.loadDepotsAndVehicleType();
});

// Main screen
Router.onScreenActivated(Router.SCREENS.MAIN, async () => {
    setTimeout(async () => {
        MainMap.init();
        await loadMainScreenData();
    }, 100);
});

AppState.subscribe('vehicleCount', (newCount) => {
    updateFleetSummary();
});

AppState.subscribe('selectedOrders', () => {
    updatePlanRoutesButton();
});
AppState.subscribe('selectedVehicles', () => {
    updatePlanRoutesButton();
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

    await checkForRunningJobs();

    // Initialize event listeners
    initEventListeners();

    console.log('VRP System ready!');
});

/**
 * Check for running jobs on startup
 */
async function checkForRunningJobs() {
    try {
        const runningJob = await getCurrentRunningJob();
        if (runningJob) {
            console.log('Found running job:', runningJob);
            Toast.info(`You have a running job #${runningJob.id}`);
            // Optionally auto-open monitoring modal
            // JobMonitoringModal.open(runningJob, { enablePolling: true });
        }
    } catch (error) {
        console.error('Failed to check running jobs:', error);
    }
}

/**
 * Open Route Planning Modal
 */
function openRoutePlanningModal() {
    const selectedOrders = AppState.selectedOrdersCount;
    const selectedVehicles = AppState.selectedVehicles.size;
    if (selectedOrders === 0) {
        Toast.error('Please select at least 1 order');
        return;
    }
    if (selectedVehicles === 0) {
        Toast.error('Please select at least 1 vehicle');
        return;
    }

    RoutePlanningModal.open();
}

/**
 * View job history
 */
async function viewJobHistory() {
    Loading.show('Loading job history...');
    try {
        const jobs = await getJobHistory(20);
        if (jobs.length === 0) {
            Toast.info('No job history found');
            return;
        }
// TODO: Open job history modal/screen
        console.log('Job history:', jobs);
        Toast.info('Job history view - Coming soon!');
    } catch (error) {
        console.error('Failed to load job history:', error);
        Toast.error('Failed to load job history');
    } finally {
        Loading.hide();
    }
}

function updatePlanRoutesButton() {
    const planButton = document.getElementById('btn-plan-routes');
    const selectedCount = document.getElementById('selected-count');
    if (planButton) {
        const hasOrders = AppState.selectedOrdersCount > 0;
        const hasVehicles = AppState.selectedVehicles.size > 0;

        planButton.disabled = !(hasOrders && hasVehicles);
    }
    if (selectedCount) {
        selectedCount.textContent = AppState.selectedOrdersCount;
    }
}

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
            userName.textContent = user.username;
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
        } else if (!setupStatus.hasVehicleTypes) {
            Router.goTo(Router.SCREENS.VEHICLE_TYPE_SETUP);
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
        const [depots, vehicleTypes, fleet] = await Promise.all([
            getDepots(),
            getVehicleTypes(),
            getFleet()
        ]);

        const totalVehicleCount = fleet ?
            fleet.reduce((sum, f) => sum + f.vehicle_count, 0) : 0;

        const hasDepots = depots && depots.length > 0;
        const hasVehicleTypes = vehicleTypes && vehicleTypes.length > 0;
        const hasFleet = totalVehicleCount > 0;

        return {
            complete: hasDepots && hasVehicleTypes && hasFleet,
            hasDepots,
            hasVehicleTypes,
            hasFleet,
            depots,
            vehicleTypes,
            fleet
        };
    } catch (error) {
        console.error('Setup check failed:', error);
        return {
            complete: false,
            hasDepots: false,
            hasVehicleTypes: false,
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
    VehicleTypeForm.init();
    FleetForm.init();
    ImportModal.init();
    EditOrderModal.init();
    RoutePlanningModal.init();
    JobMonitoringModal.init();
    BackgroundJobModal.init();

    Sidebar.init();
    Navbar.init();
    ResizableDivider.init();
    OrdersTable.init();
    OrdersFilters.init();
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

        const apiResponse = await getVehicle();
        const vehicles = apiResponse.content || [];
        Sidebar.updateVehiclesList(vehicles)
        AppState.setAllVehicles(vehicles);

        const today = new Date().toISOString().split('T')[0];
        AppState.setFilterDate(today);
        await OrdersTable.loadOrders();

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
    await OrdersTable.loadOrders();
}

/**
 * Manual refresh - Invalidate cache và reload data
 */
async function refreshAllData() {
    const branchId = getCurrentBranch();

    // Show loading
    Loading.show('Refreshing data...');

    try {
        // Invalidate all caches
        invalidateQuery(QueryKeys.orders.all(branchId));
        invalidateQuery(QueryKeys.vehicles.all(branchId));
        invalidateQuery(QueryKeys.depots.all(branchId));

        // Reload orders
        await loadOrders();

        // Reload vehicles
        const apiResponse = await getVehicle();
        const vehicles = apiResponse.content || [];
        Sidebar.updateVehiclesList(vehicles);
        AppState.setAllVehicles(vehicles);

    } catch (error) {
        console.error('Refresh failed:', error);
        Toast.error('Failed to refresh data');
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

window.exportOrders = exportOrders;
window.loadOrders = loadOrders;
window.openAddOrderModal = openAddOrderModal;
window.refreshAllData = refreshAllData;
window.openRoutePlanningModal = openRoutePlanningModal;
window.viewJobHistory = viewJobHistory;
window.updatePlanRoutesButton = updatePlanRoutesButton;

console.log('App.js loaded successfully!');