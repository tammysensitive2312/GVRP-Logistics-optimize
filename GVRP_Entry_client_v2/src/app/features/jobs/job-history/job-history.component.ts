import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  OnInit,
  signal
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize, map } from 'rxjs/operators';

import { JobDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { SolutionStore } from '@core/services/solution.store';
import { ToastService } from '@shared/services/toast.service';

const HISTORY_LIMIT = 50;

/**
 * Job History
 *
 * New screen - V1 had no equivalent. `background-job-modal.js` called a global
 * `viewJobHistory()` that was never defined anywhere, so background jobs were
 * effectively fire-and-forget.
 *
 * Scope: list jobs and open the solution of a finished one. Selecting a row deep
 * links to /jobs/:id so a specific run can be shared or reloaded.
 */
@Component({
  selector: 'app-job-history',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  templateUrl: './job-history.component.html',
  styleUrl: './job-history.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class JobHistoryComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly solutionStore = inject(SolutionStore);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly jobs = signal<readonly JobDTO[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  /** Id of the job whose solution is being fetched. */
  readonly openingSolutionFor = signal<number | null>(null);

  /** Route param, so /jobs/:id survives a reload and can be shared. */
  private readonly routeJobId = toSignal(
    this.route.paramMap.pipe(
      map(params => {
        const raw = params.get('id');
        if (raw === null) return null;

        const parsed = Number(raw);
        return Number.isInteger(parsed) ? parsed : null;
      })
    ),
    { initialValue: null }
  );

  readonly selectedJob = computed(() => {
    const id = this.routeJobId();
    if (id === null) return null;

    return this.jobs().find(job => job.id === id) ?? null;
  });

  readonly isEmpty = computed(() => !this.loading() && this.jobs().length === 0);

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);

    this.api
      .getJobHistory(HISTORY_LIMIT)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: jobs => this.jobs.set(sortByNewest(jobs ?? [])),
        error: (error: unknown) => {
          console.error('Failed to load job history:', error);
          this.error.set(extractMessage(error) ?? 'Failed to load job history');
        }
      });
  }

  select(job: JobDTO): void {
    void this.router.navigate(['/jobs', job.id]);
  }

  clearSelection(): void {
    void this.router.navigate(['/jobs']);
  }

  canViewSolution(job: JobDTO): boolean {
    return job.status === 'COMPLETED' && job.solution_id != null;
  }

  /** Loads the solution into the shared store, then shows it on the dashboard. */
  viewSolution(job: JobDTO): void {
    const solutionId = job.solution_id;
    if (!this.canViewSolution(job) || solutionId == null) return;
    if (this.openingSolutionFor() !== null) return;

    this.openingSolutionFor.set(job.id);

    this.solutionStore
      .loadById(solutionId)
      .pipe(
        finalize(() => this.openingSolutionFor.set(null)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => {
          this.toast.success('Solution loaded! Switch between tabs to view it');
          void this.router.navigate(['/main']);
        },
        error: (error: unknown) => {
          console.error('Failed to load solution:', error);
          this.toast.error(extractMessage(error) ?? 'Failed to load solution');
        }
      });
  }

  statusClass(status: JobDTO['status']): string {
    switch (status) {
      case 'COMPLETED':
        return 'badge badge-success';
      case 'PROCESSING':
      case 'PENDING':
        return 'badge badge-info';
      case 'CANCELLED':
        return 'badge badge-neutral';
      case 'FAILED':
        return 'badge badge-danger';
    }
  }

  progressOf(job: JobDTO): number {
    const progress = job.progress;
    if (progress === null || progress === undefined || !Number.isFinite(progress)) {
      return job.status === 'COMPLETED' ? 100 : 0;
    }
    return Math.max(0, Math.min(100, progress));
  }
}

function sortByNewest(jobs: readonly JobDTO[]): JobDTO[] {
  return [...jobs].sort((a, b) => {
    const timeA = Date.parse(a.created_at);
    const timeB = Date.parse(b.created_at);

    if (Number.isNaN(timeA) || Number.isNaN(timeB)) return b.id - a.id;
    return timeB - timeA;
  });
}

function extractMessage(error: unknown): string | null {
  if (typeof error === 'object' && error !== null && 'message' in error) {
    const message = (error as { message?: unknown }).message;
    if (typeof message === 'string' && message.trim().length > 0) return message;
  }
  return null;
}
