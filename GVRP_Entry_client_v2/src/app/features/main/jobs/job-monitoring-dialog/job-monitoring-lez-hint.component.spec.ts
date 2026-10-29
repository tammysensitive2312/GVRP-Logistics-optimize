import { TestBed } from '@angular/core/testing';
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

/** BF-C4 (BROWSER-FIX-001, C5): the unserved alert also points to the LEZ panel. */
describe('JobMonitoringDialogComponent LEZ hint', () => {
  it('BF-C4: keeps the existing sentence and mentions low-emission zones', async () => {
    const solution: SolutionDTO = {
      id: 9, job_id: 7, total_cost: 1_000_000, total_distance: 80, total_time: 6,
      total_co2: 12, total_vehicles_used: 3, served_orders: 118, unserved_orders: 2, routes: []
    };
    const frontier = {
      job_id: 7, solution_id: 9, latest_solution_id: 9, active_candidate_id: null,
      clustered: false, cluster_count: null, candidates: []
    } as unknown as ParetoFrontierDTO;
    const api = jasmine.createSpyObj<ApiService>('ApiService',
      ['getSolutionById', 'getPareto', 'getJobById', 'cancelJob']);
    api.getSolutionById.and.returnValue(of(solution));
    api.getPareto.and.returnValue(of(frontier));
    const polling = {
      jobProgress$: new Subject<JobProgressDTO>(),
      jobUpdated$: new Subject<JobDTO>(),
      jobCompleted$: new Subject<JobDTO>(),
      jobFailed$: new Subject<JobDTO>(),
      jobCancelled$: new Subject<JobDTO>(),
      pollError$: new Subject<number>(),
      startPolling: jasmine.createSpy('startPolling'),
      stopPolling: jasmine.createSpy('stopPolling')
    } as unknown as JobPollingService;
    const job: JobDTO = {
      id: 7, status: 'COMPLETED', progress: 100, created_at: '2026-10-18T08:00:00', solution_id: 9
    };

    await TestBed.configureTestingModule({
      imports: [JobMonitoringDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: JobPollingService, useValue: polling },
        { provide: MatDialogRef, useValue: jasmine.createSpyObj('MatDialogRef', ['close']) },
        { provide: MAT_DIALOG_DATA, useValue: { job, enablePolling: false } },
        { provide: ConfirmService, useValue: jasmine.createSpyObj('ConfirmService', ['ask']) },
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['success', 'error']) },
        { provide: TranslocoService, useValue: { translate: (key: string) => key } }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(JobMonitoringDialogComponent);
    fixture.detectChanges();
    fixture.detectChanges();

    const alert = (fixture.nativeElement as HTMLElement).querySelector('.alert-warning');
    expect(alert).not.toBeNull();
    const text = alert?.textContent ?? '';
    expect(text).toContain('Check capacity, time windows and the number of selected vehicles.');
    expect(text).toContain('low-emission');
    expect(text).toContain('compliance panel of the Route tab');
  });
});
