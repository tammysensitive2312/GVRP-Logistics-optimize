import { Component, Inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatIconModule } from '@angular/material/icon';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { JobDTO, SolutionDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { JobPollingService } from '@core/services/job-polling.service';
import { ToastService } from '@shared/services/toast.service';

export interface JobMonitoringDialogData {
  job: JobDTO;
}

@Component({
  selector: 'app-job-monitoring-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatProgressBarModule,
    MatIconModule
  ],
  templateUrl: './job-monitoring-dialog.component.html',
  styleUrl: './job-monitoring-dialog.component.scss'
})
export class JobMonitoringDialogComponent implements OnInit, OnDestroy {
  currentJob: JobDTO;
  solution: SolutionDTO | null = null;
  isLoadingSolution = false;
  fakeProgress = 0;
  private progressInterval: any;
  private destroy$ = new Subject<void>();

  get statusText(): string {
    const map: Record<string, string> = {
      PENDING: '⏳ Pending...',
      PROCESSING: '🔄 In progress',
      COMPLETED: '✅ Completed',
      FAILED: '❌ Failed',
      CANCELLED: '🚫 Cancelled'
    };
    return map[this.currentJob.status] || this.currentJob.status;
  }

  get canViewResult(): boolean {
    return this.currentJob.status === 'COMPLETED' && !!this.solution;
  }

  get isRunning(): boolean {
    return this.currentJob.status === 'PENDING' || this.currentJob.status === 'PROCESSING';
  }

  constructor(
    private dialogRef: MatDialogRef<JobMonitoringDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: JobMonitoringDialogData,
    private apiService: ApiService,
    private pollingService: JobPollingService,
    private toast: ToastService
  ) {
    this.currentJob = data.job;
  }

  ngOnInit(): void {
    this.startFakeProgress();
    // Subscribe to polling updates
    this.pollingService.jobUpdated$
      .pipe(takeUntil(this.destroy$))
      .subscribe(job => {
        this.currentJob = job;
      });

    this.pollingService.jobCompleted$
      .pipe(takeUntil(this.destroy$))
      .subscribe(job => {
        this.currentJob = job;
        this.completeFakeProgress();
        this.toast.success('Optimization completed!');

        if (job.solution_id) {
          this.fetchSolution(job.solution_id);
        }
      });

    this.pollingService.jobFailed$
      .pipe(takeUntil(this.destroy$))
      .subscribe(job => {
        this.currentJob = job;
        this.toast.error('Optimization failed!');
      });

    // Start polling
    this.pollingService.startPolling(this.currentJob.id, 3000);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private startFakeProgress(): void {
    this.fakeProgress = 0;
    this.progressInterval = setInterval(() => {
      if (this.fakeProgress < 40) {
        this.fakeProgress += Math.random() * 4 + 2;
      } else if (this.fakeProgress < 75) {
        this.fakeProgress += Math.random() * 2 + 1;
      } else if (this.fakeProgress < 95) {
        this.fakeProgress += Math.random() * 0.5 + 0.1;
      }
    }, 1000)
  }

  private completeFakeProgress(): void {
    this.stopFakeProgress();
    this.fakeProgress = 100;
  }

  private stopFakeProgress(): void {
    if (this.progressInterval) {
      clearInterval(this.progressInterval);
      this.progressInterval = null;
    }
  }

  private fetchSolution(solutionId: number): void {
    this.isLoadingSolution = true;

    this.apiService.getSolutionById(solutionId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (solution) => {
          this.solution = solution;
          this.isLoadingSolution = false;
        },
        error: () => {
          this.toast.error('Failed to load solution');
          this.isLoadingSolution = false;
        }
      });
  }

  onViewResult(): void {
    this.dialogRef.close({ solution: this.solution });
  }

  onCancelJob(): void {
    this.apiService.cancelJob(this.currentJob.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.toast.success('Job cancelled');
          this.pollingService.stopPolling();
          this.dialogRef.close(null);
        },
        error: () => this.toast.error('Failed to cancel job')
      });
  }

  onClose(): void {
    this.dialogRef.close(null);
  }
}
