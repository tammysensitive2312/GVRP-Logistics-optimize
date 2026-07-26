import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export const LATITUDE_BOUNDS = { min: -90, max: 90 } as const;
export const LONGITUDE_BOUNDS = { min: -180, max: 180 } as const;

/**
 * Validators ported from V1 `scripts/utils/validation.js`.
 * Kept generic so fleet / order / vehicle forms can reuse them.
 */

/** Rejects values that are empty or whitespace-only (V1: `!value || value.trim() === ''`). */
export function notBlank(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value;
    if (value === null || value === undefined) return { notBlank: true };
    if (typeof value === 'string' && value.trim().length === 0) {
      return { notBlank: true };
    }
    return null;
  };
}

export function latitudeRange(): ValidatorFn {
  return boundedNumber('latitudeRange', LATITUDE_BOUNDS);
}

export function longitudeRange(): ValidatorFn {
  return boundedNumber('longitudeRange', LONGITUDE_BOUNDS);
}

/**
 * Group-level validator: both coordinates must be present.
 * V1 raised "Vui lòng chọn vị trí trên bản đồ" when either was missing.
 */
export function requiredLocation(
  latitudeKey = 'latitude',
  longitudeKey = 'longitude'
): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const latitude = group.get(latitudeKey)?.value;
    const longitude = group.get(longitudeKey)?.value;

    return isFiniteNumber(latitude) && isFiniteNumber(longitude)
      ? null
      : { requiredLocation: true };
  };
}

function boundedNumber(
  errorKey: string,
  bounds: { readonly min: number; readonly max: number }
): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value;

    // Empty is handled by requiredLocation / required, not here.
    if (value === null || value === undefined || value === '') return null;

    const parsed = Number(value);
    if (!Number.isFinite(parsed)) {
      return { [errorKey]: { value, ...bounds } };
    }

    return parsed >= bounds.min && parsed <= bounds.max
      ? null
      : { [errorKey]: { value: parsed, ...bounds } };
  };
}

function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}
