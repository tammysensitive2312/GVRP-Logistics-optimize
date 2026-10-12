import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';

import { OrderDTO } from '@core/models';
import { PaginatedResponse } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { SolutionStore } from '@core/services/solution.store';
import { ToastService } from '@shared/services/toast.service';
import { VehicleSelectionService } from '@shared/services/vehicle-selection.service';

import { OrdersSectionComponent } from './orders-section.component';

describe('OrdersSectionComponent bulk edit', () => {
  let fixture: ComponentFixture<OrdersSectionComponent>;
  let component: OrdersSectionComponent;
  let api: jasmine.SpyObj<ApiService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let toast: jasmine.SpyObj<ToastService>;

  const order = (id: number): OrderDTO => ({
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
    delivery_date: '2026-10-11',
    status: 'SCHEDULED',
    created_at: '',
    updated_at: ''
  });

  beforeEach(async () => {
    api = jasmine.createSpyObj<ApiService>('ApiService',
      ['getOrders', 'bulkUpdateOrderStatus']);
    api.getOrders.and.returnValue(of({
      content: [], total_elements: 0, total_pages: 0
    } as unknown as PaginatedResponse<OrderDTO>));
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
  });

  it('does nothing without selection', () => {
    component.selectedOrders = [];

    component.onBulkEditStatus();

    expect(dialog.open).not.toHaveBeenCalled();
  });

  it('sends ids + status, reports, reloads and clears', () => {
    dialog.open.and.returnValue({
      afterClosed: () => of({ status: 'ON_ROUTE' })
    } as never);
    api.bulkUpdateOrderStatus.and.returnValue(of({
      updated: [{ id: 11, status: 'ON_ROUTE' }],
      failed: [{ id: 12, order_code: 'ORD-12', reason: 'Cannot transition' }]
    }));
    component.selectedOrders = [order(11), order(12)];

    component.onBulkEditStatus();

    expect(api.bulkUpdateOrderStatus).toHaveBeenCalledWith(
      { ids: [11, 12], status: 'ON_ROUTE' });
    expect(component.bulkResult()?.updated.length).toBe(1);
    expect(component.bulkResult()?.failed.length).toBe(1);
    expect(toast.success).toHaveBeenCalledWith('1 updated, 1 refused');
    expect(component.selectedOrders).toEqual([]);
    expect(api.getOrders.calls.count()).toBeGreaterThan(1);
  });

  it('reloads on transport failure without a report', () => {
    dialog.open.and.returnValue({
      afterClosed: () => of({ status: 'ON_ROUTE' })
    } as never);
    api.bulkUpdateOrderStatus.and.returnValue(
      throwError(() => new Error('down')));
    component.selectedOrders = [order(11)];

    component.onBulkEditStatus();

    expect(component.bulkResult()).toBeNull();
    expect(toast.error).toHaveBeenCalled();
  });

  it('clears selection on filter and page change', () => {
    component.selectedOrders = [order(11)];

    component.onFilterChange({ date: '2026-10-11' });
    expect(component.selectedOrders).toEqual([]);

    component.selectedOrders = [order(11)];
    component.onPageChange({ pageIndex: 1, pageSize: 5, length: 10 });
    expect(component.selectedOrders).toEqual([]);
  });

  it('groups refused rows by reason and collapses large lists', () => {
    dialog.open.and.returnValue({
      afterClosed: () => of({ status: 'COMPLETED' })
    } as never);
    const failed = Array.from({ length: 100 }, (_, i) => ({
      id: 100 + i,
      order_code: `ORD-${100 + i}`,
      reason: `Cannot transition order 'ORD-${100 + i}' from [SCHEDULED] to [COMPLETED]`
    }));
    api.bulkUpdateOrderStatus.and.returnValue(of({ updated: [], failed }));
    component.selectedOrders = [order(100)];

    component.onBulkEditStatus();

    expect(component.bulkGroups().length).toBe(1);
    expect(component.bulkGroups()[0].rows.length).toBe(100);
    expect(component.bulkGroups()[0].reason).toBe(
      'Cannot transition from [SCHEDULED] to [COMPLETED]');
    expect(component.bulkDetailsOpen()).toBeFalse();
  });

  it('opens details automatically for small refusal lists', () => {
    dialog.open.and.returnValue({
      afterClosed: () => of({ status: 'ON_ROUTE' })
    } as never);
    api.bulkUpdateOrderStatus.and.returnValue(of({
      updated: [],
      failed: [
        { id: 1, order_code: 'ORD-1', reason: 'Reason A' },
        { id: 2, order_code: 'ORD-2', reason: 'Reason B' }
      ]
    }));
    component.selectedOrders = [order(1), order(2)];

    component.onBulkEditStatus();

    expect(component.bulkGroups().length).toBe(2);
    expect(component.bulkDetailsOpen()).toBeTrue();
  });

  it('toggles details and resets on dismiss', () => {
    component.bulkDetailsOpen.set(false);

    component.toggleBulkDetails();
    expect(component.bulkDetailsOpen()).toBeTrue();

    component.clearBulkResult();
    expect(component.bulkResult()).toBeNull();
    expect(component.bulkDetailsOpen()).toBeFalse();
  });
  it('drops cleared fields instead of keeping stale values', () => {
    component.onFilterChange({ date: '2026-10-11', status: 'SCHEDULED' });
    expect(component.currentFilters).toEqual({ date: '2026-10-11', status: 'SCHEDULED' });

    // Clearing the date picker emits a filter WITHOUT the date key.
    component.onFilterChange({ status: 'SCHEDULED' });
    expect('date' in component.currentFilters).toBeFalse();
    expect(component.currentFilters).toEqual({ status: 'SCHEDULED' });
  });
});
