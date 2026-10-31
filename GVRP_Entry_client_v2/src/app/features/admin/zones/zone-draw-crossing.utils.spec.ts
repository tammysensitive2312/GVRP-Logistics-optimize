import { polygonFromPoints, ringSelfIntersects } from './zone-draw.utils';

/** LF-C1 (LEZ-FIX-001, D2): crossing edges of a drawn outline. */
describe('ringSelfIntersects', () => {
  /** Closed GeoJSON ring of Leaflet `[lat, lng]` offsets from central Hà Nội, in 0.01°. */
  const ring = (...offsets: [number, number][]): [number, number][] =>
    polygonFromPoints(offsets.map(([dLat, dLng]) => [21.0 + dLat / 100, 105.8 + dLng / 100]))!
      .coordinates[0];

  it('LF-C1: a square does not cross', () => {
    expect(ringSelfIntersects(ring([0, 0], [0, 1], [1, 1], [1, 0]))).toBeFalse();
  });

  it('LF-C1: corners clicked 1-3-2-4 make a bow tie', () => {
    expect(ringSelfIntersects(ring([0, 0], [1, 1], [0, 1], [1, 0]))).toBeTrue();
  });

  it('LF-C1: a triangle does not cross', () => {
    expect(ringSelfIntersects(ring([0, 0], [0, 1], [1, 1]))).toBeFalse();
  });

  it('LF-C1: a concave "L" shape does not cross', () => {
    expect(ringSelfIntersects(ring([0, 0], [0, 2], [1, 2], [1, 1], [2, 1], [2, 0]))).toBeFalse();
  });

  it('LF-C1: a vertex touching a non-adjacent edge counts as crossing', () => {
    // The fourth corner lies on the first edge.
    expect(ringSelfIntersects(ring([0, 0], [0, 4], [4, 4], [0, 2], [4, 0]))).toBeTrue();
  });
});
