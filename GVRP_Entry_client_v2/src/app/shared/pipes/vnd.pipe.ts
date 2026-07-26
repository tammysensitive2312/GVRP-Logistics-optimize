import { Pipe, PipeTransform } from '@angular/core';

/**
 * Formats a number as Vietnamese dong (VND).
 * Same output as V1's `Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' })`
 * (used in vehicle-type-form.js, vehicle-card.js and the admin managers), without
 * needing `registerLocaleData` for the app locale.
 */
@Pipe({
  name: 'vnd',
  standalone: true
})
export class VndPipe implements PipeTransform {
  private static readonly formatter = new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND'
  });

  transform(value: number | null | undefined): string {
    if (value === null || value === undefined || !Number.isFinite(value)) {
      return '—';
    }
    return VndPipe.formatter.format(value);
  }
}
