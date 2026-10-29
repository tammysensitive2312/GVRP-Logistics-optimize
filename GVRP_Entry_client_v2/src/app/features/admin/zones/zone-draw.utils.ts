/** A GeoJSON Polygon with one ring of `[longitude, latitude]` positions. */
export interface GeoJsonPolygon {
  type: 'Polygon';
  coordinates: [number, number][][];
}

/**
 * Polygon of the points a planner clicked on the editor map (UX-LEZ-001, D1).
 *
 * Pure: points come as Leaflet `[lat, lng]`; the result is GeoJSON
 * (x = longitude, y = latitude, as the server stores it), rounded to 6
 * decimals, with the first position repeated to close the ring. Fewer than 3
 * distinct points is not an area → null.
 */
export function polygonFromPoints(points: readonly [number, number][]): GeoJsonPolygon | null {
  const ring: [number, number][] = [];
  const seen = new Set<string>();
  for (const [lat, lng] of points) {
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
      continue;
    }
    const position: [number, number] = [round6(lng), round6(lat)];
    const key = position.join(',');
    if (seen.has(key)) {
      continue;
    }
    seen.add(key);
    ring.push(position);
  }
  if (ring.length < 3) {
    return null;
  }
  return { type: 'Polygon', coordinates: [[...ring, [...ring[0]] as [number, number]]] };
}

function round6(value: number): number {
  return Math.round(value * 1e6) / 1e6;
}

/** A small rectangle in central Hà Nội (Hoàn Kiếm), for "Load example". */
export const EXAMPLE_ZONE: GeoJsonPolygon = {
  type: 'Polygon',
  coordinates: [[
    [105.845, 21.022],
    [105.858, 21.022],
    [105.858, 21.036],
    [105.845, 21.036],
    [105.845, 21.022]
  ]]
};
