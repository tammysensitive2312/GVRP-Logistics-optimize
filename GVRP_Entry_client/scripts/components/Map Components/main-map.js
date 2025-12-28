/**
 * Main Map Component
 * Handles main screen map with depots, orders, and real route display
 */

export class MainMap {
    static #map = null;
    static #markers = [];
    static #routeLayers = null;
    static #DEFAULT_CENTER = [21.028511, 105.804817];
    static #DEFAULT_ZOOM = 13;

    // OSRM server (public demo server - consider self-hosting for production)
    static #OSRM_SERVER = 'https://router.project-osrm.org';

    /**
     * Initialize main map
     */
    static init() {
        if (this.#map) {
            this.#map.remove();
        }

        const mapElement = document.getElementById('main-map');
        if (!mapElement) {
            console.warn('Main map element not found');
            return;
        }

        this.#map = L.map('main-map', {
            fadeAnimation: true,
            zoomAnimation: true,
            zoomAnimationThreshold: 500,
            updateWhenIdle: true,
            updateWhenZooming: false,
            preferCanvas: true
        }).setView(this.#DEFAULT_CENTER, this.#DEFAULT_ZOOM);

        this.#addTileLayer();
        this.#addScaleControl();
        this.#addCustomControls();

        this.#routeLayers = L.featureGroup().addTo(this.#map);
        this.#markers = [];

        if (typeof window !== 'undefined') {
            window.mainMap = this.#map;
        }

        console.log('Main map initialized');
    }

    /**
     * Display the optimization results (routes) on map with real routing
     */
    static async displaySolution(solution) {
        if (!this.#map || !solution || !solution.routes) return;

        if (!this.#routeLayers) {
            this.#routeLayers = L.featureGroup().addTo(this.#map);
        }
        this.#routeLayers.clearLayers();
        this.clearAll();

        const colors = ['#3498db', '#e74c3c', '#2ecc71', '#f1c40f', '#9b59b6', '#34495e'];

        // Show loading indicator
        const loadingControl = this.#showLoadingControl('Drawing routes...');

        try {
            // Process routes sequentially to avoid overwhelming the API
            for (let index = 0; index < solution.routes.length; index++) {
                const route = solution.routes[index];
                const routeColor = colors[index % colors.length];

                await this.#displaySingleRoute(route, routeColor);

                // Update loading progress
                loadingControl.setText(
                    `Drawing routes... ${index + 1}/${solution.routes.length}`
                );
            }

            // Fit bounds after all routes are drawn
            if (this.#routeLayers.getLayers().length > 0) {
                try {
                    const bounds = this.#routeLayers.getBounds();
                    if (bounds.isValid()) {
                        this.#map.fitBounds(bounds.pad(0.1));
                    }
                } catch (e) {
                    console.warn('Bounds invalid:', e);
                }
            }

        } catch (error) {
            console.error('Error displaying solution:', error);
        } finally {
            // Remove loading indicator
            this.#map.removeControl(loadingControl);
        }

        setTimeout(() => this.#map.invalidateSize(), 300);
    }

    /**
     * Display a single route with real road routing
     * @private
     */
    static async #displaySingleRoute(route, routeColor) {
        // Prepare stops in correct order
        let stops = [...route.stops];

        // Move end depot to end
        const endDepotIndex = stops.findIndex(stop =>
            stop.type === 'DEPOT' && stop.departure_time === null
        );

        if (endDepotIndex !== -1 && endDepotIndex !== stops.length - 1) {
            const endDepot = stops.splice(endDepotIndex, 1)[0];
            stops.push(endDepot);
        }

        // Create markers for all stops
        stops.forEach((stop, idx) => {
            const marker = this.#createRouteStopMarker(
                stop,
                routeColor,
                route.vehicle_license_plate,
                idx === 0 ? 'start' : (idx === stops.length - 1 ? 'end' : 'stop')
            );
            marker.addTo(this.#routeLayers);
        });

        // Get coordinates for routing
        const coordinates = stops.map(stop => [stop.longitude, stop.latitude]);

        // Fetch real route from OSRM
        try {
            const geometry = await this.#fetchRouteGeometry(coordinates);

            if (geometry && geometry.length > 0) {
                // Draw the real route
                const polyline = L.polyline(geometry, {
                    color: routeColor,
                    weight: 5,
                    opacity: 0.7,
                    smoothFactor: 1
                }).addTo(this.#routeLayers);

                // Add route info tooltip
                polyline.bindTooltip(
                    `🚚 ${route.vehicle_license_plate}<br>` +
                    `📦 ${route.order_count} orders<br>` +
                    `📏 ${route.distance.toFixed(1)} km<br>` +
                    `⏱️ ${this.#formatDuration(route.service_time)}`,
                    { sticky: true }
                );

                // Add arrows to show direction
                this.#addDirectionArrows(polyline, routeColor);
            } else {
                // Fallback to straight lines if routing fails
                console.warn('Routing failed, using straight lines');
                this.#drawStraightLine(coordinates, routeColor, route);
            }

        } catch (error) {
            console.error('Routing error:', error);
            // Fallback to straight lines
            this.#drawStraightLine(coordinates, routeColor, route);
        }
    }

    /**
     * Fetch route geometry from OSRM
     * @private
     */
    static async #fetchRouteGeometry(coordinates) {
        if (coordinates.length < 2) return null;

        // Build OSRM request URL
        const coordsString = coordinates.map(c => `${c[0]},${c[1]}`).join(';');
        const url = `${this.#OSRM_SERVER}/route/v1/driving/${coordsString}?overview=full&geometries=geojson`;

        try {
            const response = await fetch(url);

            if (!response.ok) {
                throw new Error(`OSRM request failed: ${response.status}`);
            }

            const data = await response.json();

            if (data.code === 'Ok' && data.routes && data.routes.length > 0) {
                // Convert GeoJSON coordinates [lng, lat] to Leaflet format [lat, lng]
                const geometry = data.routes[0].geometry.coordinates.map(
                    coord => [coord[1], coord[0]]
                );
                return geometry;
            }

            return null;

        } catch (error) {
            console.error('OSRM fetch error:', error);
            return null;
        }
    }

    /**
     * Draw straight line as fallback
     * @private
     */
    static #drawStraightLine(coordinates, routeColor, route) {
        const latLngs = coordinates.map(c => [c[1], c[0]]);

        const polyline = L.polyline(latLngs, {
            color: routeColor,
            weight: 4,
            opacity: 0.6,
            dashArray: '10, 10' // Dashed to indicate it's not real routing
        }).addTo(this.#routeLayers);

        polyline.bindTooltip(
            `🚚 ${route.vehicle_license_plate} (Approximate route)`,
            { sticky: true }
        );
    }

    /**
     * Add direction arrows to polyline
     * @private
     */
    static #addDirectionArrows(polyline, color) {
        // Add decorators to show direction
        if (typeof L.polylineDecorator !== 'undefined') {
            const decorator = L.polylineDecorator(polyline, {
                patterns: [
                    {
                        offset: '10%',
                        repeat: 100,
                        symbol: L.Symbol.arrowHead({
                            pixelSize: 12,
                            pathOptions: {
                                fillOpacity: 1,
                                weight: 0,
                                color: color
                            }
                        })
                    }
                ]
            }).addTo(this.#routeLayers);
        }
    }

    /**
     * Create route stop marker
     * @private
     */
    static #createRouteStopMarker(stop, color, plate, type = 'stop') {
        const isDepot = stop.type === 'DEPOT';

        let html;
        if (isDepot) {
            // Depot markers - blue background as requested
            html = `
                <div style="
                    background: #4A90E2;
                    color: white;
                    width: 32px;
                    height: 32px;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    border: 3px solid white;
                    font-size: 16px;
                    font-weight: bold;
                    box-shadow: 0 3px 6px rgba(0,0,0,0.4);
                ">
                    🏢
                </div>
            `;
        } else {
            // Order markers - numbered with route color
            html = `
                <div style="
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
                ">
                    ${stop.sequence_number || ''}
                </div>
            `;
        }

        const icon = L.divIcon({
            html: html,
            className: 'route-stop-marker',
            iconSize: [32, 32],
            iconAnchor: [16, 16]
        });

        const marker = L.marker([stop.latitude, stop.longitude], { icon });

        // Popup content
        let popupContent = `<div style="min-width: 200px;">`;

        if (isDepot) {
            popupContent += `
                <strong style="font-size: 14px;">🏢 ${stop.location_name}</strong><br>
                <hr style="margin: 6px 0; border: none; border-top: 1px solid #E5E5E5;">
                <div style="font-size: 12px; line-height: 1.6;">
                    <strong>Vehicle:</strong> ${plate}<br>
                    <strong>Type:</strong> ${type === 'start' ? 'Start Depot' : 'End Depot'}<br>
                    <strong>Time:</strong> ${stop.arrival_time || stop.departure_time}
                </div>
            `;
        } else {
            popupContent += `
                <strong style="font-size: 14px;">📦 Stop #${stop.sequence_number}</strong><br>
                <strong style="font-size: 13px;">${stop.location_name}</strong>
                <hr style="margin: 6px 0; border: none; border-top: 1px solid #E5E5E5;">
                <div style="font-size: 12px; line-height: 1.6;">
                    <strong>Vehicle:</strong> ${plate}<br>
                    <strong>Arrival:</strong> ${stop.arrival_time}<br>
                    <strong>Departure:</strong> ${stop.departure_time}<br>
                    ${stop.demand ? `<strong>Demand:</strong> ${stop.demand} kg<br>` : ''}
                    ${stop.wait_time > 0 ? `<strong>Wait time:</strong> ${stop.wait_time} min<br>` : ''}
                    <strong>Load after:</strong> ${stop.load_after.toFixed(1)} kg
                </div>
            `;
        }

        popupContent += `</div>`;

        marker.bindPopup(popupContent);

        return marker;
    }

    /**
     * Show loading control
     * @private
     */
    static #showLoadingControl(text) {
        const LoadingControl = L.Control.extend({
            options: { position: 'topright' },

            onAdd: function() {
                const div = L.DomUtil.create('div', 'leaflet-bar leaflet-control');
                div.style.background = 'white';
                div.style.padding = '8px 12px';
                div.style.fontSize = '12px';
                div.innerHTML = `
                    <div style="display: flex; align-items: center; gap: 8px;">
                        <div style="
                            width: 16px; 
                            height: 16px; 
                            border: 2px solid #4A90E2; 
                            border-top-color: transparent; 
                            border-radius: 50%; 
                            animation: spin 1s linear infinite;
                        "></div>
                        <span id="loading-text">${text}</span>
                    </div>
                `;
                return div;
            },

            setText: function(newText) {
                const textEl = document.getElementById('loading-text');
                if (textEl) textEl.textContent = newText;
            }
        });

        const control = new LoadingControl();
        this.#map.addControl(control);

        // Add spinner animation if not exists
        if (!document.getElementById('spinner-keyframes')) {
            const style = document.createElement('style');
            style.id = 'spinner-keyframes';
            style.textContent = `
                @keyframes spin {
                    to { transform: rotate(360deg); }
                }
            `;
            document.head.appendChild(style);
        }

        return control;
    }

    /**
     * Format duration from minutes to readable format
     * @private
     */
    static #formatDuration(minutes) {
        const hours = Math.floor(minutes / 60);
        const mins = Math.round(minutes % 60);

        if (hours > 0) {
            return `${hours}h ${mins}m`;
        }
        return `${mins}m`;
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
                  🎯
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
     */
    static loadDepots(depots) {
        if (!this.#map || !depots) return;

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
            zIndexOffset: 1000
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
     */
    static loadOrders(orders) {
        if (!this.#map || !orders) return;

        this.#clearMarkersByType('order');

        orders.forEach(order => {
            const marker = this.#createOrderMarker(order);
            this.#markers.push(marker);
        });

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

        marker.bindPopup(this.#createOrderPopup(order, markerColor));

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

    static #getStatusColor(status) {
        const colors = {
            'SCHEDULED': '#D0021B',
            'ON_ROUTE': '#F5A623',
            'COMPLETED': '#7ED321',
            'FAILED': '#9B9B9B'
        };
        return colors[status] || '#D0021B';
    }

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

    static #clearMarkersByType(type) {
        this.#markers = this.#markers.filter(marker => {
            if (marker.markerType === type) {
                this.#map.removeLayer(marker);
                return false;
            }
            return true;
        });
    }

    static clearAll() {
        if (this.#routeLayers) this.#routeLayers.clearLayers();

        this.#markers.forEach(marker => {
            this.#map.removeLayer(marker);
        });
        this.#markers = [];
    }

    static highlightOrder(orderId) {
        const marker = this.#markers.find(m => m.orderId === orderId);
        if (marker) {
            this.#map.setView(marker.getLatLng(), 15, { animate: true });
            marker.openPopup();
        }
    }

    static centerTo(lat, lng, zoom = 15) {
        if (this.#map) {
            this.#map.setView([lat, lng], zoom, { animate: true });
        }
    }

    static recenter() {
        if (!this.#map) return;

        // Recenter based on route layers if they exist
        if (this.#routeLayers && this.#routeLayers.getLayers().length > 0) {
            try {
                const bounds = this.#routeLayers.getBounds();
                if (bounds.isValid()) {
                    this.#map.fitBounds(bounds.pad(0.1));
                    return;
                }
            } catch (e) {
                console.warn('Route bounds invalid:', e);
            }
        }

        // Fallback to markers
        if (this.#markers.length > 0) {
            const group = L.featureGroup(this.#markers);
            this.#map.fitBounds(group.getBounds().pad(0.1));
        }
    }

    static fitBounds() {
        this.recenter();
    }

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

    static invalidateSize() {
        if (this.#map) {
            this.#map.invalidateSize();
        }
    }

    static getMap() {
        return this.#map;
    }

    static getMarkers() {
        return [...this.#markers];
    }

    static getMarkersByType(type) {
        return this.#markers.filter(m => m.markerType === type);
    }

    static destroy() {
        if (this.#map) {
            this.clearAll();
            this.#map.remove();
            this.#map = null;
        }
    }

    static getRouteLayerCount() {
        return this.#routeLayers ? this.#routeLayers.getLayers().length : 0;
    }

    static getRouteLayers() {
        return this.#routeLayers;
    }
}

// Export for global access
if (typeof window !== 'undefined') {
    window.MainMap = MainMap;
}

// Backward compatibility
window.initMainMap = () => MainMap.init();
window.loadDepotMarkers = (depots) => MainMap.loadDepots(depots);
window.loadOrderMarkers = (orders) => MainMap.loadOrders(orders);
window.highlightMarker = (orderId) => MainMap.highlightOrder(orderId);
window.centerMapToDepot = (lat, lng) => MainMap.centerTo(lat, lng);
window.recenterMainMap = () => MainMap.recenter();
window.toggleMapFullscreen = () => MainMap.toggleFullscreen();