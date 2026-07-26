import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  signal
} from '@angular/core';

import { SolutionDTO } from '@core/models';
import {
  calculateTimeBounds,
  minutesToTime,
  orderStops,
  routeColor,
  timeToMinutes
} from '@shared/utils/solution-view.utils';

type BarKind = 'service' | 'driving';

interface GanttBarVM {
  kind: BarKind;
  leftPercent: number;
  widthPercent: number;
  color: string;
  title: string;
}

interface GanttRowVM {
  index: number;
  licensePlate: string;
  orderCount: number;
  bars: GanttBarVM[];
}

interface TimeMarkerVM {
  label: string;
  leftPercent: number;
}

const SERVICE_COLOR = '#e74c3c';
/** V1 skipped bars narrower than this, to avoid slivers. */
const MIN_BAR_WIDTH_PERCENT = 0.1;

const MIN_ZOOM = 1;
const MAX_ZOOM = 4;
const ZOOM_STEP = 0.5;

/**
 * Timeline View (Gantt chart)
 *
 * Migrated from V1 `TimelineView` in
 * `scripts/components/Solution Views/solution-view.js`.
 *
 * Difference from V1: zoom actually works. V1's "Zoom In / Zoom Out" buttons only
 * showed a "Coming soon!" toast. Here zoom stretches the timeline width and the
 * chart scrolls horizontally; Reset returns to 1x.
 */
@Component({
  selector: 'app-timeline-view',
  standalone: true,
  imports: [],
  templateUrl: './timeline-view.component.html',
  styleUrl: './timeline-view.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TimelineViewComponent {
  readonly solution = input.required<SolutionDTO>();

  readonly zoom = signal(MIN_ZOOM);

  readonly canZoomIn = computed(() => this.zoom() < MAX_ZOOM);
  readonly canZoomOut = computed(() => this.zoom() > MIN_ZOOM);
  readonly timelineWidthPercent = computed(() => this.zoom() * 100);

  readonly bounds = computed(() => calculateTimeBounds(this.solution().routes));

  readonly timeMarkers = computed<TimeMarkerVM[]>(() => {
    const { startMinutes, endMinutes, durationMinutes } = this.bounds();
    if (durationMinutes <= 0) return [];

    const markers: TimeMarkerVM[] = [];
    const hourCount = Math.ceil(durationMinutes / 60);

    for (let i = 0; i <= hourCount; i++) {
      const minutes = startMinutes + i * 60;
      if (minutes > endMinutes) break;

      markers.push({
        label: minutesToTime(minutes),
        leftPercent: ((minutes - startMinutes) / durationMinutes) * 100
      });
    }

    return markers;
  });

  readonly rows = computed<GanttRowVM[]>(() => {
    const { startMinutes, durationMinutes } = this.bounds();

    return this.solution().routes.map((route, index) => ({
      index,
      licensePlate: route.vehicle_license_plate,
      orderCount: route.order_count,
      bars:
        durationMinutes > 0
          ? this.buildBars(orderStops(route.stops), routeColor(index), startMinutes, durationMinutes)
          : []
    }));
  });

  /** V1 `#validateTimeline`, surfaced in the UI instead of only console.error. */
  readonly warnings = computed<string[]>(() => {
    const routes = this.solution().routes;
    if (routes.length === 0) return ['Solution has no routes to display'];

    const messages: string[] = [];

    routes.forEach((route, index) => {
      const label = route.vehicle_license_plate || `Route ${index + 1}`;
      if (!route.start_time || !route.end_time) {
        messages.push(`${label}: missing start/end time`);
      }
      if (!route.stops || route.stops.length === 0) {
        messages.push(`${label}: has no stops`);
      }
    });

    return messages;
  });

  zoomIn(): void {
    this.zoom.update(zoom => Math.min(MAX_ZOOM, zoom + ZOOM_STEP));
  }

  zoomOut(): void {
    this.zoom.update(zoom => Math.max(MIN_ZOOM, zoom - ZOOM_STEP));
  }

  resetZoom(): void {
    this.zoom.set(MIN_ZOOM);
  }

  private buildBars(
    stops: readonly { type: string; location_name: string; arrival_time: string; departure_time?: string }[],
    color: string,
    dayStart: number,
    dayDuration: number
  ): GanttBarVM[] {
    const bars: GanttBarVM[] = [];

    for (let i = 0; i < stops.length - 1; i++) {
      const current = stops[i];
      const next = stops[i + 1];

      const arrival = timeToMinutes(current.arrival_time);
      const departure = timeToMinutes(current.departure_time ?? current.arrival_time);
      const nextArrival = timeToMinutes(next.arrival_time);

      if (
        Number.isNaN(arrival) ||
        Number.isNaN(departure) ||
        Number.isNaN(nextArrival)
      ) {
        continue;
      }

      if (current.type === 'ORDER') {
        const bar = this.buildBar(
          'service',
          arrival,
          departure,
          dayStart,
          dayDuration,
          SERVICE_COLOR,
          `Service at ${current.location_name}`
        );
        if (bar) bars.push(bar);
      }

      const driving = this.buildBar(
        'driving',
        departure,
        nextArrival,
        dayStart,
        dayDuration,
        color,
        `Driving to ${next.location_name}`
      );
      if (driving) bars.push(driving);
    }

    return bars;
  }

  private buildBar(
    kind: BarKind,
    fromMinutes: number,
    toMinutes: number,
    dayStart: number,
    dayDuration: number,
    color: string,
    title: string
  ): GanttBarVM | null {
    const leftPercent = clampPercent(((fromMinutes - dayStart) / dayDuration) * 100);
    const rawWidth = ((toMinutes - fromMinutes) / dayDuration) * 100;
    const widthPercent = Math.max(0, Math.min(100 - leftPercent, rawWidth));

    if (widthPercent <= MIN_BAR_WIDTH_PERCENT) return null;

    return { kind, leftPercent, widthPercent, color, title };
  }
}

function clampPercent(value: number): number {
  return Math.max(0, Math.min(100, value));
}
