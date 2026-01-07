// src/app/shared/services/map.service.ts
import { Injectable } from '@angular/core';
import * as L from 'leaflet';
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

  private readonly ROUTE_COLORS = ['#3498db', '#e74c3c', '#2ecc71', '#f1c40f', '#9b59b6', '#34495e'];

  // Observable for map ready state
  private mapReady$ = new BehaviorSubject<boolean>(false);

  constructor(
    private markerService: MarkerService,
    private routingService: RoutingService
  ) {}

  // ============================================
  // INITIALIZATION
  // ============================================

  initializeMap(map: L.Map): void {
    this.map = map;
    this.routeLayers = L.featureGroup().addTo(this.map);

    // Add scale control
    L.control.scale({
      position: 'bottomleft',
      imperial: false
    }).addTo(this.map);

    this.mapReady$.next(true);
  }

  isMapReady(): Observable<boolean> {
    return this.mapReady$.asObservable();
  }

  // ============================================
  // DEPOT MANAGEMENT
  // ============================================

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

  // ============================================
  // ORDER MANAGEMENT
  // ============================================

  loadOrders(orders: OrderDTO[], onOrderClick?: (orderId: number) => void): void {
    this.clearOrderMarkers();

    orders.forEach(order => {
      const marker = this.markerService.createOrderMarker(order, onOrderClick);
      marker.addTo(this.map);
      this.orderMarkers.push(marker);
    });

    if (orders.length > 0) {
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
          opacity: 0.8
        }).addTo(this.routeLayers);

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

  private clearRoutes(): void {
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
