/**
 * Loading Overlay Module
 * Manages loading state and overlay display
 */

export class Loading {
    static overlayElement = null;
    static loadingCount = 0; // Track multiple loading requests

    /**
     * Initialize the loading module
     */
    static init() {
        this.overlayElement = document.getElementById('loading-overlay');

        if (!this.overlayElement) {
            console.error('Loading overlay element not found');
        }
    }

    /**
     * Show loading overlay
     * @param {string} message - Optional custom loading message
     */
    static show(message = 'Đang xử lý...') {
        if (!this.overlayElement) {
            this.init();
        }

        this.loadingCount++;

        if (this.overlayElement) {
            // Update message if provided
            const textElement = this.overlayElement.querySelector('.loading-text');
            if (textElement && message) {
                textElement.textContent = message;
            }

            this.overlayElement.style.display = 'flex';
        }
    }

    /**
     * Hide loading overlay
     */
    static hide() {
        this.loadingCount = Math.max(0, this.loadingCount - 1);

        // Only hide when all loading requests are done
        if (this.loadingCount === 0 && this.overlayElement) {
            this.overlayElement.style.display = 'none';
        }
    }

    /**
     * Force hide loading (useful for error cases)
     */
    static forceHide() {
        this.loadingCount = 0;
        if (this.overlayElement) {
            this.overlayElement.style.display = 'none';
        }
    }

    /**
     * Check if loading is active
     * @returns {boolean}
     */
    static isActive() {
        return this.loadingCount > 0;
    }
}

// Backward compatibility - Keep global function for now
window.showLoading = (show) => {
    if (show) {
        Loading.show();
    } else {
        Loading.hide();
    }
};