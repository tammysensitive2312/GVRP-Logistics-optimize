import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { RouteDTO, SolutionDTO, StopDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { SolutionStore } from '@core/services/solution.store';
import { ROUTE_COLORS } from '@shared/utils/solution-view.utils';

import { RouteViewComponent } from './route-view.component';

const stop = (partial: Partial<StopDTO>): StopDTO => ({
  type: 'ORDER',
  location_name: 'Stop',
  latitude: 0,
  longitude: 0,
  arrival_time: '08:30:00',
  load_after: 120.5,
  wait_time: 0,
  ...partial
});

const buildRoute = (partial: Partial<RouteDTO> = {}): RouteDTO => ({
  vehicle_id: 1,
  vehicle_license_plate: '29A-12345',
  start_time: '08:00:00',
  end_time: '10:00:00',
  distance: 20.44,
  service_time: 1.5,
  order_count: 2,
  load_utilization: 62.5,
  stops: [
    stop({ type: 'DEPOT', location_name: 'Kho Hà Nội', arrival_time: '08:00:00', departure_time: '08:00:00' }),
    stop({ location_name: 'Order A', sequence_number: 1, demand: 50, departure_time: '08:45:00', wait_time: 5 }),
    stop({ type: 'DEPOT', location_name: 'Kho Hà Nội', arrival_time: '10:00:00' })
  ],
  ...partial
});

const solution: SolutionDTO = {
  id: 9,
  job_id: 4,
  total_cost: 1234567,
  total_distance: 42.37,
  total_time: 2.5,
  total_co2: 12.3,
  total_vehicles_used: 2,
  served_orders: 5,
  unserved_orders: 1,
  routes: [buildRoute(), buildRoute({ vehicle_license_plate: '29B-54321' })]
};

describe('RouteViewComponent', () => {
  let fixture: ComponentFixture<RouteViewComponent>;
  let component: RouteViewComponent;
  let api: jasmine.SpyObj<ApiService>;
  let store: jasmine.SpyObj<SolutionStore>;

  beforeEach(async () => {
    api = jasmine.createSpyObj<ApiService>('ApiService',
      ['evaluateRoutes', 'saveRouteRevision', 'latestRevisionId']);
    store = jasmine.createSpyObj<SolutionStore>('SolutionStore',
      ['setSolution', 'loadById']);

    await TestBed.configureTestingModule({
      imports: [RouteViewComponent],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: SolutionStore, useValue: store }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(RouteViewComponent);
    fixture.componentRef.setInput('solution', solution);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('formats the summary the way V1 did', () => {
    const summary = component.summary();

    expect(summary.totalRoutes).toBe(2);
    expect(summary.totalDistanceText).toBe('42.4 km');
    // total_time is in hours: 2.5 * 60 = 150 minutes.
    expect(summary.totalTimeText).toBe('2h 30m');
    expect(summary.totalCostText).toBe(`${(1234567).toLocaleString('vi-VN')} VND`);
  });

  describe('copy delivery stops', () => {
    let original: PropertyDescriptor | undefined;
    let write: jasmine.Spy;
    beforeEach(() => {
      original = Object.getOwnPropertyDescriptor(navigator, 'clipboard');
      write = jasmine.createSpy('writeText').and.returnValue(Promise.resolve());
      Object.defineProperty(navigator, 'clipboard', {configurable: true, value: {writeText: write}});
    });
    afterEach(() => {
      if (original) Object.defineProperty(navigator, 'clipboard', original);
      else Reflect.deleteProperty(navigator, 'clipboard');
    });

    it('copies only delivery names in displayed order with consecutive numbering', async () => {
      fixture.componentRef.setInput('solution', {...solution, routes: [buildRoute({stops: [
        stop({type: 'DEPOT', location_name: 'Depot', departure_time: '08:00'}),
        stop({location_name: 'Khách B\n  Hà Nội'}),
        stop({location_name: 'Khách B\n  Hà Nội'}),
        stop({location_name: ''}),
        stop({type: 'DEPOT', location_name: 'Depot'})
      ]})]});
      fixture.detectChanges();
      const card = component.routeCards()[0];
      await component.copyStops(card);
      expect(write).toHaveBeenCalledOnceWith('1. Khách B Hà Nội\n2. Khách B Hà Nội\n3. Unnamed stop');
      expect(component.copyStatus().get(card)).toBe('success');
    });

    it('disables depot-only routes and does not access clipboard', async () => {
      fixture.componentRef.setInput('solution', {...solution, routes: [buildRoute({stops: [stop({type: 'DEPOT'})]})]});
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.btn-copy').disabled).toBeTrue();
      await component.copyStops(component.routeCards()[0]);
      expect(write).not.toHaveBeenCalled();
    });

    it('blocks repeated clicks while pending, without selecting the route', async () => {
      let finish!: () => void;
      write.and.returnValue(new Promise<void>(resolve => finish = resolve));
      const selected = jasmine.createSpy('selected');
      component.routeSelected.subscribe(selected);
      const button: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-copy');
      button.click();
      fixture.detectChanges();
      expect(button.disabled).toBeTrue();
      expect(button.textContent).toContain('Copying');
      await component.copyStops(component.routeCards()[0]);
      expect(write).toHaveBeenCalledTimes(1);
      expect(selected).not.toHaveBeenCalled();
      finish();
      await fixture.whenStable();
      fixture.detectChanges();
      expect(button.disabled).toBeFalse();
      expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('copied');
    });

    it('shows an actionable failure and permits retry after denial', async () => {
      write.and.returnValue(Promise.reject(new Error('Denied')));
      const card = component.routeCards()[0];
      await component.copyStops(card);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.copy-error').textContent).toContain('Allow clipboard access');
      write.and.returnValue(Promise.resolve());
      await component.copyStops(card);
      expect(component.copyStatus().get(card)).toBe('success');
    });

    it('handles browsers without clipboard support', async () => {
      Object.defineProperty(navigator, 'clipboard', {configurable: true, value: undefined});
      const card = component.routeCards()[0];
      await component.copyStops(card);
      expect(component.copyStatus().get(card)).toBe('error');
    });

    it('does not attach stale clipboard results to a replacement solution', async () => {
      let finish!: () => void;
      write.and.returnValue(new Promise<void>(resolve => finish = resolve));
      const oldCard = component.routeCards()[0];
      const pending = component.copyStops(oldCard);
      fixture.componentRef.setInput('solution', {...solution, routes: [buildRoute({vehicle_license_plate: 'NEW'})]});
      fixture.detectChanges();
      finish();
      await pending;
      expect(component.copyStatus().get(component.routeCards()[0])).toBeUndefined();
      expect(component.copyStatus().has(oldCard)).toBeFalse();
    });
  });

  it('assigns palette colours per route index', () => {
    const cards = component.routeCards();

    expect(cards[0].color).toBe(ROUTE_COLORS[0]);
    expect(cards[1].color).toBe(ROUTE_COLORS[1]);
  });

  it('formats per-route stats', () => {
    const card = component.routeCards()[0];

    expect(card.distanceText).toBe('20.4 km');
    expect(card.timeText).toBe('1h 30m');
    expect(card.loadText).toBe('63%');
    expect(card.timeRange).toBe('08:00:00 - 10:00:00');
  });

  it('marks depots and orders distinctly and drops the connector on the last stop', () => {
    const stops = component.routeCards()[0].stops;

    expect(stops[0].isDepot).toBeTrue();
    expect(stops[0].markerLabel).toBe('🏢');
    expect(stops[1].isDepot).toBeFalse();
    expect(stops[1].markerLabel).toBe('1');
    expect(stops[1].loadAfterText).toBe('120.5');
    expect(stops[1].waitTime).toBe(5);
    expect(stops[stops.length - 1].showConnector).toBeFalse();
  });

  it('starts collapsed and toggles independently per route', () => {
    expect(component.isExpanded(0)).toBeFalse();

    component.toggleRoute(0);
    expect(component.isExpanded(0)).toBeTrue();
    expect(component.isExpanded(1)).toBeFalse();

    component.toggleRoute(0);
    expect(component.isExpanded(0)).toBeFalse();
  });

  it('emits the route index when a card header is clicked', () => {
    const emitted: number[] = [];
    component.routeSelected.subscribe(index => emitted.push(index));

    component.onRouteHeaderClick(1);

    expect(emitted).toEqual([1]);
  });

  describe('expand all / collapse all', () => {
    const toolbarButtons = (): HTMLButtonElement[] =>
      Array.from(fixture.nativeElement.querySelectorAll('.routes-toolbar .btn-toolbar'));

    it('exposes Expand all, Collapse all and Customize buttons', () => {
      const labels = toolbarButtons().map(button => button.textContent?.trim());

      expect(labels).toEqual(['Expand all', 'Collapse all', 'Customize']);
    });

    it('expands every card and collapses every card', () => {
      component.expandAllRoutes();
      expect(component.isExpanded(0)).toBeTrue();
      expect(component.isExpanded(1)).toBeTrue();
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelectorAll('.route-card-details').length).toBe(2);

      component.collapseAllRoutes();
      expect(component.isExpanded(0)).toBeFalse();
      expect(component.isExpanded(1)).toBeFalse();
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelectorAll('.route-card-details').length).toBe(0);
    });

    it('activates through the toolbar buttons without selecting a route', () => {
      const selected = jasmine.createSpy('selected');
      component.routeSelected.subscribe(selected);
      const [expand, collapse] = toolbarButtons();

      expand.click();
      fixture.detectChanges();
      expect(component.isExpanded(0)).toBeTrue();
      expect(component.isExpanded(1)).toBeTrue();

      collapse.click();
      fixture.detectChanges();
      expect(component.isExpanded(0)).toBeFalse();
      expect(component.isExpanded(1)).toBeFalse();
      expect(selected).not.toHaveBeenCalled();
    });

    it('keeps per-route toggling independent after expand all', () => {
      component.expandAllRoutes();
      component.toggleRoute(0);

      expect(component.isExpanded(0)).toBeFalse();
      expect(component.isExpanded(1)).toBeTrue();
    });

    it('disables all toolbar buttons when there are no routes', () => {
      fixture.componentRef.setInput('solution', {...solution, routes: []});
      fixture.detectChanges();

      const buttons = toolbarButtons();
      expect(buttons.length).toBe(3);
      expect(buttons.every(button => button.disabled)).toBeTrue();
    });
  });

  describe('route customization', () => {
    const editableStop = (orderId: number, name: string): StopDTO => ({
      type: 'ORDER',
      order_id: orderId,
      location_name: name,
      latitude: 0,
      longitude: 0,
      arrival_time: '08:30:00',
      load_after: 50,
      wait_time: 0
    });
    const depotStop = (name: string): StopDTO => ({
      type: 'DEPOT',
      location_name: name,
      latitude: 0,
      longitude: 0,
      arrival_time: '08:00:00',
      departure_time: '08:00:00',
      load_after: 80,
      wait_time: 0
    });
    const editableSolution: SolutionDTO = {
      ...solution,
      routes: [{
        vehicle_id: 7,
        vehicle_license_plate: '29A-12345',
        start_time: '08:00:00',
        end_time: '10:00:00',
        distance: 20.44,
        service_time: 1.5,
        order_count: 2,
        load_utilization: 62.5,
        stops: [
          depotStop('Depot'),
          editableStop(11, 'Order A'),
          editableStop(12, 'Order B'),
          depotStop('Depot')
        ]
      }]
    };
    const feasiblePreview = {
      feasible: true,
      violations: [],
      routes: [{
        ...editableSolution.routes[0],
        stops: [
          depotStop('Depot'),
          {...editableStop(11, 'Order A'), arrival_time: '08:02:00'},
          {...editableStop(12, 'Order B'), arrival_time: '08:14:00'},
          depotStop('Depot')
        ]
      }],
      totals: {
        total_distance: 3, total_time: 1.5, total_cost: 100000,
        total_co2: 0.6, vehicles_used: 1, orders_served: 2
      },
      warnings: []
    };

    beforeEach(() => {
      fixture.componentRef.setInput('solution', editableSolution);
      fixture.detectChanges();
      api.evaluateRoutes.and.returnValue(of(structuredClone(feasiblePreview)));
    });

    afterEach(() => {
      // Drops debounce their preview; cancel the session so no timer leaks into the next test.
      component.cancelEdit();
    });

    it('builds a draft with depots pinned and requests a preview', () => {
      component.startEdit();

      expect(component.editing()).toBeTrue();
      expect(component.draftRoutes().length).toBe(1);
      expect(component.draftRoutes()[0].orders.map(stop => stop.order_id)).toEqual([11, 12]);
      expect(component.draftRoutes()[0].depotStart?.location_name).toBe('Depot');
      expect(api.evaluateRoutes).toHaveBeenCalledWith(9, {
        routes: [{ vehicle_id: 7, stop_order_ids: [11, 12] }]
      });
    });

    it('applies recalculated times and allows saving on a feasible preview', () => {
      component.startEdit();
      fixture.detectChanges();

      expect(component.draftRoutes()[0].orders[0].arrival_time).toBe('08:02:00');
      expect(component.canSave()).toBeTrue();
      const save: HTMLButtonElement | null =
        fixture.nativeElement.querySelector('.edit-actions .btn-primary');
      expect(save?.disabled).toBeFalse();
    });

    it('reorders within a route and marks the preview stale', () => {
      component.startEdit();
      const orders = component.draftRoutes()[0].orders;
      component.drop({
        previousContainer: { data: orders },
        container: { data: orders },
        previousIndex: 0,
        currentIndex: 1
      } as never);
      fixture.detectChanges();

      expect(component.draftRoutes()[0].orders.map(stop => stop.order_id)).toEqual([12, 11]);
      expect(component.previewStale()).toBeTrue();
      expect(component.canSave()).toBeFalse();
      expect(component.canUndo()).toBeTrue();
    });

    it('restores the previous order on undo and refreshes the preview', () => {
      component.startEdit();
      const orders = component.draftRoutes()[0].orders;
      component.drop({
        previousContainer: { data: orders },
        container: { data: orders },
        previousIndex: 0,
        currentIndex: 1
      } as never);

      component.undo();
      // Drops are debounced; retry flushes the pending refresh synchronously.
      component.retryPreview();

      expect(component.draftRoutes()[0].orders.map(stop => stop.order_id)).toEqual([11, 12]);
      expect(api.evaluateRoutes).toHaveBeenCalledTimes(2);
    });

    it('blocks saving on an infeasible preview and lists violations', () => {
      api.evaluateRoutes.and.returnValue(of({
        ...structuredClone(feasiblePreview),
        feasible: false,
        violations: [{ code: 'TIME_WINDOW', route_index: 0, order_id: 11, detail: 'late' }]
      }));
      component.startEdit();
      fixture.detectChanges();

      expect(component.canSave()).toBeFalse();
      expect(fixture.nativeElement.querySelector('.edit-violations').textContent)
        .toContain('TIME_WINDOW');
    });

    it('keeps edits and offers retry when evaluation is unreachable', () => {
      api.evaluateRoutes.and.returnValue(throwError(() => ({ status: 502 })));
      component.startEdit();
      fixture.detectChanges();

      expect(component.evaluateError()).toContain('unreachable');
      expect(component.canSave()).toBeFalse();
      expect(fixture.nativeElement.querySelector('.edit-error').textContent).toContain('Retry');
    });

    it('saves the revision into the store and exits edit mode', () => {
      const saved = {...editableSolution, id: 10};
      api.saveRouteRevision.and.returnValue(of(saved));
      component.startEdit();

      component.save();

      expect(api.saveRouteRevision).toHaveBeenCalledWith(9, {
        routes: [{ vehicle_id: 7, stop_order_ids: [11, 12] }]
      });
      expect(store.setSolution).toHaveBeenCalledWith(saved);
      expect(component.editing()).toBeFalse();
    });

    it('discards the draft without touching the store', () => {
      component.startEdit();
      component.cancelEdit();

      expect(component.editing()).toBeFalse();
      expect(component.draftRoutes()).toEqual([]);
      expect(store.setSolution).not.toHaveBeenCalled();
    });
  });
});
