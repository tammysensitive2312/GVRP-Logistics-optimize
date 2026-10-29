import { EXAMPLE_ZONE, polygonFromPoints } from './zone-draw.utils';

/** UL-T1 (UX-LEZ-001, D1): polygon of the clicked points. */
describe('polygonFromPoints', () => {
  it('UL-T1: two points are not an area', () => {
    expect(polygonFromPoints([[21.0, 105.8], [21.1, 105.9]])).toBeNull();
  });

  it('UL-T1: three points give a closed ring in [lng, lat]', () => {
    const polygon = polygonFromPoints([[21.0, 105.8], [21.0, 105.9], [21.1, 105.9]]);

    expect(polygon).toEqual({
      type: 'Polygon',
      coordinates: [[[105.8, 21.0], [105.9, 21.0], [105.9, 21.1], [105.8, 21.0]]]
    });
  });

  it('UL-T1: duplicates do not count; fewer than 3 distinct points → null', () => {
    expect(polygonFromPoints([[21.0, 105.8], [21.0, 105.8], [21.1, 105.9]])).toBeNull();

    const polygon = polygonFromPoints([[21.0, 105.8], [21.0, 105.8], [21.0, 105.9], [21.1, 105.9]]);
    expect(polygon?.coordinates[0].length).toBe(4);
  });

  it('UL-T1: values are rounded to 6 decimals', () => {
    const polygon = polygonFromPoints([[21.12345678, 105.87654321], [21.2, 105.8], [21.3, 105.9]]);

    expect(polygon?.coordinates[0][0]).toEqual([105.876543, 21.123457]);
  });

  it('EXAMPLE_ZONE is a closed Polygon in Hà Nội', () => {
    const ring = EXAMPLE_ZONE.coordinates[0];
    expect(EXAMPLE_ZONE.type).toBe('Polygon');
    expect(ring[0]).toEqual(ring[ring.length - 1]);
    for (const [lng, lat] of ring) {
      expect(lng).toBeGreaterThan(105.7);
      expect(lng).toBeLessThan(105.95);
      expect(lat).toBeGreaterThan(20.95);
      expect(lat).toBeLessThan(21.1);
    }
  });
});
