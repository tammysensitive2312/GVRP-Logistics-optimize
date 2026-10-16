import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Observable, of } from 'rxjs';
import * as L from 'leaflet';

import { DriverRouteDTO, RouteSendDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { AuthService } from '@core/services/auth.service';
import { RoutingService } from '@shared/services/routing.service';

import { DriverViewComponent, googleMapsUrl } from './driver-view.component';

describe('DriverViewComponent', () => {
  let fixture: ComponentFixture<DriverViewComponent>;
  let component: DriverViewComponent;
  let api: jasmine.SpyObj<ApiService>;
  let auth: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;
  let routing: jasmine.SpyObj<RoutingService>;

  const sends = [
    {
      id: 1, solution_id: 9, route_id: 21, vehicle_license_plate: '29A-1',
      driver_id: 2, driver_name: 'Tai Lai', sender_name: 'boss',
      sent_at: '2026-10-06T09:00:00', status: 'CURRENT'
    },
    {
      id: 0, solution_id: 8, route_id: 21, vehicle_license_plate: '29A-1',
      driver_id: 2, driver_name: 'Tai Lai', sender_name: 'boss',
      sent_at: '2026-10-05T09:00:00', status: 'SUPERSEDED'
    }
  ];

  beforeEach(async () => {
    api = jasmine.createSpyObj<ApiService>('ApiService',
      ['myDriverRoutes', 'driverRouteDetail']);
    api.myDriverRoutes.and.returnValue(of(sends as unknown as RouteSendDTO[]));
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['logout']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    routing = jasmine.createSpyObj<RoutingService>('RoutingService',
      ['getRouteGeometry', 'getRouteLegs', 'getStraightLine']);
    routing.getRouteGeometry.and.returnValue(of(null));
    routing.getRouteLegs.and.returnValue(of(null));
    await TestBed.configureTestingModule({
      imports: [DriverViewComponent],
      providers: [
        { provide: ApiService, useValue: api },
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
        { provide: RoutingService, useValue: routing }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DriverViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('lists current first with a superseded banner', () => {
    expect(component.sends().length).toBe(2);
    expect(fixture.nativeElement.querySelector('.driver-banner').textContent)
      .toContain('newer version');
    expect(fixture.nativeElement.textContent).toContain('superseded');
  });

  it('opens the detail with customer info', () => {
    api.driverRouteDetail.and.returnValue(of({
      send_id: 1, solution_id: 9, route_id: 21,
      vehicle_license_plate: '29A-1', status: 'CURRENT',
      stops: [{
        sequence_number: 1, type: 'ORDER', order_id: 11,
        location_name: 'Khach', address: 'Hanoi',
        latitude: 21, longitude: 105,
        arrival_time: '08:30:00', departure_time: null,
        customer_name: 'Khach', customer_phone: '0902',
        delivery_notes: 'Goi truoc', demand: 10
      }]
    } as unknown as DriverRouteDTO));
    component.openSend(sends[0] as unknown as RouteSendDTO);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('0902');
    expect(fixture.nativeElement.textContent).toContain('Goi truoc');
  });

  it('shows an empty state and logs out', () => {
    api.myDriverRoutes.and.returnValue(of([]));
    component.ngOnInit();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No routes assigned yet');

    auth.logout.and.returnValue(of(undefined));
    component.logout();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('reports list failure', () => {
    api.myDriverRoutes.and.returnValue(
      new Observable<never>(subscriber => subscriber.error(new Error('x'))));
    component.ngOnInit();
    fixture.detectChanges();

    expect(component.error()).toContain('Could not load assigned routes');
  });

  describe('googleMapsUrl', () => {
    it('encodes the stop coordinates', () => {
      expect(googleMapsUrl(21.03, 105.8))
        .toBe('https://www.google.com/maps/dir/?api=1&destination=21.03,105.8');
    });

    it('returns null without coordinates', () => {
      expect(googleMapsUrl(null, 105.8)).toBeNull();
      expect(googleMapsUrl(21.03, null)).toBeNull();
    });
  });

    describe('detail map and navigation', () => {
    it('renders the map exactly once per opened send', () => {      // The nested beforeEach already opened (and rendered) one detail;
      // go back and reset counts so only this open is measured.
      component.backToList();
      routing.getRouteGeometry.calls.reset();
      api.driverRouteDetail.and.returnValue(of({
        send_id: 1, solution_id: 9, route_id: 21,
        vehicle_license_plate: '29A-1', status: 'CURRENT',
        stops: [{
          sequence_number: 1, type: 'ORDER', order_id: 11,
          location_name: 'Depot', address: null,
          latitude: 21.0, longitude: 105.8,
          arrival_time: '08:00:00', departure_time: null,
          customer_name: null, customer_phone: null,
          delivery_notes: null, demand: null
        }]
      } as unknown as DriverRouteDTO));
      component.openSend(sends[0] as unknown as RouteSendDTO);
      fixture.detectChanges();
      fixture.detectChanges();
      fixture.detectChanges();

      // One geometry fetch total: no re-render loop re-creating L.map
      // (which throws "Map container is already initialized").
      expect(routing.getRouteGeometry.calls.count()).toBe(1);
      expect(fixture.nativeElement.querySelector('#driver-mini-map')).not.toBeNull();
    });
    const detail = {
      send_id: 1, solution_id: 9, route_id: 21,
      vehicle_license_plate: '29A-1', status: 'CURRENT',
      stops: [{
        sequence_number: 1, type: 'ORDER', order_id: 11,
        location_name: 'Khach', address: 'Hanoi',
        latitude: 21.03, longitude: 105.8,
        arrival_time: '08:30:00', departure_time: null,
        customer_name: 'Khach', customer_phone: '0902',
        delivery_notes: 'Goi truoc', demand: 10
      }]
    };

    beforeEach(() => {
      api.driverRouteDetail.and.returnValue(of(detail as unknown as DriverRouteDTO));
      component.openSend(sends[0] as unknown as RouteSendDTO);
      fixture.detectChanges();
    });

    it('renders the map container, note and navigate link', () => {
      expect(fixture.nativeElement.querySelector('#driver-mini-map')).not.toBeNull();
      expect(fixture.nativeElement.textContent).toContain('for guidance only');
      const link: HTMLAnchorElement | null =
        fixture.nativeElement.querySelector('a[href*="google.com/maps/dir"]');
      expect(link?.href).toContain('destination=21.03,105.8');
      expect(link?.target).toBe('_blank');
    });

    it('formats sent_at readably', () => {
      component.backToList();
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('06/10/2026');
      expect(fixture.nativeElement.textContent).not.toContain('T09:00:00');
    });

    it('shows the next step up front and the full list on toggle', () => {
      routing.getRouteLegs.and.returnValue(of([{
        summary: 'A-B', distanceKm: 1.2, durationMin: 5,
        steps: [
          { instruction: 'Head out', distanceM: 0 },
          { instruction: 'Turn right onto Pho Hue', distanceM: 900 }
        ]
      }]));
      component.backToList();
      component.openSend(sends[0] as unknown as RouteSendDTO);
      fixture.detectChanges();

      expect(component.maneuvers().length).toBe(1);
      // Next-step card visible; full list collapsed.
      expect(fixture.nativeElement.querySelector('.guide-current').textContent)
        .toContain('Turn right onto Pho Hue');
      expect(fixture.nativeElement.querySelector('.driver-maneuvers')).toBeNull();

      component.toggleGuidance();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.driver-maneuvers').textContent)
        .toContain('Turn right onto Pho Hue');
      expect(fixture.nativeElement.querySelector('.driver-maneuvers').textContent)
        .not.toContain('Head out');
    });

    it('collapses the stop list behind a next-stop preview', () => {
      const text: string = fixture.nativeElement.textContent;

      // Next-stop card shows the delivery stop up front.
      expect(fixture.nativeElement.querySelector('.next-stop').textContent)
        .toContain('Khach');
      expect(fixture.nativeElement.querySelector('.next-stop').textContent)
        .toContain('0902');
      // Full list hidden until expanded.
      expect(fixture.nativeElement.querySelector('.driver-stops')).toBeNull();

      component.toggleStops();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.driver-stops').textContent)
        .toContain('Goi truoc');
      expect(text).toContain('Khach');
    });

    it('resets disclosure state when reopening', () => {
      component.toggleGuidance();
      component.toggleStops();
      component.backToList();

      expect(component.guidanceOpen()).toBeFalse();
      expect(component.stopsOpen()).toBeFalse();
    });

    it('hides guidance when legs fail', () => {
      routing.getRouteLegs.and.returnValue(of(null));
      component.backToList();
      component.openSend(sends[0] as unknown as RouteSendDTO);
      fixture.detectChanges();

      expect(component.maneuvers()).toEqual([]);
      expect(fixture.nativeElement.querySelector('.driver-maneuvers')).toBeNull();
    });
  });

  describe('GPS dot', () => {
    const originalGeo = Object.getOwnPropertyDescriptor(window.navigator, 'geolocation');

    beforeEach(() => {
      api.driverRouteDetail.and.returnValue(of({
        send_id: 1, solution_id: 9, route_id: 21,
        vehicle_license_plate: '29A-1', status: 'CURRENT',
        stops: [{
          sequence_number: 1, type: 'ORDER', order_id: 11,
          location_name: 'Khach', address: 'Hanoi',
          latitude: 21.0, longitude: 105.8,
          arrival_time: '08:00:00', departure_time: null,
          customer_name: null, customer_phone: null,
          delivery_notes: null, demand: null
        }]
      } as unknown as DriverRouteDTO));
    });

    afterEach(() => {
      if (originalGeo) {
        Object.defineProperty(window.navigator, 'geolocation', originalGeo);
      }
    });

    function mockGeolocation(
      impl: (ok: PositionCallback, err?: PositionErrorCallback) => number) {
      const clearWatch = jasmine.createSpy('clearWatch');
      Object.defineProperty(window.navigator, 'geolocation', {
        configurable: true,
        value: { watchPosition: jasmine.createSpy('watchPosition').and.callFake(impl), clearWatch }
      });
      return clearWatch;
    }

    function positionAt(lat: number, lon: number): GeolocationPosition {
      return {
        coords: {
          latitude: lat, longitude: lon, accuracy: 25,
          altitude: null, altitudeAccuracy: null, heading: null, speed: null
        } as GeolocationCoordinates,
        timestamp: 0
      } as GeolocationPosition;
    }

    it('shows the dot on position and clears the watch on back', () => {
      const clearWatch = mockGeolocation(ok => {
        ok(positionAt(21.0, 105.8));
        return 7;
      });
      component.openSend(sends[0] as unknown as RouteSendDTO);
      fixture.detectChanges();

      expect(document.querySelectorAll('#driver-mini-map path').length)
        .toBeGreaterThan(0);

      component.backToList();
      expect(clearWatch).toHaveBeenCalledWith(7);
    });

    it('draws the dot above stop pins so a driver at a stop still sees it', () => {
      mockGeolocation(ok => {
        ok(positionAt(21.0, 105.8)); // exactly on the stop pin
        return 9;
      });
      component.openSend(sends[0] as unknown as RouteSendDTO);
      fixture.detectChanges();

      const gpsPane = document.querySelector<HTMLElement>('#driver-mini-map .leaflet-driverGps-pane');
      const markerPane = document.querySelector<HTMLElement>('#driver-mini-map .leaflet-marker-pane');
      expect(gpsPane).not.toBeNull();
      expect(Number(gpsPane!.style.zIndex))
        .toBeGreaterThan(Number(getComputedStyle(markerPane!).zIndex));
      const dot = gpsPane!.querySelector('path[fill="#1a73e8"]');
      expect(dot).not.toBeNull();
      expect(dot!.classList.contains('leaflet-interactive')).toBeFalse();
    });

    it('stays silent when permission is denied', () => {
      mockGeolocation((ok, err) => {
        err?.({ code: 1, message: 'denied', PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 } as GeolocationPositionError);
        return 8;
      });

      expect(() => {
        component.openSend(sends[0] as unknown as RouteSendDTO);
        fixture.detectChanges();
      }).not.toThrow();
    });
  });

  describe('follow mode', () => {
    const originalGeo = Object.getOwnPropertyDescriptor(window.navigator, 'geolocation');
    const originalPerms = Object.getOwnPropertyDescriptor(window.navigator, 'permissions');
    let cbs: PositionCallback[];
    let permState: string | null;
    let geoOk: PositionCallback | null;
    let geoErr: PositionErrorCallback | null | undefined;

    const pos = (lat: number, lon: number): GeolocationPosition => ({
      coords: {
        latitude: lat, longitude: lon, accuracy: 25,
        altitude: null, altitudeAccuracy: null, heading: null, speed: null
      } as GeolocationCoordinates,
      timestamp: 0
    } as GeolocationPosition);

    const deniedError = (): GeolocationPositionError => ({
      code: 1, message: 'denied',
      PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3
    } as GeolocationPositionError);

    beforeEach(() => {
      cbs = [];
      permState = null;
      geoOk = null;
      geoErr = undefined;
      Object.defineProperty(window.navigator, 'geolocation', {
        configurable: true,
        value: {
          watchPosition: jasmine.createSpy('watchPosition')
            .and.callFake((cb: PositionCallback) => {
              cbs.push(cb);
              return 11;
            }),
          clearWatch: jasmine.createSpy('clearWatch'),
          getCurrentPosition: jasmine.createSpy('getCurrentPosition')
            .and.callFake((ok: PositionCallback, err?: PositionErrorCallback) => {
              geoOk = ok;
              geoErr = err;
            })
        }
      });
      Object.defineProperty(window.navigator, 'permissions', {
        configurable: true,
        value: {
          query: jasmine.createSpy('query').and.callFake(() => {
            if (permState === null) {
              return Promise.reject(new Error('no API'));
            }
            return Promise.resolve({ state: permState });
          })
        }
      });
      api.driverRouteDetail.and.returnValue(of({
        send_id: 1, solution_id: 9, route_id: 21,
        vehicle_license_plate: '29A-1', status: 'CURRENT',
        stops: [{
          sequence_number: 1, type: 'ORDER', order_id: 11,
          location_name: 'Khach', address: null,
          latitude: 21.0, longitude: 105.8,
          arrival_time: null, departure_time: null,
          customer_name: null, customer_phone: null,
          delivery_notes: null, demand: null
        }]
      } as unknown as DriverRouteDTO));
      component.openSend(sends[0] as unknown as RouteSendDTO);
      fixture.detectChanges();
    });

    afterEach(() => {
      if (originalGeo) {
        Object.defineProperty(window.navigator, 'geolocation', originalGeo);
      }
      if (originalPerms) {
        Object.defineProperty(window.navigator, 'permissions', originalPerms);
      }
    });

    it('is off by default and never pans', () => {
      expect(component.following()).toBeFalse();
      const map = (component as unknown as { map: L.Map }).map;
      const panTo = spyOn(map, 'panTo');
      cbs.forEach(cb => cb(pos(21.1, 105.9)));

      expect(panTo).not.toHaveBeenCalled();
    });

    it('turns on directly when already granted', async () => {
      permState = 'granted';

      component.toggleFollow();
      await fixture.whenStable();

      expect(component.following()).toBeTrue();
      expect(component.followGate()).toBe('none');
    });

    it('shows help when already denied', async () => {
      permState = 'denied';

      component.toggleFollow();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(component.following()).toBeFalse();
      expect(component.followGate()).toBe('help');
      expect(fixture.nativeElement.textContent).toContain('blocked');
    });

    it('asks in-app first when undecided, then tracks on approval', async () => {
      permState = 'prompt';

      component.toggleFollow();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(component.followGate()).toBe('prompt');
      expect(fixture.nativeElement.textContent).toContain('keeps the map centered');

      component.allowFollow();
      expect(geoOk).not.toBeNull();
      geoOk?.(pos(21.1, 105.9));

      expect(component.following()).toBeTrue();
      expect(component.followGate()).toBe('none');
    });

    it('shows help when the native prompt is refused', async () => {
      permState = 'prompt';

      component.toggleFollow();
      await fixture.whenStable();
      component.allowFollow();
      geoErr?.(deniedError());
      fixture.detectChanges();

      expect(component.following()).toBeFalse();
      expect(component.followGate()).toBe('help');
    });

    it('dismisses the gate without tracking', async () => {
      permState = 'prompt';

      component.toggleFollow();
      await fixture.whenStable();
      component.dismissGate();

      expect(component.followGate()).toBe('none');
      expect(component.following()).toBeFalse();
    });

    it('pans to the dot while following', async () => {
      permState = 'granted';

      component.toggleFollow();
      await fixture.whenStable();
      const map = (component as unknown as { map: L.Map }).map;
      const panTo = spyOn(map, 'panTo');
      cbs.forEach(cb => cb(pos(21.1, 105.9)));

      expect(panTo).toHaveBeenCalled();
    });

    it('pauses on manual drag and stops on toggle off', async () => {
      permState = 'granted';

      component.toggleFollow();
      await fixture.whenStable();
      const map = (component as unknown as { map: L.Map }).map;
      map.fire('dragstart');

      expect(component.following()).toBeFalse();

      const panTo = spyOn(map, 'panTo');
      cbs.forEach(cb => cb(pos(21.1, 105.9)));
      expect(panTo).not.toHaveBeenCalled();
    });
  });
});
