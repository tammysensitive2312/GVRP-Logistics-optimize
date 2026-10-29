import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';

import { ApiService } from './api.service';

/** UO-C3 (UX-ORDERS-001, C2): the import call posts the FormData as is. */
describe('ApiService importOrders', () => {
  let service: ApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('UO-C3: POSTs …/orders/import with the FormData body and no explicit Content-Type', () => {
    const body = new FormData();
    body.append('textData', 'ORD-1|A||1 Trang Tien|1|||1||5');
    body.append('deliveryDate', '2026-10-29');

    service.importOrders(body).subscribe();

    const request = httpMock.expectOne(req => req.url.endsWith('/orders/import'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBe(body);
    expect(request.request.headers.has('Content-Type')).toBeFalse();
    request.flush({ success: true, imported_count: 1, skipped_count: 0, errors: [] });
  });
});
