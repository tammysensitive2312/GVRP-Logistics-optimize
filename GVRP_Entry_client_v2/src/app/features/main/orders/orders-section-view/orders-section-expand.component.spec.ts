import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { signal } from '@angular/core';

import {
  OrderDTO,
  PaginatedResponse,
  ParetoFrontierDTO,
  SolutionComplianceDTO,
  SolutionDTO
} from '@core/models';
import { ApiService } from '@core/services/api.service';
import { LezApiService } from '@core/services/lez-api.service';
import { SolutionStore } from '@core/services/solution.store';
import { MapService } from '@shared/services/map.service';
import { ToastService } from '@shared/services/toast.service';
import { VehicleSelectionService } from '@shared/services/vehicle-selection.service';

import { ExpandedViewDialogComponent } from '../expanded-view-dialog/expanded-view-dialog.component';
import { OrdersSectionComponent } from './orders-section.component';

/** UL-T8 (UX-LEZ-001, D8): Expand opens the large dialog; closing reloads the tab's panel. */
describe('OrdersSectionComponent expand', () => {
  let fixture: ComponentFixture<OrdersSectionComponent>;
  let component: OrdersSectionComponent;
  let dialog: jasmine.SpyObj<MatDialog>;

  const solution = {
    id: 9, job_id: 7, total_cost: 1, total_distance: 1, total_time: 1, total_co2: 1,
    total_vehicles_used: 0, served_orders: 0, unserved_orders: 0, routes: []
  } as unknown as SolutionDTO;

  beforeEach(async () => {
    const api = jasmine.createSpyObj<ApiService>('ApiService',
      ['getOrders', 'bulkUpdateOrderStatus', 'submitRoutePlanningJob', 'getPareto']);
    api.getOrders.and.returnValue(of({
      content: [], total_elements: 0, total_pages: 0
    } as unknown as PaginatedResponse<OrderDTO>));
    api.getPareto.and.returnValue(of({ candidates: [] } as unknown as ParetoFrontierDTO));
    const lez = jasmine.createSpyObj<LezApiService>('LezApiService', ['orderZones', 'solutionCompliance']);
    lez.orderZones.and.returnValue(of([]));
    lez.solutionCompliance.and.returnValue(of({
      solution_id: 9, routes: [], zones: [], lez_unassigned: []
    } as unknown as SolutionComplianceDTO));
    dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    dialog.open.and.returnValue({ afterClosed: () => of(undefined) } as never);
    const store = jasmine.createSpyObj<SolutionStore>('SolutionStore', ['loadById', 'setSolution', 'clear']);
    (store as unknown as { solution: unknown }).solution = signal(solution).asReadonly();

    await TestBed.configureTestingModule({
      imports: [OrdersSectionComponent, NoopAnimationsModule],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: LezApiService, useValue: lez },
        { provide: MatDialog, useValue: dialog },
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['success', 'error', 'info']) },
        { provide: VehicleSelectionService, useValue: { getSelectedIds: () => [], selectedVehicleIds$: of([]) } },
        { provide: SolutionStore, useValue: store },
        { provide: MapService, useValue: jasmine.createSpyObj('MapService', ['focusLeg', 'displaySolution']) },
        { provide: Router, useValue: jasmine.createSpyObj<Router>('Router', ['navigate']) }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(OrdersSectionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('UL-T8: Expand on Route opens the dialog with the route view and the solution', () => {
    const panel = { load: jasmine.createSpy('load') };
    (component as unknown as { compliancePanel: unknown }).compliancePanel = panel;

    component.openExpanded('route');

    expect(dialog.open).toHaveBeenCalledTimes(1);
    const [componentType, config] = dialog.open.calls.mostRecent().args;
    expect(componentType).toBe(ExpandedViewDialogComponent);
    expect(config?.data).toEqual({ view: 'route', solution });
    expect(config?.width).toBe('95vw');
    expect(config?.height).toBe('90vh');
    expect(panel.load).toHaveBeenCalledTimes(1);
  });

  it('UL-T8: Expand on Timeline opens the timeline view', () => {
    component.openExpanded('timeline');

    expect(dialog.open.calls.mostRecent().args[1]?.data).toEqual({ view: 'timeline', solution });
  });
});
