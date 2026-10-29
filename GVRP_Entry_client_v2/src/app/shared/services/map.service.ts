// src/app/shared/services/map.service.ts
import { Injectable } from '@angular/core';
import * as L from 'leaflet';
// Side-effect import: registers L.polylineDecorator / L.Symbol.arrowHead.
import 'leaflet-polylinedecorator';
import { BehaviorSubject, Observable, firstValueFrom } from 'rxjs';

import { DepotDTO, OrderDTO, RouteGeometryDTO, SolutionDTO, RouteDTO, StopDTO } from '@core/models';
import { LezApiService } from '@core/services/lez-api.service';
import { orderStops, routeColor } from '@shared/utils/solution-view.utils';
import { violationFocus, LegFocus } from '@shared/utils/map-focus.utils';
import { completeRoutePolyline, storedLegPaths } from '@shared/utils/route-geometry.utils';
import { MarkerService } from './marker.service';
import { RoutingService } from './routing.service';

/** What `focusLeg` needs of one drawn route (LEZ-001 P1c review fix, R3). */
interface FocusableRoute {
  /** Markers in plan order, so a violation `seq` addresses the right one. */
  planMarkers: (L.Marker | undefined)[];
  /** Stops in plan order, the order the compliance panel uses. */
  planStops: StopDTO[];
  /** Decoded stored paths per leg in plan order, or null when the route was
   * drawn from OSRM / straight lines; the highlight then uses the two stops. */
  legPaths: [number, number][][] | null;
}

@Injectable({
  providedIn: 'root'
})
export class MapService {
  private map!: L.Map;
  private depotMarkers: L.Marker[] = [];
  private orderMarkers: L.Marker[] = [];
  private routeLayers!: L.FeatureGroup;
  /** Enabled low-emission zones, drawn once per solution (LEZ-001 P1c, C9). */
  private zoneLayers!: L.FeatureGroup;
  /** Temporary leg/stop highlight of one violation, cleared on the next focus. */
  private highlightLayer!: L.FeatureGroup;
  /** Stop markers and drawn legs per route id, for `focusLeg`. */
  private focusable = new Map<number, FocusableRoute>();
  /** True while a solution is drawn, so late-arriving orders don't steal the view. */
  private solutionDisplayed = false;

  /** Route colours come from the shared V1 palette in solution-view.utils. */

  // Observable for map ready state
  private mapReady$ = new BehaviorSubject<boolean>(false);

  constructor(
    private markerService: MarkerService,
    private routingService: RoutingService,
    private lezApi: LezApiService
  ) {}

  /**
   * Binds the service to a Leaflet map.
   *
   * This service is a root singleton while <app-map> is recreated on every visit
   * to /main, so state from the previous map has to be dropped here - otherwise
   * marker arrays keep pointing at destroyed layers and a new scale control is
   * stacked on top of the map each time.
   */
  initializeMap(map: L.Map): void {
    this.map = map;
    this.depotMarkers = [];
    this.orderMarkers = [];
    this.solutionDisplayed = false;
    this.focusable.clear();
    this.routeLayers = L.featureGroup().addTo(this.map);
    this.zoneLayers = L.featureGroup().addTo(this.map);
    this.highlightLayer = L.featureGroup().addTo(this.map);

    L.control.scale({
      position: 'bottomleft',
      imperial: false
    }).addTo(this.map);

    this.mapReady$.next(true);
  }

  isMapReady(): Observable<boolean> {
    return this.mapReady$.asObservable();
  }

  loadDepots(depots: DepotDTO[]): void {
    this.clearDepotMarkers();

    depots.forEach(depot => {
      const marker = this.markerService.createDepotMarker(depot);
      marker.addTo(this.map);
      this.depotMarkers.push(marker);
    });
  }

  private clearDepotMarkers(): void {
    this.depotMarkers.forEach(marker => this.map.removeLayer(marker));
    this.depotMarkers = [];
  }

