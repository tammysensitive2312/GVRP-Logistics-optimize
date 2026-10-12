import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';

import {
  RoutingService,
  formatLegDistance,
  maneuverInstruction
} from './routing.service';

describe('RoutingService legs', () => {
  let service: RoutingService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(RoutingService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('maps OSRM legs to maneuver steps', done => {
    service.getRouteLegs([[105.8, 21.0], [105.81, 21.01]]).subscribe(legs => {
      expect(legs?.length).toBe(1);
      expect(legs?.[0].steps.length).toBe(3);
      expect(legs?.[0].steps[1].instruction).toContain('Pho Hue');
      done();
    });

    const req = httpMock.expectOne(r => r.url.includes('/route/v1/driving/'));
    expect(req.request.url).toContain('steps=true');
    req.flush({
      code: 'Ok',
      routes: [{
        legs: [{
          summary: 'A-B', distance: 1250, duration: 300,
          steps: [
            { maneuver: { type: 'depart' }, name: '', distance: 100 },
            { maneuver: { type: 'turn', modifier: 'right' }, name: 'Pho Hue', distance: 900 },
            { maneuver: { type: 'arrive' }, name: '', distance: 250 }
          ]
        }]
      }]
    });
  });

  it('returns null when OSRM reports an error code', done => {
    service.getRouteLegs([[105.8, 21.0], [105.81, 21.01]]).subscribe(legs => {
      expect(legs).toBeNull();
      done();
    });

    httpMock.expectOne(() => true).flush({ code: 'NoRoute' });
  });

  it('returns null for fewer than two coordinates without a request', () => {
    let emitted: unknown = 'pending';
    service.getRouteLegs([[105.8, 21.0]]).subscribe(legs => (emitted = legs));

    expect(emitted).toBeNull();
    httpMock.expectNone(() => true);
  });

  describe('maneuverInstruction', () => {
    it('covers the main maneuver kinds', () => {
      expect(maneuverInstruction({ type: 'depart' }, 'Depot'))
        .toBe('Head out onto Depot');
      expect(maneuverInstruction({ type: 'turn', modifier: 'right' }, 'Pho Hue'))
        .toBe('Turn right onto Pho Hue');
      expect(maneuverInstruction({ type: 'turn', modifier: 'uturn' }, ''))
        .toBe('Make a U-turn');
      expect(maneuverInstruction({ type: 'new name' }, 'Dai La'))
        .toBe('Continue onto Dai La');
      expect(maneuverInstruction({ type: 'continue' }, ''))
        .toBe('Continue straight');
      expect(maneuverInstruction({ type: 'roundabout' }, 'Cau Giay'))
        .toBe('Enter the roundabout onto Cau Giay');
      expect(maneuverInstruction({ type: 'arrive' }, ''))
        .toBe('Arrive at destination');
      expect(maneuverInstruction({ type: 'turn' }, ''))
        .toBe('Turn');
      expect(maneuverInstruction({ type: 'end of road', modifier: 'left' }, 'Pho Gia Quat'))
        .toBe('At the end of the road, turn left onto Pho Gia Quat');
      expect(maneuverInstruction({ type: 'fork', modifier: 'slight left' }, 'Cau Chuong Duong'))
        .toBe('Keep slight left at the fork onto Cau Chuong Duong');
      expect(maneuverInstruction({ type: 'merge' }, ''))
        .toBe('Merge onto the road');
      expect(maneuverInstruction({ type: 'off ramp' }, 'CT37'))
        .toBe('Take the off-ramp onto CT37');
      expect(maneuverInstruction(null, null)).toBe('Continue');
    });
  });

  describe('formatLegDistance', () => {
    it('uses meters below 1 km', () => {
      expect(formatLegDistance(950)).toBe('950 m');
      expect(formatLegDistance(1250)).toBe('1.3 km');
      expect(formatLegDistance(null)).toBe('0 m');
    });
  });
});
