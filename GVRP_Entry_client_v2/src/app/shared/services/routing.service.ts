// src/app/shared/services/routing.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import * as L from 'leaflet';

export interface RouteGeometry {
  coordinates: L.LatLngExpression[];
  distance: number;
  duration: number;
}

export interface ManeuverStep {
  instruction: string;
  distanceM: number;
}

export interface RouteLeg {
  summary: string;
  distanceKm: number;
  durationMin: number;
  steps: ManeuverStep[];
}

export interface OsrmManeuver {
  type?: string;
  modifier?: string;
  location?: number[];
}

const MODIFIER_WORDS: Record<string, string> = {
  uturn: 'Make a U-turn',
  'sharp right': 'Turn sharply right',
  'sharp left': 'Turn sharply left',
  right: 'Turn right',
  left: 'Turn left',
  'slight right': 'Turn slightly right',
  'slight left': 'Turn slightly left'
};

/** Human instruction for one OSRM maneuver; street names pass through verbatim. */
export function maneuverInstruction(
  maneuver: OsrmManeuver | null | undefined, name: string | null | undefined): string {
  const road = (name ?? '').trim();
  const onto = road ? ` onto ${road}` : '';
  const type = maneuver?.type ?? '';
  const modifier = maneuver?.modifier ?? '';

  switch (type) {
    case 'depart':
      return road ? `Head out onto ${road}` : 'Head out';
    case 'arrive':
      return 'Arrive at destination';
    case 'roundabout':
    case 'rotary':
      return road ? `Enter the roundabout onto ${road}` : 'Enter the roundabout';
    case 'exit roundabout':
    case 'exit rotary':
      return road ? `Exit the roundabout onto ${road}` : 'Exit the roundabout';
    case 'new name':
      return road ? `Continue onto ${road}` : 'Continue onto the new road';
    case 'continue':
      return road ? `Continue straight${onto}` : 'Continue straight';
    case 'turn':
      return `${MODIFIER_WORDS[modifier] ?? 'Turn'}${onto}`;
    case 'end of road':
      return modifier
        ? `At the end of the road, ${MODIFIER_WORDS[modifier]?.toLowerCase() ?? 'turn'}${onto}`
        : `At the end of the road${onto}`;
    case 'fork':
      return modifier
        ? `Keep ${modifier} at the fork${onto}`
        : `Keep to the fork${onto}`;
    case 'merge':
      return `Merge${onto || ' onto the road'}`;
    case 'on ramp':
      return `Take the on-ramp${onto}`;
    case 'off ramp':
      return `Take the off-ramp${onto}`;
    case '':
      return road ? `Continue${onto}` : 'Continue';
    default:
      return `${type}${modifier ? ` ${modifier}` : ''}${onto}`.trim() || 'Continue';
  }
}

/** 950 -> "950 m"; 1250 -> "1.3 km". */
export function formatLegDistance(meters: number | null | undefined): string {
  const value = meters ?? 0;
  if (value < 1000) return `${Math.round(value)} m`;
  return `${(value / 1000).toFixed(1)} km`;
}

@Injectable({
  providedIn: 'root'
})
export class RoutingService {
  private readonly OSRM_SERVER = 'https://router.project-osrm.org';

  constructor(private http: HttpClient) {}

  /**
   * Fetch route geometry from OSRM
   */
  getRouteGeometry(coordinates: number[][]): Observable<RouteGeometry | null> {
    if (coordinates.length < 2) {
      return of(null);
    }

    const coordsString = coordinates.map(c => `${c[0]},${c[1]}`).join(';');
    const url = `${this.OSRM_SERVER}/route/v1/driving/${coordsString}?overview=full&geometries=geojson`;

    return this.http.get<any>(url).pipe(
      map(data => {
        if (data.code === 'Ok' && data.routes?.[0]) {
          const route = data.routes[0];
          return {
            coordinates: route.geometry.coordinates.map(
              (coord: number[]) => [coord[1], coord[0]] as L.LatLngExpression
            ),
            distance: route.distance / 1000, // meters to km
            duration: route.duration / 60 // seconds to minutes
          };
        }
        return null;
      }),
      catchError(error => {
        console.error('OSRM fetch error:', error);
        return of(null);
      })
    );
  }

  /**
   * Fetch per-leg maneuver steps from OSRM (for in-app guidance lists).
   * Existing geometry callers are untouched; this is additive.
   */
  getRouteLegs(coordinates: number[][]): Observable<RouteLeg[] | null> {
    if (coordinates.length < 2) {
      return of(null);
    }

    const coordsString = coordinates.map(c => `${c[0]},${c[1]}`).join(';');
    const url = `${this.OSRM_SERVER}/route/v1/driving/${coordsString}`
      + `?overview=false&steps=true&geometries=geojson`;

    return this.http.get<any>(url).pipe(
      map(data => {
        if (data.code !== 'Ok' || !data.routes?.[0]?.legs) {
          return null;
        }
        return data.routes[0].legs.map((leg: any): RouteLeg => ({
          summary: leg.summary ?? '',
          distanceKm: (leg.distance ?? 0) / 1000,
          durationMin: (leg.duration ?? 0) / 60,
          steps: (leg.steps ?? []).map((step: any): ManeuverStep => ({
            instruction: maneuverInstruction(step.maneuver, step.name),
            distanceM: step.distance ?? 0
          }))
        }));
      }),
      catchError(error => {
        console.error('OSRM legs fetch error:', error);
        return of(null);
      })
    );
  }

  /**
   * Get straight line fallback
   */
  getStraightLine(coordinates: number[][]): L.LatLngExpression[] {
    return coordinates.map(c => [c[1], c[0]] as L.LatLngExpression);
  }
}
