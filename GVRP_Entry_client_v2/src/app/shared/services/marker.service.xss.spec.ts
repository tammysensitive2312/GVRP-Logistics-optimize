import { TestBed } from '@angular/core/testing';
import * as L from 'leaflet';

import { DepotDTO, OrderDTO } from '@core/models';

import { MarkerService } from './marker.service';

/**
 * XS-T2, XS-T3 (SEC-XSS-001, D2): hostile API text in marker popups is shown
 * as text. Popups are opened on a real Leaflet map and their DOM is read.
 */
describe('MarkerService popups escape API text', () => {
  const HOSTILE = '<img src=x onerror="window.__x=1">';

  let service: MarkerService;
  let host: HTMLDivElement;
  let map: L.Map;

  const openPopup = (marker: L.Marker): HTMLElement => {
    marker.addTo(map);
    marker.openPopup();
    return marker.getPopup()!.getElement()!;
  };

  const expectInert = (element: HTMLElement): void => {
    expect(element.querySelector('img')).toBeNull();
    expect(element.textContent).toContain(HOSTILE);
    expect((window as unknown as { __x?: unknown }).__x).toBeUndefined();
  };

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(MarkerService);
    host = document.createElement('div');
    host.style.width = '400px';
    host.style.height = '300px';
    document.body.appendChild(host);
    map = L.map(host, { center: [21, 105], zoom: 12, maxZoom: 18 });
  });

  afterEach(() => {
    map.remove();
    host.remove();
  });

  it('XS-T2: depot popup shows a hostile name and address as text', () => {
    const depot = {
      id: 1, name: HOSTILE, address: HOSTILE, latitude: 21.03, longitude: 105.8
    } as unknown as DepotDTO;

    expectInert(openPopup(service.createDepotMarker(depot)));
  });

  it('XS-T3: order popup shows hostile order fields as text', () => {
    const order = {
      id: 7, order_code: HOSTILE, customer_name: HOSTILE, customer_phone: HOSTILE,
      address: HOSTILE, demand: 5, time_window_start: HOSTILE, time_window_end: HOSTILE,
      status: 'SCHEDULED', latitude: 21.03, longitude: 105.8
    } as unknown as OrderDTO;

    const element = openPopup(service.createOrderMarker(order, undefined, () => undefined));

    expectInert(element);
    expect(element.querySelector('[data-order-edit]')).not.toBeNull();
  });

  it('XS-T3: route-stop popup shows hostile stop name and plate as text', () => {
    const stop = {
      type: 'CUSTOMER', sequence_number: 2, location_name: HOSTILE,
      arrival_time: '08:00', departure_time: '08:10', demand: 3, wait_time: 0,
      load_after: 10, latitude: 21.03, longitude: 105.8
    };

    expectInert(openPopup(service.createRouteStopMarker(stop, '#1976d2', HOSTILE, 'stop')));
  });

  it('XS-T3: depot route-stop popup shows a hostile depot name as text', () => {
    const stop = {
      type: 'DEPOT', location_name: HOSTILE, departure_time: '07:00',
      latitude: 21.03, longitude: 105.8
    };

    expectInert(openPopup(service.createRouteStopMarker(stop, '#1976d2', 'PLATE-1', 'start')));
  });

  it('XS-T3: an ordinary name is shown without visible entities', () => {
    const depot = {
      id: 1, name: 'Kho A & B', address: 'Hà Nội', latitude: 21.03, longitude: 105.8
    } as unknown as DepotDTO;

    const text = openPopup(service.createDepotMarker(depot)).textContent ?? '';

    expect(text).toContain('Kho A & B');
    expect(text).not.toContain('&amp;');
  });
});
