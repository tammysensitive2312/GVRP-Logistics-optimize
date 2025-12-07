/**
 * Toast Notification Module
 * Simple, dependency-free toast notifications
 */

export class Toast {
    /**
     * Show a toast notification
     * @param {string} message - The message to display
     * @param {string} type - Type of toast: 'success', 'error', or ''
     * @param {number} duration - Duration in milliseconds (default: 3000)
     */
    static show(message, type = '', duration = 3000) {
        const toast = document.getElementById('toast');

        if (!toast) {
            console.error('Toast element not found');
            return;
        }

        toast.textContent = message;
        toast.className = `toast show ${type}`;

        // Auto-hide after duration
        setTimeout(() => {
            toast.classList.remove('show');
        }, duration);
    }

    /**
     * Show success toast
     * @param {string} message
     */
    static success(message, duration) {
        this.show(message, 'success', duration);
    }

    /**
     * Show error toast
     * @param {string} message
     */
    static error(message, duration) {
        this.show(message, 'error', duration);
    }

    /**
     * Show info toast (neutral)
     * @param {string} message
     */
    static info(message, duration) {
        this.show(message, '', duration);
    }
}

// Backward compatibility - Keep global function for now
window.showToast = (message, type) => Toast.show(message, type);