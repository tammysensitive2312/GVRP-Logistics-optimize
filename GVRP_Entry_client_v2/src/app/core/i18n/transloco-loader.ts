import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Translation, TranslocoLoader } from '@jsverse/transloco';
import { Observable } from 'rxjs';

import { SKIP_LOADING } from '@core/interceptors/loading.context';

/**
 * Loads translation dictionaries from `public/i18n/<lang>.json`.
 * They live under `public/` because angular.json only copies that folder.
 */
@Injectable({ providedIn: 'root' })
export class TranslocoHttpLoader implements TranslocoLoader {
  private readonly http = inject(HttpClient);

  getTranslation(lang: string): Observable<Translation> {
    return this.http.get<Translation>(`/i18n/${lang}.json`, {
      // Dictionary fetches should not trip the global loading indicator.
      context: new HttpContext().set(SKIP_LOADING, true)
    });
  }
}
