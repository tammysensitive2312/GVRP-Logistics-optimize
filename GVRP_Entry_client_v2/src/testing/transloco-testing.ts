import { TranslocoTestingModule, TranslocoTestingOptions } from '@jsverse/transloco';

/**
 * Transloco setup for unit tests.
 *
 * The dictionaries are intentionally empty: Transloco's default missing handler
 * echoes the key back, so specs assert on stable keys
 * (`depotSetup.errors.nameRequired`) instead of user-facing copy that product
 * wording changes would break.
 */
export function translocoTesting(options: TranslocoTestingOptions = {}) {
  return TranslocoTestingModule.forRoot({
    langs: { en: {}, vi: {} },
    translocoConfig: {
      availableLangs: ['en', 'vi'],
      defaultLang: 'en'
    },
    preloadLangs: true,
    ...options
  });
}
