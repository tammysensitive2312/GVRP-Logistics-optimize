import { TestBed } from '@angular/core/testing';
import * as L from 'leaflet';
import { of } from 'rxjs';

import { RouteDTO } from '@core/models';
import { LezApiService } from '@core/services/lez-api.service';

import { MapService } from './map.service';
import { MarkerService } from './marker.service';
import { RoutingService } from './routing.service';

/**
 * XS-T5 (SEC-XSS-001, D2): zone and route tooltips on the solution map show
 * hostile API text as text. Private drawers are reached by bracket access.
 */
describe('MapService tooltips escape API text', () => {
  const HOSTILE = '<img src=x onerror="window.__x=1">';

  let service: MapService;
  let host: HTMLDivElement;
  let map: L.Map;

  const square = {
    type: 'Polygon',
    coordinates: [[[105.79, 21.02], [105.81, 21.02], [105.81, 21.04], [105.79, 21.04], [105.79, 21.02]]]
  };

  const tooltipOf = (group: L.FeatureGroup): HTMLElement => {
    const layers: L.Layer[] = [];
    group.eachLayer(layer => layers.push(layer));
    const withTooltip = layers.find(layer => layer.getTooltip());
    expect(withTooltip).toBeDefined();
    withTooltip!.openTooltip([21.03, 105.8]);
    return withTooltip!.getTooltip()!.getElement()!;
  };

  const expectInert = (element: HTMLElement): void => {
    expect(element.querySelector('img')).toBeNull();
    expect(element.textContent).toContain(HOSTILE);
    expect((window as unknown as { __x?: unknown }).__x).toBeUndefined();
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        { provide: MarkerService, useValue: {} },
        {
          provide: RoutingService,
          useValue: { getStraightLine: () => [[21.02, 105.79], [21.04, 105.81]] }
        },
        {
          provide: LezApiService,
          useValue: { listZones: () => of([{ id: 1, name: HOSTILE, enabled: true, geometry: square }]) }
        }
      ]
    });
    service = TestBed.inject(MapService);

    host = document.createElement('div');
    host.style.width = '400px';
    host.style.height = '300px';
    document.body.appendChild(host);
    map = L.map(host, { center: [21.03, 105.8], zoom: 12, maxZoom: 18 });
    service.initializeMap(map);
  });

  afterEach(() => {
    map.remove();
    host.remove();
  });

  it('XS-T5: zone tooltip shows a hostile zone name as text', () => {
    const internals = service as unknown as { drawZones(): void; zoneLayers: L.FeatureGroup };

    internals.drawZones();

    expectInert(tooltipOf(internals.zoneLayers));
  });

  it('XS-T5: approximate route tooltip shows a hostile plate as text', () => {
    const internals = service as unknown as {
      drawStraightLine(coordinates: number[][], color: string, route: RouteDTO): void;
      routeLayers: L.FeatureGroup;
    };
    const route = { vehicle_license_plate: HOSTILE, order_count: 1, distance: 1 } as unknown as RouteDTO;

    internals.drawStraightLine([[105.79, 21.02], [105.81, 21.04]], '#1976d2', route);

    expectInert(tooltipOf(internals.routeLayers));
  });
});
