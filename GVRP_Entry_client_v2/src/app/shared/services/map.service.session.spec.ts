import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import * as L from 'leaflet';
import { of, Subject, throwError } from 'rxjs';

import { BranchZoneDTO, RouteDTO, RouteGeometryDTO, SolutionDTO, StopDTO } from '@core/models';
import { LezApiService } from '@core/services/lez-api.service';
import { SessionResetService } from '@core/services/session-reset.service';

import { MapService } from './map.service';
import { MarkerService } from './marker.service';
import { RouteGeometry, RoutingService } from './routing.service';

/**
 * SEC-CLIENT-002 (audit lead #8, follow-up of SEC-011).
 *
 * MapService is a root singleton that outlives logout and login, while
 * <app-map> and its Leaflet map are recreated. A zone list or route geometry
 * requested while planner A's solution was being drawn must not be drawn on
 * planner B's map when it arrives after a session reset.
 *
 * Real Leaflet maps, real SessionResetService; the HTTP-backed sources of the
 * map (`LezApiService`, `RoutingService`) are Subjects so the test decides
 * when a response "arrives".
 */
describe('MapService across a session reset (SEC-CLIENT-002)', () => {
  let service: MapService;
  let sessionReset: SessionResetService;
  let lez: jasmine.SpyObj<LezApiService>;
  let routing: jasmine.SpyObj<RoutingService>;
  let markers: jasmine.SpyObj<MarkerService>;

  const hosts: HTMLDivElement[] = [];
  const maps: L.Map[] = [];

  const ENCODED = '_p~iF~ps|U_ulLnnqC_mqNvxq`@';

  const zoneOfA: BranchZoneDTO = {
    id: 31,
    name: 'Zone of branch A',
    enabled: true,
    geometry: {
      type: 'Polygon',
      coordinates: [[[105.79, 21.02], [105.81, 21.02], [105.81, 21.04], [105.79, 21.04], [105.79, 21.02]]]
    }
  } as unknown as BranchZoneDTO;

  const stop = (seq: number, lat: number, lng: number): StopDTO =>
    ({
      sequence_number: seq,
      latitude: lat,
      longitude: lng,
      arrival_time: `08:0${seq}:00`,
      departure_time: `08:0${seq}:30`,
      type: 'ORDER',
      location_name: `Customer of A ${seq}`
    } as unknown as StopDTO);

  const routeOfA = (id: number | null): RouteDTO =>
    ({
      id,
      vehicle_license_plate: `29A-${id ?? 0}`,
      order_count: 2,
      distance: 3.5,
      stops: [stop(1, 21.02, 105.79), stop(2, 21.03, 105.80), stop(3, 21.04, 105.81)]
    } as unknown as RouteDTO);

  const solutionOfA = (...routes: RouteDTO[]): SolutionDTO => ({ id: 92, routes } as unknown as SolutionDTO);

  const geometryOfA = (routeId: number): RouteGeometryDTO => ({
    route_id: routeId,
    legs: [{ from_seq: 0, to_seq: 1, polyline: ENCODED }]
  });

  const newMap = (): L.Map => {
    const host = document.createElement('div');
    host.style.width = '400px';
    host.style.height = '300px';
    document.body.appendChild(host);
    hosts.push(host);
    const map = L.map(host, { center: [21.03, 105.8], zoom: 12, maxZoom: 18 });
    maps.push(map);
    return map;
  };

  /** <app-map> destroyed: ngx-leaflet removes its Leaflet map. */
  const destroyMap = (map: L.Map): void => {
    map.remove();
    maps.splice(maps.indexOf(map), 1);
  };

  /** Vector layers (zone polygons, route polylines, arrows) and markers on one map. */
  const drawnOn = (map: L.Map): { paths: number; markers: number } => {
    let paths = 0;
    let markerCount = 0;
    map.eachLayer(layer => {
      if (layer instanceof L.Path) paths++;
      if (layer instanceof L.Marker) markerCount++;
    });
    return { paths, markers: markerCount };
  };

  /** Lets the pending awaits of displaySolution run up to the next request. */
  const settle = () => new Promise<void>(resolve => setTimeout(resolve));

  beforeEach(() => {
    lez = jasmine.createSpyObj<LezApiService>('LezApiService', ['listZones', 'routeGeometry']);
    lez.listZones.and.returnValue(of([]));
    lez.routeGeometry.and.returnValue(throwError(() => new Error('no stored geometry')));

    routing = jasmine.createSpyObj<RoutingService>('RoutingService', ['getRouteGeometry', 'getStraightLine']);
    routing.getRouteGeometry.and.returnValue(of(null));
    routing.getStraightLine.and.callFake(
      (coordinates: number[][]) => coordinates.map(c => [c[1], c[0]] as L.LatLngExpression)
    );

    markers = jasmine.createSpyObj<MarkerService>('MarkerService', ['createRouteStopMarker']);
    markers.createRouteStopMarker.and.callFake((s: StopDTO) => L.marker([s.latitude, s.longitude]));

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: LezApiService, useValue: lez },
        { provide: RoutingService, useValue: routing },
        { provide: MarkerService, useValue: markers }
      ]
    });
    service = TestBed.inject(MapService);
    sessionReset = TestBed.inject(SessionResetService);
    spyOn(console, 'warn');
    spyOn(console, 'error');
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    [...maps].forEach(map => destroyMap(map));
    hosts.splice(0).forEach(host => host.remove());
  });

  it('MC-T1: a zone list requested for A\'s solution is not drawn on B\'s map', async () => {
    const zones$ = new Subject<BranchZoneDTO[]>();
    lez.listZones.and.returnValue(zones$);
    const mapA = newMap();
    service.initializeMap(mapA);
    await service.displaySolution(solutionOfA());

    sessionReset.resetBranchState();
    destroyMap(mapA);
    const mapB = newMap();
    service.initializeMap(mapB);
    zones$.next([zoneOfA]);
    zones$.complete();

    expect(drawnOn(mapB)).toEqual({ paths: 0, markers: 0 });
  });

  it('MC-T2: A\'s stored route geometry and remaining routes are not drawn on B\'s map', async () => {
    const geometry$ = new Subject<RouteGeometryDTO>();
    lez.routeGeometry.and.returnValue(geometry$);
    const mapA = newMap();
    service.initializeMap(mapA);
    const drawing = service.displaySolution(solutionOfA(routeOfA(1), routeOfA(2)));
    await settle();

    sessionReset.resetBranchState();
    destroyMap(mapA);
    const mapB = newMap();
    service.initializeMap(mapB);
    geometry$.next(geometryOfA(1));
    geometry$.complete();
    await drawing;

    expect(drawnOn(mapB)).toEqual({ paths: 0, markers: 0 });
  });

  it('MC-T3: A\'s OSRM route geometry arriving after the reset is not drawn on B\'s map', async () => {
    const osrm$ = new Subject<RouteGeometry | null>();
    routing.getRouteGeometry.and.returnValue(osrm$);
    const mapA = newMap();
    service.initializeMap(mapA);
    const drawing = service.displaySolution(solutionOfA(routeOfA(1)));
    await settle();
    expect(routing.getRouteGeometry).toHaveBeenCalledTimes(1);

    sessionReset.resetBranchState();
    destroyMap(mapA);
    const mapB = newMap();
    service.initializeMap(mapB);
    osrm$.next({ coordinates: [[21.02, 105.79], [21.04, 105.81]], distance: 3.5, duration: 9 });
    osrm$.complete();
    await drawing;

    expect(drawnOn(mapB)).toEqual({ paths: 0, markers: 0 });
  });

  it('MC-T4: a reset removes what A\'s solution drew on a map that is still open', async () => {
    lez.listZones.and.returnValue(of([zoneOfA]));
    lez.routeGeometry.and.returnValue(of(geometryOfA(1)));
    const mapA = newMap();
    service.initializeMap(mapA);
    await service.displaySolution(solutionOfA(routeOfA(1)));
    expect(drawnOn(mapA).paths).toBeGreaterThan(0);

    sessionReset.resetBranchState();

    expect(drawnOn(mapA)).toEqual({ paths: 0, markers: 0 });
  });

  it('MC-T5: within one session a solution is still drawn completely (zones, routes, markers)', async () => {
    lez.listZones.and.returnValue(of([zoneOfA]));
    lez.routeGeometry.and.callFake((id: number) =>
      id === 1 ? of(geometryOfA(1)) : throwError(() => new Error('no stored geometry'))
    );
    const map = newMap();
    service.initializeMap(map);

    await service.displaySolution(solutionOfA(routeOfA(1), routeOfA(2)));

    const zoneLayers = (service as unknown as { zoneLayers: L.FeatureGroup }).zoneLayers;
    expect(zoneLayers.getLayers().length).toBe(1);
    // 3 stop markers per route; route 1 stored polyline + arrows, route 2 straight line.
    expect(drawnOn(map).markers).toBe(6);
    expect(drawnOn(map).paths).toBeGreaterThanOrEqual(3);
    expect(routing.getStraightLine).toHaveBeenCalledTimes(1);
  });

  it('MC-T6: clearing the solution while its geometry is in flight leaves no route behind', async () => {
    const geometry$ = new Subject<RouteGeometryDTO>();
    lez.routeGeometry.and.returnValue(geometry$);
    const map = newMap();
    service.initializeMap(map);
    const drawing = service.displaySolution(solutionOfA(routeOfA(1), routeOfA(2)));
    await settle();

    service.clearSolution();
    geometry$.next(geometryOfA(1));
    geometry$.complete();
    await drawing;

    expect(drawnOn(map)).toEqual({ paths: 0, markers: 0 });
  });
});
