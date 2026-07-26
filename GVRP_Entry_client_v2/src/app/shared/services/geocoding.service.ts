import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map, timeout } from 'rxjs/operators';

import { SKIP_LOADING } from '@core/interceptors/loading.context';

interface NominatimReverseResponse {
  display_name?: string;
}

/**
 * Geocoding Service
 *
 * Reverse geocoding via Nominatim, ported from V1 `DepotMap.#reverseGeocode`.
 * Notes:
 * - Nominatim's usage policy allows ~1 request/second, so callers must debounce
 *   or switchMap; this service never retries on its own.
 * - Requests skip the global loading indicator (third-party, best-effort call).
 * - On any failure it resolves to the formatted coordinates instead of erroring,
 *   matching V1 behaviour where the address falls back to "lat, lng".
 */
@Injectable({ providedIn: 'root' })
export class GeocodingService {
  private static readonly NOMINATIM_REVERSE_URL =
    'https://nominatim.openstreetmap.org/reverse';
  private static readonly REQUEST_TIMEOUT_MS = 8000;

  private readonly http = inject(HttpClient);

  reverseGeocode(latitude: number, longitude: number): Observable<string> {
    const fallback = this.formatCoordinates(latitude, longitude);

    const params = new HttpParams()
      .set('format', 'json')
      .set('lat', latitude.toString())
      .set('lon', longitude.toString())
      .set('addressdetails', '1');

    return this.http
      .get<NominatimReverseResponse>(GeocodingService.NOMINATIM_REVERSE_URL, {
        params,
        context: new HttpContext().set(SKIP_LOADING, true)
      })
      .pipe(
        timeout(GeocodingService.REQUEST_TIMEOUT_MS),
        map(response => response?.display_name?.trim() || fallback),
        catchError((error: unknown) => {
          console.warn('Reverse geocoding failed:', error);
          return of(fallback);
        })
      );
  }

  formatCoordinates(latitude: number, longitude: number): string {
    return `${latitude.toFixed(6)}, ${longitude.toFixed(6)}`;
  }
}
