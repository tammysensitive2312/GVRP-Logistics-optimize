// src/app/shared/services/map.service.ts
import { Injectable } from '@angular/core';
import * as L from 'leaflet';
// Side-effect import: registers L.polylineDecorator / L.Symbol.arrowHead.
import 'leaflet-polylinedecorator';
import { BehaviorSubject, Observable, firstValueFrom } from 'rxjs';

import { DepotDTO, OrderDTO, SolutionDTO, RouteDTO } from '@core/models';
import { MarkerService } from './marker.service';
import { RoutingService } from './routing.service';

@Injectable({
  providedIn: 'root'
})
export class MapService {
  private map!: L.Map;
  private depotMarkers: L.Marker[] = [];
  private orderMarkers: L.Marker[] = [];
  private routeLayers!: L.FeatureGroup;
  /** True while a solution is drawn, so late-arriving orders don't steal the view. */
  private solutionDisplayed = false;

  private readonly ROUTE_COLORS = ['#3498db', '#e74c3c', '#2ecc71', '#f1c40f', '#9b59b6', '#34495e'];

  // Observable for map ready state
  private mapReady$ = new BehaviorSubject<boolean>(false);

  constructor(
    private markerService: MarkerService,
    private routingService: RoutingService
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
    this.routeLayers = L.featureGroup().addTo(this.map);

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

    for (let index = 0; index < solution.routes.length; index++) {
      const route = solution.routes[index];
      const routeColor = this.ROUTE_COLORS[index % this.ROUTE_COLORS.length];

      await this.displayRoute(route, routeColor);
    }

    // Fit bounds
    if (this.routeLayers.getLayers().length > 0) {
      const bounds = this.routeLayers.getBounds();
      if (bounds.isValid()) {
        this.map.fitBounds(bounds.pad(0.1));
      }
    }
  }

  private async displayRoute(route: RouteDTO, color: string): Promise<void> {
    // Sort stops
    const stops = [...route.stops].sort((a, b) => {
      const timeA = a.arrival_time || a.departure_time || '00:00:00';
      const timeB = b.arrival_time || b.departure_time || '00:00:00';
      return timeA.localeCompare(timeB);
    });

    // Add stop markers
    stops.forEach((stop, idx) => {
      const type = idx === 0 ? 'start' : idx === stops.length - 1 ? 'end' : 'stop';
      const marker = this.markerService.createRouteStopMarker(
        stop,
        color,
        route.vehicle_license_plate,
        type
      );
      marker.addTo(this.routeLayers);
    });

    // Draw route polyline
    const coordinates = stops.map(stop => [stop.longitude, stop.latitude]);

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

  private drawStraightLine(coordinates: number[][], color: string, route: RouteDTO): void {
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
    if (this.routeLayers) {
      this.routeLayers.clearLayers();
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
