import { AfterViewChecked, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import * as L from 'leaflet';
import 'leaflet-polylinedecorator';

import { DriverRouteDTO, DriverStopDTO, RouteSendDTO } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { AuthService } from '@core/services/auth.service';
import { ManeuverStep, RoutingService, formatLegDistance } from '@shared/services/routing.service';

/** Google Maps directions link to one stop (no key, no billing). Null-safe. */
export function googleMapsUrl(latitude: number | null, longitude: number | null): string | null {
  if (latitude === null || latitude === undefined
    || longitude === null || longitude === undefined) {
    return null;
  }
  return `https://www.google.com/maps/dir/?api=1&destination=${latitude},${longitude}`;
}

@Component({
  selector: 'app-driver-view',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './driver-view.component.html',
  styleUrl: './driver-view.component.scss'
})
export class DriverViewComponent implements OnInit, OnDestroy, AfterViewChecked {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly routing = inject(RoutingService);

  private map: L.Map | null = null;
  private mappedSendId: number | null = null;
  private geoWatchId: number | null = null;
  private geoDot: L.CircleMarker | null = null;
  private geoRing: L.Circle | null = null;

  readonly sends = signal<RouteSendDTO[]>([]);
  readonly selected = signal<DriverRouteDTO | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly detailError = signal<string | null>(null);
  readonly maneuvers = signal<ManeuverStep[]>([]);
  readonly following = signal(false);
  readonly followGate = signal<'none' | 'prompt' | 'help'>('none');

  /**
   * Progressive disclosure (Google-Maps style): the full maneuver list and
   * the full stop list stay collapsed behind one tap each; only the next
   * step / next stop shows up front. Verified live: 23 maneuvers dumped at
   * once buried the stop list on a real route.
   */
  readonly guidanceOpen = signal(false);
  readonly stopsOpen = signal(false);

  /** First delivery stop (depots skipped); null when there are no stops. */
  readonly nextStop = computed<DriverStopDTO | null>(() => {
    const stops = this.selected()?.stops ?? [];
    return stops.find(stop => stop.type !== 'DEPOT') ?? stops[0] ?? null;
  });

  ngOnInit(): void {
    this.api.myDriverRoutes().subscribe({
      next: sends => {
        this.sends.set(sends);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Could not load assigned routes. Retry.');
      }
    });
  }

  get hasSuperseded(): boolean {
    return this.sends().some(send => send.status === 'SUPERSEDED');
  }

  openSend(send: RouteSendDTO): void {
    this.detailError.set(null);
    this.mappedSendId = null;
    this.maneuvers.set([]);
    this.guidanceOpen.set(false);
    this.stopsOpen.set(false);
    this.api.driverRouteDetail(send.id).subscribe({
      next: detail => this.selected.set(detail),
      error: () => this.detailError.set('Could not load route detail.')
    });
  }

  backToList(): void {
    this.stopGeoWatch();
    this.destroyMap();
    this.mappedSendId = null;
    this.maneuvers.set([]);
    this.guidanceOpen.set(false);
    this.stopsOpen.set(false);
    this.following.set(false);
    this.followGate.set('none');
    this.selected.set(null);
  }

  ngOnDestroy(): void {
    this.stopGeoWatch();
    this.destroyMap();
  }

  ngAfterViewChecked(): void {
    const selected = this.selected();
    if (!selected || this.mappedSendId === selected.send_id) return;
    this.renderMiniMap(selected);
    // Flag set only here (never inside destroyMap/render): one render per
    // opened send, so re-creating L.map on an initialized container cannot loop.
    this.mappedSendId = selected.send_id;
  }

  toggleFollow(): void {
    if (this.following()) {
      this.following.set(false);
      return;
    }
    void this.requestFollowPermission();
  }

  /** In-app permission gate before tracking (native prompt follows approval). */
  allowFollow(): void {
    this.followGate.set('none');
    const geo = typeof navigator !== 'undefined' ? navigator.geolocation : undefined;
    if (!geo?.getCurrentPosition) {
      this.followGate.set('help');
      return;
    }
    geo.getCurrentPosition(
      () => this.following.set(true),
      () => this.followGate.set('help'),
      { timeout: 10000 }
    );
  }

  dismissGate(): void {
    this.followGate.set('none');
  }

  toggleGuidance(): void {
    this.guidanceOpen.update(open => !open);
  }

  toggleStops(): void {
    this.stopsOpen.update(open => !open);
  }

  private async requestFollowPermission(): Promise<void> {
    const geo = typeof navigator !== 'undefined' ? navigator.geolocation : undefined;
    if (!geo) {
      this.followGate.set('help');
      return;
    }
    let state: string | null = null;
    try {
      const permissions = (navigator as Navigator & {
        permissions?: { query(o: { name: string }): Promise<{ state: string }> };
      }).permissions;
      if (permissions?.query) {
        state = (await permissions.query({ name: 'geolocation' })).state;
      }
    } catch {
      state = null;
    }
    if (state === 'granted') {
      this.following.set(true);
    } else if (state === 'denied') {
      this.followGate.set('help');
    } else {
      this.followGate.set('prompt');
    }
  }

  googleMapsUrl(stop: { latitude: number | null; longitude: number | null }): string | null {
    return googleMapsUrl(stop.latitude, stop.longitude);
  }

  formatLegDistance(meters: number): string {
    return formatLegDistance(meters);
  }

  private renderMiniMap(detail: DriverRouteDTO): void {
    this.destroyMap();
    try {
      const el = document.getElementById('driver-mini-map');
      const points = detail.stops.filter(
        stop => stop.latitude !== null && stop.longitude !== null);
      if (!el || points.length === 0) return;

      const map = L.map(el, { zoomControl: true });
      this.map = map;
      // Manual gestures pause follow mode: never fight the driver (FM-002).
      map.on('dragstart zoomstart', () => this.following.set(false));
      L.tileLayer('https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
        attribution: '© Google',
        maxZoom: 20
      }).addTo(map);

      const latLngs: L.LatLngExpression[] = [];
      points.forEach((stop, i) => {
        const latLng: L.LatLngExpression = [stop.latitude as number, stop.longitude as number];
        latLngs.push(latLng);
        const isDepot = stop.type === 'DEPOT';
        const label = isDepot ? '🏢' : String(stop.sequence_number ?? i + 1);
        const color = isDepot ? '#0b8043' : '#1976d2';
        // Inline styles (planner-marker pattern): Leaflet creates marker DOM
        // imperatively, so component-scoped CSS never matches it.
        // Number-only pins; names live in the stop list + tap tooltips, so dense
        // clusters stay readable instead of overlapping label pills.
        L.marker(latLng, {
          icon: L.divIcon({
            className: isDepot ? 'driver-pin driver-depot' : 'driver-pin',
            html: `<span style="display:flex;align-items:center;justify-content:center;`
              + `width:34px;height:34px;min-width:34px;border-radius:50%;`
              + `background:${color};color:#fff;font-size:15px;font-weight:bold;`
              + `border:3px solid #fff;box-shadow:0 1px 5px rgba(0,0,0,0.5);">${label}</span>`,
            iconSize: [34, 34],
            iconAnchor: [17, 17]
          })
        }).addTo(map).bindTooltip(stop.location_name);
      });

      const coords = points.map(stop => [stop.longitude as number, stop.latitude as number]);
      this.routing.getRouteGeometry(coords).subscribe(geometry => {
        if (!this.map) return;
        const line = geometry?.coordinates
          ? L.polyline(geometry.coordinates, { color: '#1976d2', weight: 5, opacity: 0.8 })
          : L.polyline(latLngs, {
            color: '#1976d2', weight: 4, opacity: 0.6, dashArray: '10, 10'
          });
        line.addTo(this.map);
        L.polylineDecorator(line, {
          patterns: [{
            offset: 50,
            repeat: 120,
            symbol: L.Symbol.arrowHead({
              pixelSize: 12, polygon: true, pathOptions: { color: '#1976d2', fillOpacity: 1 }
            })
          }]
        }).addTo(this.map);
        const bounds = L.latLngBounds(latLngs);
        if (bounds.isValid()) this.map.fitBounds(bounds.pad(0.1));
      });

      this.routing.getRouteLegs(coords).subscribe(legs => {
        if (!legs) {
          this.maneuvers.set([]);
          return;
        }
        // Drop zero-length depart/arrive noise; every real maneuver has distance.
        this.maneuvers.set(legs.flatMap(leg => leg.steps).filter(step => step.distanceM > 0));
      });

      this.startGeoWatch(map);
    } catch {
      this.destroyMap();
    }
  }

  private startGeoWatch(map: L.Map): void {
    this.stopGeoWatch();
    const geo = typeof navigator !== 'undefined' ? navigator.geolocation : undefined;
    if (!geo) return;
    try {
      this.geoWatchId = geo.watchPosition(
        position => {
          if (!this.map) return;
          const at: L.LatLngExpression = [
            position.coords.latitude, position.coords.longitude];
          if (!this.geoDot) {
            this.geoDot = L.circleMarker(at, {
              radius: 8, color: '#fff', weight: 2, fillColor: '#1a73e8', fillOpacity: 1
            }).addTo(this.map);
            this.geoRing = L.circle(at, {
              radius: position.coords.accuracy ?? 0,
              color: '#1a73e8', weight: 1, opacity: 0.5, fillOpacity: 0.15
            }).addTo(this.map);
          } else {
            this.geoDot.setLatLng(at);
            this.geoRing?.setLatLng(at)
              .setRadius(position.coords.accuracy ?? 0);
          }
          if (this.following() && this.map) {
            this.map.panTo(at, { animate: true });
          }
        },
        () => undefined,
        { enableHighAccuracy: true, timeout: 10000 }
      );
    } catch {
      this.geoWatchId = null;
    }
  }

  private stopGeoWatch(): void {
    if (this.geoWatchId !== null && typeof navigator !== 'undefined'
      && navigator.geolocation) {
      try {
        navigator.geolocation.clearWatch(this.geoWatchId);
      } catch {
        // Display-only aid; never break the view.
      }
    }
    this.geoWatchId = null;
    this.geoDot = null;
    this.geoRing = null;
  }

  private destroyMap(): void {
    if (this.map) {
      this.map.remove();
      this.map = null;
    }
  }

  logout(): void {
    this.auth.logout().subscribe({
      next: () => void this.router.navigate(['/login']),
      error: () => void this.router.navigate(['/login'])
    });
  }
}
