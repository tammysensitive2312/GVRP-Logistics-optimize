import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';

import { OrderDTO, OrderFilter, PaginatedResponse } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { LezApiService } from '@core/services/lez-api.service';
import { SolutionStore } from '@core/services/solution.store';
import { ToastService } from '@shared/services/toast.service';
import { VehicleSelectionService } from '@shared/services/vehicle-selection.service';
import { localDateIso } from '@shared/utils/date.utils';

import { OrdersSectionComponent } from './orders-section.component';

/**
 * MO-T1 of MAP-ORDERS-001 (the map follows the table) and the D2 default
 * date. New spec file: the accepted orders-section specs stay unchanged.
 */
describe('OrdersSectionComponent map feed', () => {
  let fixture: ComponentFixture<OrdersSectionComponent>;
  let component: OrdersSectionComponent;
  let api: jasmine.SpyObj<ApiService>;
  let emitted: OrderDTO[][];

  const order = (id: number, deliveryDate = '2026-10-29'): OrderDTO => ({
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

  const page = (orders: OrderDTO[]) => of({
    content: orders, total_elements: 12, total_pages: 3
  } as unknown as PaginatedResponse<OrderDTO>);

  beforeEach(async () => {
    api = jasmine.createSpyObj<ApiService>('ApiService',
      ['getOrders', 'bulkUpdateOrderStatus', 'submitRoutePlanningJob', 'getPareto']);
    // Page n of date d holds orders whose ids encode both, so each reload is distinguishable.
    api.getOrders.and.callFake((filter: OrderFilter, pageIndex: number) =>
      page([order(pageIndex * 10 + (filter.date === '2026-10-28' ? 100 : 1), filter.date ?? '')]));
    const lez = jasmine.createSpyObj<LezApiService>('LezApiService', ['orderZones']);
    lez.orderZones.and.returnValue(of([]));
    const vehicles = {
      getSelectedIds: () => [],
      selectedVehicleIds$: of([])
    } as unknown as VehicleSelectionService;
    const store = jasmine.createSpyObj<SolutionStore>('SolutionStore',
      ['loadById', 'setSolution', 'clear']);
    (store as unknown as { solution: unknown }).solution = signal(null).asReadonly();

    await TestBed.configureTestingModule({
      imports: [OrdersSectionComponent, NoopAnimationsModule],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: LezApiService, useValue: lez },
        { provide: MatDialog, useValue: jasmine.createSpyObj<MatDialog>('MatDialog', ['open']) },
        { provide: ToastService, useValue: jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error', 'info']) },
        { provide: VehicleSelectionService, useValue: vehicles },
        { provide: SolutionStore, useValue: store },
        { provide: Router, useValue: jasmine.createSpyObj<Router>('Router', ['navigate']) }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(OrdersSectionComponent);
    component = fixture.componentInstance;
    emitted = [];
    component.ordersLoaded.subscribe(orders => emitted.push(orders));
    fixture.detectChanges();
  });

  it('D2: the initial request uses the local today', () => {
    expect(api.getOrders.calls.first().args[0].date).toBe(localDateIso());
  });

  it('MO-T1: emits the loaded page, as a new array', () => {
    expect(emitted.length).toBe(1);
    expect(emitted[0].map(o => o.id)).toEqual([1]);
    expect(emitted[0]).not.toBe(component.orders);
  });

  it('MO-T1: emits again with the new page after a page change and a date change', () => {
    component.onPageChange({ pageIndex: 1, pageSize: 5, length: 12 });
    component.onFilterChange({ date: '2026-10-28' });

    expect(emitted.length).toBe(3);
    expect(emitted[1].map(o => o.id)).toEqual([11]);
    expect(emitted[2].map(o => o.id)).toEqual([100]);
    expect(emitted[2][0].delivery_date).toBe('2026-10-28');
  });

  it('MO-T1: a failed load emits nothing', () => {
    spyOn(console, 'error');
    api.getOrders.and.returnValue(throwError(() => new Error('boom')));

    component.onPageChange({ pageIndex: 2, pageSize: 5, length: 12 });

    expect(emitted.length).toBe(1);
  });
});
