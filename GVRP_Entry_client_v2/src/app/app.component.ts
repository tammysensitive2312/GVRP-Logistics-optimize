import {Component} from '@angular/core';
import { RouterOutlet } from '@angular/router';
import {LeafletModule} from '@asymmetrik/ngx-leaflet';
import * as L from 'leaflet';



@Component({
  selector: 'app-root',
  imports: [RouterOutlet, LeafletModule],
  standalone: true,
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'GVRP System';

  options = {
    layers: [
      L.tileLayer('https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
        maxZoom: 18,
        attribution: '© OpenStreetMap'
      })
    ],
    zoom: 13,
    center: new L.LatLng(21.0285, 105.8542)
  };

  onMapReady(map: L.Map) {
    const officeIcon = L.divIcon({
      html: '<div style="font-size: 30px;">🏢</div>',
      className: 'custom-div-icon',
      iconSize: [40, 40],
      iconAnchor: [20, 20]
    });

    L.marker([21.0285, 105.8542], { icon: officeIcon })
      .addTo(map)
      .bindPopup('Tòa nhà văn phòng');
  }
}
