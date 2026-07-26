import { FormControl, FormGroup } from '@angular/forms';

import {
  latitudeRange,
  longitudeRange,
  notBlank,
  requiredLocation
} from './geo.validators';

describe('geo.validators', () => {
  describe('notBlank', () => {
    const validator = notBlank();

    it('rejects empty and whitespace-only values', () => {
      expect(validator(new FormControl(''))).toEqual({ notBlank: true });
      expect(validator(new FormControl('   '))).toEqual({ notBlank: true });
      expect(validator(new FormControl(null))).toEqual({ notBlank: true });
    });

    it('accepts a value with actual content', () => {
      expect(validator(new FormControl('Kho Hà Nội'))).toBeNull();
    });
  });

  describe('latitudeRange', () => {
    const validator = latitudeRange();

    it('accepts values inside [-90, 90] and the bounds themselves', () => {
      expect(validator(new FormControl(21.028511))).toBeNull();
      expect(validator(new FormControl(-90))).toBeNull();
      expect(validator(new FormControl(90))).toBeNull();
    });

    it('rejects values outside the range', () => {
      expect(validator(new FormControl(90.1))).toBeTruthy();
      expect(validator(new FormControl(-91))).toBeTruthy();
    });

    it('rejects non-numeric values', () => {
      expect(validator(new FormControl('abc'))).toBeTruthy();
    });

    it('stays silent on empty values so required-checks own that case', () => {
      expect(validator(new FormControl(null))).toBeNull();
      expect(validator(new FormControl(''))).toBeNull();
    });
  });

  describe('longitudeRange', () => {
    const validator = longitudeRange();

    it('accepts values inside [-180, 180]', () => {
      expect(validator(new FormControl(105.804817))).toBeNull();
      expect(validator(new FormControl(-180))).toBeNull();
      expect(validator(new FormControl(180))).toBeNull();
    });

    it('rejects values outside the range', () => {
      expect(validator(new FormControl(180.5))).toBeTruthy();
      expect(validator(new FormControl(-181))).toBeTruthy();
    });
  });

  describe('requiredLocation', () => {
    const validator = requiredLocation();

    const buildGroup = (latitude: number | null, longitude: number | null) =>
      new FormGroup({
        latitude: new FormControl<number | null>(latitude),
        longitude: new FormControl<number | null>(longitude)
      });

    it('passes when both coordinates are present', () => {
      expect(validator(buildGroup(21.028511, 105.804817))).toBeNull();
    });

    it('accepts 0 as a valid coordinate', () => {
      expect(validator(buildGroup(0, 0))).toBeNull();
    });

    it('fails when either coordinate is missing', () => {
      expect(validator(buildGroup(null, 105.804817))).toEqual({ requiredLocation: true });
      expect(validator(buildGroup(21.028511, null))).toEqual({ requiredLocation: true });
      expect(validator(buildGroup(null, null))).toEqual({ requiredLocation: true });
    });
  });
});
