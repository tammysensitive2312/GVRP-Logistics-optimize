import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { signal } from '@angular/core';

import { OrderDTO, PaginatedResponse, ParetoFrontierDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { SolutionStore } from '@core/services/solution.store';
import { ConfirmService } from '@shared/services/confirm.service';
import { ToastService } from '@shared/services/toast.service';
import { VehicleSelectionService } from '@shared/services/vehicle-selection.service';

import { OrdersSectionComponent } from './orders-section.component';

/** U-T6: the Pareto tab is rendered only when the frontier has candidates. */
describe('OrdersSectionComponent Pareto tab', () => {
  let fixture: ComponentFixture<OrdersSectionComponent>;
  let component: OrdersSectionComponent;
  let api: jasmine.SpyObj<ApiService>;

  const frontier: ParetoFrontierDTO = {
    job_id: 4,
    solution_id: 9,
    latest_solution_id: 9,
    active_candidate_id: null,
    clustered: false,
    cluster_count: null,
    candidates: [{
      id: 1, index: 0, label: 'K0', cost_weight: 0.5, co2_weight: 0.5,
      total_cost: 1_000_000, total_distance: 80, total_co2: 12, total_time: 6,
      total_vehicles_used: 3, served_orders: 120, unserved_orders: 0,
      frontier_status: 'FRONTIER', engine_selected: true, applicable: true,
      not_applicable_reason: null
    }]
  };

  beforeEach(async () => {
    api = jasmine.createSpyObj<ApiService>('ApiService',
      ['getOrders', 'bulkUpdateOrderStatus', 'getPareto']);
    api.getOrders.and.returnValue(of({
      content: [], total_elements: 0, total_pages: 0
    } as unknown as PaginatedResponse<OrderDTO>));
    api.getPareto.and.returnValue(of(frontier));

    const dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    const toast = jasmine.createSpyObj<ToastService>('ToastService',
      ['success', 'error', 'info']);
    const confirm = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['ask']);
    const vehicles = {
      getSelectedIds: () => [],
      selectedVehicleIds$: of([])
    } as unknown as jasmine.SpyObj<VehicleSelectionService>;
    const store = jasmine.createSpyObj<SolutionStore>('SolutionStore',
      ['loadById', 'setSolution', 'clear']);
    (store as unknown as { solution: unknown }).solution = signal(null).asReadonly();
    const router = jasmine.createSpyObj<Router>('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [OrdersSectionComponent, NoopAnimationsModule],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: MatDialog, useValue: dialog },
        { provide: ToastService, useValue: toast },
        { provide: ConfirmService, useValue: confirm },
        { provide: VehicleSelectionService, useValue: vehicles },
        { provide: SolutionStore, useValue: store },
        { provide: Router, useValue: router }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(OrdersSectionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('hides the Pareto tab when there are no candidates', () => {
    component.paretoFrontier.set({ ...frontier, candidates: [] });
    fixture.detectChanges();

    expect(component.showParetoTab()).toBeFalse();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Pareto');
  });

  it('shows the Pareto tab when candidates exist', () => {
    component.paretoFrontier.set(frontier);
    fixture.detectChanges();

    expect(component.showParetoTab()).toBeTrue();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Pareto');
  });
});