  loadOrders(
    orders: OrderDTO[],
    onOrderClick?: (orderId: number) => void,
    onOrderEdit?: (orderId: number) => void
  ): void {
    this.clearOrderMarkers();

    orders.forEach(order => {
      const marker = this.markerService.createOrderMarker(order, onOrderClick, onOrderEdit);
      marker.addTo(this.map);
      this.orderMarkers.push(marker);
    });

    // Orders load asynchronously and used to arrive after a solution was drawn,
    // yanking the viewport away from the routes. The solution wins.
    if (orders.length > 0 && !this.solutionDisplayed) {
      this.fitBoundsToMarkers();
    }
  }

  highlightOrder(orderId: number): void {
    const marker = this.orderMarkers.find(m => (m as any)._orderId === orderId);
    if (marker) {
      this.map.setView(marker.getLatLng(), 15, { animate: true });
      marker.openPopup();
    }
  }

  private clearOrderMarkers(): void {
    this.orderMarkers.forEach(marker => this.map.removeLayer(marker));
    this.orderMarkers = [];
  }

  // ============================================
  // SOLUTION DISPLAY
  // ============================================

  async displaySolution(solution: SolutionDTO): Promise<void> {
    this.clearRoutes();
    this.clearOrderMarkers();
    this.solutionDisplayed = true;
    this.drawZones();

    for (let index = 0; index < solution.routes.length; index++) {
      const route = solution.routes[index];
      const color = routeColor(index);

      await this.displayRoute(route, color);
    }

    // Fit bounds
    if (this.routeLayers.getLayers().length > 0) {
      const bounds = this.routeLayers.getBounds();
      if (bounds.isValid()) {
        this.map.fitBounds(bounds.pad(0.1));
      }
    }
  }

  /**
   * Enabled low-emission zones of the branch (LEZ-001 P1c, C9).
   *
   * Drawn once per solution in their own layer, so `clearRoutes` removes them
   * with the polylines. A failed request only costs the overlay: the routes
   * are drawn as before.
   */
  private drawZones(): void {
    this.zoneLayers.clearLayers();
    this.lezApi.listZones().subscribe({
      next: zones => {
        for (const zone of zones ?? []) {
          if (!zone.enabled || !zone.geometry) {
            continue;
          }
          try {
            const layer = L.geoJSON(zone.geometry as GeoJSON.GeoJsonObject, {
              style: {
                color: '#b42318',
                weight: 1,
                fillColor: '#f8b4b4',
                fillOpacity: 0.25
              }
            });
            layer.bindTooltip(zone.name, { sticky: true });
            layer.addTo(this.zoneLayers);
          } catch (error) {
            console.warn('Zone overlay skipped:', error);
          }
        }
      },
      error: error => console.warn('Zone overlay skipped:', error)
    });
  }

