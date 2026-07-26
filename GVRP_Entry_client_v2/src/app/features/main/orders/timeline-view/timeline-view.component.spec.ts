import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RouteDTO, SolutionDTO, StopDTO } from '@core/models';

import { TimelineViewComponent } from './timeline-view.component';

const stop = (partial: Partial<StopDTO>): StopDTO => ({
  type: 'ORDER',
  location_name: 'Stop',
  latitude: 0,
  longitude: 0,
  arrival_time: '08:00:00',
  load_after: 0,
  wait_time: 0,
  ...partial
});

const buildRoute = (partial: Partial<RouteDTO> = {}): RouteDTO => ({
  vehicle_id: 1,
  vehicle_license_plate: '29A-12345',
  start_time: '08:00:00',
  end_time: '10:00:00',
  distance: 20,
  service_time: 1,
  order_count: 1,
  load_utilization: 40,
  stops: [
    stop({ type: 'DEPOT', location_name: 'Depot', arrival_time: '08:00:00', departure_time: '08:00:00' }),
    stop({ location_name: 'Order A', arrival_time: '08:30:00', departure_time: '09:00:00' }),
    stop({ type: 'DEPOT', location_name: 'Depot', arrival_time: '10:00:00' })
  ],
  ...partial
});

const buildSolution = (routes: RouteDTO[]): SolutionDTO => ({
  id: 1,
  job_id: 1,
  total_cost: 100000,
  total_distance: 20,
  total_time: 2,
  total_co2: 5,
  total_vehicles_used: routes.length,
  served_orders: 1,
  unserved_orders: 0,
  routes
});

describe('TimelineViewComponent', () => {
  let fixture: ComponentFixture<TimelineViewComponent>;
  let component: TimelineViewComponent;

  const render = (solution: SolutionDTO) => {
    fixture = TestBed.createComponent(TimelineViewComponent);
    fixture.componentRef.setInput('solution', solution);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TimelineViewComponent]
    }).compileComponents();
  });

  it('builds hourly time markers across the window', () => {
    render(buildSolution([buildRoute()]));

    expect(component.timeMarkers().map(m => m.label)).toEqual([
      '08:00',
      '09:00',
      '10:00'
    ]);
    expect(component.timeMarkers()[0].leftPercent).toBe(0);
    expect(component.timeMarkers()[2].leftPercent).toBe(100);
  });

  it('creates a service bar for orders and driving bars between stops', () => {
    render(buildSolution([buildRoute()]));

    const bars = component.rows()[0].bars;
    const service = bars.filter(bar => bar.kind === 'service');
    const driving = bars.filter(bar => bar.kind === 'driving');

    expect(service.length).toBe(1);
    // 08:30 -> 09:00 inside an 08:00-10:00 window: starts at 25%, spans 25%.
    expect(service[0].leftPercent).toBeCloseTo(25, 5);
    expect(service[0].widthPercent).toBeCloseTo(25, 5);
    expect(driving.length).toBe(2);
  });

  it('skips bars narrower than the V1 threshold', () => {
    const tinyGap = buildRoute({
      stops: [
        stop({ type: 'DEPOT', location_name: 'Depot', arrival_time: '08:00:00', departure_time: '08:00:00' }),
        stop({ location_name: 'Order A', arrival_time: '08:00:01', departure_time: '08:00:02' }),
        stop({ type: 'DEPOT', location_name: 'Depot', arrival_time: '10:00:00' })
      ]
    });

    render(buildSolution([tinyGap]));

    expect(component.rows()[0].bars.every(bar => bar.widthPercent > 0.1)).toBeTrue();
  });

  it('reports routes with missing times instead of only logging', () => {
    render(buildSolution([buildRoute({ start_time: '', end_time: '' })]));

    expect(component.warnings().length).toBe(1);
    expect(component.warnings()[0]).toContain('missing start/end time');
  });

  it('warns when there are no routes', () => {
    render(buildSolution([]));

    expect(component.warnings()).toEqual(['Solution has no routes to display']);
    expect(component.rows()).toEqual([]);
  });

  it('zooms within bounds and resets', () => {
    render(buildSolution([buildRoute()]));

    expect(component.canZoomOut()).toBeFalse();

    component.zoomIn();
    expect(component.zoom()).toBe(1.5);
    expect(component.timelineWidthPercent()).toBe(150);

    for (let i = 0; i < 10; i++) component.zoomIn();
    expect(component.zoom()).toBe(4);
    expect(component.canZoomIn()).toBeFalse();

    component.resetZoom();
    expect(component.zoom()).toBe(1);
  });
});
