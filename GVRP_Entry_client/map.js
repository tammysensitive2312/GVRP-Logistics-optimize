/**
 * Leaflet Map Integration for VRP System
 * Handles all map-related functionality
 */

// Map instances
let depotSetupMap = null;
let mainMap = null;

// Map markers
let depotMarker = null;
let mainMapMarkers = [];
let markerClusterGroup = null;

// Default coordinates (Hanoi)
const DEFAULT_CENTER = [21.028511, 105.804817];
const DEFAULT_ZOOM = 13;

// ============================================
// SCREEN 1: DEPOT SETUP MAP
// ============================================

/**
 * Initialize depot setup map
 */
function initDepotSetupMap() {
    if (depotSetupMap) {
        depotSetupMap.remove();
    }

    // Create map
    depotSetupMap = L.map('depot-map', {
        // fadeAnimation: true,
        // zoomAnimation: true,
        // zoomAnimationThreshold: 500,
        updateWhenIdle: true,
        updateWhenZooming: false,
        preferCanvas: true
    }).setView(DEFAULT_CENTER, DEFAULT_ZOOM);

    // Add tile layer
    L.tileLayer('https://mt1.google.com/vt/lyrs=r&x={x}&y={y}&z={z}', { // r = roadmap, s = satellite, y = hybrid
        maxZoom: 20,
        attribution: '© Google'
    }).addTo(map);

    // Add click event to place marker
    depotSetupMap.on('click', function(e) {
        placeDepotMarker(e.latlng);
    });

    console.log('Depot setup map initialized');
}

/**
 * Place or move depot marker
 * @param {Object} latlng - {lat, lng}
 */
function placeDepotMarker(latlng) {
    // Remove existing marker
    if (depotMarker) {
        depotSetupMap.removeLayer(depotMarker);
    }

    // Create custom icon
    const depotIcon = L.divIcon({
        html: '<div style="background: #4A90E2; color: white; width: 40px; height: 40px; border-radius: 50% 50% 50% 0; transform: rotate(-45deg); display: flex; align-items: center; justify-content: center; box-shadow: 0 2px 8px rgba(0,0,0,0.3);"><span style="transform: rotate(45deg); font-size: 20px;">📍</span></div>',
        className: 'custom-marker',
        iconSize: [40, 40],
        iconAnchor: [20, 40]
    });

    // Add new marker
    depotMarker = L.marker(latlng, { icon: depotIcon }).addTo(depotSetupMap);

    // Update form fields
    document.getElementById('depot-lat').value = latlng.lat.toFixed(6);
    document.getElementById('depot-lng').value = latlng.lng.toFixed(6);

    // Reverse geocoding (get address from coordinates)
    reverseGeocode(latlng.lat, latlng.lng);

    // Add popup
    depotMarker.bindPopup(`
        <strong>Vị trí đã chọn</strong><br>
        Lat: ${latlng.lat.toFixed(6)}<br>
        Lng: ${latlng.lng.toFixed(6)}
    `).openPopup();
}

/**
 * Reverse geocoding using Nominatim API
 * @param {number} lat
 * @param {number} lng
 */
async function reverseGeocode(lat, lng) {
    try {
        const response = await fetch(
            `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&addressdetails=1`
        );

        if (response.ok) {
            const data = await response.json();
            const address = data.display_name || `${lat}, ${lng}`;
            document.getElementById('depot-address').value = address;
        }
    } catch (error) {
        console.warn('Reverse geocoding failed:', error);
        document.getElementById('depot-address').value = `${lat}, ${lng}`;
    }
}

// ============================================
// SCREEN 3: MAIN MAP
// ============================================

/**
 * Initialize main map
 */
