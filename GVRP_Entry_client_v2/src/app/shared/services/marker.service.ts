import { Injectable } from '@angular/core';
import * as L from 'leaflet';
import { DepotDTO, OrderDTO } from '@core/models';

@Injectable({
  providedIn: 'root'
})
export class MarkerService {
  private readonly STATUS_COLORS: Record<string, string> = {
    'SCHEDULED': '#D0021B',
    'ON_ROUTE': '#F5A623',
    'COMPLETED': '#7ED321',
    'FAILED': '#9B9B9B'
  };

  // ============================================
  // DEPOT MARKERS
  // ============================================

  createDepotMarker(depot: DepotDTO): L.Marker {
    const icon = this.createDepotIcon();
    const marker = L.marker([depot.latitude, depot.longitude], {
      icon,
      zIndexOffset: 1000
    });

    marker.bindPopup(this.createDepotPopup(depot));
    return marker;
  }

  private createDepotIcon(): L.DivIcon {
    return L.divIcon({
      html: `
        <div style="
          background: #4A90E2;
          color: white;
          width: 36px;
          height: 36px;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          box-shadow: 0 2px 8px rgba(0,0,0,0.3);
          border: 3px solid white;
        ">
          <span style="font-size: 18px;">🏢</span>
        </div>
      `,
      className: 'depot-marker',
      iconSize: [36, 36],
      iconAnchor: [18, 18]
    });
  }

  private createDepotPopup(depot: DepotDTO): string {
    return `
      <div style="text-align: center; min-width: 200px;">
        <strong style="font-size: 16px;">${depot.name}</strong><br>
        <span style="font-size: 12px; color: #666;">${depot.address}</span><br>
        <span style="font-size: 11px; color: #999;">
          Lat: ${depot.latitude.toFixed(6)}, Lng: ${depot.longitude.toFixed(6)}
        </span>
      </div>
    `;
  }

  // ============================================
  // ORDER MARKERS
  // ============================================

  createOrderMarker(
    order: OrderDTO,
    onClick?: (orderId: number) => void,
    onEdit?: (orderId: number) => void
  ): L.Marker {
    const markerColor = this.STATUS_COLORS[order.status] || '#D0021B';
    const icon = this.createOrderIcon(markerColor);

    const marker = L.marker([order.latitude, order.longitude], { icon });
    marker.bindPopup(this.createOrderPopup(order, markerColor, !!onEdit));

    // Store order ID
    (marker as any)._orderId = order.id;

    // Handle click
    if (onClick) {
      marker.on('click', () => onClick(order.id));
    }

    // V1's popup had inline `onclick="EditOrderModal.open(id)"`. Angular has no
    // globals to call, so the button is wired on popupopen instead.
    if (onEdit) {
      marker.on('popupopen', event => {
        const popupElement = (event as L.PopupEvent).popup.getElement();
        const button = popupElement?.querySelector<HTMLButtonElement>(
          '[data-order-edit]'
        );

        button?.addEventListener(
          'click',
          () => {
            marker.closePopup();
            onEdit(order.id);
          },
          { once: true }
        );
      });
    }

    return marker;
  }

  private createOrderIcon(color: string): L.DivIcon {
    return L.divIcon({
      html: `
        <div style="
          background: ${color};
          color: white;
          width: 32px;
          height: 32px;
          border-radius: 50% 50% 50% 0;
          transform: rotate(-45deg);
          display: flex;
          align-items: center;
          justify-content: center;
          box-shadow: 0 2px 6px rgba(0,0,0,0.3);
          border: 2px solid white;
        ">
          <span style="transform: rotate(45deg); font-size: 14px; font-weight: bold;">📦</span>
        </div>
      `,
      className: 'order-marker',
      iconSize: [32, 32],
      iconAnchor: [16, 32]
    });
  }

