/** The fields of a violation row the grouping needs. */
export interface GroupableViolation {
  text: string;
  code: string;
  detail: string | null;
  seq: number;
}

/** A violation row, or the one summary line of repeated geometry findings. */
export type GroupedViolation<T extends GroupableViolation> = T | {
  text: string;
  code: 'LEZ_GEOMETRY_MISSING';
  detail: null;
  seq: number;
  /** Not clickable: it stands for several legs. */
  summary: true;
};

/**
 * One route's violation rows with repeated `LEZ_GEOMETRY_MISSING` findings
 * folded into one line (UX-LEZ-001, D9). Pure. Two or more geometry findings
 * become a single non-clickable line placed where the first one was; a single
 * finding and every other violation are returned unchanged and in order.
 */
export function groupGeometryMissing<T extends GroupableViolation>(rows: readonly T[]): GroupedViolation<T>[] {
  const missing = rows.filter(row => row.code === 'LEZ_GEOMETRY_MISSING');
  if (missing.length < 2) {
    return [...rows];
  }
  const grouped: GroupedViolation<T>[] = [];
  let placed = false;
  for (const row of rows) {
    if (row.code !== 'LEZ_GEOMETRY_MISSING') {
      grouped.push(row);
    } else if (!placed) {
      placed = true;
      grouped.push({
        text: `${missing.length} legs have no stored road geometry — it is fetched and checked when the route is sent.`,
        code: 'LEZ_GEOMETRY_MISSING',
        detail: null,
        seq: row.seq,
        summary: true
      });
    }
  }
  return grouped;
}

/** True for the summary line of grouped geometry findings. */
export function isGeometrySummary(row: GroupableViolation & { summary?: boolean }): boolean {
  return row.summary === true;
}
