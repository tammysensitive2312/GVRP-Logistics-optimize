/**
 * Shared calendar-date helpers.
 *
 * Pure helper: the planner's local calendar date as `yyyy-MM-dd`.
 * `toISOString()` gives the UTC date, which is the previous day between
 * 00:00 and 07:00 +07:00 (MAP-ORDERS-001, MO-R2).
 */
export function localDateIso(date: Date = new Date()): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
