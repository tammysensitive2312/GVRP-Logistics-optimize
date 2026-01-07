// src/app/shared/components/map/map.component.ts
import { Component, OnInit, OnDestroy, Input, Output, EventEmitter, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LeafletModule } from '@asymmetrik/ngx-leaflet';
import * as L from 'leaflet';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { DepotDTO, OrderDTO, SolutionDTO } from '@core/models';
import { MapService } from '@shared/services/map.service';

@Component({
  selector: 'app-map',
  standalone: true,
  imports: [CommonModule, LeafletModule],
  templateUrl: './map.component.html',
  styleUrls: ['./map.component.scss']
})
export class MapComponent implements OnInit, OnDestroy, OnChanges {
  @Input() depots: DepotDTO[] = [];
  @Input() orders: OrderDTO[] = [];
  @Input() solution: SolutionDTO | null = null;
  @Input() highlightedOrderId: number | null = null;

  @Output() orderClicked = new EventEmitter<number>();
  @Output() mapReady = new EventEmitter<L.Map>();

  mapOptions!: L.MapOptions;
  private map!: L.Map;
  private destroy$ = new Subject<void>();

  constructor(private mapService: MapService) {}

  ngOnInit(): void {
    this.initializeMapOptions();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.map) return;

    if (changes['depots']) {
      this.mapService.loadDepots(this.depots);
    }

    if (changes['orders']) {
      this.mapService.loadOrders(this.orders, (id) => this.orderClicked.emit(id));
    }

    if (changes['solution']) {
      if (this.solution) {
        this.mapService.displaySolution(this.solution);
      } else {
        this.mapService.clearAll();
      }
    }

    if (changes['highlightedOrderId'] && this.highlightedOrderId) {
      this.mapService.highlightOrder(this.highlightedOrderId);
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initializeMapOptions(): void {
    this.mapOptions = {
      layers: [
        L.tileLayer('https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
          maxZoom: 20,
          attribution: '© Google'
        })
      ],
      zoom: 13,
      center: L.latLng(21.028511, 105.804817),
      zoomControl: true,
      fadeAnimation: true,
      zoomAnimation: true,
      preferCanvas: true
    };
  }

  onMapReady(map: L.Map): void {
    this.map = map;
    this.mapService.initializeMap(map);

    // Load initial data
    this.mapService.loadDepots(this.depots);
    this.mapService.loadOrders(this.orders, (id) => this.orderClicked.emit(id));

    this.mapReady.emit(map);
    setTimeout(() => this.mapService.invalidateSize(), 100);
  }

  // UI Event Handlers
  recenter(): void {
    this.mapService.recenter();
  }

  toggleFullscreen(): void {
    const element = this.map.getContainer().parentElement;
    if (!element) return;

    if (!document.fullscreenElement) {
      element.requestFullscreen().catch(err => {
        console.error('Fullscreen error:', err);
      });
    } else {
      document.exitFullscreen();
    }
  }
}
