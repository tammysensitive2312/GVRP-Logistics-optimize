import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';

import {
  FrontierStatus,
  NotApplicableReason,
  ParetoCandidateDTO,
  ParetoFrontierDTO,
  SolutionDTO
} from '@core/models';
import { ApiService } from '@core/services/api.service';
import { SolutionStore } from '@core/services/solution.store';
import { ConfirmService } from '@shared/services/confirm.service';
import { ToastService } from '@shared/services/toast.service';

import { ParetoViewComponent } from './pareto-view.component';

const candidate = (
  partial: Partial<ParetoCandidateDTO> & { id: number; index: number }
): ParetoCandidateDTO => ({
  label: `K${partial.index}`,
  cost_weight: 0.5,
  co2_weight: 0.5,
  total_cost: 1_000_000,
  total_distance: 80,
  total_co2: 12,
  total_time: 6,
  total_vehicles_used: 3,
  served_orders: 120,
  unserved_orders: 0,
  frontier_status: 'FRONTIER',
  engine_selected: false,
  applicable: true,
  not_applicable_reason: null,
  ...partial
});

const solution: SolutionDTO = {
  id: 9,
  job_id: 4,
  total_cost: 1_000_000,
  total_distance: 80,
  total_time: 6,
  total_co2: 12,
  total_vehicles_used: 3,
  served_orders: 120,
  unserved_orders: 0,
  routes: []
};

const frontier: ParetoFrontierDTO = {
  job_id: 4,
  solution_id: 9,
  latest_solution_id: 10,
  active_candidate_id: 2,
  clustered: true,
  cluster_count: 7,
  candidates: [
    candidate({ id: 1, index: 0, total_cost: 900_000, total_co2: 14 }),
    candidate({ id: 2, index: 1, total_cost: 1_000_000, total_co2: 12,
      applicable: false, not_applicable_reason: 'ALREADY_ACTIVE' }),
    candidate({ id: 3, index: 2, total_cost: 1_100_000, total_co2: 10, engine_selected: true }),
    candidate({ id: 4, index: 3, total_cost: 1_200_000, total_co2: 15,
      frontier_status: 'DOMINATED', applicable: false, not_applicable_reason: 'NOT_ON_FRONTIER' }),
    candidate({ id: 5, index: 4, total_cost: 800_000, total_co2: 9, served_orders: 100,
      frontier_status: 'SERVICE_DOMINATED', applicable: false,
      not_applicable_reason: 'NOT_ON_FRONTIER' })
  ]
};

