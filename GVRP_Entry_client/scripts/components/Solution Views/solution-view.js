/**
 * Solution Views Components
 * Handles displaying solution in different views: Orders, Routes, Timeline
 */

import { DOMHelpers } from '../../utils/dom-helpers.js';
import { Toast } from '../../utils/toast.js';

/**
 * Solution Display Manager
 * Coordinates between different views
 */
export class SolutionDisplay {
    static #currentSolution = null;
    static #currentView = 'orders';
    /**
     * Set current solution and display it
     */
    static setSolution(solution) {
        this.#currentSolution = solution;

        console.log('📊 Solution loaded:', solution);

        // Enable Route and Timeline tabs
        this.#enableSolutionTabs();

        // Display in current view
        this.displayInView(this.#currentView);

        RouteView.display(solution);
        TimelineView.display(solution);
    }

    /**
     * Enable Route and Timeline tabs
     * @private
     */
    static #enableSolutionTabs() {
        const routeBtn = document.querySelector('.tab-btn[data-tab="route-tab"]');
        const timelineBtn = document.querySelector('.tab-btn[data-tab="timeline-tab"]');

        if (routeBtn) {
            routeBtn.disabled = false;
            routeBtn.style.opacity = '1';
            routeBtn.style.cursor = 'pointer';
        }

        if (timelineBtn) {
            timelineBtn.disabled = false;
            timelineBtn.style.opacity = '1';
            timelineBtn.style.cursor = 'pointer';
        }
    }

