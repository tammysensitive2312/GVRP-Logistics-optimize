import {
  APP_INITIALIZER,
  ApplicationConfig,
  importProvidersFrom,
  inject,
  provideZoneChangeDetection
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import {provideHttpClient, withInterceptors} from '@angular/common/http';

import { authInterceptor } from '@core/interceptors/auth.interceptor';
import { errorInterceptor } from '@core/interceptors/error.interceptor';
import { loadingInterceptor } from '@core/interceptors/loading.interceptor';
import { provideAnimations } from '@angular/platform-browser/animations';
import { MatNativeDateModule } from '@angular/material/core';
import { provideTransloco } from '@jsverse/transloco';

import { TranslocoHttpLoader } from '@core/i18n/transloco-loader';
import { DEFAULT_LANGUAGE, LanguageService } from '@core/i18n/language.service';
import { environment } from '@environments/environment';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([
        authInterceptor,
        errorInterceptor,
        loadingInterceptor
        ]
      )
    ),
    provideAnimations(),
    importProvidersFrom(MatNativeDateModule),
    provideTransloco({
      config: {
        availableLangs: ['en', 'vi'],
        defaultLang: DEFAULT_LANGUAGE,
        fallbackLang: DEFAULT_LANGUAGE,
        // English is the source of truth; missing Vietnamese keys fall back to it.
        missingHandler: { useFallbackTranslation: true },
        reRenderOnLangChange: true,
        prodMode: environment.production
      },
      loader: TranslocoHttpLoader
    }),
    // Applies the stored language before the first render.
    // (Angular 18 has no provideAppInitializer yet.)
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: () => {
        const language = inject(LanguageService);
        return () => language.init();
      }
    }
  ]
};
