/**
 * Main Map Component
 * Handles main screen map with depots and orders
 */

export class MainMap {
    static #map = null;
    static #markers = [];
    static #DEFAULT_CENTER = [21.028511, 105.804817];
    static #DEFAULT_ZOOM = 13;

    /**
     * Initialize main map
     */
    static init() {
        // Remove existing map if any
        if (this.#map) {
            this.#map.remove();
        }

        const mapElement = document.getElementById('main-map');
        if (!mapElement) {
            console.warn('Main map element not found');
            return;
        }

        // Create map
        this.#map = L.map('main-map', {
            fadeAnimation: true,
            zoomAnimation: true,
            zoomAnimationThreshold: 500,
            updateWhenIdle: true,
            updateWhenZooming: false,
            preferCanvas: true
        }).setView(this.#DEFAULT_CENTER, this.#DEFAULT_ZOOM);

        // Add tile layer
        this.#addTileLayer();

        // Add scale control
        this.#addScaleControl();

        // Add custom controls
        this.#addCustomControls();

        // Make globally accessible for ResizableDivider
        if (typeof window !== 'undefined') {
            window.mainMap = this.#map;
        }

        console.log('Main map initialized');
    }

    /**
     * Add tile layer
     * @private
     */
    static #addTileLayer() {
        L.tileLayer('https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
            maxZoom: 20,
            attribution: '© Google'
        }).addTo(this.#map);
    }

    /**
     * Add scale control
     * @private
     */
    static #addScaleControl() {
        L.control.scale({
            position: 'bottomleft',
            imperial: false
        }).addTo(this.#map);
    }

    /**
     * Add custom controls
     * @private
     */
    static #addCustomControls() {
        // Recenter button
        const recenterControl = L.control({ position: 'topright' });
        recenterControl.onAdd = () => {
            const div = L.DomUtil.create('div', 'leaflet-bar leaflet-control');
            div.innerHTML = `
        <a href="#" title="Recenter to all markers" 
           style="width: 30px; height: 30px; line-height: 30px; text-align: center; text-decoration: none; font-size: 18px;">
          📍
        </a>
      `;
            div.onclick = (e) => {
                e.preventDefault();
                this.recenter();
            };
            return div;
        };
        recenterControl.addTo(this.#map);

        // Fullscreen button
        const fullscreenControl = L.control({ position: 'topright' });
        fullscreenControl.onAdd = () => {
            const div = L.DomUtil.create('div', 'leaflet-bar leaflet-control');
            div.innerHTML = `
        <a href="#" title="Fullscreen" 
           style="width: 30px; height: 30px; line-height: 30px; text-align: center; text-decoration: none; font-size: 16px;">
          🔲
        </a>
      `;
            div.onclick = (e) => {
                e.preventDefault();
                this.toggleFullscreen();
            };
            return div;
        };
        fullscreenControl.addTo(this.#map);

        // Listen for fullscreen changes
        document.addEventListener('fullscreenchange', () => {
            setTimeout(() => {
                if (this.#map) {
                    this.#map.invalidateSize();
                }
            }, 100);
        });
    }

    /**
     * Load depot markers
     * @param {Array} depots - Array of depot objects
     */
    static loadDepots(depots) {
        if (!this.#map || !depots) return;

        // Clear existing depot markers
        this.#clearMarkersByType('depot');

        depots.forEach(depot => {
            const marker = this.#createDepotMarker(depot);
            this.#markers.push(marker);
        });
    }

    /**
     * Create depot marker
     * @private
     */
    static #createDepotMarker(depot) {
        const depotIcon = L.divIcon({
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

        const marker = L.marker([depot.latitude, depot.longitude], {
            icon: depotIcon,
            zIndexOffset: 1000 // Keep depots on top
        }).addTo(this.#map);

        marker.bindPopup(`
      <div style="text-align: center;">
        <strong style="font-size: 16px;">${depot.name}</strong><br>
        <span style="font-size: 12px; color: #666;">${depot.address}</span><br>
        <span style="font-size: 11px; color: #999;">
          Lat: ${depot.latitude.toFixed(6)}, Lng: ${depot.longitude.toFixed(6)}
        </span>
      </div>
    `);

        marker.markerType = 'depot';
        marker.depotId = depot.id;

        return marker;
    }

    /**
     * Load order markers
     * @param {Array} orders - Array of order objects
     */
    static loadOrders(orders) {
        if (!this.#map || !orders) return;

        // Clear existing order markers
        this.#clearMarkersByType('order');

        orders.forEach(order => {
            const marker = this.#createOrderMarker(order);
            this.#markers.push(marker);
        });

        // Fit bounds if there are markers
        if (orders.length > 0) {
            this.fitBounds();
        }
    }

    /**
     * Create order marker
     * @private
     */
    static #createOrderMarker(order) {
        const markerColor = this.#getStatusColor(order.status);

        const orderIcon = L.divIcon({
            html: `
        <div style="
          background: ${markerColor};
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

        const marker = L.marker([order.latitude, order.longitude], {
            icon: orderIcon
        }).addTo(this.#map);

        // Bind popup
        marker.bindPopup(this.#createOrderPopup(order, markerColor));

        // Click handler
        marker.on('click', () => {
            if (typeof highlightTableRow === 'function') {
                highlightTableRow(order.id);
            }
        });

        marker.markerType = 'order';
        marker.orderId = order.id;
        marker.orderData = order;

        return marker;
    }

    /**
     * Get status color
     * @private
     */
    static #getStatusColor(status) {
        const colors = {
            'SCHEDULED': '#D0021B',   // Red
            'ON_ROUTE': '#F5A623',    // Orange
            'COMPLETED': '#7ED321',   // Green
            'FAILED': '#9B9B9B'       // Gray
        };
        return colors[status] || '#D0021B';
    }

    /**
     * Create order popup content
     * @private
     */
    static #createOrderPopup(order, markerColor) {
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
        <div style="margin-top: 12px; display: flex; gap: 8px;">
          <label style="font-size: 12px; cursor: pointer;">
            <input type="checkbox" onchange="toggleOrderSelection(${order.id}, this.checked)"> Select
          </label>
          <button onclick="EditOrderModal.open(${order.id})" 
                  style="flex: 1; padding: 6px; background: #4A90E2; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">
            Edit
          </button>
        </div>
      </div>
    `;
    }

    /**
     * Clear markers by type
     * @private
     */
    static #clearMarkersByType(type) {
        this.#markers = this.#markers.filter(marker => {
            if (marker.markerType === type) {
                this.#map.removeLayer(marker);
                return false;
            }
            return true;
        });
    }

    /**
     * Clear all markers
     */
    static clearAll() {
        this.#markers.forEach(marker => {
            this.#map.removeLayer(marker);
        });
        this.#markers = [];
    }

    /**
     * Highlight marker by order ID
     * @param {number} orderId
     */
    static highlightOrder(orderId) {
        const marker = this.#markers.find(m => m.orderId === orderId);
        if (marker) {
            this.#map.setView(marker.getLatLng(), 15, { animate: true });
            marker.openPopup();
        }
    }

    /**
     * Center map to coordinates
     * @param {number} lat
     * @param {number} lng
     * @param {number} zoom
     */
    static centerTo(lat, lng, zoom = 15) {
        if (this.#map) {
            this.#map.setView([lat, lng], zoom, { animate: true });
        }
    }

    /**
     * Recenter to show all markers
     */
    static recenter() {
        if (!this.#map || this.#markers.length === 0) return;

        const group = L.featureGroup(this.#markers);
        this.#map.fitBounds(group.getBounds().pad(0.1));
    }

    /**
     * Fit bounds to show all markers
     */
    static fitBounds() {
        this.recenter();
    }

    /**
     * Toggle fullscreen
     */
    static toggleFullscreen() {
        const mapContainer = document.querySelector('.map-container');
        if (!mapContainer) return;

        if (!document.fullscreenElement) {
            mapContainer.requestFullscreen().catch(err => {
                console.error('Fullscreen error:', err);
            });
        } else {
            document.exitFullscreen();
        }
    }

    /**
     * Invalidate map size (call after resize)
     */
    static invalidateSize() {
        if (this.#map) {
            this.#map.invalidateSize();
        }
    }

    /**
     * Get map instance
     */
    static getMap() {
        return this.#map;
    }

    /**
     * Get all markers
     */
    static getMarkers() {
        return [...this.#markers];
    }

    /**
     * Get markers by type
     * @param {string} type - 'depot' or 'order'
     */
    static getMarkersByType(type) {
        return this.#markers.filter(m => m.markerType === type);
    }

    /**
     * Destroy map instance
     */
    static destroy() {
        if (this.#map) {
            this.clearAll();
            this.#map.remove();
            this.#map = null;
        }
    }
}

// Export for global access
if (typeof window !== 'undefined') {
    window.MainMap = MainMap;
}

// Backward compatibility
window.initMainMap = () => {
    MainMap.init();
};

window.loadDepotMarkers = (depots) => {
    MainMap.loadDepots(depots);
};

window.loadOrderMarkers = (orders) => {
    MainMap.loadOrders(orders);
};

window.highlightMarker = (orderId) => {
    MainMap.highlightOrder(orderId);
};

window.centerMapToDepot = (lat, lng) => {
    MainMap.centerTo(lat, lng);
};

window.recenterMainMap = () => {
    MainMap.recenter();
};

window.toggleMapFullscreen = () => {
    MainMap.toggleFullscreen();
};