import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
  signal
} from '@angular/core';

import { SolutionDTO } from '@core/models';
import {
  formatDuration,
  formatVndAmount,
  orderStops,
  routeColor
} from '@shared/utils/solution-view.utils';

interface StopVM {
  isDepot: boolean;
  markerLabel: string;
  markerColor: string;
  locationName: string;
  arrivalTime: string;
  departureTime: string | null;
  demand: number | null;
  loadAfterText: string;
  waitTime: number;
  showConnector: boolean;
}

interface RouteCardVM {
  index: number;
  color: string;
  licensePlate: string;
  orderCount: number;
  distanceText: string;
  timeText: string;
  loadText: string;
  timeRange: string;
  stops: StopVM[];
}

interface SummaryVM {
  totalRoutes: number;
  totalDistanceText: string;
  totalTimeText: string;
  totalCostText: string;
}

/**
 * Route View
 *
 * Migrated from V1 `RouteView` in
 * `scripts/components/Solution Views/solution-view.js`.
 *
 * Unit note carried over from V1: `solution.total_time` and `route.service_time`
 * are multiplied by 60 before formatting, i.e. the backend reports them in hours.
 */
@Component({
  selector: 'app-route-view',
  standalone: true,
  imports: [],
  templateUrl: './route-view.component.html',
  styleUrl: './route-view.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RouteViewComponent {
  readonly solution = input.required<SolutionDTO>();

  /** Emitted when a route card header is clicked (V1 left this as a TODO). */
  readonly routeSelected = output<number>();

  private readonly expandedRoutes = signal<ReadonlySet<number>>(new Set<number>());

  readonly summary = computed<SummaryVM>(() => {
    const solution = this.solution();

    return {
      totalRoutes: solution.total_vehicles_used,
      totalDistanceText: `${solution.total_distance.toFixed(1)} km`,
      totalTimeText: formatDuration(solution.total_time * 60),
      totalCostText: `${formatVndAmount(solution.total_cost)} VND`
    };
  });

  readonly routeCards = computed<RouteCardVM[]>(() =>
    this.solution().routes.map((route, index) => {
      const color = routeColor(index);
      const stops = orderStops(route.stops);

      return {
        index,
        color,
        licensePlate: route.vehicle_license_plate,
        orderCount: route.order_count,
        distanceText: `${route.distance.toFixed(1)} km`,
        timeText: formatDuration(route.service_time * 60),
        loadText: `${(route.load_utilization ?? 0).toFixed(0)}%`,
        timeRange: `${route.start_time} - ${route.end_time}`,
        stops: stops.map((stop, stopIndex) => ({
          isDepot: stop.type === 'DEPOT',
          markerLabel:
            stop.type === 'DEPOT' ? '🏢' : String(stop.sequence_number ?? stopIndex),
          markerColor: stop.type === 'DEPOT' ? '#4A90E2' : color,
          locationName: stop.location_name,
          arrivalTime: stop.arrival_time,
          departureTime: stop.departure_time ?? null,
          demand: stop.demand ?? null,
          loadAfterText: stop.load_after.toFixed(1),
          waitTime: stop.wait_time,
          showConnector: stopIndex !== stops.length - 1
        }))
      };
    })
  );

  isExpanded(index: number): boolean {
    return this.expandedRoutes().has(index);
  }

  toggleRoute(index: number): void {
    // New Set each time so OnPush consumers of the signal always see the change.
    this.expandedRoutes.update(expanded => {
      const next = new Set(expanded);
      if (next.has(index)) {
        next.delete(index);
      } else {
        next.add(index);
      }
      return next;
    });
  }

  onRouteHeaderClick(index: number): void {
    this.routeSelected.emit(index);
  }
}