  private async displayRoute(route: RouteDTO, color: string): Promise<void> {
    // Sort stops
    const stops = [...route.stops].sort((a, b) => {
      const timeA = a.arrival_time || a.departure_time || '00:00:00';
      const timeB = b.arrival_time || b.departure_time || '00:00:00';
      return timeA.localeCompare(timeB);
    });

    // Add stop markers (arrival order, as before). The focus registry indexes
    // them by plan order instead, because a violation `seq` is a plan index.
    const markerByStop = new Map<StopDTO, L.Marker>();
    stops.forEach((stop, idx) => {
      const type = idx === 0 ? 'start' : idx === stops.length - 1 ? 'end' : 'stop';
      const marker = this.markerService.createRouteStopMarker(
        stop,
        color,
        route.vehicle_license_plate,
        type
      );
      marker.addTo(this.routeLayers);
      markerByStop.set(stop, marker);
    });

    const planStops = orderStops(route.stops);
    const entry: FocusableRoute = {
      planMarkers: planStops.map(stop => markerByStop.get(stop)),
      planStops,
      legPaths: null
    };
    if (route.id != null) {
      this.focusable.set(route.id, entry);
    }

    // Draw route polyline
    const coordinates = stops.map(stop => [stop.longitude, stop.latitude]);

    // Geometry source rule (design §3.4): stored server geometry wins when it
    // is complete, so no OSRM request is made for this route.
    const stored = await this.storedPath(route);
    if (stored) {
      entry.legPaths = stored.legs;
      const polyline = L.polyline(stored.polyline, {
        color,
        weight: 5,
        opacity: 0.8,
        smoothFactor: 1
      }).addTo(this.routeLayers);
      this.addDirectionArrows(polyline, color);
      polyline.bindTooltip(
        `<div style="font-weight: bold;">🚚 ${route.vehicle_license_plate}</div>` +
        `📦 ${route.order_count} orders - 📏 ${route.distance.toFixed(1)} km`,
        { sticky: true }
      );
      return;
    }

    try {
      const routeGeometry = await firstValueFrom(
        this.routingService.getRouteGeometry(coordinates)
      );

      if (routeGeometry?.coordinates) {
        const polyline = L.polyline(routeGeometry.coordinates, {
          color,
          weight: 5,
          opacity: 0.8,
          smoothFactor: 1
        }).addTo(this.routeLayers);

        this.addDirectionArrows(polyline, color);

        polyline.bindTooltip(
          `<div style="font-weight: bold;">🚚 ${route.vehicle_license_plate}</div>` +
          `📦 ${route.order_count} orders - 📏 ${route.distance.toFixed(1)} km`,
          { sticky: true }
        );
      } else {
        // Fallback to straight line
        this.drawStraightLine(coordinates, color, route);
      }
    } catch (error) {
      console.error('Route drawing error:', error);
      this.drawStraightLine(coordinates, color, route);
    }
  }

  /**
   * Stored geometry of one route, or null when the request fails or the
   * geometry is incomplete (a leg without polyline). The caller then keeps
   * today's OSRM / straight-line path, as design §3.4 requires.
   *
   * The per-leg paths are returned as well: the highlight of one violation
   * decodes only its own leg (review-p1c R3).
   */
  private async storedPath(
    route: RouteDTO
  ): Promise<{ polyline: [number, number][]; legs: [number, number][][] } | null> {
    if (route.id == null) {
      return null;
    }
    try {
      const geometry: RouteGeometryDTO = await firstValueFrom(this.lezApi.routeGeometry(route.id));
      const polyline = completeRoutePolyline(geometry);
      if (!polyline) {
        return null;
      }
      return { polyline, legs: storedLegPaths(geometry) ?? [] };
    } catch (error) {
      console.warn('Stored route geometry unavailable:', error);
      return null;
    }
  }

  /**
   * Highlights what one violation points at (LEZ-001 P1c review fix, R3):
   * the offending leg for `ZONE_TRANSIT` / `LEZ_GEOMETRY_MISSING` / the leg
   * variant of `LEZ_DATA_MISSING`, otherwise the stop and the leg reaching it.
   *
   * The highlight is a separate layer, removed on the next call and by
   * `clearRoutes`; the route polyline keeps its colour. Safe to call before
   * any solution is drawn.
   */
  focusLeg(routeId: number, seq: number, code: string, detail?: string | null): void {
    const entry = this.focusable.get(routeId);
    if (!entry || !this.map) {
      return;
    }

    const focus = violationFocus(code, seq, detail);
    this.clearHighlight();

    let highlighted: L.Polyline | null = null;
    if (focus.leg) {
      const latLngs = this.legLatLngs(entry, focus.leg);
      if (latLngs) {
        highlighted = L.polyline(latLngs, {
          color: '#b42318',
          weight: 8,
          opacity: 1
        }).addTo(this.highlightLayer);
      }
    }

    const marker = focus.stopSeq != null ? entry.planMarkers[focus.stopSeq] : undefined;
    if (marker) {
      this.map.setView(marker.getLatLng(), Math.max(this.map.getZoom(), 15), { animate: true });
      marker.openPopup();
      return;
    }
    if (highlighted) {
      const bounds = highlighted.getBounds();
      if (bounds.isValid()) {
        this.map.fitBounds(bounds.pad(0.2));
      }
    }
  }

