import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';

import { GeocodingService } from './geocoding.service';

describe('GeocodingService', () => {
  let service: GeocodingService;
  let httpMock: HttpTestingController;

  const LAT = 21.028511;
  const LNG = 105.804817;
  const FALLBACK = '21.028511, 105.804817';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(GeocodingService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('returns display_name from Nominatim', done => {
    service.reverseGeocode(LAT, LNG).subscribe(address => {
      expect(address).toBe('1 Đinh Tiên Hoàng, Hà Nội');
      done();
    });

    const req = httpMock.expectOne(
      r => r.url === 'https://nominatim.openstreetmap.org/reverse'
    );
    expect(req.request.params.get('lat')).toBe(LAT.toString());
    expect(req.request.params.get('lon')).toBe(LNG.toString());
    expect(req.request.params.get('format')).toBe('json');

    req.flush({ display_name: '1 Đinh Tiên Hoàng, Hà Nội' });
  });

  it('falls back to formatted coordinates when the request fails', done => {
    service.reverseGeocode(LAT, LNG).subscribe(address => {
      expect(address).toBe(FALLBACK);
      done();
    });

    httpMock
      .expectOne(r => r.url === 'https://nominatim.openstreetmap.org/reverse')
      .error(new ProgressEvent('network error'));
  });

  it('falls back when the response has no display_name', done => {
    service.reverseGeocode(LAT, LNG).subscribe(address => {
      expect(address).toBe(FALLBACK);
      done();
    });

    httpMock
      .expectOne(r => r.url === 'https://nominatim.openstreetmap.org/reverse')
      .flush({});
  });

  it('formats coordinates to six decimals', () => {
    expect(service.formatCoordinates(21.0285114, 105.8048172)).toBe(
      '21.028511, 105.804817'
    );
  });
});
