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
 * BF-C2 (BROWSER-FIX-001, C2): "Select All Orders" selects every order
 * matching the filters across all pages; any filter, page or row change ends it.
 */
describe('OrdersSectionComponent select all matching', () => {
  let fixture: ComponentFixture<OrdersSectionComponent>;
  let component: OrdersSectionComponent;
  let api: jasmine.SpyObj<ApiService>;
  let toast: jasmine.SpyObj<ToastService>;

  const order = (id: number): OrderDTO => ({
    id, branch_id: 1, order_code: `ORD-${id}`, customer_name: 'Khach', address: 'Hanoi',
    latitude: 21, longitude: 105, demand: 10, service_time: 15, priority: 1,
    delivery_date: '2026-10-29', status: 'SCHEDULED', created_at: '', updated_at: ''
  });
  const pageOf = (orders: OrderDTO[], total: number) => of({
    content: orders, total_elements: total, total_pages: Math.ceil(total / 5)
  } as unknown as PaginatedResponse<OrderDTO>);
  const all = Array.from({ length: 37 }, (_, i) => order(i + 1));

  const setup = async (total: number): Promise<void> => {
    api = jasmine.createSpyObj<ApiService>('ApiService',
      ['getOrders', 'bulkUpdateOrderStatus', 'submitRoutePlanningJob', 'getPareto']);
    api.getOrders.and.callFake((_filter, page, size) =>
      page === 0 && size === total ? pageOf(all.slice(0, total), total) : pageOf(all.slice(0, Math.min(5, total)), total));
    const lez = jasmine.createSpyObj<LezApiService>('LezApiService', ['orderZones']);
    lez.orderZones.and.returnValue(of([]));
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error', 'info']);
    const store = jasmine.createSpyObj<SolutionStore>('SolutionStore', ['loadById', 'setSolution', 'clear']);
    (store as unknown as { solution: unknown }).solution = signal(null).asReadonly();

    await TestBed.configureTestingModule({
      imports: [OrdersSectionComponent, NoopAnimationsModule],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: LezApiService, useValue: lez },
        { provide: MatDialog, useValue: jasmine.createSpyObj<MatDialog>('MatDialog', ['open']) },
        { provide: ToastService, useValue: toast },
        { provide: VehicleSelectionService, useValue: { getSelectedIds: () => [], selectedVehicleIds$: of([]) } },
        { provide: SolutionStore, useValue: store },
        { provide: Router, useValue: jasmine.createSpyObj<Router>('Router', ['navigate']) }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(OrdersSectionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  const selectAll = (): void => {
    component.onSelectAllClick();
    fixture.detectChanges();
  };

  it('BF-C2: requests every matching order with the table filters and selects them', async () => {
    await setup(37);
    api.getOrders.calls.reset();

    selectAll();

    expect(api.getOrders).toHaveBeenCalledOnceWith(component.currentFilters, 0, 37);
    expect(component.selectedOrders.length).toBe(37);
    expect(component.allMatchingSelected).toBeTrue();
    expect(component.orders.length).toBe(5);
    expect(toast.success).toHaveBeenCalledWith('Selected 37 orders matching the filters.');
    expect((fixture.nativeElement as HTMLElement).textContent)
      .toContain('All 37 orders matching the filters are selected.');
  });

  it('BF-C2: the toolbar output is wired to select all', async () => {
    await setup(37);
    const spy = spyOn(component, 'onSelectAllClick');

    const toolbar = fixture.debugElement.query(
      debug => debug.name === 'app-orders-action-toolbar');
    toolbar.triggerEventHandler('selectAllClick');

    expect(spy).toHaveBeenCalled();
  });

  it('BF-C2: a page change ends the selection', async () => {
    await setup(37);
    selectAll();

    component.onPageChange({ pageIndex: 1, pageSize: 5, length: 37 });

    expect(component.allMatchingSelected).toBeFalse();
    expect(component.selectedOrders).toEqual([]);
  });

  it('BF-C2: a filter change ends the selection', async () => {
    await setup(37);
    selectAll();

    component.onFilterChange({ date: '2026-10-28' });

    expect(component.allMatchingSelected).toBeFalse();
  });

  it('BF-C2: a table selection event ends it and keeps the table selection', async () => {
    await setup(37);
    selectAll();

    component.onOrderSelectionChange([order(2)]);

    expect(component.allMatchingSelected).toBeFalse();
    expect(component.selectedOrders.map(o => o.id)).toEqual([2]);
  });

  it('BF-C2: clear selection ends it', async () => {
    await setup(37);
    selectAll();

    component.onClearSelection();

    expect(component.allMatchingSelected).toBeFalse();
    expect(component.selectedOrders).toEqual([]);
  });

  it('BF-C2: no matching order sends no request and shows an info toast', async () => {
    await setup(0);
    api.getOrders.calls.reset();

    selectAll();

    expect(api.getOrders).not.toHaveBeenCalled();
    expect(toast.info).toHaveBeenCalledWith('No order matches the filters.');
    expect(component.allMatchingSelected).toBeFalse();
  });

  it('BF-C2: an error keeps the selection and shows an error toast', async () => {
    await setup(37);
    component.onOrderSelectionChange([order(3)]);
    api.getOrders.and.returnValue(throwError(() => ({ message: 'boom' })));

    selectAll();

    expect(component.selectedOrders.map(o => o.id)).toEqual([3]);
    expect(component.allMatchingSelected).toBeFalse();
    expect(toast.error).toHaveBeenCalledWith('boom');
  });
});
