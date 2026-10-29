import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';

import { LezApiService } from './lez-api.service';

/** BF-C7 (BROWSER-FIX-001, C7): renaming a COPIED zone is a PATCH with `{ name }`. */
describe('LezApiService renameZone', () => {
  let service: LezApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(LezApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('BF-C7: sends PATCH /zones/5 with the name only', () => {
    service.renameZone(5, 'X').subscribe();

    const request = httpMock.expectOne(req => req.url.endsWith('/zones/5'));
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ name: 'X' });
    request.flush({});
  });
});
