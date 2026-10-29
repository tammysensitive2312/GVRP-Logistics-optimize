import { formatCoordinate, hasMapCoordinates } from './coordinate.utils';

/** MO-T4 of MAP-ORDERS-001: which orders can be placed on the map. */
describe('hasMapCoordinates', () => {
  it('MO-T4: rejects a missing point and null coordinates', () => {
    expect(hasMapCoordinates(null)).toBeFalse();
    expect(hasMapCoordinates(undefined)).toBeFalse();
    expect(hasMapCoordinates({ latitude: null, longitude: null })).toBeFalse();
    expect(hasMapCoordinates({ latitude: 21, longitude: null })).toBeFalse();
    expect(hasMapCoordinates({ latitude: null, longitude: 105 })).toBeFalse();
  });

  it('MO-T4: rejects NaN and infinite values', () => {
    expect(hasMapCoordinates({ latitude: NaN, longitude: 105 })).toBeFalse();
    expect(hasMapCoordinates({ latitude: 21, longitude: NaN })).toBeFalse();
    expect(hasMapCoordinates({ latitude: Infinity, longitude: 105 })).toBeFalse();
  });

  it('MO-T4: rejects out-of-range values', () => {
    expect(hasMapCoordinates({ latitude: 90.5, longitude: 105 })).toBeFalse();
    expect(hasMapCoordinates({ latitude: -91, longitude: 105 })).toBeFalse();
    expect(hasMapCoordinates({ latitude: 21, longitude: 180.1 })).toBeFalse();
    expect(hasMapCoordinates({ latitude: 21, longitude: -181 })).toBeFalse();
  });

  it('MO-T4: accepts valid values, including the range bounds and zero', () => {
    expect(hasMapCoordinates({ latitude: 21.028511, longitude: 105.804817 })).toBeTrue();
    expect(hasMapCoordinates({ latitude: 0, longitude: 0 })).toBeTrue();
    expect(hasMapCoordinates({ latitude: -90, longitude: 180 })).toBeTrue();
    expect(hasMapCoordinates({ latitude: 90, longitude: -180 })).toBeTrue();
  });

  it('leaves formatCoordinate unchanged', () => {
    expect(formatCoordinate(21.0285114)).toBe('21.028511');
  });
});
