import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { JobDTO, SolutionDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { SolutionStore } from '@core/services/solution.store';
import { ToastService } from '@shared/services/toast.service';

import { JobHistoryComponent } from './job-history.component';

const job = (partial: Partial<JobDTO>): JobDTO => ({
  id: 1,
  status: 'COMPLETED',
  progress: 100,
  created_at: '2026-07-20T08:00:00Z',
  ...partial
});

const solution: SolutionDTO = {
  id: 55,
  job_id: 3,
  total_cost: 1000,
  total_distance: 10,
  total_time: 1,
  total_co2: 2,
  total_vehicles_used: 1,
  served_orders: 3,
  unserved_orders: 0,
  routes: []
};

describe('JobHistoryComponent', () => {
  let fixture: ComponentFixture<JobHistoryComponent>;
  let component: JobHistoryComponent;

  let api: jasmine.SpyObj<Pick<ApiService, 'getJobHistory'>>;
  let solutionStore: jasmine.SpyObj<Pick<SolutionStore, 'loadById'>>;
  let toast: jasmine.SpyObj<ToastService>;
  let router: jasmine.SpyObj<Router>;
  let paramMap$: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  const jobs: JobDTO[] = [
    job({ id: 1, created_at: '2026-07-20T08:00:00Z', solution_id: 11 }),
    job({ id: 2, created_at: '2026-07-22T08:00:00Z', status: 'PROCESSING', progress: 40 }),
    job({ id: 3, created_at: '2026-07-21T08:00:00Z', status: 'FAILED', progress: 0, error_message: 'infeasible' })
  ];

  const createComponent = () => {
    fixture = TestBed.createComponent(JobHistoryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  beforeEach(async () => {
    api = jasmine.createSpyObj<Pick<ApiService, 'getJobHistory'>>('ApiService', [
      'getJobHistory'
    ]);
    solutionStore = jasmine.createSpyObj<Pick<SolutionStore, 'loadById'>>('SolutionStore', [
      'loadById'
    ]);
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error', 'info']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    paramMap$ = new BehaviorSubject(convertToParamMap({}));

    api.getJobHistory.and.returnValue(of(jobs));
    solutionStore.loadById.and.returnValue(of(solution));
    router.navigate.and.resolveTo(true);

    await TestBed.configureTestingModule({
      imports: [JobHistoryComponent],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: SolutionStore, useValue: solutionStore },
        { provide: ToastService, useValue: toast },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: { paramMap: paramMap$.asObservable() } }
      ]
    }).compileComponents();
  });

  it('loads history newest-first', () => {
    createComponent();

    expect(api.getJobHistory).toHaveBeenCalled();
    expect(component.jobs().map(j => j.id)).toEqual([2, 3, 1]);
  });

  it('shows the empty state when there are no jobs', () => {
    api.getJobHistory.and.returnValue(of([]));

    createComponent();

    expect(component.isEmpty()).toBeTrue();
  });

  it('surfaces load errors', () => {
    api.getJobHistory.and.returnValue(throwError(() => ({ message: 'boom' })));

    createComponent();

    expect(component.error()).toBe('boom');
    expect(component.loading()).toBeFalse();
  });

  it('selects the job from the route param', () => {
    paramMap$.next(convertToParamMap({ id: '3' }));

    createComponent();

    expect(component.selectedJob()?.id).toBe(3);
  });

  it('ignores a non-numeric route param', () => {
    paramMap$.next(convertToParamMap({ id: 'abc' }));

    createComponent();

    expect(component.selectedJob()).toBeNull();
  });

  it('only offers the solution for completed jobs that have one', () => {
    createComponent();

    expect(component.canViewSolution(job({ id: 1, solution_id: 11 }))).toBeTrue();
    expect(component.canViewSolution(job({ id: 1, solution_id: undefined }))).toBeFalse();
    expect(
      component.canViewSolution(job({ id: 2, status: 'PROCESSING', solution_id: 11 }))
    ).toBeFalse();
  });

  it('loads the solution into the store and goes to the dashboard', () => {
    createComponent();

    component.viewSolution(job({ id: 1, solution_id: 11 }));

    expect(solutionStore.loadById).toHaveBeenCalledWith(11);
    expect(toast.success).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/main']);
    expect(component.openingSolutionFor()).toBeNull();
  });

  it('stays put and reports when the solution fails to load', () => {
    solutionStore.loadById.and.returnValue(throwError(() => ({ message: 'gone' })));
    createComponent();

    component.viewSolution(job({ id: 1, solution_id: 11 }));

    expect(toast.error).toHaveBeenCalledWith('gone');
    expect(router.navigate).not.toHaveBeenCalledWith(['/main']);
  });

  it('deep links when a row is selected', () => {
    createComponent();

    component.select(job({ id: 3 }));

    expect(router.navigate).toHaveBeenCalledWith(['/jobs', 3]);
  });

  it('falls back to 100% progress for completed jobs missing the field', () => {
    createComponent();

    expect(component.progressOf(job({ progress: undefined as unknown as number }))).toBe(100);
    expect(
      component.progressOf(job({ status: 'FAILED', progress: undefined as unknown as number }))
    ).toBe(0);
    expect(component.progressOf(job({ progress: 140 }))).toBe(100);
  });
});
