/**
 * Optimized Route Planning Modal
 * Different behavior based on optimization speed
 */

import { AppState } from '../../core/state.js';
import { Toast } from '../../utils/toast.js';
import { Loading } from '../../utils/loading.js';
import { DOMHelpers } from '../../utils/dom-helpers.js';
import { BackgroundJobModal } from "./background-job-modal.js";
import { JobMonitoringModal } from "./job-monitoring-modal.js";

export class RoutePlanningModal {
    static #modal = null;
    static #form = null;
    static #currentJobId = null;
    static #pollingInterval = null;

    static init() {
        this.#modal = document.getElementById('modal-route-planning');
        this.#form = document.getElementById('route-planning-form');

        if (!this.#modal || !this.#form) {
            console.warn('Route planning modal elements not found');
            return;
        }

        this.#form.addEventListener('submit', (e) => {
            this.handleSubmit(e);
        });

        // Listen to speed changes to show estimated time
        const speedSelect = document.getElementById('optimization-speed');
        if (speedSelect) {
            speedSelect.addEventListener('change', () => {
                this.#updateEstimatedTime();
            });
        }
    }

    static open() {
        if (!this.#modal) return;

        const selectedOrders = AppState.selectedOrdersCount;
        const selectedVehicles = AppState.selectedVehicles.size;

        if (selectedOrders === 0) {
            Toast.error('Vui lòng chọn ít nhất 1 đơn hàng');
            return;
        }

        if (selectedVehicles === 0) {
            Toast.error('Vui lòng chọn ít nhất 1 xe');
            return;
        }

        this.#updateSelectionSummary(selectedOrders, selectedVehicles);
        this.#modal.classList.add('active');
        this.#setDefaults();
        this.#updateEstimatedTime();
    }

    static close() {
        if (!this.#modal) return;
        this.#modal.classList.remove('active');
        this.#reset();
    }

    /**
     * Update estimated time based on speed selection
     * @private
     */
    static #updateEstimatedTime() {
        const speed = DOMHelpers.getValue('optimization-speed');
        const estimatedTimeEl = document.getElementById('estimated-time');
        const waitingModeEl = document.getElementById('waiting-mode-info');

        if (!estimatedTimeEl) return;

        const timeInfo = {
            'FAST': {
                time: '2-3 minutes',
                mode: 'active',
                description: 'You can monitor progress in real-time'
            },
            'NORMAL': {
                time: '5-8 minutes',
                mode: 'background',
                description: 'Job will run in background. You will receive email when complete'
            },
            'HIGH_QUALITY': {
                time: '10-15 minutes',
                mode: 'background',
                description: 'Job will run in background. You will receive email when complete'
            }
        };

    }

    static #updateSelectionSummary(orderCount, vehicleCount) {
        DOMHelpers.setText('selected-orders-count', orderCount);
        DOMHelpers.setText('selected-vehicles-count', vehicleCount);

        const selectedOrderIds = Array.from(AppState.selectedOrders);
        const totalDemand = AppState.allOrders
            .filter(o => selectedOrderIds.includes(o.id))
            .reduce((sum, o) => sum + (o.demand || 0), 0);

        DOMHelpers.setText('total-demand', totalDemand.toFixed(1));

        // const selectedVehicleIds = Array.from(AppState.selectedVehicles);
        // const totalCapacity = AppState.allVehicles
        //     .filter(v => selectedVehicleIds.includes(v.id))
        //     .reduce((sum, v) => sum + (v.capacity || 0), 0);
        //
        // DOMHelpers.setText('total-capacity', totalCapacity.toFixed(1));

        // const utilization = totalCapacity > 0
        //     ? ((totalDemand / totalCapacity) * 100).toFixed(1)
        //     : 0;
        // DOMHelpers.setText('capacity-utilization', utilization);

        // const warningDiv = document.getElementById('capacity-warning');
        // if (warningDiv) {
        //     if (totalDemand > totalCapacity) {
        //         warningDiv.style.display = 'block';
        //     } else {
        //         warningDiv.style.display = 'none';
        //     }
        // }
    }

    static #setDefaults() {
        DOMHelpers.setValue('optimization-goal', 'MINIMIZE_COST');
        DOMHelpers.setValue('optimization-speed', 'NORMAL');
        DOMHelpers.setValue('time-window-mode', 'STRICT');

        const allowUnassignedCheckbox = document.getElementById('allow-unassigned');
        if (allowUnassignedCheckbox) {
            allowUnassignedCheckbox.checked = false;
        }

        const enableParetoCheckbox = document.getElementById('enable-pareto');
        if (enableParetoCheckbox) {
            enableParetoCheckbox.checked = false;
        }
    }

    static async handleSubmit(event) {
        event.preventDefault();

        const formData = this.#getFormData();

        if (!this.#validate(formData)) {
            return;
        }

        Loading.show('Đang gửi yêu cầu tối ưu hóa...');

        try {
            const request = {
                order_ids: Array.from(AppState.selectedOrders),
                vehicle_ids: Array.from(AppState.selectedVehicles),
                preferences: {
                    goal: formData.goal,
                    speed: formData.speed,
                    allow_unassigned_orders: formData.allowUnassigned,
                    time_window_mode: formData.timeWindowMode,
                    enable_pareto_analysis: formData.enablePareto
                }
            };

            const job = await submitRoutePlanningJob(request);

            Toast.success(`Job #${job.id} đã được tạo!`);

            this.close();

            this.#currentJobId = job.id;

            // Different behavior based on speed
            if (formData.speed === 'FAST') {
                // FAST MODE: Show progress modal with polling
                JobMonitoringModal.open(job, { enablePolling: true, pollingInterval: 3000 });
            } else {
                // NORMAL/HIGH_QUALITY MODE: Show background job confirmation
                BackgroundJobModal.open(job);
            }

        } catch (error) {
            console.error('Failed to submit job:', error);
            Toast.error('Không thể tạo job. Vui lòng thử lại.');
        } finally {
            Loading.hide();
        }
    }

    static #getFormData() {
        return {
            goal: DOMHelpers.getValue('optimization-goal'),
            speed: DOMHelpers.getValue('optimization-speed'),
            timeWindowMode: DOMHelpers.getValue('time-window-mode'),
            allowUnassigned: document.getElementById('allow-unassigned')?.checked || false,
            enablePareto: document.getElementById('enable-pareto')?.checked || false
        };
    }

    static #validate(data) {
        if (!data.goal) {
            Toast.error('Vui lòng chọn mục tiêu tối ưu');
            return false;
        }

        if (!data.speed) {
            Toast.error('Vui lòng chọn tốc độ tối ưu');
            return false;
        }

        return true;
    }

    static #reset() {
        if (this.#form) {
            this.#form.reset();
        }
        this.#currentJobId = null;
    }
}

if (typeof window !== 'undefined') {
    window.RoutePlanningModal = RoutePlanningModal;
}

window.openRoutePlanningModal = () => RoutePlanningModal.open();
window.closeRoutePlanningModal = () => RoutePlanningModal.close();

