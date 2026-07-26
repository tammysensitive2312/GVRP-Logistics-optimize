import { RouteDTO, StopDTO } from '@core/models';

/** V1 route colour palette (solution-view.js and main-map.js used the same list). */
export const ROUTE_COLORS = [
  '#3498db',
  '#e74c3c',
  '#2ecc71',
  '#f1c40f',
  '#9b59b6',
  '#34495e'
] as const;

export function routeColor(index: number): string {
  return ROUTE_COLORS[index % ROUTE_COLORS.length];
}

/** "HH:mm:ss" -> minutes since midnight. Returns NaN for unusable input. */
export function timeToMinutes(time: string | null | undefined): number {
  if (!time) return Number.NaN;

  const [hours, minutes, seconds] = time.split(':').map(Number);
  if (!Number.isFinite(hours) || !Number.isFinite(minutes)) return Number.NaN;

  return hours * 60 + minutes + (Number.isFinite(seconds) ? seconds / 60 : 0);
}

/** Minutes since midnight -> "HH:mm". */
export function minutesToTime(totalMinutes: number): string {
  const hours = Math.floor(totalMinutes / 60);
  const minutes = Math.floor(totalMinutes % 60);
  return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}`;
}

/** V1 `#formatDuration`: minutes -> "2h 15m" / "45m". */
export function formatDuration(minutes: number): string {
  if (!Number.isFinite(minutes)) return '—';

  const hours = Math.floor(minutes / 60);
  const mins = Math.round(minutes % 60);

  return hours > 0 ? `${hours}h ${mins}m` : `${mins}m`;
}

/**
 * V1 ordering rule (used by both the route list and the Gantt chart):
 * the closing depot is identified by `type === 'DEPOT' && departure_time == null`
 * and is moved to the end of the list.
 */
export function orderStops(stops: readonly StopDTO[]): StopDTO[] {
  const ordered = [...stops];

  const endDepotIndex = ordered.findIndex(
    stop => stop.type === 'DEPOT' && stop.departure_time == null
  );

  if (endDepotIndex !== -1 && endDepotIndex !== ordered.length - 1) {
    const [endDepot] = ordered.splice(endDepotIndex, 1);
    ordered.push(endDepot);
  }

  return ordered;
}

export interface TimeBounds {
  startTime: string;
  endTime: string;
  startMinutes: number;
  endMinutes: number;
  durationMinutes: number;
}

/**
 * V1 `#calculateTimeBounds`: earliest start / latest end across routes,
 * falling back to 08:00-18:00 and widening to a minimum 60-minute window.
 */
export function calculateTimeBounds(routes: readonly RouteDTO[]): TimeBounds {
  let earliest: string | null = null;
  let latest: string | null = null;

  for (const route of routes) {
    if (!route.start_time || !route.end_time) continue;

    if (earliest === null || route.start_time < earliest) earliest = route.start_time;
    if (latest === null || route.end_time > latest) latest = route.end_time;
  }

  if (!earliest || !latest) {
    earliest = '08:00:00';
    latest = '18:00:00';
  }

  const startMinutes = timeToMinutes(earliest);
  let endMinutes = timeToMinutes(latest);

  if (endMinutes - startMinutes < 60) {
    endMinutes = startMinutes + 60;
    latest = minutesToTime(endMinutes);
  }

  return {
    startTime: earliest,
    endTime: latest,
    startMinutes,
    endMinutes,
    durationMinutes: endMinutes - startMinutes
  };
}

/** Plain vi-VN number formatting, matching V1's `toLocaleString('vi-VN')`. */
const numberFormatter = new Intl.NumberFormat('vi-VN');

export function formatVndAmount(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return '—';
  return numberFormatter.format(Math.round(value));
}
