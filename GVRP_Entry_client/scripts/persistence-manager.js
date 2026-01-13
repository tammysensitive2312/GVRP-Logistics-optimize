import { AppState } from './core/state.js';
import { MainMap } from './components/Map Components/main-map.js';

export class PersistenceManager {
    static #cachedSolution = null;
    /**
     * Restore all UI state and map data.
     */
    static async restore() {
        const state = AppState.getState();
        console.log('🔄 PersistenceManager: Khởi động khôi phục...', state);

        // this.#restoreSelections(state);

        if (state.activeSolutionId) {
            await this.#restoreSolution(state.activeSolutionId);
        }

        if (state.activeJobId) {
            await this.#restoreJob(state.activeJobId);
        }

        if (state.activeModal) {
            await this.#restoreModal(state.activeModal);
        }

    }

    /**
     * Restore solution with caching
     * @private
     */
    static async #restoreSolution(solutionId) {
        try {
            console.log('🗺️ Restoring solution:', solutionId);

            // Use cache if available
            if (this.#cachedSolution && this.#cachedSolution.id === solutionId) {
                console.log('📦 Using cached solution');
                this.#displaySolution(this.#cachedSolution);
                return;
            }

            const solution = await window.getSolutionById(solutionId);

            if (solution) {
                const orderStats = {
                    scheduled: solution.served_orders || 0,
                    completed: 0,
                    total: (solution.served_orders || 0) + (solution.unserved_orders || 0),
                    unassigned: solution.unserved_orders || 0
                };

                AppState.setOrderStats(orderStats);

                this.#cachedSolution = solution;
                this.#displaySolution(solution);
                console.log('✅ Solution restored:', solution.id);
            }
        } catch (e) {
            console.error('❌ Lỗi khôi phục solution:', e);
            AppState.activeSolutionId = null;
            this.#cachedSolution = null;
        }
    }

    /**
     * Display solution on map and views
     * @private
     */
    static #displaySolution(solution) {
        // Display on map
        if (typeof MainMap !== 'undefined') {
            MainMap.displaySolution(solution);
        }

        // Display in solution views
        if (typeof SolutionDisplay !== 'undefined') {
            SolutionDisplay.setSolution(solution);
        }

        // Enable route/timeline tabs
        const routeBtn = document.querySelector('.tab-btn[data-tab="route-tab"]');
        const timelineBtn = document.querySelector('.tab-btn[data-tab="timeline-tab"]');

        if (routeBtn) {
            routeBtn.disabled = false;
            routeBtn.style.opacity = '1';
        }
        if (timelineBtn) {
            timelineBtn.disabled = false;
            timelineBtn.style.opacity = '1';
        }
    }

    /**
     * Restore job monitoring
     * @private
     */
    static async #restoreJob(jobId) {
        try {
            console.log('⏱️ Restoring job:', jobId);

            const job = await window.getJobById(jobId);

            if (job && (job.status === 'PROCESSING' || job.status === 'PENDING')) {
                // Still running - open monitoring modal
                if (typeof JobMonitoringModal !== 'undefined') {
                    JobMonitoringModal.open(job, { enablePolling: true });
                }
            } else if (job && job.status === 'COMPLETED' && job.solution_id) {
                // Completed - restore solution instead
                AppState.activeSolutionId = job.solution_id;
                AppState.activeJobId = null;
                await this.#restoreSolution(job.solution_id);
            } else {
                // Job done or cancelled
                AppState.activeJobId = null;
            }
        } catch (e) {
            console.error('❌ Lỗi khôi phục job:', e);
            AppState.activeJobId = null;
        }
    }

    static async #restoreModal(activeModal) {

        await new Promise(resolve => setTimeout(resolve, 500));

        switch (activeModal) {
            case 'import':
                if (typeof window.openImportModal === 'function') {
                    window.openImportModal();
                }
                break;

            case 'route-planning':
                if (typeof RoutePlanningModal !== 'undefined') {
                    // Open modal with restored selections
                    RoutePlanningModal.open({ restore: true });
                }
                break;
        }
    }

    static clearCache() {
        this.#cachedSolution = null;
    }
}