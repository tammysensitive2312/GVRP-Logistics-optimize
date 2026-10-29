import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { DriverRouteDTO, RouteSendDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { AuthService } from '@core/services/auth.service';
import { RoutingService } from '@shared/services/routing.service';

import { DriverViewComponent } from './driver-view.component';

/**
 * DV-T1: the driver view uses the stored server geometry and instructions
 * (LEZ-001 P1c, C10). New spec file: `driver-view.component.spec.ts` keeps its
 * own assertions unchanged.
 */
describe('DriverViewComponent stored geometry', () => {
  let fixture: ComponentFixture<DriverViewComponent>;
  let component: DriverViewComponent;
  let api: jasmine.SpyObj<ApiService>;
  let routing: jasmine.SpyObj<RoutingService>;

  const POLYLINE = '_p~iF~ps|U_ulLnnqC_mqNvxq`@';

  const send = {
    id: 1, solution_id: 9, route_id: 21, vehicle_license_plate: '29A-1',
    driver_id: 2, driver_name: 'Tai Lai', sender_name: 'boss',
    sent_at: '2026-10-06T09:00:00', status: 'CURRENT'
  } as unknown as RouteSendDTO;

  const detail = (withInstructions = true): DriverRouteDTO => ({
    send_id: 1,
    solution_id: 9,
    route_id: 21,
    vehicle_license_plate: '29A-1',
    status: 'CURRENT',
    stops: [
      {
        sequence_number: 1, type: 'DEPOT', order_id: null,
        location_name: 'Kho', address: null, latitude: 21.0, longitude: 105.8,
        arrival_time: '08:00:00', departure_time: null,
        customer_name: null, customer_phone: null, delivery_notes: null, demand: null,
        geometry_to_next: POLYLINE,
        instructions_to_next: withInstructions
          ? [{ text: 'Rẽ phải vào đường Nguyễn Trãi', distance_m: 410 }]
          : []
      },
      {
        sequence_number: 2, type: 'ORDER', order_id: 11,
        location_name: 'Khach A', address: 'Hanoi', latitude: 21.01, longitude: 105.81,
        arrival_time: '08:30:00', departure_time: null,
        customer_name: 'Khach A', customer_phone: null, delivery_notes: null, demand: 10,
        geometry_to_next: POLYLINE,
        instructions_to_next: withInstructions
          ? [{ text: 'Đến nơi', distance_m: 30 }, { text: 'Đi thẳng', distance_m: 0 }]
          : [],
        zone_names: ['Hanoi LEZ']
      },
      {
        sequence_number: 3, type: 'DEPOT', order_id: null,
        location_name: 'Kho', address: null, latitude: 21.0, longitude: 105.8,
        arrival_time: '10:00:00', departure_time: null,
        customer_name: null, customer_phone: null, delivery_notes: null, demand: null
      }
    ]
  });

  beforeEach(async () => {
    api = jasmine.createSpyObj<ApiService>('ApiService',
      ['myDriverRoutes', 'driverRouteDetail']);
    api.myDriverRoutes.and.returnValue(of([send]));
    const auth = jasmine.createSpyObj<AuthService>('AuthService', ['logout']);
    const router = jasmine.createSpyObj<Router>('Router', ['navigate']);
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

  const open = (withInstructions = true): void => {
    api.driverRouteDetail.and.returnValue(of(detail(withInstructions)));
    component.openSend(send);
    fixture.detectChanges();
    fixture.detectChanges();
  };

  it('makes no OSRM request when the server data is complete', () => {
    open();

    expect(routing.getRouteGeometry).not.toHaveBeenCalled();
    expect(routing.getRouteLegs).not.toHaveBeenCalled();
  });

  it('builds the maneuvers from the stored instruction text', () => {
    open();

    expect(component.maneuvers().map(step => step.instruction)).toEqual([
      'Rẽ phải vào đường Nguyễn Trãi',
      'Đến nơi'
    ]);
    expect(fixture.nativeElement.textContent)
      .toContain('Rẽ phải vào đường Nguyễn Trãi');
  });

  it('renders the zone chip of a stop', () => {
    open();

    expect(fixture.nativeElement.textContent).toContain('LEZ: Hanoi LEZ');
  });

  it('falls back to OSRM maneuvers when the legs carry no instruction', () => {
    open(false);

    expect(routing.getRouteGeometry).not.toHaveBeenCalled();
    expect(routing.getRouteLegs).toHaveBeenCalled();
  });

  it('keeps today\'s path when the geometry is incomplete', () => {
    const legacy = detail();
    legacy.stops[1].geometry_to_next = null;
    api.driverRouteDetail.and.returnValue(of(legacy));

    component.openSend(send);
    fixture.detectChanges();
    fixture.detectChanges();

    expect(routing.getRouteGeometry).toHaveBeenCalled();
    expect(routing.getRouteLegs).toHaveBeenCalled();
  });
});
