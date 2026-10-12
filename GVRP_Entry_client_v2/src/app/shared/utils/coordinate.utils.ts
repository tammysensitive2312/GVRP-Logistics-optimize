/**
 * Shared coordinate formatting.
 *
 * Pure helper: formats a single coordinate value with 6 decimal places,
 * matching the V1 `toFixed(6)` display used by depot setup and geocoding.
 */
export function formatCoordinate(value: number): string {
  return value.toFixed(6);
}
