/**
 * Screen Restoration Logic
 * Handles proper screen initialization after page refresh
 */

import { AppState } from './core/state.js';
import { Router } from './core/router.js';

export class ScreenRestoration {
    /**
     * Initialize app and restore last screen
     */
    static async init() {
        // Load saved state
        AppState.loadFromLocalStorage();

        // Check setup status
        const setupStatus = await this.checkSetupStatus();

        if (!setupStatus.hasDepots || !setupStatus.hasFleet) {
            // Setup incomplete - force to setup screens
            return this.goToSetupScreen(setupStatus);
        }

        // Setup complete - restore last screen or go to main
        return this.restoreLastScreen();
    }

    /**
     * Check if setup is complete
     * @returns {Promise<Object>} Setup status
     */
    static async checkSetupStatus() {
        try {
            const [depots, fleet] = await Promise.all([
                getDepots(),
                getFleet()
            ]);

            return {
                hasDepots: depots && depots.length > 0,
                hasFleet: fleet && fleet.vehicleCount > 0,
                depots,
                fleet
            };
        } catch (error) {
            console.error('Setup check failed:', error);
            return {
                hasDepots: false,
                hasFleet: false,
                depots: [],
                fleet: null
            };
        }
    }

    /**
     * Go to appropriate setup screen
     */
    static goToSetupScreen(setupStatus) {
        if (!setupStatus.hasDepots) {
            console.log('No depots found - going to depot setup');
            Router.goTo(Router.SCREENS.DEPOT_SETUP);
        } else if (!setupStatus.hasFleet) {
            console.log('No fleet found - going to fleet setup');
            Router.goTo(Router.SCREENS.FLEET_SETUP);
        }
    }

    /**
     * Restore last screen user was on
     */
    static restoreLastScreen() {
        const lastScreen = AppState.getLastScreen();

        // Valid screens to restore
        const validScreens = [
            Router.SCREENS.DEPOT_SETUP,
            Router.SCREENS.FLEET_SETUP,
            Router.SCREENS.MAIN
        ];

        if (lastScreen && validScreens.includes(lastScreen)) {
            console.log('Restoring last screen:', lastScreen);
            Router.goTo(lastScreen);
        } else {
            // Default to main screen if no valid last screen
            console.log('No valid last screen - going to main');
            Router.goTo(Router.SCREENS.MAIN);
        }
    }

    /**
     * Clear saved screen (useful for logout)
     */
    static clearSavedScreen() {
        AppState.clearLocalStorage();
    }
}

// Usage in app.js:
// import { ScreenRestoration } from './screen-restoration.js';
//
// document.addEventListener('DOMContentLoaded', async function() {
//     if (!requireAuth()) return;
//
//     displayUserInfo();
//     await ScreenRestoration.init();
//     initEventListeners();
// });