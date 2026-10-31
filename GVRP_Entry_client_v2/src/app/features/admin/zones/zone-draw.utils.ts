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

/**
 * True when two non-adjacent edges of the closed ring cross or touch (a "bow
 * tie", LEZ-FIX-001 D2).
 *
 * Pure: the ring is the closed GeoJSON ring of `polygonFromPoints` (first
 * position = last). Every pair of non-adjacent edges is tested, the first and
 * last edge counting as adjacent; collinear overlap counts as touching. The
 * server's geometry check stays the authority.
 */
export function ringSelfIntersects(ring: readonly [number, number][]): boolean {
  const edges = ring.length - 1;
  for (let i = 0; i < edges; i++) {
    for (let j = i + 2; j < edges; j++) {
      if (i === 0 && j === edges - 1) {
        continue;
      }
      if (segmentsIntersect(ring[i], ring[i + 1], ring[j], ring[j + 1])) {
        return true;
      }
    }
  }
  return false;
}

/** Tolerance for "on the line": rounded degrees give cross products far above it. */
const ORIENTATION_EPSILON = 1e-12;

function orientation(a: readonly number[], b: readonly number[], c: readonly number[]): number {
  const cross = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
  return Math.abs(cross) <= ORIENTATION_EPSILON ? 0 : Math.sign(cross);
}

/** True when `c`, collinear with `a`-`b`, lies within the bounding box of `a`-`b`. */
function onSegment(a: readonly number[], b: readonly number[], c: readonly number[]): boolean {
  return Math.min(a[0], b[0]) <= c[0] && c[0] <= Math.max(a[0], b[0])
    && Math.min(a[1], b[1]) <= c[1] && c[1] <= Math.max(a[1], b[1]);
}

function segmentsIntersect(
  p1: readonly number[], p2: readonly number[], q1: readonly number[], q2: readonly number[]
): boolean {
  const o1 = orientation(p1, p2, q1);
  const o2 = orientation(p1, p2, q2);
  const o3 = orientation(q1, q2, p1);
  const o4 = orientation(q1, q2, p2);
  if (o1 !== o2 && o3 !== o4 && o1 !== 0 && o2 !== 0 && o3 !== 0 && o4 !== 0) {
    return true;
  }
  return (o1 === 0 && onSegment(p1, p2, q1))
    || (o2 === 0 && onSegment(p1, p2, q2))
    || (o3 === 0 && onSegment(q1, q2, p1))
    || (o4 === 0 && onSegment(q1, q2, p2));
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