function initMainMap() {
    if (mainMap) {
        mainMap.remove();
    }

    // Create map
    mainMap = L.map('main-map', {
        fadeAnimation: true,
        zoomAnimation: true,
        zoomAnimationThreshold: 500,
        updateWhenIdle: true,
        updateWhenZooming: false,
        preferCanvas: true
    }).setView(DEFAULT_CENTER, DEFAULT_ZOOM);

    // Tile Việt Nam siêu nhanh + có tên đường đầy đủ (KHUYẾN NGHỊ)
    // L.tileLayer('https://{s}.tile.openstreetmap.vn/t/{z}/{x}/{y}.png', {
    //     attribution: '© OpenStreetMap Việt Nam',
    //     maxZoom: 19,
    //     subdomains: 'abc'
    // }).addTo(mainMap);

    // L.tileLayer('https://tiles.stadiamaps.com/tiles/alidade_smooth/{z}/{x}/{y}{r}.png', {
    //     maxZoom: 20,
    //     attribution: '© Stadia Maps'
    // }).addTo(mainMap);

    L.tileLayer('https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
        maxZoom: 20,
        attribution: '© Google'
    }).addTo(mainMap);

    // Scale control
    L.control.scale({
        position: 'bottomleft',
        imperial: false
    }).addTo(mainMap);

    // Add custom controls
    addMapControls();

    console.log('Main map initialized');
}

/**
 * Add custom map controls
 */
function addMapControls() {
    // Recenter button
    const recenterControl = L.control({ position: 'topright' });
    recenterControl.onAdd = function() {
        const div = L.DomUtil.create('div', 'leaflet-bar leaflet-control');
        div.innerHTML = '<a href="#" title="Recenter to depots" style="width: 30px; height: 30px; line-height: 30px; text-align: center; text-decoration: none; font-size: 18px;">📍</a>';
        div.onclick = function(e) {
            e.preventDefault();
            recenterMainMap();
        };
        return div;
    };
    recenterControl.addTo(mainMap);

    // Fullscreen button
    const fullscreenControl = L.control({ position: 'topright' });
    fullscreenControl.onAdd = function() {
        const div = L.DomUtil.create('div', 'leaflet-bar leaflet-control');
        div.innerHTML = '<a href="#" title="Fullscreen" style="width: 30px; height: 30px; line-height: 30px; text-align: center; text-decoration: none; font-size: 16px;">🔲</a>';
        div.onclick = function(e) {
            e.preventDefault();
            toggleMapFullscreen();
        };
        return div;
    };
    fullscreenControl.addTo(mainMap);
}

/**
 * Load depot markers on main map
 * @param {Array} depots - Array of depot objects
 */
function loadDepotMarkers(depots) {
    if (!mainMap) return;

    // Clear existing depot markers
    clearDepotMarkers();

    depots.forEach(depot => {
        // Create depot icon (blue house)
        const depotIcon = L.divIcon({
            html: '<div style="background: #4A90E2; color: white; width: 36px; height: 36px; border-radius: 50%; display: flex; align-items: center; justify-content: center; box-shadow: 0 2px 8px rgba(0,0,0,0.3); border: 3px solid white;"><span style="font-size: 18px;">🏢</span></div>',
            className: 'depot-marker',
            iconSize: [36, 36],
            iconAnchor: [18, 18]
        });

        const marker = L.marker([depot.latitude, depot.longitude], {
            icon: depotIcon,
            zIndexOffset: 1000 // Keep depots on top
        }).addTo(mainMap);

        marker.bindPopup(`
            <div style="text-align: center;">
                <strong style="font-size: 16px;">${depot.name}</strong><br>
                <span style="font-size: 12px; color: #666;">${depot.address}</span><br>
                <span style="font-size: 11px; color: #999;">Lat: ${depot.latitude.toFixed(6)}, Lng: ${depot.longitude.toFixed(6)}</span>
            </div>
        `);

        mainMapMarkers.push(marker);
    });
}

/**
 * Load order markers on main map
 * @param {Array} orders - Array of order objects
 */
