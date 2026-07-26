import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type FlagCountry = 'gb' | 'vn';

/**
 * Renders a country flag from `public/flags/<country>.svg`.
 *
 * Not emoji: Windows ships no country-flag glyphs, so 🇬🇧 / 🇻🇳 render as the bare
 * letters "GB" / "VN" in Chrome on Windows.
 * Not inline paths either - the official SVGs live as assets so they can be
 * replaced without touching code.
 *
 * Expected files (4:3 ratio):
 *   public/flags/gb.svg
 *   public/flags/vn.svg
 */
@Component({
  selector: 'app-flag-icon',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <img
      class="flag"
      [src]="src()"
      [alt]="label()"
      [width]="width()"
      [height]="height()"
      draggable="false"/>
  `,
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
    }

    .flag {
      display: block;
      border-radius: 2px;
      object-fit: cover;
      box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.12);
    }
  `
})
export class FlagIconComponent {
  readonly country = input.required<FlagCountry>();
  /** Alt text; empty by default so the flag stays decorative next to a label. */
  readonly label = input('');
  readonly width = input(22);
  readonly height = input(16);

  readonly src = computed(() => `/flags/${this.country()}.svg`);
}
