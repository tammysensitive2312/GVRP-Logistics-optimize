import { inject, Injectable, OnDestroy } from '@angular/core';
import { EMPTY, interval, Subject, Subscription } from 'rxjs';
import { catchError, switchMap, takeUntil } from 'rxjs/operators';

import { JobDTO } from '@core/models';
import { ApiService } from './api.service';

/**
 * Job Polling Service
 *
 * Fixes two defects found while migrating V1's job-monitoring-modal:
 * - CANCELLED used to be pushed onto `jobFailed$`, so cancelling a job showed
 *   "Optimization failed!". V1 treated cancellation as a normal end state.
 * - A single failed poll errored the whole `interval` chain and killed polling
 *   permanently. V1 caught per-tick errors and kept polling.
 */
@Injectable({ providedIn: 'root' })
export class JobPollingService implements OnDestroy {
  /** Give up only after this many consecutive failures. */
  private static readonly MAX_CONSECUTIVE_ERRORS = 5;

  private readonly apiService = inject(ApiService);

  private pollingSubscription: Subscription | null = null;
  private readonly destroy$ = new Subject<void>();
  private consecutiveErrors = 0;

  readonly jobCompleted$ = new Subject<JobDTO>();
  readonly jobFailed$ = new Subject<JobDTO>();
  readonly jobCancelled$ = new Subject<JobDTO>();
  readonly jobUpdated$ = new Subject<JobDTO>();
  /** Emits the consecutive-failure count whenever a poll request fails. */
  readonly pollError$ = new Subject<number>();

  startPolling(jobId: number, intervalMs = 3000): void {
    this.stopPolling();
    this.consecutiveErrors = 0;

    this.pollingSubscription = interval(intervalMs)
      .pipe(
        switchMap(() =>
          this.apiService.getJobById(jobId).pipe(
            catchError((error: unknown) => {
              this.consecutiveErrors++;
              console.error('Polling error:', error);
              this.pollError$.next(this.consecutiveErrors);

              if (this.consecutiveErrors >= JobPollingService.MAX_CONSECUTIVE_ERRORS) {
                this.stopPolling();
              }

              // Swallow the error so the interval keeps ticking.
              return EMPTY;
            })
          )
        ),
        takeUntil(this.destroy$)
      )
      .subscribe(job => {
        this.consecutiveErrors = 0;
        this.jobUpdated$.next(job);

        switch (job.status) {
          case 'COMPLETED':
            this.jobCompleted$.next(job);
            this.stopPolling();
            break;
          case 'FAILED':
            this.jobFailed$.next(job);
            this.stopPolling();
            break;
          case 'CANCELLED':
            this.jobCancelled$.next(job);
            this.stopPolling();
            break;
          default:
            break;
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
    this.destroy$.next();
    this.destroy$.complete();
  }
}
