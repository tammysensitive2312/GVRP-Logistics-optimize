import { decodePolyline } from './polyline.utils';

describe('decodePolyline', () => {
  it('decodes the published Google example (PU-T1)', () => {
    // Same vector as the Engine API test EB-T3.
    const points = decodePolyline('_p~iF~ps|U_ulLnnqC_mqNvxq`@');

    expect(points.length).toBe(3);
    expect(points[0][0]).toBeCloseTo(38.5, 5);
    expect(points[0][1]).toBeCloseTo(-120.2, 5);
    expect(points[1][0]).toBeCloseTo(40.7, 5);
    expect(points[1][1]).toBeCloseTo(-120.95, 5);
    expect(points[2][0]).toBeCloseTo(43.252, 5);
    expect(points[2][1]).toBeCloseTo(-126.453, 5);
  });

  it('returns no points for an empty string', () => {
    expect(decodePolyline('')).toEqual([]);
  });

  it('decodes a single point', () => {
    // First point of the published sample.
    expect(decodePolyline('_p~iF~ps|U')).toEqual([[38.5, -120.2]]);
  });
});
