import { DOMHelpers } from "../../utils/dom-helpers.js";

/**
 * Background Job Modal - For NORMAL/HIGH_QUALITY mode
 */
export class BackgroundJobModal {
    static #modal = null;

    static init() {
        this.#modal = document.getElementById('modal-background-job');
        if (!this.#modal) {
            console.warn('Background job modal not found');
        }
    }

    static open(job) {
        if (!this.#modal) return;

        DOMHelpers.setText('bg-job-id', `#${job.id}`);

        const userEmail = getCurrentUser()?.email || 'your registered email';
        DOMHelpers.setText('bg-job-email', userEmail);

        this.#modal.classList.add('active');
    }

    static close() {
        if (!this.#modal) return;
        this.#modal.classList.remove('active');
    }

    static viewJobHistory() {
        this.close();
        if (typeof viewJobHistory === 'function') {
            viewJobHistory();
        }
    }
}

window.closeBackgroundJobModal = () => BackgroundJobModal.close();
window.viewJobHistoryFromBg = () => BackgroundJobModal.viewJobHistory();
