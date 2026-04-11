import { Injectable, OnDestroy } from '@angular/core';
import { interval, Subject, Subscription } from 'rxjs';
import { switchMap, takeUntil, filter } from 'rxjs/operators';
import { ApiService } from './api.service';
import { JobDTO } from '@core/models';

@Injectable({ providedIn: 'root' })
export class JobPollingService implements OnDestroy {
  private pollingSubscription: Subscription | null = null;
  private destroy$ = new Subject<void>();

  jobCompleted$ = new Subject<JobDTO>();
  jobFailed$ = new Subject<JobDTO>();
  jobUpdated$ = new Subject<JobDTO>();

  constructor(private apiService: ApiService) {}

  startPolling(jobId: number, intervalMs: number = 3000): void {
    this.stopPolling();

    this.pollingSubscription = interval(intervalMs).pipe(
      switchMap(() => this.apiService.getJobById(jobId)),
      takeUntil(this.destroy$)
    ).subscribe({
      next: (job) => {
        this.jobUpdated$.next(job);

        if (job.status === 'COMPLETED') {
          this.jobCompleted$.next(job);
          this.stopPolling();
        } else if (job.status === 'FAILED' || job.status === 'CANCELLED') {
          this.jobFailed$.next(job);
          this.stopPolling();
        }
      },
      error: (err) => console.error('Polling error:', err)
    });
  }

  stopPolling(): void {
    this.pollingSubscription?.unsubscribe();
    this.pollingSubscription = null;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
