import type { DriverStopDTO, RouteGeometryDTO } from '@core/models';
import type { ManeuverStep } from '@shared/services/routing.service';

import { decodePolyline } from './polyline.utils';

/**
 * Geometry source rule of the maps (LEZ-001 P1c, design §3.4 / §3.5).
 *
 * These are pure decisions so the rule can be tested without a Leaflet map:
 * the caller only falls back to OSRM when the function returns null.
 */

/**
 * The stored path of one plan route, as `[lat, lng]` pairs, or null when the
 * server geometry is incomplete. Complete means at least one leg and a
 * non-empty polyline on **every** leg: a partly routed route would otherwise
 * draw straight jumps between the routed pieces.
 */
export function completeRoutePolyline(
  geometry: RouteGeometryDTO | null | undefined
): [number, number][] | null {
  const legs = [...(geometry?.legs ?? [])]
    .filter(leg => leg && typeof leg.from_seq === 'number')
    .sort((a, b) => a.from_seq - b.from_seq);
  if (legs.length === 0) {
    return null;
  }

  const path: [number, number][] = [];
  for (const leg of legs) {
    if (!leg.polyline) {
      return null;
    }
    const decoded = decodePolyline(leg.polyline);
    if (decoded.length === 0) {
      return null;
    }
    // The first point of a leg repeats the last point of the previous one.
    if (path.length > 0) {
      decoded.shift();
    }
    path.push(...decoded);
  }
  return path.length >= 2 ? path : null;
}

/**
 * The stored path of every leg, in plan order, or null when the geometry is
 * incomplete (the same completeness rule as {@link completeRoutePolyline}).
 *
 * The legs stay separate so one leg can be highlighted on the planner map
 * without redrawing the route (review-p1c R3).
 */
export function storedLegPaths(
  geometry: RouteGeometryDTO | null | undefined
): [number, number][][] | null {
  const legs = [...(geometry?.legs ?? [])]
    .filter(leg => leg && typeof leg.from_seq === 'number')
    .sort((a, b) => a.from_seq - b.from_seq);
  if (legs.length === 0) {
    return null;
  }

  const paths: [number, number][][] = [];
  for (const leg of legs) {
    if (!leg.polyline) {
      return null;
    }
    const decoded = decodePolyline(leg.polyline);
    if (decoded.length === 0) {
      return null;
    }
    paths.push(decoded);
  }
  return paths;
}

/**
 * The driver map's per-leg paths, or null when any consecutive stop pair has
 * no stored polyline. The driver map draws one polyline per leg, so the legs
 * stay separate instead of being joined into one path.
 */
export function completeDriverLegs(
  stops: readonly DriverStopDTO[]
): [number, number][][] | null {
  if (stops.length < 2) {
    return null;
  }
  const legs: [number, number][][] = [];
  for (let i = 0; i < stops.length - 1; i++) {
    const encoded = stops[i].geometry_to_next;
    if (!encoded) {
      return null;
    }
    const decoded = decodePolyline(encoded);
    if (decoded.length < 2) {
      return null;
    }
    legs.push(decoded);
  }
  return legs;
}

/**
 * Stored turn-by-turn maneuvers of a driver route, or null when the route has
 * no usable stored instructions (then today's OSRM maneuver list is kept).
 *
 * Design §3.5: server instructions are used only when the geometry is complete
 * and at least one leg carries a non-empty instruction list; the same
 * `distance > 0` filter as the OSRM path applies after that.
 */
export function driverInstructionSteps(
  stops: readonly DriverStopDTO[]
): ManeuverStep[] | null {
  if (!completeDriverLegs(stops)) {
    return null;
  }
  const steps: ManeuverStep[] = [];
  let sawInstruction = false;
  for (let i = 0; i < stops.length - 1; i++) {
    const instructions = stops[i].instructions_to_next ?? [];
    if (instructions.length > 0) {
      sawInstruction = true;
    }
    for (const instruction of instructions) {
      steps.push({
        instruction: instruction.text,
        distanceM: instruction.distance_m ?? 0
      });
    }
  }
  if (!sawInstruction) {
    return null;
  }
  return steps.filter(step => step.distanceM > 0);
}
