import { DOMHelpers } from "../../utils/dom-helpers.js";
import { Toast } from "../../utils/toast.js";
import { Loading } from "../../utils/loading.js";

/**
 * Job Monitoring Modal - For FAST mode
 */
export class JobMonitoringModal {
    static #modal = null;
    static #currentJob = null;
    static #pollingInterval = null;
    static #options = {};

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
    }

    static update(job) {
        this.#currentJob = job;

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

        if (job.createdAt) {
            DOMHelpers.setText('job-created-at', new Date(job.createdAt).toLocaleString('vi-VN'));
        }
        if (job.startedAt) {
            DOMHelpers.setText('job-started-at', new Date(job.startedAt).toLocaleString('vi-VN'));
        }
        if (job.completedAt) {
            DOMHelpers.setText('job-completed-at', new Date(job.completedAt).toLocaleString('vi-VN'));
        }

        const resultSection = document.getElementById('job-result-section');
        if (resultSection) {
            if (job.status === 'COMPLETED') {
                resultSection.style.display = 'block';
                this.#displayResult(job);
            } else {
                resultSection.style.display = 'none';
            }
        }

        const errorSection = document.getElementById('job-error-section');
        if (errorSection) {
            if (job.status === 'FAILED' && job.errorMessage) {
                errorSection.style.display = 'block';
                DOMHelpers.setText('job-error-message', job.errorMessage);
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

    static #displayResult(job) {
        if (!job.result) return;

        DOMHelpers.setText('result-total-cost',
            job.result.total_cost?.toLocaleString('vi-VN') || '—');
        DOMHelpers.setText('result-total-distance',
            job.result.total_distance?.toFixed(2) || '—');
        DOMHelpers.setText('result-total-time',
            job.result.total_time?.toFixed(2) || '—');
        DOMHelpers.setText('result-total-co2',
            job.result.total_co2 || '—');
        DOMHelpers.setText('result-assigned-orders',
            job.result.assigned_orders || '—');

        const unassignedCount = job.result.unassignedOrders || 0;
        DOMHelpers.setText('result-unassigned-orders', unassignedCount);

        const unassignedWarning = document.getElementById('unassigned-warning');
        if (unassignedWarning) {
            unassignedWarning.style.display = unassignedCount > 0 ? 'block' : 'none';
        }
    }

    static #updateButtons(job) {
        const cancelBtn = document.getElementById('btn-cancel-job');
        const viewResultBtn = document.getElementById('btn-view-result');

        if (cancelBtn) {
            cancelBtn.style.display =
                (job.status === 'PENDING' || job.status === 'PROCESSING') ? 'inline-block' : 'none';
        }

        if (viewResultBtn) {
            viewResultBtn.style.display =
                job.status === 'COMPLETED' ? 'inline-block' : 'none';
        }
    }

    static #startPolling(jobId) {
        this.#stopPolling();

        this.#pollingInterval = setInterval(async () => {
            try {
                const job = await getJobById(jobId);
                this.update(job);

                if (job.status === 'COMPLETED' || job.status === 'FAILED' || job.status === 'CANCELLED') {
                    this.#stopPolling();

                    if (job.status === 'COMPLETED') {
                        Toast.success('Optimization completed!');
                    }
                }
            } catch (error) {
                console.error('Polling error:', error);
            }
        }, this.#options.pollingInterval);
    }

    static #stopPolling() {
        if (this.#pollingInterval) {
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
            this.update(job);

        } catch (error) {
            console.error('Failed to cancel job:', error);
            Toast.error('Không thể hủy job');
        } finally {
            Loading.hide();
        }
    }

    static viewResult() {
        Toast.info('Result view - Coming soon!');
        this.close();
    }
}

if (typeof window !== 'undefined') {
    window.JobMonitoringModal = JobMonitoringModal;
}

window.closeJobMonitoringModal = () => JobMonitoringModal.close();
window.cancelCurrentJob = async () => await JobMonitoringModal.cancelJob();
window.viewJobResult = () => JobMonitoringModal.viewResult();