  private createOrderPopup(
    order: OrderDTO,
    markerColor: string,
    withEditAction: boolean
  ): string {
    const editAction = withEditAction
      ? `
        <div style="margin-top: 12px;">
          <button type="button" data-order-edit
                  style="width: 100%; padding: 6px; background: #4A90E2; color: white;
                         border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">
            Edit
          </button>
        </div>
      `
      : '';

    return `
      <div style="min-width: 200px;">
        <strong style="font-size: 16px; color: #333;">${order.order_code}</strong>
        <hr style="margin: 8px 0; border: none; border-top: 1px solid #E5E5E5;">
        <div style="font-size: 13px; line-height: 1.6;">
          <strong>Customer:</strong> ${order.customer_name}<br>
          ${order.customer_phone ? `<strong>Phone:</strong> ${order.customer_phone}<br>` : ''}
          <strong>Address:</strong> ${order.address}<br>
          <strong>Demand:</strong> ${order.demand} kg<br>
          ${order.time_window_start ? `<strong>Time:</strong> ${order.time_window_start} - ${order.time_window_end}<br>` : ''}
          <strong>Status:</strong> <span style="color: ${markerColor};">${order.status}</span>
        </div>
        ${editAction}
      </div>
    `;
  }

  // ============================================
  // ROUTE STOP MARKERS
  // ============================================

  createRouteStopMarker(
    stop: any,
    color: string,
    vehiclePlate: string,
    type: 'start' | 'end' | 'stop'
  ): L.Marker {
    const isDepot = stop.type === 'DEPOT';
    const icon = this.createRouteStopIcon(stop, color, isDepot);

    const marker = L.marker([stop.latitude, stop.longitude], { icon });
    marker.bindPopup(this.createRouteStopPopup(stop, vehiclePlate, type, isDepot));

    return marker;
  }

  private createRouteStopIcon(stop: any, color: string, isDepot: boolean): L.DivIcon {
    const html = isDepot
      ? `<div style="
          background: #4A90E2;
          color: white;
          width: 32px;
          height: 32px;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          border: 3px solid white;
          box-shadow: 0 3px 6px rgba(0,0,0,0.4);
        ">🏢</div>`
      : `<div style="
          background: ${color};
          color: white;
          width: 28px;
          height: 28px;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          border: 2px solid white;
          font-size: 12px;
          font-weight: bold;
          box-shadow: 0 2px 4px rgba(0,0,0,0.3);
        ">${stop.sequence_number || ''}</div>`;

    return L.divIcon({
      html,
      className: 'route-stop-marker',
      iconSize: [32, 32],
      iconAnchor: [16, 16]
    });
  }

  private createRouteStopPopup(
    stop: any,
    vehiclePlate: string,
    type: string,
    isDepot: boolean
  ): string {
    let content = `<div style="min-width: 200px;">`;

    if (isDepot) {
      content += `
        <strong style="font-size: 14px;">🏢 ${stop.location_name}</strong><br>
        <hr style="margin: 6px 0;">
        <div style="font-size: 12px; line-height: 1.6;">
          <strong>Vehicle:</strong> ${vehiclePlate}<br>
          <strong>Type:</strong> ${type === 'start' ? 'Start Depot' : 'End Depot'}<br>
          <strong>Time:</strong> ${stop.arrival_time || stop.departure_time}
        </div>
      `;
    } else {
      content += `
        <strong style="font-size: 14px;">📦 Stop #${stop.sequence_number}</strong><br>
        <strong style="font-size: 13px;">${stop.location_name}</strong>
        <hr style="margin: 6px 0;">
        <div style="font-size: 12px; line-height: 1.6;">
          <strong>Vehicle:</strong> ${vehiclePlate}<br>
          <strong>Arrival:</strong> ${stop.arrival_time}<br>
          <strong>Departure:</strong> ${stop.departure_time}<br>
          ${stop.demand ? `<strong>Demand:</strong> ${stop.demand} kg<br>` : ''}
          ${stop.wait_time > 0 ? `<strong>Wait time:</strong> ${stop.wait_time} min<br>` : ''}
          <strong>Load after:</strong> ${stop.load_after.toFixed(1)} kg
        </div>
      `;
    }

    content += `</div>`;
    return content;
  }
}
