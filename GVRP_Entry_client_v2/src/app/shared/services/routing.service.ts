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
   * Get straight line fallback
   */
  getStraightLine(coordinates: number[][]): L.LatLngExpression[] {
    return coordinates.map(c => [c[1], c[0]] as L.LatLngExpression);
  }
}
