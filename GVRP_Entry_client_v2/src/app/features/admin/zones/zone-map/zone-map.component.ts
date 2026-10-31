import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild
} from '@angular/core';
import * as L from 'leaflet';

import { DepotDTO } from '@core/models';

import { GeoJsonPolygon, polygonFromPoints, ringSelfIntersects } from '../zone-draw.utils';

const DEPOT_ICON = L.divIcon({
  html: `<div style="
      background: #4A90E2;
      width: 24px;
      height: 24px;
      border-radius: 50%;
      border: 2px solid #fff;
      box-shadow: 0 1px 4px rgba(0,0,0,0.35);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 13px;
    ">🏢</div>`,
  className: 'zone-map-depot-marker',
  iconSize: [24, 24],
  iconAnchor: [12, 12],
  tooltipAnchor: [12, 0]
});

/**
 * Small Leaflet preview for a zone geometry and the branch depots (LEZ-001
 * P1c, C1/C2). Uses the same Google tile layer as `MapComponent`.
 *
 * Read-only by default. With `editable`, a plain-DOM control (no plugin,
 * UX-LEZ-001 D2) lets the planner click the corners of one polygon:
 * Draw → click points → Finish emits `polygonDrawn`; Undo point and Cancel
 * edit the draft. The existing geometry stays drawn under the draft.
 * While drawing, double-click does not zoom and an outline whose edges cross
 * cannot be finished (LEZ-FIX-001 D1/D2).
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
  /** Shows the drawing control (COPIED zone editor only). */
  @Input() editable = false;
  /** The finished drawing as a GeoJSON Polygon `[lng, lat]`. */
  @Output() polygonDrawn = new EventEmitter<GeoJsonPolygon>();

  @ViewChild('host', { static: true })
  private host!: ElementRef<HTMLDivElement>;

  private map?: L.Map;
  private geometryLayer?: L.GeoJSON;
  private depotLayer?: L.LayerGroup;

  private drawControl?: L.Control;
  private draftLayer?: L.LayerGroup;
  private finishButton?: HTMLButtonElement;
  private crossingMessage?: HTMLSpanElement;
  /** True while map clicks add points to the draft. */
  drawing = false;
  /** Draft points as Leaflet `[lat, lng]`. */
  points: [number, number][] = [];
  private readonly onMapClick = (event: L.LeafletMouseEvent): void =>
    this.addPoint(event.latlng.lat, event.latlng.lng);

  ngAfterViewInit(): void {
    this.init();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (this.map && (changes['geometry'] || changes['depots'])) {
      this.render();
    }
    if (this.map && changes['editable']) {
      this.syncDrawControl();
    }
  }

  ngOnDestroy(): void {
    this.map?.off('click', this.onMapClick);
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
      this.syncDrawControl();
      setTimeout(() => this.map?.invalidateSize(), 0);
    } catch (error) {
      // A preview that cannot render must not break the page.
      console.warn('Zone map unavailable', error);
      this.map = undefined;
    }
  }

  // ---------- drawing (editable only) ----------

  startDraw(): void {
    if (!this.map || !this.editable) {
      return;
    }
    this.drawing = true;
    this.points = [];
    this.map.on('click', this.onMapClick);
    this.map.doubleClickZoom.disable();
    this.map.getContainer().style.cursor = 'crosshair';
    this.renderDraft();
  }

  addPoint(lat: number, lng: number): void {
    if (!this.drawing) {
      return;
    }
    // A double-click sends two clicks on the same spot; keep one point so one Undo removes it.
    const last = this.points[this.points.length - 1];
    if (last && last[0] === lat && last[1] === lng) {
      return;
    }
    this.points = [...this.points, [lat, lng]];
    this.renderDraft();
  }

  undoPoint(): void {
    if (!this.drawing || this.points.length === 0) {
      return;
    }
    this.points = this.points.slice(0, -1);
    this.renderDraft();
  }

  /** Emits the polygon of at least 3 distinct points whose edges do not cross, and leaves draw mode. */
  finishDraw(): void {
    const polygon = this.drawing ? this.finishablePolygon() : null;
    if (!polygon) {
      return;
    }
    this.stopDrawing();
    this.polygonDrawn.emit(polygon);
  }

  cancelDraw(): void {
    this.stopDrawing();
  }

  private stopDrawing(): void {
    this.drawing = false;
    this.points = [];
    if (this.map) {
      this.map.off('click', this.onMapClick);
      this.map.doubleClickZoom.enable();
      this.map.getContainer().style.cursor = '';
    }
    this.renderDraft();
  }

  private syncDrawControl(): void {
    if (!this.map) {
      return;
    }
    if (this.editable && !this.drawControl) {
      const control = new L.Control({ position: 'topright' });
      control.onAdd = () => this.buildDrawControl();
      control.addTo(this.map);
      this.drawControl = control;
      this.draftLayer = L.layerGroup().addTo(this.map);
    } else if (!this.editable && this.drawControl) {
      this.stopDrawing();
      this.drawControl.remove();
      this.drawControl = undefined;
      this.draftLayer?.remove();
      this.draftLayer = undefined;
    }
  }

  private buildDrawControl(): HTMLElement {
    const container = L.DomUtil.create('div', 'leaflet-bar zone-draw-control');
    container.style.background = '#fff';
    container.style.padding = '4px';
    L.DomEvent.disableClickPropagation(container);
    const row = L.DomUtil.create('div', 'zone-draw-buttons', container);
    row.style.display = 'flex';
    row.style.gap = '4px';
    const button = (label: string, action: () => void): HTMLButtonElement => {
      const element = L.DomUtil.create('button', 'zone-draw-btn', row) as HTMLButtonElement;
      element.type = 'button';
      element.textContent = label;
      element.style.cursor = 'pointer';
      L.DomEvent.on(element, 'click', event => {
        L.DomEvent.stop(event);
        action();
      });
      return element;
    };
    button('Draw', () => this.startDraw());
    button('Undo point', () => this.undoPoint());
    this.finishButton = button('Finish', () => this.finishDraw());
    button('Cancel', () => this.cancelDraw());
    // A span, not a button: the control's buttons stay Draw, Undo point, Finish, Cancel.
    // Its own line under the buttons; width 0 + min-width 100% wraps it to the button row's
    // width, so the control does not grow and the buttons do not move when it appears.
    this.crossingMessage = L.DomUtil.create('span', 'zone-draw-message', container) as HTMLSpanElement;
    this.crossingMessage.textContent = 'Edges cross — undo points or cancel and redraw.';
    this.crossingMessage.style.color = '#b91c1c';
    this.crossingMessage.style.width = '0';
    this.crossingMessage.style.minWidth = '100%';
    this.crossingMessage.style.marginTop = '4px';
    this.crossingMessage.style.display = 'none';
    this.updateFinishButton();
    return container;
  }

  private updateFinishButton(): void {
    if (this.finishButton) {
      this.finishButton.disabled = !this.drawing || this.finishablePolygon() === null;
    }
    if (this.crossingMessage) {
      this.crossingMessage.style.display = this.drawing && this.draftCrosses() ? 'block' : 'none';
    }
  }

  /** The draft polygon, or null when it has fewer than 3 points or its edges cross. */
  private finishablePolygon(): GeoJsonPolygon | null {
    const polygon = polygonFromPoints(this.points);
    return polygon && !ringSelfIntersects(polygon.coordinates[0]) ? polygon : null;
  }

  private draftCrosses(): boolean {
    const polygon = polygonFromPoints(this.points);
    return polygon !== null && ringSelfIntersects(polygon.coordinates[0]);
  }

  /** Dashed outline and small circles of the draft, above the stored geometry. */
  private renderDraft(): void {
    this.updateFinishButton();
    if (!this.draftLayer) {
      return;
    }
    this.draftLayer.clearLayers();
    if (this.points.length === 0) {
      return;
    }
    L.polyline(this.points, { color: '#1d4ed8', weight: 2, dashArray: '6 4' }).addTo(this.draftLayer);
    for (const point of this.points) {
      L.circleMarker(point, { radius: 4, color: '#1d4ed8', fillOpacity: 1 }).addTo(this.draftLayer);
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
        title: depot.name,
        icon: DEPOT_ICON
      }).bindTooltip(depot.name);
      marker.addTo(this.depotLayer);
      bounds.extend([depot.latitude, depot.longitude]);
    }

    // Keep the drawing draft above a re-rendered geometry.
    if (this.draftLayer) {
      this.draftLayer.remove();
      this.draftLayer.addTo(this.map);
    }

    if (bounds.isValid()) {
      this.map.fitBounds(bounds, { padding: [20, 20], maxZoom: 15 });
    }
  }
}
