import { DOMHelpers } from "../../utils/dom-helpers.js";
import { Toast } from "../../utils/toast.js";
import { Loading } from "../../utils/loading.js";
import { MainMap } from "../Map Components/main-map.js";

/**
 * Job Monitoring Modal - For FAST mode
 */
export class JobMonitoringModal {
    static #modal = null;
    static #currentJob = null;
    static #currentSolution = null;
    static #pollingInterval = null;
    static #options = {};
    static #isFetchingSolution = false; // Prevent duplicate fetches

    static init() {
        this.#modal = document.getElementById('modal-job-monitoring');

        const btnView = document.getElementById('btn-view-result');
        if (btnView) {
            btnView.addEventListener('click', () => this.viewResult());
        }

        const btnCancel = document.getElementById('btn-cancel-job');
        if (btnCancel) {
            btnCancel.addEventListener('click', () => this.cancelJob());
        }

        if (!this.#modal) {
            console.warn('Job monitoring modal not found');
        }
    }

    static open(job, options = {}) {
        if (!this.#modal) return;

        this.#currentJob = job;
        this.#currentSolution = null;
        this.#isFetchingSolution = false;
        this.#options = {
            enablePolling: options.enablePolling || false,
            pollingInterval: options.pollingInterval || 3000
        };

        this.update(job);
        this.#modal.classList.add('active');

        if (this.#options.enablePolling) {
            this.#startPolling(job.id);
        }
    }

    static close() {
        if (!this.#modal) return;

        this.#stopPolling();
        this.#modal.classList.remove('active');
        this.#currentJob = null;
        this.#currentSolution = null;
        this.#isFetchingSolution = false;
    }

    /**
     * Update job status and fetch solution if completed
     * ⚠️ THIS MUST BE ASYNC and AWAITED
     */
    static async update(job) {
        this.#currentJob = job;

        // Update job info
        DOMHelpers.setText('job-id', `#${job.id}`);
        DOMHelpers.setText('job-status', this.#getStatusText(job.status));

        const statusBadge = document.getElementById('job-status-badge');
        if (statusBadge) {
            statusBadge.className = `status-badge ${job.status.toLowerCase()}`;
            statusBadge.textContent = job.status;
        }

        const progress = job.progress || 0;
        DOMHelpers.setText('job-progress-text', `${progress}%`);

        const progressBar = document.getElementById('job-progress-bar');
        if (progressBar) {
            progressBar.style.width = `${progress}%`;
        }

        if (job.created_at) {
            DOMHelpers.setText('job-created-at',
                new Date(job.created_at).toLocaleString('vi-VN'));
        }
        if (job.started_at) {
            DOMHelpers.setText('job-started-at',
                new Date(job.started_at).toLocaleString('vi-VN'));
        }
        if (job.completed_at) {
            DOMHelpers.setText('job-completed-at',
                new Date(job.completed_at).toLocaleString('vi-VN'));
        }

        const resultSection = document.getElementById('job-result-section');
        const errorSection = document.getElementById('job-error-section');

        // ✅ KEY FIX: Handle completed job - fetch solution
        if (job.status === 'COMPLETED') {
            // Only fetch if we have solution_id and haven't fetched yet
            if (job.solution_id && !this.#currentSolution && !this.#isFetchingSolution) {
                console.log('🔍 Job completed, fetching solution:', job.solution_id);
                await this.#fetchAndDisplaySolution(job.solution_id);
            }

            if (resultSection) {
                resultSection.style.display = 'block';
            }
        } else {
            if (resultSection) {
                resultSection.style.display = 'none';
            }
        }

        // Handle failed job
        if (errorSection) {
            if (job.status === 'FAILED' && job.error_message) {
                errorSection.style.display = 'block';
                DOMHelpers.setText('job-error-message', job.error_message);
            } else {
                errorSection.style.display = 'none';
            }
        }

        this.#updateButtons(job);
    }

    static #getStatusText(status) {
        const statusMap = {
            'PENDING': '⏳ Pending...',
            'PROCESSING': '🔄 In progress',
            'COMPLETED': '✅ Completed',
            'FAILED': '❌ Failed',
            'CANCELLED': '🚫 Cancelled'
        };
        return statusMap[status] || status;
    }

    /**
     * Fetch and display solution
     * @private
     */
    static async #fetchAndDisplaySolution(solutionId) {
        // Prevent duplicate fetch
        if (this.#isFetchingSolution) {
            console.log('⚠️ Already fetching solution, skipping...');
            return;
        }

        this.#isFetchingSolution = true;

        try {
            console.log('📡 Fetching solution from API:', solutionId);
            Loading.show('Loading solution...');

            const solution = await getSolutionById(solutionId);

            console.log('✅ Solution fetched successfully:', solution);
            this.#currentSolution = solution;

            this.#displayResult(solution);

        } catch (error) {
            console.error('❌ Failed to fetch solution:', error);
            Toast.error('Failed to load solution details');

            // Show error in result section
            const resultSection = document.getElementById('job-result-section');
            if (resultSection) {
                resultSection.innerHTML = `
                    <div class="alert alert-danger">
                        <strong>Error:</strong> Failed to load solution details. 
                        <button onclick="JobMonitoringModal.retryFetchSolution(${solutionId})" 
                                class="btn btn-sm btn-secondary">
                            Retry
                        </button>
                    </div>
                `;
            }
        } finally {
            Loading.hide();
            this.#isFetchingSolution = false;
        }
    }

