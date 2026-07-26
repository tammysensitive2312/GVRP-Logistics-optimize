import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RouteDTO, SolutionDTO, StopDTO } from '@core/models';
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

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RouteViewComponent]
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
});
