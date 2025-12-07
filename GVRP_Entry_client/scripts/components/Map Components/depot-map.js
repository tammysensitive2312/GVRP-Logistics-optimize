/**
 * Depot Setup Map Component
 * Handles map for depot location selection
 */

import { DepotForm } from '../Form Components/depot-form.js';

export class DepotMap {
    static #map = null;
    static #marker = null;
    static #DEFAULT_CENTER = [21.028511, 105.804817];
    static #DEFAULT_ZOOM = 13;

    /**
     * Initialize depot setup map
     */
    static init() {
        // Remove existing map if any
        if (this.#map) {
            this.#map.remove();
        }

        const mapElement = document.getElementById('depot-map');
        if (!mapElement) {
            console.warn('Depot map element not found');
            return;
        }

        // Create map
        this.#map = L.map('depot-map', {
            fadeAnimation: true,
            zoomAnimation: true,
            zoomAnimationThreshold: 500,
            updateWhenIdle: true,
            updateWhenZooming: false,
            preferCanvas: true
        }).setView(this.#DEFAULT_CENTER, this.#DEFAULT_ZOOM);

        // Add tile layer
        this.#addTileLayer();

        // Add click event to place marker
        this.#map.on('click', (e) => {
            this.placeMarker(e.latlng);
        });

        // Pass map instance to DepotForm
        if (typeof DepotForm !== 'undefined') {
            DepotForm.setMap(this.#map);
        }

        console.log('Depot map initialized');
    }

    /**
     * Add tile layer
     * @private
     */
    static #addTileLayer() {
        L.tileLayer('https://tiles.stadiamaps.com/tiles/alidade_smooth/{z}/{x}/{y}{r}.png', {
            maxZoom: 20,
            attribution: '© Stadia Maps'
        }).addTo(this.#map);
    }

    /**
     * Place or move depot marker
     * @param {Object} latlng - Leaflet LatLng object
     */
    static placeMarker(latlng) {
        // Remove existing marker
        if (this.#marker) {
            this.#map.removeLayer(this.#marker);
        }

        // Create custom icon
        const depotIcon = this.#createDepotIcon();

        // Add new marker
        this.#marker = L.marker(latlng, { icon: depotIcon }).addTo(this.#map);

        // Update DepotForm
        if (typeof DepotForm !== 'undefined') {
            DepotForm.updateLocation(latlng.lat, latlng.lng);
            DepotForm.setMarker(this.#marker);
        }

        // Reverse geocoding
        this.#reverseGeocode(latlng.lat, latlng.lng);

        // Add popup
        this.#marker.bindPopup(`
      <strong>Vị trí đã chọn</strong><br>
      Lat: ${latlng.lat.toFixed(6)}<br>
      Lng: ${latlng.lng.toFixed(6)}
    `).openPopup();
    }

    /**
     * Create depot icon
     * @private
     */
    static #createDepotIcon() {
        return L.divIcon({
            html: `
        <div style="
          background: #4A90E2;
          color: white;
          width: 40px;
          height: 40px;
          border-radius: 50% 50% 50% 0;
          transform: rotate(-45deg);
          display: flex;
          align-items: center;
          justify-content: center;
          box-shadow: 0 2px 8px rgba(0,0,0,0.3);
        ">
          <span style="transform: rotate(45deg); font-size: 20px;">📍</span>
        </div>
      `,
            className: 'custom-marker',
            iconSize: [40, 40],
            iconAnchor: [20, 40]
        });
    }

    /**
     * Reverse geocoding using Nominatim API
     * @private
     */
    static async #reverseGeocode(lat, lng) {
        try {
            const response = await fetch(
                `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&addressdetails=1`
            );

            if (response.ok) {
                const data = await response.json();
                const address = data.display_name || `${lat}, ${lng}`;

                // Update DepotForm with address
                if (typeof DepotForm !== 'undefined') {
                    DepotForm.updateLocation(lat, lng, address);
                }
            }
        } catch (error) {
            console.warn('Reverse geocoding failed:', error);

            // Update with coordinates only
            if (typeof DepotForm !== 'undefined') {
                DepotForm.updateLocation(lat, lng, `${lat}, ${lng}`);
            }
        }
    }

    /**
     * Get map instance
     */
    static getMap() {
        return this.#map;
    }

    /**
     * Get current marker
     */
    static getMarker() {
        return this.#marker;
    }

    /**
     * Set view to coordinates
     * @param {number} lat
     * @param {number} lng
     * @param {number} zoom
     */
    static setView(lat, lng, zoom = this.#DEFAULT_ZOOM) {
        if (this.#map) {
            this.#map.setView([lat, lng], zoom, { animate: true });
        }
    }

    /**
     * Destroy map instance
     */
    static destroy() {
        if (this.#map) {
            this.#map.remove();
            this.#map = null;
            this.#marker = null;
        }
    }
}

// Export for global access
if (typeof window !== 'undefined') {
    window.DepotMap = DepotMap;
}

// Backward compatibility
window.initDepotSetupMap = () => {
    DepotMap.init();
};

window.placeDepotMarker = (latlng) => {
    DepotMap.placeMarker(latlng);
};