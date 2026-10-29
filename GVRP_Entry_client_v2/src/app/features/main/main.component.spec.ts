import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA, signal } from '@angular/core';
import { of } from 'rxjs';

import { OrderDTO, PaginatedResponse } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { SolutionStore } from '@core/services/solution.store';
import { MapService } from '@shared/services/map.service';
import { ToastService } from '@shared/services/toast.service';
import { localDateIso } from '@shared/utils/date.utils';

import { MainComponent } from './main.component';

/**
 * MO-T6 of MAP-ORDERS-001: the map is fed by the order table, the sidebar
 * stats keep their own source. Child components are shallow (no imports).
 */
describe('MainComponent map wiring', () => {
  let fixture: ComponentFixture<MainComponent>;
  let component: MainComponent;
  let api: jasmine.SpyObj<ApiService>;

  const order = (id: number, status: OrderDTO['status']): OrderDTO =>
    ({ id, status } as unknown as OrderDTO);
  const initial = [order(1, 'SCHEDULED'), order(2, 'COMPLETED')];

  beforeEach(async () => {
    spyOn(console, 'log');
    api = jasmine.createSpyObj<ApiService>('ApiService', ['getVehicles', 'getDepots', 'getOrders']);
    api.getVehicles.and.returnValue(of([]));
    api.getDepots.and.returnValue(of([]));
    api.getOrders.and.returnValue(of({
      content: initial, total_elements: 2, total_pages: 1
    } as unknown as PaginatedResponse<OrderDTO>));
    const store = {} as { solution: unknown };
    store.solution = signal(null).asReadonly();

    await TestBed.configureTestingModule({
      imports: [MainComponent],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: ToastService, useValue: jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']) },
        { provide: MapService, useValue: jasmine.createSpyObj<MapService>('MapService', ['invalidateSize', 'centerTo', 'highlightOrder']) },
        { provide: SolutionStore, useValue: store }
      ]
    })
      .overrideComponent(MainComponent, { set: { imports: [], schemas: [NO_ERRORS_SCHEMA] } })
      .compileComponents();

    fixture = TestBed.createComponent(MainComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('D2: the dashboard load uses the local today', () => {
    expect(api.getOrders.calls.first().args[0].date).toBe(localDateIso());
  });

  it('MO-T6: onTableOrdersLoaded sets mapOrders; orders and stats unchanged', () => {
    const statsBefore = { ...component.stats };
    const page = [order(5, 'UNASSIGNED')];

    component.onTableOrdersLoaded(page);

    expect(component.mapOrders).toEqual(page);
    expect(component.orders).toBe(initial);
    expect(component.stats).toEqual(statsBefore);
  });

  it('MO-T6: the map element is bound to mapOrders, not to the stats orders', () => {
    const page = [order(5, 'UNASSIGNED')];
    component.onTableOrdersLoaded(page);
    fixture.detectChanges();

    const mapElement = fixture.nativeElement.querySelector('app-map') as { orders?: OrderDTO[] };
    expect(mapElement.orders).toEqual(page);
  });

  it('MO-T6: mapOrders starts empty until the table reports a page', () => {
    const fresh = TestBed.createComponent(MainComponent).componentInstance;
    expect(fresh.mapOrders).toEqual([]);
  });
});