describe('ParetoViewComponent', () => {
  let fixture: ComponentFixture<ParetoViewComponent>;
  let component: ParetoViewComponent;
  let host: HTMLElement;
  let api: jasmine.SpyObj<ApiService>;
  let store: jasmine.SpyObj<SolutionStore>;
  let confirm: jasmine.SpyObj<ConfirmService>;
  let toast: jasmine.SpyObj<ToastService>;

  beforeEach(async () => {
    api = jasmine.createSpyObj<ApiService>('ApiService',
      ['applyParetoCandidate', 'latestRevisionId']);
    store = jasmine.createSpyObj<SolutionStore>('SolutionStore',
      ['setSolution', 'loadById']);
    confirm = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['ask']);
    confirm.ask.and.returnValue(of(true));
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);

    await TestBed.configureTestingModule({
      imports: [ParetoViewComponent, NoopAnimationsModule],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: SolutionStore, useValue: store },
        { provide: ConfirmService, useValue: confirm },
        { provide: ToastService, useValue: toast }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ParetoViewComponent);
    fixture.componentRef.setInput('solution', solution);
    fixture.componentRef.setInput('frontier', frontier);
    component = fixture.componentInstance;
    host = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  // U-T2
  it('renders one point group per candidate with distinct status markers', () => {
    expect(host.querySelectorAll('.point').length).toBe(5);
    expect(host.querySelectorAll('.point-marker--frontier').length).toBe(3);
    expect(host.querySelectorAll('.point-marker--dominated').length).toBe(1);
    expect(host.querySelectorAll('.point-cross').length).toBe(1);
    expect(host.querySelectorAll('.frontier-line').length).toBe(1);
  });

  it('marks the active point with a ring and the recommended point with a star', () => {
    expect(host.querySelectorAll('.active-ring').length).toBe(1);
    expect(host.querySelector('[data-candidate-id="2"] .active-ring')).not.toBeNull();
    expect(host.querySelector('[data-candidate-id="1"] .active-ring')).toBeNull();

    expect(host.querySelectorAll('.engine-star').length).toBe(1);
    expect(host.querySelector('[data-candidate-id="3"] .engine-star')).not.toBeNull();
  });

  it('shows the clustered approximation note with the cluster count', () => {
    expect(host.querySelector('.cluster-chip')?.textContent).toContain('Clustered · 7 clusters');
    expect(host.querySelector('.cluster-chip')?.getAttribute('title')).toContain('clustered problem');
  });

  // U-T3
  it('selects a candidate when its row is clicked', () => {
    const row = host.querySelector('[data-candidate-id="3"].candidate-row') as HTMLElement;

    row.click();
    fixture.detectChanges();

    expect(component.selectedId()).toBe(3);
    expect(host.querySelector('[data-candidate-id="3"].candidate-row')?.classList)
      .toContain('selected');
  });

  const reasonCases: {
    reason: NotApplicableReason;
    status: FrontierStatus;
    text: string;
  }[] = [
    { reason: 'NOT_ON_FRONTIER', status: 'DOMINATED',
      text: 'Dominated — another point is better on both cost and CO₂' },
    { reason: 'NOT_ON_FRONTIER', status: 'SERVICE_DOMINATED', text: 'Serves fewer orders' },
    { reason: 'ORDER_SET_DIFFERS', status: 'FRONTIER',
      text: 'Covers a different set of orders than the active plan' },
    { reason: 'NO_PLAN', status: 'FRONTIER', text: 'No stored routes for this point' },
    { reason: 'ALREADY_ACTIVE', status: 'FRONTIER', text: 'This is the active plan' }
  ];

  for (const testCase of reasonCases) {
    it(`disables Apply and shows the reason for ${testCase.reason}/${testCase.status}`, () => {
      const testFrontier: ParetoFrontierDTO = {
        ...frontier,
        active_candidate_id: 1,
        candidates: [
          candidate({ id: 1, index: 0 }),
          candidate({
            id: 2,
            index: 1,
            frontier_status: testCase.status,
            applicable: false,
            not_applicable_reason: testCase.reason
          })
        ]
      };
      fixture.componentRef.setInput('frontier', testFrontier);
      component.select(2);
      fixture.detectChanges();

      const button = host.querySelector('.btn-apply') as HTMLButtonElement;
      expect(button.disabled).toBeTrue();
      expect(host.querySelector('.reason-text')?.textContent).toContain(testCase.text);
    });
  }

  it('enables Apply for an applicable, non-active candidate', () => {
    component.select(1);
    fixture.detectChanges();

    const button = host.querySelector('.btn-apply') as HTMLButtonElement;
    expect(button).not.toBeNull();
    expect(button.disabled).toBeFalse();
    expect(host.querySelector('.reason-text')).toBeNull();
  });

  // U-T4
  it('applies the selected candidate, updates the store and emits reload', () => {
    const saved: SolutionDTO = { ...solution, id: 11 };
    api.applyParetoCandidate.and.returnValue(of(saved));
    let reloads = 0;
    component.reload.subscribe(() => reloads++);

    component.select(1);
    fixture.detectChanges();
    component.apply();

    expect(confirm.ask).toHaveBeenCalled();
    expect(api.applyParetoCandidate).toHaveBeenCalledWith(9, 1);
    expect(store.setSolution).toHaveBeenCalledWith(saved);
    expect(reloads).toBe(1);
    expect(toast.success).toHaveBeenCalled();
  });

  it('does nothing when the confirmation is dismissed', () => {
    confirm.ask.and.returnValue(of(false));
    component.select(1);
    fixture.detectChanges();

    component.apply();

    expect(api.applyParetoCandidate).not.toHaveBeenCalled();
    expect(store.setSolution).not.toHaveBeenCalled();
  });

  // U-T5
  it('shows the conflict banner on 409 and loads the latest revision', () => {
    api.applyParetoCandidate.and.returnValue(throwError(() => ({ status: 409 })));
    api.latestRevisionId.and.returnValue(of({ id: 42 }));
    store.loadById.and.returnValue(of({ ...solution, id: 42 }));

    component.select(1);
    fixture.detectChanges();
    component.apply();
    fixture.detectChanges();

    expect(store.setSolution).not.toHaveBeenCalled();
    const banner = host.querySelector('.conflict-banner');
    expect(banner?.textContent).toContain('A newer revision exists.');

    (banner?.querySelector('button') as HTMLButtonElement).click();
    expect(api.latestRevisionId).toHaveBeenCalledWith(9);
    expect(store.loadById).toHaveBeenCalledWith(42);
  });

  it('shows the server message on 422 and leaves the store untouched', () => {
    api.applyParetoCandidate.and.returnValue(throwError(() => ({
      status: 422,
      error: { errors: [{ message: 'Order set differs from the active plan' }] }
    })));

    component.select(1);
    fixture.detectChanges();
    component.apply();

    expect(toast.error).toHaveBeenCalledWith('Order set differs from the active plan');
    expect(store.setSolution).not.toHaveBeenCalled();
    expect(host.querySelector('.conflict-banner')).toBeNull();
  });

  it('treats a 400 with code 0040402 as "Plan not found"', () => {
    api.applyParetoCandidate.and.returnValue(throwError(() => ({
      status: 400,
      error: { errors: [{ code: '0040402', message: 'not found' }] }
    })));

    component.select(1);
    fixture.detectChanges();
    component.apply();

    expect(toast.error).toHaveBeenCalledWith('Plan not found');
    expect(store.setSolution).not.toHaveBeenCalled();
  });
});