    /**
     * Display solution in specified view
     */
    static displayInView(viewName) {
        this.#currentView = viewName;

        if (!this.#currentSolution) {
            console.warn('No solution to display');
            return;
        }

        switch (viewName) {
            case 'orders':
                // Already handled by OrdersTable
                break;

            case 'route':
                RouteView.display(this.#currentSolution);
                break;

            case 'timeline':
                TimelineView.display(this.#currentSolution);
                break;
        }
    }

    /**
     * Clear solution
     */
    static clearSolution() {
        this.#currentSolution = null;
        RouteView.clear();
        TimelineView.clear();

        // Disable tabs
        const routeBtn = document.querySelector('.tab-btn[data-tab="route-tab"]');
        const timelineBtn = document.querySelector('.tab-btn[data-tab="timeline-tab"]');

        if (routeBtn) {
            routeBtn.disabled = true;
            routeBtn.style.opacity = '0.5';
        }
        if (timelineBtn) {
            timelineBtn.disabled = true;
            timelineBtn.style.opacity = '0.5';
        }
    }

    /**
     * Get current solution
     */
    static getCurrentSolution() {
        return this.#currentSolution;
    }
}

/**
 * Route View Component
 * Shows map + route list with details
 */
export class RouteView {
    /**
     * Display routes view
     */
    static display(solution) {
        console.log('🗺️ Displaying route view');

        const container = document.getElementById('route-tab');
        if (!container) return;

        // Clear and rebuild
        container.innerHTML = this.#buildRouteViewHTML(solution);

        // Attach event listeners
        this.#attachEventListeners(solution);
    }

    /**
     * Build route view HTML
     * @private
     */
    static #buildRouteViewHTML(solution) {
        return `
            <div class="route-view">
                <!-- Summary Header -->
                <div class="route-summary">
                    <h3>📊 Solution Summary</h3>
                    <div class="summary-stats">
                        <div class="stat-item">
                            <span class="stat-label">Total Routes:</span>
                            <span class="stat-value">${solution.total_vehicles_used}</span>
                        </div>
                        <div class="stat-item">
                            <span class="stat-label">Total Distance:</span>
                            <span class="stat-value">${solution.total_distance.toFixed(1)} km</span>
                        </div>
                        <div class="stat-item">
                            <span class="stat-label">Total Time:</span>
                            <span class="stat-value">${this.#formatDuration(solution.total_time * 60)}</span>
                        </div>
                        <div class="stat-item">
                            <span class="stat-label">Total Cost:</span>
                            <span class="stat-value">${solution.total_cost.toLocaleString('vi-VN')} VND</span>
                        </div>
                    </div>
                </div>

                <!-- Routes List -->
                <div class="routes-container">
                    <div class="routes-list">
                        ${this.#buildRoutesListHTML(solution.routes)}
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * Build routes list HTML
     * @private
     */
    static #buildRoutesListHTML(routes) {
        const colors = ['#3498db', '#e74c3c', '#2ecc71', '#f1c40f', '#9b59b6', '#34495e'];

        return routes.map((route, index) => {
            const color = colors[index % colors.length];
            const loadUtil = route.load_utilization || 0;

            return `
                <div class="route-card" data-route-index="${index}">
                    <div class="route-card-header" style="border-left: 4px solid ${color};">
                        <div class="route-title">
                            <span class="route-icon" style="background: ${color};">🚚</span>
                            <strong>${route.vehicle_license_plate}</strong>
                        </div>
                        <button class="btn-expand" onclick="RouteView.toggleRoute(${index})">
                            <span class="expand-icon">▼</span>
                        </button>
                    </div>
                    
                    <div class="route-card-summary">
                        <div class="route-stat">
                            <span class="label">Orders:</span>
                            <span class="value">${route.order_count}</span>
                        </div>
                        <div class="route-stat">
                            <span class="label">Distance:</span>
                            <span class="value">${route.distance.toFixed(1)} km</span>
                        </div>
                        <div class="route-stat">
                            <span class="label">Time:</span>
                            <span class="value">${this.#formatDuration(route.service_time * 60)}</span>
                        </div>
                        <div class="route-stat">
                            <span class="label">Load:</span>
                            <span class="value">${loadUtil.toFixed(0)}%</span>
                        </div>
                    </div>
                    
                    <div class="route-card-details" id="route-details-${index}" style="display: none;">
                        <div class="route-timeline">
                            <div class="timeline-header">
                                <strong>Route Timeline</strong>
                                <span class="time-range">${route.start_time} - ${route.end_time}</span>
                            </div>
                            
                            ${this.#buildStopsTimelineHTML(route.stops, color)}
                        </div>
                    </div>
                </div>
            `;
        }).join('');
    }

    /**
     * Build stops timeline HTML
     * @private
     */
    static #buildStopsTimelineHTML(stops, color) {
        // Sort stops by time, move end depot to end
        let sortedStops = [...stops];
        const endDepotIndex = sortedStops.findIndex(s =>
            s.type === 'DEPOT' && s.departure_time === null
        );

        if (endDepotIndex !== -1 && endDepotIndex !== sortedStops.length - 1) {
            const endDepot = sortedStops.splice(endDepotIndex, 1)[0];
            sortedStops.push(endDepot);
        }

        return sortedStops.map((stop, idx) => {
            const isDepot = stop.type === 'DEPOT';
            const isLast = idx === sortedStops.length - 1;

            return `
                <div class="timeline-stop">
                    <div class="stop-marker" style="background: ${isDepot ? '#4A90E2' : color};">
                        ${isDepot ? '🏢' : stop.sequence_number || idx}
                    </div>
                    <div class="stop-content">
                        <div class="stop-header">
                            <strong>${stop.location_name}</strong>
                            ${!isDepot ? `<span class="stop-badge">Order</span>` : ''}
                        </div>
                        <div class="stop-times">
                            <span>⏰ Arrival: ${stop.arrival_time}</span>
                            ${stop.departure_time ? `<span>🚀 Departure: ${stop.departure_time}</span>` : ''}
                        </div>
                        ${!isDepot ? `
                            <div class="stop-details">
                                <span>📦 Demand: ${stop.demand} kg</span>
                                <span>📊 Load after: ${stop.load_after.toFixed(1)} kg</span>
                                ${stop.wait_time > 0 ? `<span>⏳ Wait: ${stop.wait_time} min</span>` : ''}
                            </div>
                        ` : ''}
                    </div>
                    ${!isLast ? `<div class="stop-connector" style="border-color: ${color};"></div>` : ''}
                </div>
            `;
        }).join('');
    }

    /**
     * Toggle route details
     */
    static toggleRoute(index) {
        const details = document.getElementById(`route-details-${index}`);
        const button = document.querySelector(`.route-card[data-route-index="${index}"] .expand-icon`);

        if (details) {
            const isHidden = details.style.display === 'none';
            details.style.display = isHidden ? 'block' : 'none';
            if (button) {
                button.textContent = isHidden ? '▲' : '▼';
            }
        }
    }

    /**
     * Format duration
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
     * Attach event listeners
     * @private
     */
    static #attachEventListeners(solution) {
        // Route card click to highlight on map
        document.querySelectorAll('.route-card-header').forEach((header, idx) => {
            header.addEventListener('click', (e) => {
                if (!e.target.closest('.btn-expand')) {
                    // TODO: Highlight route on map
                    console.log('Route clicked:', idx);
                }
            });
        });
    }

    /**
     * Clear view
     */
    static clear() {
        const container = document.getElementById('route-tab');
        if (container) {
            container.innerHTML = `
                <div class="empty-state">
                    <div class="empty-icon">🗺️</div>
                    <div class="empty-text">No solution to display</div>
                    <p>Run optimization to see routes here</p>
                </div>
            `;
        }
    }
}

/**
 * Timeline View Component
 * Shows Gantt chart of routes
 */
export class TimelineView {
    /**
     * Display timeline view
     */
    static display(solution) {
        console.log('📅 Displaying timeline view');

        // Validate data
        this.#validateTimeline(solution);

        const container = document.getElementById('timeline-tab');
        if (!container) return;

        container.innerHTML = this.#buildTimelineHTML(solution);
        this.#renderGanttChart(solution);
    }

    static #validateTimeline(solution) {
        if (!solution || !solution.routes || solution.routes.length === 0) {
            console.error('Invalid solution data');
            return false;
        }

        solution.routes.forEach((route, idx) => {
            if (!route.start_time || !route.end_time) {
                console.error(`Route ${idx} missing start/end time`);
            }
            if (!route.stops || route.stops.length === 0) {
                console.error(`Route ${idx} has no stops`);
            }
        });

        return true;
    }

    static #buildTimelineHTML(solution) {
        return `
            <div class="timeline-view-container">
                <div class="timeline-header">
                    <h3>📅 Route Timeline - Gantt Chart</h3>
                    <div class="timeline-controls">
                        <button class="btn btn-sm" onclick="TimelineView.zoomIn()">🔍 Zoom In</button>
                        <button class="btn btn-sm" onclick="TimelineView.zoomOut()">🔍 Zoom Out</button>
                        <button class="btn btn-sm" onclick="TimelineView.resetZoom()">↻ Reset</button>
                    </div>
                </div>
                
                <div class="timeline-legend">
                    <div class="legend-item">
                        <span class="legend-color" style="background: #4A90E2;"></span>
                        <span>Depot</span>
                    </div>
                    <div class="legend-item">
                        <span class="legend-color" style="background: #2ecc71;"></span>
                        <span>Driving</span>
                    </div>
                    <div class="legend-item">
                        <span class="legend-color" style="background: #e74c3c;"></span>
                        <span>Service</span>
                    </div>
                    <div class="legend-item">
                        <span class="legend-color" style="background: #f1c40f;"></span>
                        <span>Waiting</span>
                    </div>
                </div>
                
                <div id="gantt-chart" class="gantt-chart-container"></div>
            </div>
        `;
    }

    static #renderGanttChart(solution) {
        const container = document.getElementById('gantt-chart');
        if (!container) return;

        const routes = solution.routes;
        if (!routes || routes.length === 0) return;

        const { startTime, endTime } = this.#calculateTimeBounds(routes);

        const chartHTML = routes.map((route, idx) => {
            return this.#buildRouteTimeline(route, idx, startTime, endTime);
        }).join('');

        container.innerHTML = `
            <div class="gantt-grid">
                <div class="gantt-sidebar">
                    ${routes.map((route, idx) => `
                        <div class="gantt-row-label">
                            <strong>🚚 ${route.vehicle_license_plate}</strong>
                            <small>${route.order_count} orders</small>
                        </div>
                    `).join('')}
                </div>
                
                <div class="gantt-timeline">
                    <div class="gantt-time-header">
                        ${this.#buildTimeHeader(startTime, endTime)}
                    </div>
                    <div class="gantt-rows">
                        ${chartHTML}
                    </div>
                </div>
            </div>
        `;
    }

    static #calculateTimeBounds(routes) {
        let earliest = null;
        let latest = null;

        routes.forEach(route => {
            if (!route.start_time || !route.end_time) return;

            if (earliest === null || route.start_time < earliest) {
                earliest = route.start_time;
            }
            if (latest === null || route.end_time > latest) {
                latest = route.end_time;
            }
        });

        if (!earliest || !latest) {
            earliest = '08:00:00';
            latest = '18:00:00';
        }

        const startMinutes = this.#timeToMinutes(earliest);
        const endMinutes = this.#timeToMinutes(latest);
        const duration = endMinutes - startMinutes;

        if (duration < 60) {
            latest = this.#minutesToTime(startMinutes + 60);
        }

        return { startTime: earliest, endTime: latest };
    }

    static #buildTimeHeader(startTime, endTime) {
        const start = this.#timeToMinutes(startTime);
        const end = this.#timeToMinutes(endTime);
        const duration = end - start;

        const hours = Math.ceil(duration / 60);
        const interval = 60;

        let headerHTML = '';

        for (let i = 0; i <= hours; i++) {
            const timeInMinutes = start + (i * interval);
            if (timeInMinutes > end) break;

            const timeStr = this.#minutesToTime(timeInMinutes);
            const position = ((timeInMinutes - start) / duration) * 100;

            headerHTML += `
                <div class="time-marker" style="left: ${position.toFixed(2)}%;">
                    ${timeStr}
                </div>
            `;
        }

        return headerHTML;
    }

    static #buildRouteTimeline(route, index, dayStartTime, dayEndTime) {
        const colors = ['#3498db', '#e74c3c', '#2ecc71', '#f1c40f', '#9b59b6', '#34495e'];
        const routeColor = colors[index % colors.length];

        const dayStart = this.#timeToMinutes(dayStartTime);
        const dayEnd = this.#timeToMinutes(dayEndTime);
        const dayDuration = dayEnd - dayStart;

        if (dayDuration <= 0) {
            console.error('Invalid day duration');
            return '<div class="gantt-row"></div>';
        }

        let stops = [...route.stops];
        const endDepotIndex = stops.findIndex(s =>
            s.type === 'DEPOT' && s.departure_time === null
        );
        if (endDepotIndex !== -1 && endDepotIndex !== stops.length - 1) {
            const endDepot = stops.splice(endDepotIndex, 1)[0];
            stops.push(endDepot);
        }

        let barsHTML = '';

        for (let i = 0; i < stops.length - 1; i++) {
            const currentStop = stops[i];
            const nextStop = stops[i + 1];

            const arrivalTime = this.#timeToMinutes(currentStop.arrival_time);
            const departureTime = this.#timeToMinutes(currentStop.departure_time || currentStop.arrival_time);
            const nextArrivalTime = this.#timeToMinutes(nextStop.arrival_time);

            if (isNaN(arrivalTime) || isNaN(departureTime) || isNaN(nextArrivalTime)) {
                continue;
            }

            // Service bar
            if (currentStop.type === 'ORDER') {
                const serviceStart = Math.max(0, Math.min(100, ((arrivalTime - dayStart) / dayDuration) * 100));
                const serviceWidth = Math.max(0, Math.min(100 - serviceStart, ((departureTime - arrivalTime) / dayDuration) * 100));

                if (serviceWidth > 0.1) {
                    barsHTML += `
                        <div class="gantt-bar service-bar" 
                             style="left: ${serviceStart.toFixed(2)}%; width: ${serviceWidth.toFixed(2)}%; background: #e74c3c;"
                             title="Service at ${currentStop.location_name}">
                        </div>
                    `;
                }
            }

            // Driving bar
            const drivingStart = Math.max(0, Math.min(100, ((departureTime - dayStart) / dayDuration) * 100));
            const drivingWidth = Math.max(0, Math.min(100 - drivingStart, ((nextArrivalTime - departureTime) / dayDuration) * 100));

            if (drivingWidth > 0.1) {
                barsHTML += `
                    <div class="gantt-bar driving-bar" 
                         style="left: ${drivingStart.toFixed(2)}%; width: ${drivingWidth.toFixed(2)}%; background: ${routeColor};"
                         title="Driving to ${nextStop.location_name}">
                    </div>
                `;
            }
        }

        return `<div class="gantt-row">${barsHTML}</div>`;
    }

    static #timeToMinutes(timeStr) {
        if (!timeStr) return 0;
        const [hours, minutes, seconds] = timeStr.split(':').map(Number);
        return hours * 60 + minutes + (seconds / 60);
    }

    /**
     * Convert minutes to time
     * @private
     */
    static #minutesToTime(minutes) {
        const h = Math.floor(minutes / 60);
        const m = Math.floor(minutes % 60);
        return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}`;
    }

    static #formatDuration(minutes) {
        const h = Math.floor(minutes / 60);
        const m = Math.round(minutes % 60);
        return h > 0 ? `${h}h ${m}m` : `${m}m`;
    }

    static zoomIn() {
        Toast.info('Zoom in - Coming soon!');
    }

    static zoomOut() {
        Toast.info('Zoom out - Coming soon!');
    }

    static resetZoom() {
        const solution = SolutionDisplay.getCurrentSolution();
        if (solution) {
            this.display(solution);
        }
    }

    static clear() {
        const container = document.getElementById('timeline-tab');
        if (container) {
            container.innerHTML = `
                <div class="empty-state">
                    <div class="empty-icon">📅</div>
                    <div class="empty-text">No timeline to display</div>
                    <p>Run optimization to see timeline here</p>
                </div>
            `;
        }
    }
}

// Export for global access
if (typeof window !== 'undefined') {
    window.SolutionDisplay = SolutionDisplay;
    window.RouteView = RouteView;
    window.TimelineView = TimelineView;
}