import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  OnDestroy,
  OnInit,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatIconModule } from '@angular/material/icon';
import { switchMap } from 'rxjs/operators';
import { EMPTY } from 'rxjs';

import { JobDTO, SolutionDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { JobPollingService } from '@core/services/job-polling.service';
import { ConfirmService } from '@shared/services/confirm.service';
import { ToastService } from '@shared/services/toast.service';

export interface JobMonitoringDialogData {
  job: JobDTO;
  /** V1 `open(job, { enablePolling })`: history entries opened read-only. */
  enablePolling?: boolean;
  pollingIntervalMs?: number;
}

export interface JobMonitoringDialogResult {
  solution: SolutionDTO;
}

const STATUS_TEXT: Record<JobDTO['status'], string> = {
  PENDING: '⏳ Pending...',
  PROCESSING: '🔄 In progress',
  COMPLETED: '✅ Completed',
  FAILED: '❌ Failed',
  CANCELLED: '🚫 Cancelled'
};

/**
 * Job Monitoring dialog (V1 FAST mode modal).
 *
 * Fixes carried over from `job-monitoring-modal.js`:
 * - Progress is `job.progress` from the API. The previous version fabricated a
 *   random curve with setInterval and never read the real value.
 * - A job that is already COMPLETED when the dialog opens now loads its solution,
 *   so "View Result" appears (V1 did this inside `update()`).
 * - Cancel asks for confirmation and refreshes the job instead of closing blind.
 * - Solution fetch has a re-entry guard and a Retry action on failure.
 * - CANCELLED no longer renders as "Optimization failed!".
 */
@Component({
  selector: 'app-job-monitoring-dialog',
  standalone: true,
  imports: [
    DatePipe,
    DecimalPipe,
    MatDialogModule,
    MatButtonModule,
    MatProgressBarModule,
    MatIconModule
  ],
  templateUrl: './job-monitoring-dialog.component.html',
  styleUrl: './job-monitoring-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class JobMonitoringDialogComponent implements OnInit, OnDestroy {
  private readonly dialogRef =
    inject<MatDialogRef<JobMonitoringDialogComponent, JobMonitoringDialogResult | null>>(
      MatDialogRef
    );
  private readonly data = inject<JobMonitoringDialogData>(MAT_DIALOG_DATA);
  private readonly api = inject(ApiService);
  private readonly polling = inject(JobPollingService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly currentJob = signal<JobDTO>(this.data.job);
  readonly solution = signal<SolutionDTO | null>(null);
  readonly isLoadingSolution = signal(false);
  readonly solutionError = signal(false);
  readonly cancelling = signal(false);
  /** Raised after repeated poll failures so the user knows data may be stale. */
  readonly pollingDegraded = signal(false);

  readonly statusText = computed(() => STATUS_TEXT[this.currentJob().status]);
  readonly statusClass = computed(
    () => `status-${this.currentJob().status.toLowerCase()}`
  );
  readonly progress = computed(() => clampProgress(this.currentJob().progress));
  readonly isRunning = computed(() => {
    const status = this.currentJob().status;
    return status === 'PENDING' || status === 'PROCESSING';
  });
  readonly isCompleted = computed(() => this.currentJob().status === 'COMPLETED');
  readonly isFailed = computed(() => this.currentJob().status === 'FAILED');
  readonly canViewResult = computed(() => this.isCompleted() && this.solution() !== null);
  readonly unservedOrders = computed(() => this.solution()?.unserved_orders ?? 0);

  /** Guards against two concurrent solution fetches (V1 `#isFetchingSolution`). */
  private fetchingSolution = false;

  ngOnInit(): void {
    this.subscribeToPolling();

    // V1 called update(job) on open, which fetched the solution for a job that
    // had already finished before the dialog was shown.
    this.applyJob(this.data.job);

    if (this.data.enablePolling !== false && this.isRunning()) {
      this.polling.startPolling(this.currentJob().id, this.data.pollingIntervalMs ?? 3000);
    }
  }

  ngOnDestroy(): void {
    this.polling.stopPolling();
  }

  retryFetchSolution(): void {
    const solutionId = this.currentJob().solution_id;
    if (solutionId) {
      this.fetchingSolution = false;
      this.fetchSolution(solutionId);
    }
  }

  onViewResult(): void {
    const solution = this.solution();
    if (!solution) {
      this.toast.error('No solution available');
      return;
    }

    this.dialogRef.close({ solution });
  }

  onCancelJob(): void {
    if (this.cancelling()) return;

    this.confirm
      .ask({
        title: 'Bạn có chắc chắn muốn hủy job này?',
        message: `Job #${this.currentJob().id}`,
        confirmText: 'Hủy job',
        cancelText: 'Không',
        danger: true
      })
      .pipe(
        switchMap(confirmed => {
          if (!confirmed) return EMPTY;

          this.cancelling.set(true);
          return this.api.cancelJob(this.currentJob().id);
        }),
        // V1 refreshed the job after cancelling instead of closing immediately.
        switchMap(() => this.api.getJobById(this.currentJob().id)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: job => {
          this.cancelling.set(false);
          this.polling.stopPolling();
          this.toast.success('Đã hủy job');
          this.applyJob(job);
        },
        error: (error: unknown) => {
          this.cancelling.set(false);
          console.error('Failed to cancel job:', error);
          this.toast.error('Không thể hủy job');
        }
      });
  }

  onClose(): void {
    this.dialogRef.close(null);
  }

  private subscribeToPolling(): void {
    this.polling.jobUpdated$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(job => {
        this.pollingDegraded.set(false);
        this.applyJob(job);
      });

    this.polling.jobCompleted$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.toast.success('Optimization completed!'));

    this.polling.jobFailed$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.toast.error('Optimization failed!'));

    // V1 showed nothing extra for CANCELLED; the status badge carries it.
    this.polling.jobCancelled$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(job => this.applyJob(job));

    this.polling.pollError$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(consecutiveErrors => {
        if (consecutiveErrors >= 2) this.pollingDegraded.set(true);
      });
  }

  private applyJob(job: JobDTO): void {
    this.currentJob.set(job);

    if (
      job.status === 'COMPLETED' &&
      job.solution_id &&
      this.solution() === null &&
      !this.fetchingSolution
    ) {
      this.fetchSolution(job.solution_id);
    }
  }

  private fetchSolution(solutionId: number): void {
    if (this.fetchingSolution) return;

    this.fetchingSolution = true;
    this.isLoadingSolution.set(true);
    this.solutionError.set(false);

    this.api
      .getSolutionById(solutionId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: solution => {
          this.solution.set(solution);
          this.isLoadingSolution.set(false);
          this.fetchingSolution = false;
        },
        error: (error: unknown) => {
          console.error('Failed to fetch solution:', error);
          this.toast.error('Failed to load solution details');
          this.solutionError.set(true);
          this.isLoadingSolution.set(false);
          this.fetchingSolution = false;
        }
      });
  }
}

function clampProgress(progress: number | null | undefined): number {
  if (progress === null || progress === undefined || !Number.isFinite(progress)) return 0;
  return Math.max(0, Math.min(100, progress));
}
