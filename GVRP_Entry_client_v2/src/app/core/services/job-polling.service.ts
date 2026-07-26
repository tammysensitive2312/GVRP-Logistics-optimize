import { inject, Injectable, OnDestroy } from '@angular/core';
import { EMPTY, interval, Subject, Subscription } from 'rxjs';
import { catchError, switchMap, takeUntil } from 'rxjs/operators';

import { JobDTO, JobProgressDTO } from '@core/models';
import { ApiService } from './api.service';

/**
 * Job Polling Service
 *
 * Polls `GET /jobs/{id}/progress` for live solver telemetry (phase, percent,
 * iteration, best cost) instead of re-reading the whole job. The progress record
 * carries no `solution_id`, so once it reports a terminal state - or disappears
 * (204) - the job itself is fetched once to get the authoritative status and the
 * solution id.
 *
 * Also fixes two defects from the V1 migration:
 * - CANCELLED used to land on `jobFailed$`, showing "Optimization failed!".
 * - One failed poll used to error the `interval` chain and kill polling for good.
 */
@Injectable({ providedIn: 'root' })
export class JobPollingService implements OnDestroy {
  /** Give up only after this many consecutive failures. */
  private static readonly MAX_CONSECUTIVE_ERRORS = 5;

  private readonly apiService = inject(ApiService);

  private pollingSubscription: Subscription | null = null;
  private finalizeSubscription: Subscription | null = null;
  private readonly destroy$ = new Subject<void>();
  private consecutiveErrors = 0;

  /** Live telemetry, emitted on every successful poll. */
  readonly jobProgress$ = new Subject<JobProgressDTO>();
  readonly jobCompleted$ = new Subject<JobDTO>();
  readonly jobFailed$ = new Subject<JobDTO>();
  readonly jobCancelled$ = new Subject<JobDTO>();
  /** The job record; emitted once the run reaches a terminal state. */
  readonly jobUpdated$ = new Subject<JobDTO>();
  /** Emits the consecutive-failure count whenever a poll request fails. */
  readonly pollError$ = new Subject<number>();

  startPolling(jobId: number, intervalMs = 3000): void {
    this.stopPolling();
    this.consecutiveErrors = 0;

    this.pollingSubscription = interval(intervalMs)
      .pipe(
        switchMap(() =>
          this.apiService.getJobProgress(jobId).pipe(
            catchError((error: unknown) => {
              this.consecutiveErrors++;
              console.error('Polling error:', error);
              this.pollError$.next(this.consecutiveErrors);

              if (this.consecutiveErrors >= JobPollingService.MAX_CONSECUTIVE_ERRORS) {
                this.stopPolling();
              }

              return EMPTY;
            })
          )
        ),
        takeUntil(this.destroy$)
      )
      .subscribe(progress => {
        this.consecutiveErrors = 0;

        if (progress) {
          this.jobProgress$.next(progress);
        }

        if (progress === null || isFinished(progress)) {
          this.resolveFinalJob(jobId);
        }
      });
  }

  isPolling(): boolean {
    return this.pollingSubscription !== null;
  }

  stopPolling(): void {
    this.pollingSubscription?.unsubscribe();
    this.pollingSubscription = null;
  }

  ngOnDestroy(): void {
    this.stopPolling();
    this.finalizeSubscription?.unsubscribe();
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** Reads the job once to learn the real end state plus its solution id. */
  private resolveFinalJob(jobId: number): void {
    this.stopPolling();

    this.finalizeSubscription?.unsubscribe();
    this.finalizeSubscription = this.apiService
      .getJobById(jobId)
      .pipe(
        catchError((error: unknown) => {
          console.error('Failed to read the finished job:', error);
          return EMPTY;
        }),
        takeUntil(this.destroy$)
      )
      .subscribe(job => {
        this.jobUpdated$.next(job);

        switch (job.status) {
          case 'COMPLETED':
            this.jobCompleted$.next(job);
            break;
          case 'FAILED':
            this.jobFailed$.next(job);
            break;
          case 'CANCELLED':
            this.jobCancelled$.next(job);
            break;
          default:
            // Telemetry says finished but the job row is not there yet; poll on.
            this.startPolling(jobId);
            break;
        }
      });
  }
}

function isFinished(progress: JobProgressDTO): boolean {
  return progress.status !== 'RUNNING' || progress.finishedAt != null;
}
