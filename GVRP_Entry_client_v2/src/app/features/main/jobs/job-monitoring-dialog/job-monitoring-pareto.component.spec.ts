import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, Subject } from 'rxjs';
import { TranslocoService } from '@jsverse/transloco';

import { JobDTO, JobProgressDTO, ParetoFrontierDTO, SolutionDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { JobPollingService } from '@core/services/job-polling.service';
import { ConfirmService } from '@shared/services/confirm.service';
import { ToastService } from '@shared/services/toast.service';

import { JobMonitoringDialogComponent } from './job-monitoring-dialog.component';

/** U-T7: "Scenario k of n" is rendered only when the progress payload carries scenario keys. */
describe('JobMonitoringDialogComponent Pareto progress', () => {
  let fixture: ComponentFixture<JobMonitoringDialogComponent>;
  let progress$: Subject<JobProgressDTO>;
  let api: jasmine.SpyObj<ApiService>;

  const runningJob: JobDTO = {
    id: 7, status: 'PROCESSING', progress: 20, created_at: '2026-10-18T08:00:00'
  };

  const progress = (partial: Partial<JobProgressDTO>): JobProgressDTO => ({
    jobId: 7, phase: 'SOLVING', status: 'RUNNING', startedAt: '2026-10-18T08:00:00',
    percent: 50, iteration: 50, maxIterations: 100,
    ...partial
  });

  const frontier: ParetoFrontierDTO = {
    job_id: 7, solution_id: 9, latest_solution_id: 9, active_candidate_id: null,
    clustered: false, cluster_count: null,
    candidates: [1, 2, 3].map(i => ({
      id: i, index: i - 1, label: `K${i - 1}`, cost_weight: 0.5, co2_weight: 0.5,
      total_cost: 1_000_000, total_distance: 80, total_co2: 12, total_time: 6,
      total_vehicles_used: 3, served_orders: 120, unserved_orders: 0,
      frontier_status: 'FRONTIER' as const, engine_selected: i === 1, applicable: true,
      not_applicable_reason: null
    }))
  };

  const emptyFrontier: ParetoFrontierDTO = { ...frontier, candidates: [] };

  interface SetupOptions {
    job: JobDTO;
    solution?: SolutionDTO;
    frontier?: ParetoFrontierDTO;
  }

  async function setup(options: SetupOptions): Promise<void> {
    progress$ = new Subject<JobProgressDTO>();
    api = jasmine.createSpyObj<ApiService>('ApiService',
      ['getSolutionById', 'getPareto', 'getJobById', 'cancelJob']);
    if (options.solution) api.getSolutionById.and.returnValue(of(options.solution));
    api.getPareto.and.returnValue(of(options.frontier ?? emptyFrontier));

    const polling = {
      jobProgress$: progress$,
      jobUpdated$: new Subject<JobDTO>(),
      jobCompleted$: new Subject<JobDTO>(),
      jobFailed$: new Subject<JobDTO>(),
      jobCancelled$: new Subject<JobDTO>(),
      pollError$: new Subject<number>(),
      startPolling: jasmine.createSpy('startPolling'),
      stopPolling: jasmine.createSpy('stopPolling')
    } as unknown as JobPollingService;

    await TestBed.configureTestingModule({
      imports: [JobMonitoringDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: JobPollingService, useValue: polling },
        {
          provide: MatDialogRef,
          useValue: jasmine.createSpyObj('MatDialogRef', ['close'])
        },
        { provide: MAT_DIALOG_DATA, useValue: { job: options.job, enablePolling: false } },
        { provide: ConfirmService, useValue: jasmine.createSpyObj('ConfirmService', ['ask']) },
        {
          provide: ToastService,
          useValue: jasmine.createSpyObj('ToastService', ['success', 'error'])
        },
        { provide: TranslocoService, useValue: { translate: (key: string) => key } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(JobMonitoringDialogComponent);
    fixture.detectChanges();
  }

  it('renders "Scenario 3 of 5" and five dots when progress has scenario keys', async () => {
    await setup({ job: runningJob });

    progress$.next(progress({ scenario: 3, scenarioCount: 5 }));
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.textContent).toContain('Scenario 3 of 5');
    expect(host.querySelectorAll('.scenario-dot').length).toBe(5);
  });

  it('does not render a scenario hint without scenario keys', async () => {
    await setup({ job: runningJob });

    progress$.next(progress({}));
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.textContent).not.toContain('Scenario');
    expect(host.querySelectorAll('.scenario-dot').length).toBe(0);
  });

  it('shows a Pareto summary line when the completed solution has a frontier', async () => {
    const solution: SolutionDTO = {
      id: 9, job_id: 7, total_cost: 1_000_000, total_distance: 80, total_time: 6,
      total_co2: 12, total_vehicles_used: 3, served_orders: 120, unserved_orders: 0, routes: []
    };

    await setup({
      job: { ...runningJob, status: 'COMPLETED', solution_id: 9 },
      solution,
      frontier
    });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent)
      .toContain('Pareto: 3 plans on the frontier');
  });
});
