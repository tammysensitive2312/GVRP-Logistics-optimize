import { violationFocus } from './map-focus.utils';

/**
 * FR3-T2 (review-p1c R3): which indices one violation points at.
 *
 * The decision is pure, so the map rule is tested without Leaflet.
 */
describe('map focus decision', () => {
  it('points a transit violation at the leg leaving that stop', () => {
    expect(violationFocus('ZONE_TRANSIT', 1))
      .toEqual({ stopSeq: null, leg: { fromSeq: 1, toSeq: 2 } });
  });

  it('points a missing-geometry violation at its leg', () => {
    expect(violationFocus('LEZ_GEOMETRY_MISSING', 0))
      .toEqual({ stopSeq: null, leg: { fromSeq: 0, toSeq: 1 } });
  });

  it('points a stop violation at the stop and the leg that reaches it', () => {
    expect(violationFocus('ZONE_ACCESS', 2))
      .toEqual({ stopSeq: 2, leg: { fromSeq: 1, toSeq: 2 } });
  });

  it('leaves the first stop without a leg', () => {
    expect(violationFocus('ZONE_ACCESS', 0))
      .toEqual({ stopSeq: 0, leg: null });
  });

  it('reads the leg variant of LEZ_DATA_MISSING from its detail', () => {
    expect(violationFocus('LEZ_DATA_MISSING', 3, 'Vehicle may not transit through zone Z17'))
      .toEqual({ stopSeq: null, leg: { fromSeq: 3, toSeq: 4 } });
  });

  it('treats LEZ_DATA_MISSING without the transit detail as a stop', () => {
    expect(violationFocus('LEZ_DATA_MISSING', 3, 'Gross weight is unknown'))
      .toEqual({ stopSeq: 3, leg: { fromSeq: 2, toSeq: 3 } });
  });
});