    /**
     * Retry fetching solution (public method for retry button)
     */
    static async retryFetchSolution(solutionId) {
        this.#isFetchingSolution = false; // Reset flag
        await this.#fetchAndDisplaySolution(solutionId);
    }

    /**
     * Display solution results
     * @private
     */
    static #displayResult(solution) {
        if (!solution) {
            console.warn('⚠️ No solution data to display');
            return;
        }

        console.log('📊 Displaying solution results');

        // Display summary metrics
        DOMHelpers.setText('result-total-cost',
            solution.total_cost?.toLocaleString('vi-VN') || '—');

        DOMHelpers.setText('result-total-distance',
            solution.total_distance?.toFixed(2) || '—');

        DOMHelpers.setText('result-total-time',
            solution.total_time?.toFixed(2) || '—');

        DOMHelpers.setText('result-total-co2',
            solution.total_co2?.toFixed(2) || '—');

        DOMHelpers.setText('result-routes-count',
            solution.total_vehicles_used || '—');

        DOMHelpers.setText('result-assigned-orders',
            solution.served_orders || '—');

        const unassignedCount = solution.unserved_orders || 0;
        DOMHelpers.setText('result-unassigned-orders', unassignedCount);

        // Show warning if there are unassigned orders
        const unassignedWarning = document.getElementById('unassigned-warning');
        if (unassignedWarning) {
            unassignedWarning.style.display = unassignedCount > 0 ? 'block' : 'none';
        }

        console.log('✅ Solution displayed successfully');
    }

    static #updateButtons(job) {
        const cancelBtn = document.getElementById('btn-cancel-job');
        const viewResultBtn = document.getElementById('btn-view-result');

        if (cancelBtn) {
            cancelBtn.style.display =
                (job.status === 'PENDING' || job.status === 'PROCESSING')
                    ? 'inline-block'
                    : 'none';
        }

        if (viewResultBtn) {
            // ✅ Only show if we have both completed job AND solution loaded
            const canView = job.status === 'COMPLETED' && this.#currentSolution;
            viewResultBtn.style.display = canView ? 'inline-block' : 'none';
        }
    }

    static #startPolling(jobId) {
        this.#stopPolling();

        console.log('🔄 Starting polling for job:', jobId);

        this.#pollingInterval = setInterval(async () => {
            try {
                console.log('📡 Polling job status...');
                const job = await getJobById(jobId);

                // ✅ KEY FIX: AWAIT the update() call!
                await this.update(job);

                // Stop polling when job finishes
                if (job.status === 'COMPLETED' ||
                    job.status === 'FAILED' ||
                    job.status === 'CANCELLED') {

                    console.log('⏹️ Job finished, stopping polling. Status:', job.status);
                    this.#stopPolling();

                    if (job.status === 'COMPLETED') {
                        Toast.success('Optimization completed!');
                    } else if (job.status === 'FAILED') {
                        Toast.error('Optimization failed!');
                    }
                }
            } catch (error) {
                console.error('❌ Polling error:', error);
                // Don't stop polling on single error - might be temporary network issue
            }
        }, this.#options.pollingInterval);
    }

    static #stopPolling() {
        if (this.#pollingInterval) {
            console.log('⏹️ Stopping polling');
            clearInterval(this.#pollingInterval);
            this.#pollingInterval = null;
        }
    }

    static async cancelJob() {
        if (!this.#currentJob) return;

        if (!confirm('Bạn có chắc chắn muốn hủy job này?')) {
            return;
        }

        Loading.show('Đang hủy job...');

        try {
            await cancelJob(this.#currentJob.id);
            Toast.success('Đã hủy job');

            const job = await getJobById(this.#currentJob.id);
            await this.update(job); // ✅ Also await here

        } catch (error) {
            console.error('Failed to cancel job:', error);
            Toast.error('Không thể hủy job');
        } finally {
            Loading.hide();
        }
    }

    static viewResult() {
        if (!this.#currentSolution) {
            Toast.error('No solution available');
            return;
        }

        const solution = this.#currentSolution;

        // 1. Close modal
        this.close();

        if (MainMap) {
            MainMap.displaySolution(solution);
        } else {
            Toast.error('Map components not found');
        }

        if (typeof SolutionDisplay !== 'undefined') {
            SolutionDisplay.setSolution(solution);
        } else {
            console.error('SolutionDisplay not loaded');
            return;
        }

        setTimeout(() => {
            switchContentTab('route-tab');
        }, 100);

        Toast.success('Solution loaded! Switch between tabs to view different perspectives');
    }

    /**
     * Get current solution (for debugging/external access)
     */
    static getCurrentSolution() {
        return this.#currentSolution;
    }

    /**
     * Get current job (for debugging/external access)
     */
    static getCurrentJob() {
        return this.#currentJob;
    }
}

if (typeof window !== 'undefined') {
    window.JobMonitoringModal = JobMonitoringModal;
}

window.closeJobMonitoringModal = () => JobMonitoringModal.close();
window.cancelCurrentJob = async () => await JobMonitoringModal.cancelJob();
window.viewJobResult = () => JobMonitoringModal.viewResult();
