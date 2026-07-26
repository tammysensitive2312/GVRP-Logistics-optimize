import { TestBed, fakeAsync, tick, discardPeriodicTasks } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';

import { JobDTO, JobProgressDTO } from '@core/models';

import { ApiService } from './api.service';
import { JobPollingService } from './job-polling.service';

describe('JobPollingService', () => {
  let service: JobPollingService;
  let api: jasmine.SpyObj<Pick<ApiService, 'getJobProgress' | 'getJobById'>>;

  const JOB_ID = 27;
  const INTERVAL = 1000;

  const running = (overrides: Partial<JobProgressDTO> = {}): JobProgressDTO => ({
    jobId: JOB_ID,
    phase: 'SOLVING',
    status: 'RUNNING',
    startedAt: '2026-07-26T05:26:08Z',
    percent: 32,
    iteration: 650,
    maxIterations: 2000,
    routes: 4,
    unassigned: 19,
    bestCost: 7154047.38,
    elapsedSeconds: 3,
    finishedAt: null,
    ...overrides
  });

  const job = (status: JobDTO['status'], solutionId?: number): JobDTO => ({
    id: JOB_ID,
    status,
    progress: status === 'COMPLETED' ? 100 : 40,
    created_at: '2026-07-26T05:26:00Z',
    solution_id: solutionId
  });

  beforeEach(() => {
    api = jasmine.createSpyObj<Pick<ApiService, 'getJobProgress' | 'getJobById'>>(
      'ApiService',
      ['getJobProgress', 'getJobById']
    );

    TestBed.configureTestingModule({
      providers: [JobPollingService, { provide: ApiService, useValue: api }]
    });

    service = TestBed.inject(JobPollingService);
  });

  it('emits solver telemetry from the progress endpoint', fakeAsync(() => {
    api.getJobProgress.and.returnValue(of(running()));

    const seen: JobProgressDTO[] = [];
    service.jobProgress$.subscribe(progress => seen.push(progress));

    service.startPolling(JOB_ID, INTERVAL);
    tick(INTERVAL);

    expect(api.getJobProgress).toHaveBeenCalledWith(JOB_ID);
    expect(seen.length).toBe(1);
    expect(seen[0].percent).toBe(32);
    expect(seen[0].iteration).toBe(650);
    expect(service.isPolling()).toBeTrue();

    service.stopPolling();
    discardPeriodicTasks();
  }));

  it('keeps polling across the BUILDING_MATRIX phase (no percent yet)', fakeAsync(() => {
    api.getJobProgress.and.returnValue(
      of({
        jobId: JOB_ID,
        phase: 'BUILDING_MATRIX',
        status: 'RUNNING',
        startedAt: '2026-07-26T05:26:08Z',
        finishedAt: null
      } as JobProgressDTO)
    );

    service.startPolling(JOB_ID, INTERVAL);
    tick(INTERVAL * 3);

    expect(api.getJobProgress).toHaveBeenCalledTimes(3);
    expect(api.getJobById).not.toHaveBeenCalled();

    service.stopPolling();
    discardPeriodicTasks();
  }));

  it('reads the job once when progress reports a terminal status', fakeAsync(() => {
    api.getJobProgress.and.returnValue(of(running({ status: 'COMPLETED', percent: 100 })));
    api.getJobById.and.returnValue(of(job('COMPLETED', 55)));

    const completed: JobDTO[] = [];
    service.jobCompleted$.subscribe(value => completed.push(value));

    service.startPolling(JOB_ID, INTERVAL);
    tick(INTERVAL);

    expect(api.getJobById).toHaveBeenCalledOnceWith(JOB_ID);
    expect(completed.length).toBe(1);
    expect(completed[0].solution_id).toBe(55);
    expect(service.isPolling()).toBeFalse();

    discardPeriodicTasks();
  }));

  it('treats 204 (progress cleared) as finished', fakeAsync(() => {
    api.getJobProgress.and.returnValue(of(null));
    api.getJobById.and.returnValue(of(job('COMPLETED', 7)));

    service.startPolling(JOB_ID, INTERVAL);
    tick(INTERVAL);

    expect(api.getJobById).toHaveBeenCalledOnceWith(JOB_ID);
    expect(service.isPolling()).toBeFalse();

    discardPeriodicTasks();
  }));

  it('routes CANCELLED to its own stream, not to failure', fakeAsync(() => {
    api.getJobProgress.and.returnValue(of(running({ status: 'CANCELLED' })));
    api.getJobById.and.returnValue(of(job('CANCELLED')));

    const cancelled: JobDTO[] = [];
    const failed: JobDTO[] = [];
    service.jobCancelled$.subscribe(value => cancelled.push(value));
    service.jobFailed$.subscribe(value => failed.push(value));

    service.startPolling(JOB_ID, INTERVAL);
    tick(INTERVAL);

    expect(cancelled.length).toBe(1);
    expect(failed.length).toBe(0);

    discardPeriodicTasks();
  }));

  it('survives a failed poll and reports the failure count', fakeAsync(() => {
    const responses = new Subject<JobProgressDTO | null>();
    api.getJobProgress.and.returnValues(
      throwError(() => new Error('network')),
      responses.asObservable()
    );

    const errors: number[] = [];
    service.pollError$.subscribe(count => errors.push(count));

    service.startPolling(JOB_ID, INTERVAL);
    tick(INTERVAL);

    expect(errors).toEqual([1]);
    expect(service.isPolling()).toBeTrue();

    tick(INTERVAL);
    expect(api.getJobProgress).toHaveBeenCalledTimes(2);

    service.stopPolling();
    discardPeriodicTasks();
  }));

  it('gives up after five consecutive failures', fakeAsync(() => {
    api.getJobProgress.and.returnValue(throwError(() => new Error('network')));

    service.startPolling(JOB_ID, INTERVAL);
    tick(INTERVAL * 5);

    expect(service.isPolling()).toBeFalse();

    discardPeriodicTasks();
  }));
});
