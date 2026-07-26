import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  OnDestroy,
  output,
  PLATFORM_ID
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { LeafletModule } from '@asymmetrik/ngx-leaflet';
import * as L from 'leaflet';

export interface PickedLocation {
  latitude: number;
  longitude: number;
}

/**
 * Location Picker
 *
 * Inline Leaflet map where a single click places / moves one marker.
 * Ports V1 `scripts/components/Map Components/depot-map.js` (teardrop divIcon,
 * "Vị trí đã chọn" popup) without the global window.* wiring.
 *
 * Reverse geocoding is intentionally NOT done here - the parent owns that, so
 * this component stays reusable for any coordinate picking (depot, order, ...).
 */
@Component({
  selector: 'app-location-picker',
  standalone: true,
  imports: [LeafletModule],
  templateUrl: './location-picker.component.html',
  styleUrl: './location-picker.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LocationPickerComponent implements OnDestroy {
  /** Hanoi city centre - same default as V1 and MapComponent. */
  private static readonly DEFAULT_CENTER: L.LatLngTuple = [21.028511, 105.804817];
  private static readonly DEFAULT_ZOOM = 13;

  readonly latitude = input<number | null>(null);
  readonly longitude = input<number | null>(null);
  readonly zoom = input(LocationPickerComponent.DEFAULT_ZOOM);
  /** CSS height of the map container. V1 `.setup-map` was 400px. */
  readonly height = input('400px');
  readonly disabled = input(false);

  readonly locationPicked = output<PickedLocation>();

  readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  /**
   * Computed, not a field initializer: signal inputs still hold their defaults
   * while the constructor runs, so `zoom()` must be read lazily.
   * Only depends on `zoom()` - the initial centre is applied in onMapReady, so
   * later coordinate changes never rebuild these options.
   */
  readonly mapOptions = computed<L.MapOptions>(() => ({
    layers: [
      L.tileLayer('https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
        maxZoom: 20,
        attribution: '© Google'
      })
    ],
    zoom: this.zoom(),
    center: L.latLng(LocationPickerComponent.DEFAULT_CENTER),
    zoomControl: true,
    preferCanvas: true
  }));

  private map: L.Map | null = null;
  private marker: L.Marker | null = null;

  constructor() {
    if (!this.isBrowser) return;

    // Keep the marker in sync when the parent resets or pre-fills coordinates.
    effect(() => {
      const latitude = this.latitude();
      const longitude = this.longitude();

      if (!this.map) return;

      if (latitude === null || longitude === null) {
        this.removeMarker();
        return;
      }

      this.renderMarker(L.latLng(latitude, longitude), false);
    });

    // Leaflet mis-measures itself when created inside a container that is still
    // being laid out (V1 worked around this with setTimeout 100).
    afterNextRender(() => this.map?.invalidateSize());
  }

  ngOnDestroy(): void {
    this.removeMarker();
    this.map = null;
  }

  onMapReady(map: L.Map): void {
    this.map = map;

    const latitude = this.latitude();
    const longitude = this.longitude();

    if (latitude !== null && longitude !== null) {
      this.renderMarker(L.latLng(latitude, longitude), false);
      map.setView([latitude, longitude], this.zoom());
    }

    map.invalidateSize();
  }

  onMapClick(event: L.LeafletMouseEvent): void {
    if (this.disabled()) return;

    this.renderMarker(event.latlng, true);
    this.locationPicked.emit({
      latitude: event.latlng.lat,
      longitude: event.latlng.lng
    });
  }

  private renderMarker(latlng: L.LatLng, openPopup: boolean): void {
    if (!this.map) return;

    if (this.marker) {
      this.marker.setLatLng(latlng);
    } else {
      this.marker = L.marker(latlng, {
        icon: LocationPickerComponent.createPinIcon(),
        zIndexOffset: 1000
      })
        .addTo(this.map)
        .bindPopup(LocationPickerComponent.createPopupHtml(latlng));
    }

    this.marker.setPopupContent(LocationPickerComponent.createPopupHtml(latlng));
    if (openPopup) {
      this.marker.openPopup();
    }
  }

  private removeMarker(): void {
    if (this.marker && this.map) {
      this.map.removeLayer(this.marker);
    }
    this.marker = null;
  }

  private static createPinIcon(): L.DivIcon {
    return L.divIcon({
      html: '<div class="location-pin"><span class="location-pin__glyph">📍</span></div>',
      className: 'location-picker-marker',
      iconSize: [40, 40],
      iconAnchor: [20, 40],
      popupAnchor: [0, -38]
    });
  }

  private static createPopupHtml(latlng: L.LatLng): string {
    return (
      '<strong>Vị trí đã chọn</strong><br>' +
      `Lat: ${latlng.lat.toFixed(6)}<br>` +
      `Lng: ${latlng.lng.toFixed(6)}`
    );
  }
}
