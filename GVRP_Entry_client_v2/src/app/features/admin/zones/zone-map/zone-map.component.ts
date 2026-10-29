import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ViewChild
} from '@angular/core';
import * as L from 'leaflet';

import { DepotDTO } from '@core/models';

/**
 * Small read-only Leaflet preview for a zone geometry and the branch depots
 * (LEZ-001 P1c, C1/C2). Uses the same Google tile layer as `MapComponent`; no
 * plugin and no drawing tools (design §15).
 */
@Component({
  selector: 'app-zone-map',
  standalone: true,
  template: '<div #host class="zone-map" [style.height]="height"></div>',
  styles: [`
    .zone-map {
      width: 100%;
      border-radius: 6px;
      border: 1px solid #e5e5e5;
      background: #f8f9fa;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ZoneMapComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() geometry: unknown | null = null;
  @Input() depots: readonly DepotDTO[] = [];
  @Input() height = '320px';

  @ViewChild('host', { static: true })
  private host!: ElementRef<HTMLDivElement>;

  private map?: L.Map;
  private geometryLayer?: L.GeoJSON;
  private depotLayer?: L.LayerGroup;

  ngAfterViewInit(): void {
    this.init();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (this.map && (changes['geometry'] || changes['depots'])) {
      this.render();
    }
  }

  ngOnDestroy(): void {
    this.map?.remove();
    this.map = undefined;
  }

  private init(): void {
    try {
      this.map = L.map(this.host.nativeElement, {
        zoomControl: true,
        preferCanvas: true
      }).setView([21.028511, 105.804817], 11);

      L.tileLayer('https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
        maxZoom: 20,
        attribution: '© Google'
      }).addTo(this.map);

      this.render();
      setTimeout(() => this.map?.invalidateSize(), 0);
    } catch (error) {
      // A preview that cannot render must not break the page.
      console.warn('Zone map unavailable', error);
      this.map = undefined;
    }
  }

  private render(): void {
    if (!this.map) {
      return;
    }
    if (this.geometryLayer) {
      this.map.removeLayer(this.geometryLayer);
      this.geometryLayer = undefined;
    }
    if (this.depotLayer) {
      this.map.removeLayer(this.depotLayer);
      this.depotLayer = undefined;
    }

    const bounds = L.latLngBounds([]);
    if (this.geometry) {
      try {
        this.geometryLayer = L.geoJSON(this.geometry as never, {
          style: {
            color: '#c0392b',
            weight: 2,
            fillColor: '#e74c3c',
            fillOpacity: 0.2
          }
        }).addTo(this.map);
        const geometryBounds = this.geometryLayer.getBounds();
        if (geometryBounds.isValid()) {
          bounds.extend(geometryBounds);
        }
      } catch (error) {
        console.warn('Zone geometry is not drawable', error);
      }
    }

    this.depotLayer = L.layerGroup().addTo(this.map);
    for (const depot of this.depots) {
      if (depot.latitude == null || depot.longitude == null) {
        continue;
      }
      const marker = L.marker([depot.latitude, depot.longitude], {
        title: depot.name
      }).bindTooltip(depot.name);
      marker.addTo(this.depotLayer);
      bounds.extend([depot.latitude, depot.longitude]);
    }

    if (bounds.isValid()) {
      this.map.fitBounds(bounds, { padding: [20, 20], maxZoom: 15 });
    }
  }
}
