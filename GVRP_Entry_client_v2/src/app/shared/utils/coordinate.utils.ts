/**
 * Shared coordinate formatting.
 *
 * Pure helper: formats a single coordinate value with 6 decimal places,
 * matching the V1 `toFixed(6)` display used by depot setup and geocoding.
 */
export function formatCoordinate(value: number): string {
  return value.toFixed(6);
}

/**
 * True when a point can be placed on the map: finite latitude in [-90, 90]
 * and finite longitude in [-180, 180]. `OrderDTO` coordinates come from a
 * nullable location, so null and NaN reach the client (MAP-ORDERS-001, MO-R4).
 */
export function hasMapCoordinates(
  point: { latitude?: number | null; longitude?: number | null } | null | undefined
): boolean {
  const lat = point?.latitude;
  const lng = point?.longitude;
  return typeof lat === 'number' && Number.isFinite(lat) && lat >= -90 && lat <= 90
    && typeof lng === 'number' && Number.isFinite(lng) && lng >= -180 && lng <= 180;
}
