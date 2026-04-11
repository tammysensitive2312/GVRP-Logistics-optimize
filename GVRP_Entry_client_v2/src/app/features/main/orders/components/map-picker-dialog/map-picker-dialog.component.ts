import {Component, Inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import { LeafletModule } from '@asymmetrik/ngx-leaflet';
import * as L from 'leaflet';
import {th} from 'date-fns/locale';

@Component({
  selector: 'app-map-picker-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    LeafletModule
  ],
  templateUrl: './map-picker-dialog.component.html',
  styleUrl: './map-picker-dialog.component.scss'
})
export class MapPickerDialogComponent {
  mapOptions!: L.MapOptions;
  private map!: L.Map;
  private marker: L.Marker | null = null;

  selectedLat?: number;
  selectedLng?: number;

  constructor(
    public dialogRef: MatDialogRef<MapPickerDialogComponent>,
    @Inject(MAT_DIALOG_DATA)
    public data: {
      lat?: number;
      lng?: number
    })
  {}

  ngOnInit(): void {
    this.selectedLat = this.data?.lat;
    this.selectedLng = this.data?.lng;

    const centerLat = this.selectedLat || 21.028511;
    const centerLng = this.selectedLng || 105.804817;

    this.mapOptions = {
      layers: [
        L.tileLayer('https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
          maxZoom: 20,
          attribution: '© Google'
        })
      ],
      zoom: 15,
      center: L.latLng(centerLat, centerLng)
    };
  }

  onMapReady(map: L.Map): void {
    this.map = map;

    const icon = L.icon({
      iconUrl: 'https://unpkg.com/leaflet@1.7.1/dist/images/marker-icon.png',
      shadowUrl: 'https://unpkg.com/leaflet@1.7.1/dist/images/marker-shadow.png',
      iconAnchor: [12, 41]
    });

    if (this.selectedLat && this.selectedLng) {
      this.marker = L.marker([this.selectedLat, this.selectedLng], { icon }).addTo(this.map);
    }

    setTimeout(() => this.map.invalidateSize(), 100);
  }

  onMapClick(event: L.LeafletMouseEvent): void {
    this.selectedLat = event.latlng.lat;
    this.selectedLng = event.latlng.lng;

    const icon = L.icon({
      iconUrl: 'https://unpkg.com/leaflet@1.7.1/dist/images/marker-icon.png',
      shadowUrl: 'https://unpkg.com/leaflet@1.7.1/dist/images/marker-shadow.png',
      iconAnchor: [12, 41]
    });

    if (this.marker) {
      this.marker.setLatLng(event.latlng);
    } else {
      this.marker = L.marker(event.latlng, {icon}).addTo(this.map);
    }
  }

  onConfirm(): void {
    this.dialogRef.close({ lat: this.selectedLat, lng: this.selectedLng });
  }

  onCancel(): void {
    this.dialogRef.close();
  }
}