function loadOrderMarkers(orders) {
    if (!mainMap) return;

    // Clear existing order markers
    clearOrderMarkers();

    orders.forEach(order => {
        // Determine marker color by status
        let markerColor = '#D0021B'; // Red for SCHEDULED
        if (order.status === 'COMPLETED') markerColor = '#7ED321'; // Green
        if (order.status === 'ON_ROUTE') markerColor = '#F5A623'; // Orange
        if (order.status === 'FAILED') markerColor = '#9B9B9B'; // Gray

        // Create order icon (colored pin)
        const orderIcon = L.divIcon({
            html: `<div style="background: ${markerColor}; color: white; width: 32px; height: 32px; border-radius: 50% 50% 50% 0; transform: rotate(-45deg); display: flex; align-items: center; justify-content: center; box-shadow: 0 2px 6px rgba(0,0,0,0.3); border: 2px solid white;"><span style="transform: rotate(45deg); font-size: 14px; font-weight: bold;">📦</span></div>`,
            className: 'order-marker',
            iconSize: [32, 32],
            iconAnchor: [16, 32]
        });

        const marker = L.marker([order.latitude, order.longitude], {
            icon: orderIcon
        }).addTo(mainMap);

        // Add popup
        marker.bindPopup(`
            <div style="min-width: 200px;">
                <strong style="font-size: 16px; color: #333;">${order.orderCode}</strong>
                <hr style="margin: 8px 0; border: none; border-top: 1px solid #E5E5E5;">
                <div style="font-size: 13px; line-height: 1.6;">
                    <strong>Customer:</strong> ${order.customerName}<br>
                    ${order.customerPhone ? `<strong>Phone:</strong> ${order.customerPhone}<br>` : ''}
                    <strong>Address:</strong> ${order.address}<br>
                    <strong>Demand:</strong> ${order.demand} kg<br>
                    ${order.timeWindowStart ? `<strong>Time:</strong> ${order.timeWindowStart} - ${order.timeWindowEnd}<br>` : ''}
                    <strong>Status:</strong> <span style="color: ${markerColor};">${order.status}</span>
                </div>
                <div style="margin-top: 12px; display: flex; gap: 8px;">
                    <label style="font-size: 12px; cursor: pointer;">
                        <input type="checkbox" onchange="toggleOrderSelection(${order.id}, this.checked)"> Select
                    </label>
                    <button onclick="viewOrderDetails(${order.id})" style="flex: 1; padding: 6px; background: #4A90E2; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">View Details</button>
                </div>
            </div>
        `);

        // Click marker to highlight table row
        marker.on('click', function() {
            highlightTableRow(order.id);
        });

        // Store order data in marker
        marker.orderId = order.id;
        marker.orderData = order;

        mainMapMarkers.push(marker);
    });

    // Fit bounds to show all markers
    if (orders.length > 0) {
        const group = L.featureGroup(mainMapMarkers);
        mainMap.fitBounds(group.getBounds().pad(0.1));
    }
}

/**
 * Clear depot markers
 */
function clearDepotMarkers() {
    mainMapMarkers.forEach(marker => {
        if (marker.orderId === undefined) {
            mainMap.removeLayer(marker);
        }
    });
}

/**
 * Clear order markers
 */
function clearOrderMarkers() {
    mainMapMarkers.forEach(marker => {
        if (marker.orderId !== undefined) {
            mainMap.removeLayer(marker);
        }
    });
    mainMapMarkers = mainMapMarkers.filter(m => m.orderId === undefined);
}

/**
 * Highlight marker by order ID
 * @param {number} orderId
 */
function highlightMarker(orderId) {
    const marker = mainMapMarkers.find(m => m.orderId === orderId);
    if (marker) {
        mainMap.setView(marker.getLatLng(), 15, { animate: true });
        marker.openPopup();
    }
}

/**
 * Center map to depot coordinates
 * @param {number} lat
 * @param {number} lng
 */
function centerMapToDepot(lat, lng) {
    if (mainMap) {
        mainMap.setView([lat, lng], 15, { animate: true });
    }
}

/**
 * Recenter main map to show all markers
 */
function recenterMainMap() {
    if (!mainMap || mainMapMarkers.length === 0) return;

    const group = L.featureGroup(mainMapMarkers);
    mainMap.fitBounds(group.getBounds().pad(0.1));
}

/**
 * Toggle map fullscreen
 */
function toggleMapFullscreen() {
    const mapContainer = document.querySelector('.map-container');
    if (!document.fullscreenElement) {
        mapContainer.requestFullscreen().catch(err => {
            console.error('Fullscreen error:', err);
        });
    } else {
        document.exitFullscreen();
    }
}

// Listen for fullscreen changes to invalidate map size
document.addEventListener('fullscreenchange', function() {
    setTimeout(() => {
        if (mainMap) mainMap.invalidateSize();
    }, 100);
});

console.log('Map module loaded');