  /** Stored geometry of that leg, or a straight segment between its stops. */
  private legLatLngs(entry: FocusableRoute, leg: LegFocus): [number, number][] | null {
    const stored = entry.legPaths?.[leg.fromSeq];
    if (stored && stored.length >= 2) {
      return stored;
    }
    const from = entry.planStops[leg.fromSeq];
    const to = entry.planStops[leg.toSeq];
    if (!from || !to) {
      return null;
    }
    return [[from.latitude, from.longitude], [to.latitude, to.longitude]];
  }

  private clearHighlight(): void {
    if (this.highlightLayer) {
      this.highlightLayer.clearLayers();
    }
  }

  private drawStraightLine(
    coordinates: number[][],
    color: string,
    route: RouteDTO
  ): void {
    const latLngs = this.routingService.getStraightLine(coordinates);

    const polyline = L.polyline(latLngs, {
      color,
      weight: 4,
      opacity: 0.6,
      dashArray: '10, 10'
    }).addTo(this.routeLayers);

    polyline.bindTooltip(
      `🚚 ${route.vehicle_license_plate} (Approximate route)`,
      { sticky: true }
    );
  }

  /**
   * Direction arrows along a route.
   *
   * Ported 1:1 from V1 `MainMap.#addDirectionArrows` (offset 50, repeat 120,
   * 14px filled arrow heads with a white outline). The decorator is added to
   * `routeLayers`, so `clearRoutes()` removes the arrows with the polylines.
   *
   * `leaflet-polylinedecorator` is imported for its side effect - it registers
   * L.polylineDecorator and L.Symbol on the Leaflet namespace.
   */
  private addDirectionArrows(polyline: L.Polyline, color: string): void {
    L.polylineDecorator(polyline, {
      patterns: [
        {
          offset: 50,
          repeat: 120,
          symbol: L.Symbol.arrowHead({
            pixelSize: 14,
            polygon: true,
            pathOptions: {
              fillOpacity: 1,
              fillColor: color,
              stroke: true,
              color: '#ffffff',
              weight: 1
            }
          })
        }
      ]
    }).addTo(this.routeLayers);
  }

  private clearRoutes(): void {
    this.solutionDisplayed = false;
    this.focusable.clear();
    this.clearHighlight();
    if (this.routeLayers) {
      this.routeLayers.clearLayers();
    }
    if (this.zoneLayers) {
      this.zoneLayers.clearLayers();
    }
  }

  // ============================================
  // UTILITY METHODS
  // ============================================

  recenter(): void {
    if (this.routeLayers && this.routeLayers.getLayers().length > 0) {
      const bounds = this.routeLayers.getBounds();
      if (bounds.isValid()) {
        this.map.fitBounds(bounds.pad(0.1));
        return;
      }
    }

    this.fitBoundsToMarkers();
  }

  private fitBoundsToMarkers(): void {
    const allMarkers = [...this.depotMarkers, ...this.orderMarkers];

    if (allMarkers.length > 0) {
      const group = L.featureGroup(allMarkers);
      this.map.fitBounds(group.getBounds().pad(0.1));
    }
  }

  centerTo(lat: number, lng: number, zoom: number = 15): void {
    if (!this.map) return;

    if (!lat || !lng) {
      console.warn('Invalid coordinates:', lat, lng);
      return;
    }

    this.map.setView([lat, lng], zoom, { animate: true });
  }

  invalidateSize(): void {
    if (this.map) {
      this.map.invalidateSize();
    }
  }

  clearAll(): void {
    this.clearDepotMarkers();
    this.clearOrderMarkers();
    this.clearRoutes();
  }
}
