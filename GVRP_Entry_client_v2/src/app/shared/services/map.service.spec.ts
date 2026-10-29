import { TestBed } from '@angular/core/testing';
import * as L from 'leaflet';

import { OrderDTO } from '@core/models';
import { LezApiService } from '@core/services/lez-api.service';

import { MapService } from './map.service';
import { MarkerService } from './marker.service';
import { RoutingService } from './routing.service';

/**
 * D3/D4 of MAP-ORDERS-001 against a real Leaflet map in a detached-size div.
 * Marker creation is a spy that returns plain Leaflet markers.
 */
describe('MapService order markers', () => {
  let service: MapService;
  let markers: jasmine.SpyObj<MarkerService>;
  let host: HTMLDivElement;
  let map: L.Map;

  const order = (id: number, latitude: number | null, longitude: number | null) =>
    ({ id, order_code: `ORD-${id}`, latitude, longitude } as unknown as OrderDTO);

  beforeEach(() => {
    markers = jasmine.createSpyObj<MarkerService>('MarkerService',
      ['createOrderMarker', 'createDepotMarker', 'createRouteStopMarker']);
    markers.createOrderMarker.and.callFake((o: OrderDTO) => L.marker([o.latitude, o.longitude]));
    TestBed.configureTestingModule({
      providers: [
        { provide: MarkerService, useValue: markers },
        { provide: RoutingService, useValue: {} },
        { provide: LezApiService, useValue: {} }
      ]
    });
    service = TestBed.inject(MapService);

    host = document.createElement('div');
    host.style.width = '400px';
    host.style.height = '300px';
    document.body.appendChild(host);
    // maxZoom: without a tile layer, fitBounds on one marker would zoom to Infinity.
    map = L.map(host, { center: [21, 105], zoom: 12, maxZoom: 18 });
    service.initializeMap(map);
  });

  afterEach(() => {
    map.remove();
    host.remove();
  });

  it('D4: skips an order without coordinates, draws the others, warns with a count only', () => {
    const warn = spyOn(console, 'warn');

    service.loadOrders([order(1, null, null), order(2, 21.03, 105.8), order(3, NaN, 105)]);

    expect(markers.createOrderMarker).toHaveBeenCalledTimes(1);
    expect(markers.createOrderMarker.calls.mostRecent().args[0].id).toBe(2);
    expect(warn).toHaveBeenCalledTimes(1);
    const message = String(warn.calls.mostRecent().args.join(' '));
    expect(message).toContain('2');
    expect(message).not.toContain('ORD-');
    expect(warn.calls.mostRecent().args.length).toBe(1);
  });

  it('D4: no warning when every order has coordinates', () => {
    const warn = spyOn(console, 'warn');

    service.loadOrders([order(1, 21.03, 105.8)]);

    expect(warn).not.toHaveBeenCalled();
  });

  it('D3: clearSolution keeps the order markers on the map', () => {
    service.loadOrders([order(2, 21.03, 105.8)]);
    const drawn = markers.createOrderMarker.calls.mostRecent().returnValue as L.Marker;

    service.clearSolution();

    expect(map.hasLayer(drawn)).toBeTrue();
  });
});
