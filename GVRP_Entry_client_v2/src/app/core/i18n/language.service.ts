import { computed, inject, Injectable, signal } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';

import { FlagCountry } from '@shared/components/flag-icon/flag-icon.component';
import { StorageService } from '@core/services/storage.service';

export type AppLanguage = 'en' | 'vi';

export interface LanguageOption {
  code: AppLanguage;
  label: string;
  /** Country whose flag represents this language (see FlagIconComponent). */
  flag: FlagCountry;
}

export const APP_LANGUAGES: readonly LanguageOption[] = [
  { code: 'en', label: 'English', flag: 'gb' },
  { code: 'vi', label: 'Tiếng Việt', flag: 'vn' }
];

export const DEFAULT_LANGUAGE: AppLanguage = 'en';

/**
 * Runtime language switching.
 *
 * English is the default for every screen; Vietnamese is a dictionary on top.
 * The choice is remembered through StorageService so no component touches
 * localStorage directly (SSR-safe).
 */
@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly transloco = inject(TranslocoService);
  private readonly storage = inject(StorageService);

  private readonly _current = signal<AppLanguage>(DEFAULT_LANGUAGE);

  readonly current = this._current.asReadonly();
  readonly languages = APP_LANGUAGES;
  readonly currentOption = computed(
    () =>
      APP_LANGUAGES.find(language => language.code === this._current()) ?? APP_LANGUAGES[0]
  );

  readonly nextOption = computed(
    () =>
      APP_LANGUAGES.find(language => language.code !== this._current()) ?? APP_LANGUAGES[0]
  );

  init(): void {
    const stored = this.storage.getLanguage();
    this.use(isSupported(stored) ? stored : DEFAULT_LANGUAGE);
  }

  use(language: AppLanguage): void {
    this._current.set(language);
    this.transloco.setActiveLang(language);
    this.storage.setLanguage(language);
  }

  toggle(): void {
    this.use(this._current() === 'en' ? 'vi' : 'en');
  }
}

function isSupported(value: string | null): value is AppLanguage {
  return value === 'en' || value === 'vi';
}
