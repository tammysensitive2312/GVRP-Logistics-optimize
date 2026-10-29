import { ComplianceStatus, ComplianceViolationDTO } from '@core/models';

/** `[start, end]` minute pair of the day, or an `HH:mm` string. */
export type MinuteInterval = readonly [number, number];

/**
 * A minute of the day as `HH:mm`. `1440` prints as `24:00`, which is the only
 * place the rule vocabulary accepts it (P1a deviation 3 / `RuleValidator`).
 */
export function minutesToHhMm(minutes: number): string {
  const clamped = Math.max(0, Math.min(24 * 60, Math.round(minutes)));
  const hours = Math.floor(clamped / 60);
  const mins = clamped % 60;
  return `${pad(hours)}:${pad(mins)}`;
}

/** `HH:mm` → minutes, or null when the text is not a valid clock time. */
export function hhMmToMinutes(value: string | null | undefined): number | null {
  if (!value || !/^\d{2}:\d{2}$/.test(value)) {
    return null;
  }
  const [hours, mins] = value.split(':').map(Number);
  if (hours === 24 && mins === 0) {
    return 24 * 60;
  }
  if (hours > 23 || mins > 59) {
    return null;
  }
  return hours * 60 + mins;
}

/** `[[360, 540], [960, 1170]]` → `06:00–09:00, 16:00–19:30`; `—` when empty. */
export function formatIntervals(intervals: readonly MinuteInterval[] | null | undefined): string {
  if (!intervals || intervals.length === 0) {
    return '—';
  }
  return intervals
    .map(([start, end]) => `${minutesToHhMm(start)}–${minutesToHhMm(end)}`)
    .join(', ');
}

/** Human label of a compliance status; the stored enum stays the machine value. */
export function complianceStatusLabel(status: ComplianceStatus | string | null | undefined): string {
  switch (status) {
    case 'COMPLIANT':
      return 'Compliant';
    case 'VIOLATION':
      return 'Violation';
    case 'STALE':
      return 'Needs recheck';
    case 'UNCHECKED':
      return 'Not checked';
    default:
      return 'Unknown';
  }
}

/**
 * One violation as a sentence: code, the stop it happened at, the zone name
 * (the caller resolves the zone key through the compliance `zones` list) and
 * the server's detail.
 */
export function violationSummary(
  violation: ComplianceViolationDTO,
  stopName?: string | null,
  zoneName?: string | null
): string {
  const parts: string[] = [violation.code];
  if (stopName) {
    parts.push(`at ${stopName}`);
  }
  if (zoneName) {
    parts.push(`in ${zoneName}`);
  }
  if (violation.detail) {
    parts.push(`— ${violation.detail}`);
  }
  return parts.join(' ');
}

function pad(value: number): string {
  return value < 10 ? `0${value}` : `${value}`;
}
