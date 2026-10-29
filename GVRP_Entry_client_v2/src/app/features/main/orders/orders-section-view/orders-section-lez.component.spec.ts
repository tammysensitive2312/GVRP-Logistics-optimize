import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';

import { OrderDTO, PaginatedResponse } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { LezApiService } from '@core/services/lez-api.service';
import { SolutionStore } from '@core/services/solution.store';
import { ToastService } from '@shared/services/toast.service';
import { VehicleSelectionService } from '@shared/services/vehicle-selection.service';

import { OrdersSectionComponent } from './orders-section.component';

/**
 * OB-T1 (order zone badge) and JD-T1 (D4 refusal of a multi-date selection) of
 * LEZ-001 P1c. New spec file: the accepted `orders-section.component.spec.ts`
 * keeps its own assertions unchanged.
 */
describe('OrdersSectionComponent LEZ additions', () => {
  let fixture: ComponentFixture<OrdersSectionComponent>;
  let component: OrdersSectionComponent;
  let api: jasmine.SpyObj<ApiService>;
  let lez: jasmine.SpyObj<LezApiService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let toast: jasmine.SpyObj<ToastService>;

  const order = (id: number, deliveryDate = '2026-10-11'): OrderDTO => ({
    id,
    branch_id: 1,
    order_code: `ORD-${id}`,
    customer_name: 'Khach',
    address: 'Hanoi',
    latitude: 21,
    longitude: 105,
    demand: 10,
    service_time: 15,
    priority: 1,
    delivery_date: deliveryDate,
    status: 'SCHEDULED',
    created_at: '',
    updated_at: ''
  });

  const setup = async (
    orders: OrderDTO[],
    zoneRows: () => ReturnType<LezApiService['orderZones']>
  ): Promise<void> => {
    api = jasmine.createSpyObj<ApiService>('ApiService',
      ['getOrders', 'bulkUpdateOrderStatus', 'submitRoutePlanningJob']);
    api.getOrders.and.returnValue(of({
      content: orders, total_elements: orders.length, total_pages: 1
    } as unknown as PaginatedResponse<OrderDTO>));
    lez = jasmine.createSpyObj<LezApiService>('LezApiService', ['orderZones']);
    lez.orderZones.and.callFake(zoneRows);
    dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    toast = jasmine.createSpyObj<ToastService>('ToastService',
      ['success', 'error', 'info']);
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
        { provide: LezApiService, useValue: lez },
        { provide: MatDialog, useValue: dialog },
        { provide: ToastService, useValue: toast },
        { provide: VehicleSelectionService, useValue: vehicles },
        { provide: SolutionStore, useValue: store },
        { provide: Router, useValue: router }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(OrdersSectionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  it('OB-T1: asks for the zones of the visible page and renders the chip', async () => {
    await setup([order(1), order(2)], () => of([
      { order_id: 1, zones: [{ branch_zone_id: 17, name: 'Hanoi LEZ' }] },
      { order_id: 2, zones: [] }
    ]));

    expect(lez.orderZones).toHaveBeenCalledWith([1, 2]);
    expect(fixture.nativeElement.textContent).toContain('LEZ: Hanoi LEZ');
  });

  it('OB-T1: a failed request shows no chip and no toast', async () => {
    await setup([order(1)], () => throwError(() => new Error('membership down')));

    expect(fixture.nativeElement.textContent).not.toContain('LEZ:');
    expect(toast.error).not.toHaveBeenCalled();
  });

  it('JD-T1: two delivery dates never open the plan dialog and both dates are named', async () => {
    await setup([], () => of([]));
    component.selectedOrders = [order(1, '2026-10-11'), order(2, '2026-10-12')];

    component.onPlanRoutesClick();

    expect(dialog.open).not.toHaveBeenCalled();
    expect(toast.error).toHaveBeenCalledWith(
      'Orders on different delivery dates cannot be planned together: 2026-10-11, 2026-10-12.'
    );
  });

  it('JD-T1: the server refusal message is surfaced on submit', async () => {
    await setup([], () => of([]));
    dialog.open.and.returnValue({
      afterClosed: () => of({
        optimizationGoal: 'DISTANCE',
        optimizationSpeed: 'NORMAL',
        timeWindowMode: 'SOFT',
        allowUnassigned: true,
        enablePareto: false
      })
    } as never);
    api.submitRoutePlanningJob.and.returnValue(throwError(() => ({
      status: 400,
      error: { errors: [{ message: 'All orders of one plan must share a delivery date' }] }
    })));
    component.selectedOrders = [order(1)];

    component.onPlanRoutesClick();

    expect(api.submitRoutePlanningJob).toHaveBeenCalled();
    expect(toast.error).toHaveBeenCalledWith(
      'All orders of one plan must share a delivery date'
    );
  });
});
