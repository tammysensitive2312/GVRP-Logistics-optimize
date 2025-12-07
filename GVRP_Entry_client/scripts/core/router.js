/**
 * Router Module
 * Handles screen navigation and route management
 */

import { AppState } from './state.js';

export class Router {
    // Available screens
    static SCREENS = {
        DEPOT_SETUP: 'screen-depot-setup',
        FLEET_SETUP: 'screen-fleet-setup',
        MAIN: 'screen-main'
    };

    // Screen lifecycle hooks
    static #hooks = {
        beforeNavigate: [],
        afterNavigate: [],
        screenActivated: new Map()
    };

    /**
     * Navigate to a screen
     * @param {string} screenId - Screen ID to navigate to
     * @param {Object} data - Optional data to pass to screen
     */
    static async goTo(screenId, data = {}) {
        // Validate screen exists
        if (!this.#screenExists(screenId)) {
            console.error(`Screen "${screenId}" not found`);
            return;
        }

        const currentScreen = AppState.currentScreen;

        // Run beforeNavigate hooks
        const canNavigate = await this.#runBeforeNavigateHooks(currentScreen, screenId, data);
        if (!canNavigate) {
            console.log('Navigation cancelled by hook');
            return;
        }

        // Hide all screens
        this.#hideAllScreens();

        // Show target screen
        this.#showScreen(screenId);

        // Update state
        AppState.setCurrentScreen(screenId);

        // Run screen-specific activation logic
        await this.#activateScreen(screenId, data);

        // Run afterNavigate hooks
        await this.#runAfterNavigateHooks(currentScreen, screenId, data);
    }

    /**
     * Go back to previous screen
     */
    static goBack() {
        const currentScreen = AppState.currentScreen;

        // Define screen flow
        const backMap = {
            [this.SCREENS.FLEET_SETUP]: this.SCREENS.DEPOT_SETUP,
            [this.SCREENS.MAIN]: this.SCREENS.FLEET_SETUP
        };

        const previousScreen = backMap[currentScreen];
        if (previousScreen) {
            this.goTo(previousScreen);
        } else {
            console.warn('No previous screen defined');
        }
    }

    /**
     * Register screen activation handler
     * @param {string} screenId - Screen ID
     * @param {Function} handler - Activation handler function
     */
    static onScreenActivated(screenId, handler) {
        if (!this.#hooks.screenActivated.has(screenId)) {
            this.#hooks.screenActivated.set(screenId, []);
        }
        this.#hooks.screenActivated.get(screenId).push(handler);
    }

    /**
     * Register beforeNavigate hook
     * @param {Function} hook - Hook function (from, to, data) => boolean|Promise<boolean>
     */
    static beforeNavigate(hook) {
        this.#hooks.beforeNavigate.push(hook);
    }

    /**
     * Register afterNavigate hook
     * @param {Function} hook - Hook function (from, to, data) => void|Promise<void>
     */
    static afterNavigate(hook) {
        this.#hooks.afterNavigate.push(hook);
    }

    /**
     * Check if screen exists in DOM
     * @private
     */
    static #screenExists(screenId) {
        return !!document.getElementById(screenId);
    }

    /**
     * Hide all screens
     * @private
     */
    static #hideAllScreens() {
        document.querySelectorAll('.screen').forEach(screen => {
            screen.classList.remove('active');
        });
    }

    /**
     * Show specific screen
     * @private
     */
    static #showScreen(screenId) {
        const screen = document.getElementById(screenId);
        if (screen) {
            screen.classList.add('active');
        }
    }

    /**
     * Activate screen (run screen-specific logic)
     * @private
     */
    static async #activateScreen(screenId, data) {
        const handlers = this.#hooks.screenActivated.get(screenId);
        if (handlers) {
            for (const handler of handlers) {
                try {
                    await handler(data);
                } catch (error) {
                    console.error(`Error in screen activation handler for "${screenId}":`, error);
                }
            }
        }
    }

    /**
     * Run beforeNavigate hooks
     * @private
     * @returns {Promise<boolean>} - Can navigate?
     */
    static async #runBeforeNavigateHooks(from, to, data) {
        for (const hook of this.#hooks.beforeNavigate) {
            try {
                const result = await hook(from, to, data);
                if (result === false) {
                    return false;
                }
            } catch (error) {
                console.error('Error in beforeNavigate hook:', error);
                return false;
            }
        }
        return true;
    }

    /**
     * Run afterNavigate hooks
     * @private
     */
    static async #runAfterNavigateHooks(from, to, data) {
        for (const hook of this.#hooks.afterNavigate) {
            try {
                await hook(from, to, data);
            } catch (error) {
                console.error('Error in afterNavigate hook:', error);
            }
        }
    }

    /**
     * Switch between the main content tabs (Orders/Route/Timeline).
     * @param {string} targetTabId - ID of the tab to activate (e.g., 'orders-tab').
     */
    static switchContentTab(targetTabId) {
        document.querySelectorAll('.tab-content-item').forEach(content => {
            content.classList.remove('active');
        });

        const targetContent = document.getElementById(targetTabId);
        if (targetContent) {
            targetContent.classList.add('active');
        }

        document.querySelectorAll('.tab-btn').forEach(btn => {
            if (btn.getAttribute('data-tab') === targetTabId) {
                btn.classList.add('active');
            } else {
                btn.classList.remove('active');
            }
        });

        // Cần gọi hàm refresh map ở đây nếu map được đặt trong tab khác orders.
        // Ví dụ: map.invalidateSize();
    }

    /**
     * Get current screen
     * @returns {string}
     */
    static getCurrentScreen() {
        return AppState.currentScreen;
    }

    /**
     * Check if on specific screen
     * @param {string} screenId
     * @returns {boolean}
     */
    static isOnScreen(screenId) {
        return AppState.currentScreen === screenId;
    }
}

if (typeof window !== 'undefined') {
    window.Router = Router;
}

// Backward compatibility - Keep global function
window.goToScreen = (screenId) => Router.goTo(screenId);
window.switchContentTab = (tabId) => Router.switchContentTab(tabId);