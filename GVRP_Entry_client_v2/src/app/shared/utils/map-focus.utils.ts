/**
 * Where a low-emission violation points on the planner map (LEZ-001 P1c review
 * fix, review-p1c R3).
 *
 * `ComplianceChecker` reports `seq` as the 0-based plan index of a **stop**.
 * For the leg codes that stop is the departing stop of the offending leg, so
 * the leg is `seq → seq+1`; for a stop code the stop itself is the offender and
 * the leg that reaches it is `seq−1 → seq`. `LEZ_DATA_MISSING` covers both
 * cases, and only its server detail ("…transit through…") tells them apart.
 *
 * The decision is pure so it can be tested without a Leaflet map.
 */

export interface LegFocus {
  /** Plan index of the departing stop. */
  fromSeq: number;
  /** Plan index of the arriving stop. */
  toSeq: number;
}

export interface ViolationFocus {
  /** Plan index of the stop to centre on, or null for a leg-only violation. */
  stopSeq: number | null;
  /** Leg to highlight, or null when the violation concerns the first stop only. */
  leg: LegFocus | null;
}

/** Violation codes whose `seq` is the departing stop of a leg. */
const LEG_CODES: ReadonlySet<string> = new Set(['ZONE_TRANSIT', 'LEZ_GEOMETRY_MISSING']);

/** The leg variant of `LEZ_DATA_MISSING` is recognisable only by its detail. */
function isTransitDetail(code: string, detail?: string | null): boolean {
  return code === 'LEZ_DATA_MISSING'
    && (detail ?? '').toLowerCase().includes('transit');
}

export function violationFocus(
  code: string,
  seq: number,
  detail?: string | null
): ViolationFocus {
  if (LEG_CODES.has(code) || isTransitDetail(code, detail)) {
    return { stopSeq: null, leg: { fromSeq: seq, toSeq: seq + 1 } };
  }
  return {
    stopSeq: seq,
    leg: seq > 0 ? { fromSeq: seq - 1, toSeq: seq } : null
  };
}
