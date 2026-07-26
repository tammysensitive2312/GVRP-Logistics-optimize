import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Optional numeric field that must be strictly positive when filled in.
 * V1 (`Validator.validateVehicleType`) applied this to max_distance and
 * max_duration: `if (data.maxDistance && parseFloat(data.maxDistance) <= 0)`.
 */
export function greaterThanZeroWhenPresent(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value;
    if (value === null || value === undefined || value === '') return null;

    const parsed = Number(value);
    if (!Number.isFinite(parsed)) {
      return { greaterThanZero: { value } };
    }

    return parsed > 0 ? null : { greaterThanZero: { value: parsed } };
  };
}

/** Rejects negative numbers but accepts 0 and empty (V1: costs must not be negative). */
export function nonNegative(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value;
    if (value === null || value === undefined || value === '') return null;

    const parsed = Number(value);
    if (!Number.isFinite(parsed)) {
      return { nonNegative: { value } };
    }

    return parsed >= 0 ? null : { nonNegative: { value: parsed } };
  };
}
