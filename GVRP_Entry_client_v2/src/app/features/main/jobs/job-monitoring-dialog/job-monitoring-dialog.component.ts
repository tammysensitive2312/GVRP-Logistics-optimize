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
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {DatePipe, DecimalPipe} from '@angular/common';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatButtonModule} from '@angular/material/button';
import {MatProgressBarModule} from '@angular/material/progress-bar';
import {MatIconModule} from '@angular/material/icon';
import {switchMap} from 'rxjs/operators';
import {EMPTY} from 'rxjs';

import {JobDTO, JobProgressDTO, SolutionDTO} from '@core/models';
import {ApiService} from '@core/services/api.service';
import {JobPollingService} from '@core/services/job-polling.service';
import {TranslocoService} from '@jsverse/transloco';

import {ConfirmService} from '@shared/services/confirm.service';
import {ToastService} from '@shared/services/toast.service';

export interface JobMonitoringDialogData {
  job: JobDTO;
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
  private readonly i18n = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  readonly currentJob = signal<JobDTO>(this.data.job);
  /** Live solver telemetry from GET /jobs/{id}/progress. */
  readonly progressInfo = signal<JobProgressDTO | null>(null);
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
  /**
   * Real percentage: the progress endpoint while the solver runs, the job record
   * once it is done. No fabricated numbers.
   */
  readonly progress = computed(() => {
    const live = this.progressInfo();
    if (live?.percent != null) return clampProgress(live.percent);
    if (this.isCompleted()) return 100;
    return clampProgress(this.currentJob().progress);
  });

  /** 'BUILDING_MATRIX' -> 'Building matrix'. */
  readonly phaseLabel = computed(() => {
    const phase = this.progressInfo()?.phase;
    if (!phase) return null;

    const words = phase.toLowerCase().split('_');
    return words[0].charAt(0).toUpperCase() + words[0].slice(1) + (words.length > 1 ? ' ' + words.slice(1).join(' ') : '');
  });

  readonly solverStats = computed(() => {
    const live = this.progressInfo();
    if (!live || live.iteration == null) return null;

    return {
      iteration: live.iteration,
      maxIterations: live.maxIterations ?? null,
      routes: live.routes ?? null,
      unassigned: live.unassigned ?? null,
      bestCost: live.bestCost ?? null,
      elapsedSeconds: live.elapsedSeconds ?? null
    };
  });
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

    this.dialogRef.close({solution});
  }

  onCancelJob(): void {
    if (this.cancelling()) return;

    this.confirm
      .ask({
        title: this.i18n.translate('jobMonitoring.cancelConfirmTitle'),
        message: `Job #${this.currentJob().id}`,
        confirmText: this.i18n.translate('jobMonitoring.cancelConfirmButton'),
        cancelText: this.i18n.translate('jobMonitoring.cancelKeepRunning'),
        danger: true
      })
      .pipe(
        switchMap(confirmed => {
          if (!confirmed) return EMPTY;

          this.cancelling.set(true);
          return this.api.cancelJob(this.currentJob().id);
        }),
        switchMap(() => this.api.getJobById(this.currentJob().id)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: job => {
          this.cancelling.set(false);
          this.polling.stopPolling();
          this.toast.success(this.i18n.translate('jobMonitoring.cancelled'));
          this.applyJob(job);
        },
        error: (error: unknown) => {
          this.cancelling.set(false);
          console.error('Failed to cancel job:', error);
          this.toast.error(this.i18n.translate('jobMonitoring.cancelFailed'));
        }
      });
  }

  onClose(): void {
    this.dialogRef.close(null);
  }

  private subscribeToPolling(): void {
    this.polling.jobProgress$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(progress => {
        this.pollingDegraded.set(false);
        this.progressInfo.set(progress);
      });

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
