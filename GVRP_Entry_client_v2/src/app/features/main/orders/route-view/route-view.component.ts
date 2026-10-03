import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  OnDestroy,
  output,
  signal
} from '@angular/core';
import {
  CdkDragDrop,
  DragDropModule,
  moveItemInArray,
  transferArrayItem
} from '@angular/cdk/drag-drop';

import { SolutionDTO } from '@core/models';
import {
  CustomizeRoutesPayload,
  EvaluatePreviewDTO,
  RouteDTO,
  StopDTO
} from '@core/models';
import { ApiService } from '@core/services/api.service';
import { SolutionStore } from '@core/services/solution.store';
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
  copyText: string;
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

interface DraftRoute {
  vehicleId: number;
  licensePlate: string;
  color: string;
  depotStart: StopDTO | null;
  depotEnd: StopDTO | null;
  orders: StopDTO[];
}

const PREVIEW_DEBOUNCE_MS = 350;
const UNDO_DEPTH = 30;

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
  imports: [DragDropModule],
  templateUrl: './route-view.component.html',
  styleUrl: './route-view.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RouteViewComponent implements OnDestroy {
  readonly solution = input.required<SolutionDTO>();

  /** Emitted when a route card header is clicked (V1 left this as a TODO). */
  readonly routeSelected = output<number>();

  private readonly api = inject(ApiService);
  private readonly store = inject(SolutionStore);

  private readonly expandedRoutes = signal<ReadonlySet<number>>(new Set<number>());
  readonly copyStatus = signal<ReadonlyMap<RouteCardVM, 'pending' | 'success' | 'error'>>(new Map());

  async copyStops(route: RouteCardVM): Promise<void> {
    if (!route.copyText || this.copyStatus().get(route) === 'pending') return;
    this.setCopyStatus(route, 'pending');
    try {
      if (typeof navigator === 'undefined' || !navigator.clipboard?.writeText) {
        throw new Error('Clipboard unavailable');
      }
      await navigator.clipboard.writeText(route.copyText);
      this.setCopyStatus(route, 'success');
    } catch {
      this.setCopyStatus(route, 'error');
    }
  }

  private setCopyStatus(route: RouteCardVM, status: 'pending' | 'success' | 'error'): void {
    const current = this.routeCards();
    this.copyStatus.update(previous => {
      const next = new Map([...previous].filter(([card]) => current.includes(card)));
      if (current.includes(route)) next.set(route, status);
      return next;
    });
  }

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
        copyText: stops.filter(stop => stop.type === 'ORDER')
          .map((stop, i) => `${i + 1}. ${stop.location_name?.replace(/\s+/g, ' ').trim() || 'Unnamed stop'}`)
          .join('\n'),
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

  expandAllRoutes(): void {
    // New Set each time so OnPush consumers of the signal always see the change.
    this.expandedRoutes.set(new Set(this.routeCards().map(route => route.index)));
  }

  collapseAllRoutes(): void {
    // New Set each time so OnPush consumers of the signal always see the change.
    this.expandedRoutes.set(new Set<number>());
  }

  onRouteHeaderClick(index: number): void {
    this.routeSelected.emit(index);
  }

  // ---------- Route customization (edit session) ----------

  readonly editing = signal(false);
  readonly draftRoutes = signal<DraftRoute[]>([]);
  readonly preview = signal<EvaluatePreviewDTO | null>(null);
  readonly previewStale = signal(false);
  readonly evaluating = signal(false);
  readonly evaluateError = signal<string | null>(null);
  readonly saving = signal(false);
  readonly saveError = signal<string | null>(null);
  readonly saveConflict = signal(false);

  private undoStack: DraftRoute[][] = [];
  private previewTimer: ReturnType<typeof setTimeout> | null = null;

  ngOnDestroy(): void {
    if (this.previewTimer !== null) {
      clearTimeout(this.previewTimer);
    }
  }

  /** Edit mode is available for saved plans with at least one route. */
  canCustomize(): boolean {
    return this.solution().routes.length > 0;
  }

  canUndo(): boolean {
    return this.editing() && this.undoStack.length > 0;
  }

  /** Save is allowed only with a fresh feasible preview and no pending work. */
  canSave(): boolean {
    const preview = this.preview();
    return this.editing()
      && !this.evaluating()
      && !this.saving()
      && !this.previewStale()
      && this.evaluateError() === null
      && preview !== null
      && preview.feasible;
  }

  startEdit(): void {
    if (!this.canCustomize() || this.editing()) return;
    this.undoStack = [];
    this.draftRoutes.set(this.buildDraft(this.solution().routes));
    this.preview.set(null);
    this.previewStale.set(true);
    this.evaluateError.set(null);
    this.saveError.set(null);
    this.saveConflict.set(false);
    this.editing.set(true);
    this.requestPreview();
  }

  cancelEdit(): void {
    this.clearPreviewTimer();
    this.undoStack = [];
    this.draftRoutes.set([]);
    this.preview.set(null);
    this.evaluating.set(false);
    this.evaluateError.set(null);
    this.saving.set(false);
    this.saveError.set(null);
    this.saveConflict.set(false);
    this.editing.set(false);
  }

  undo(): void {
    const previous = this.undoStack.pop();
    if (!previous) return;
    this.draftRoutes.set(previous);
    this.saveError.set(null);
    this.saveConflict.set(false);
    this.markStaleAndRefresh();
  }

  drop(event: CdkDragDrop<StopDTO[]>): void {
    if (!this.editing()) return;
    this.pushUndo();
    if (event.previousContainer === event.container) {
      moveItemInArray(event.container.data, event.previousIndex, event.currentIndex);
    } else {
      transferArrayItem(
        event.previousContainer.data, event.container.data,
        event.previousIndex, event.currentIndex);
    }
    this.draftRoutes.set([...this.draftRoutes()]);
    this.saveError.set(null);
    this.saveConflict.set(false);
    this.markStaleAndRefresh();
  }

  retryPreview(): void {
    this.evaluateError.set(null);
    this.requestPreview();
  }

  save(): void {
    if (!this.canSave()) return;
    const payload = this.buildPayload();
    if (!payload) return;
    const baseId = this.solution().id;
    this.saving.set(true);
    this.saveError.set(null);
    this.saveConflict.set(false);
    this.api.saveRouteRevision(baseId, payload).subscribe({
      next: saved => {
        this.saving.set(false);
        this.store.setSolution(saved);
        this.cancelEdit();
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.saveError.set(this.describeSaveError(error));
        // Server re-evaluates on save; refresh the preview to match its verdict.
        this.requestPreview();
      }
    });
  }

  /** After a 409, jump to the newest revision and leave edit mode. */
  loadLatestAndExit(): void {
    const baseId = this.solution().id;
    this.api.latestRevisionId(baseId).subscribe({
      next: ({ id }) => {
        this.store.loadById(id).subscribe({ error: () => undefined });
        this.cancelEdit();
      },
      error: () => this.saveError.set('Could not load the latest revision. Try again.')
    });
  }

  private buildDraft(routes: RouteDTO[]): DraftRoute[] {
    return routes.map((route, index) => {
      const stops = structuredClone(route.stops);
      const depotStart = stops.find(stop => stop.type === 'DEPOT') ?? null;
      const reversed = [...stops].reverse();
      const depotEnd = reversed.find(stop => stop.type === 'DEPOT') ?? null;
      const orders = stops.filter(stop => stop.type === 'ORDER');
      return {
        vehicleId: route.vehicle_id,
        licensePlate: route.vehicle_license_plate,
        color: routeColor(index),
        depotStart,
        depotEnd: depotEnd === depotStart ? null : depotEnd,
        orders
      };
    });
  }

  private pushUndo(): void {
    this.undoStack.push(structuredClone(this.draftRoutes()));
    if (this.undoStack.length > UNDO_DEPTH) {
      this.undoStack.shift();
    }
  }

  private markStaleAndRefresh(): void {
    this.previewStale.set(true);
    this.clearPreviewTimer();
    this.previewTimer = setTimeout(() => this.requestPreview(), PREVIEW_DEBOUNCE_MS);
  }

  private clearPreviewTimer(): void {
    if (this.previewTimer !== null) {
      clearTimeout(this.previewTimer);
      this.previewTimer = null;
    }
  }

  private buildPayload(): CustomizeRoutesPayload | null {
    const routes = [];
    for (const draft of this.draftRoutes()) {
      const stopOrderIds = [];
      for (const stop of draft.orders) {
        if (stop.order_id === undefined || stop.order_id === null) {
          this.evaluateError.set(
            `Stop "${stop.location_name}" has no order id; reload the plan and retry.`);
          return null;
        }
        stopOrderIds.push(stop.order_id);
      }
      routes.push({ vehicle_id: draft.vehicleId, stop_order_ids: stopOrderIds });
    }
    return { routes };
  }

  private requestPreview(): void {
    if (!this.editing()) return;
    const payload = this.buildPayload();
    if (!payload) {
      this.evaluating.set(false);
      return;
    }
    this.evaluating.set(true);
    this.evaluateError.set(null);
    this.api.evaluateRoutes(this.solution().id, payload).subscribe({
      next: preview => {
        this.evaluating.set(false);
        this.preview.set(preview);
        this.previewStale.set(false);
        this.applyPreviewTimes(preview);
      },
      error: (error: unknown) => {
        this.evaluating.set(false);
        this.evaluateError.set(this.describeEvaluateError(error));
      }
    });
  }

  /** Copy recalculated times onto the draft (sequences stay the source of truth). */
  private applyPreviewTimes(preview: EvaluatePreviewDTO): void {
    const byVehicle = new Map(preview.routes.map(route => [route.vehicle_id, route]));
    const drafts = this.draftRoutes().map(draft => {
      const evaluated = byVehicle.get(draft.vehicleId);
      if (!evaluated) return draft;
      const byOrder = new Map(
        evaluated.stops
          .filter(stop => stop.type === 'ORDER' && stop.order_id !== undefined)
          .map(stop => [stop.order_id as number, stop]));
      const orders = draft.orders.map(stop => {
        const fresh = stop.order_id !== undefined ? byOrder.get(stop.order_id) : undefined;
        if (!fresh) return stop;
        return {
          ...stop,
          sequence_number: fresh.sequence_number,
          arrival_time: fresh.arrival_time,
          departure_time: fresh.departure_time,
          wait_time: fresh.wait_time,
          load_after: fresh.load_after
        };
      });
      const evaluatedStops = evaluated.stops;
      const depotStart = draft.depotStart && evaluatedStops.length > 0
        && evaluatedStops[0].type === 'DEPOT'
        ? { ...draft.depotStart, arrival_time: evaluatedStops[0].arrival_time,
            departure_time: evaluatedStops[0].departure_time } : draft.depotStart;
      const last = evaluatedStops[evaluatedStops.length - 1];
      const depotEnd = draft.depotEnd && last && last.type === 'DEPOT'
        ? { ...draft.depotEnd, arrival_time: last.arrival_time,
            departure_time: last.departure_time } : draft.depotEnd;
      return { ...draft, depotStart, depotEnd, orders };
    });
    this.draftRoutes.set(drafts);
  }

  private describeEvaluateError(error: unknown): string {
    const status = (error as { status?: number }).status;
    if (status === 0 || status === 502) {
      return 'Evaluation service is unreachable. Your edits are kept; retry or discard.';
    }
    const message = (error as { error?: { errors?: { message?: string }[] } })
      .error?.errors?.[0]?.message;
    return message ?? 'Could not evaluate the draft. Retry or discard.';
  }

  private describeSaveError(error: unknown): string {
    const status = (error as { status?: number }).status;
    const message = (error as { error?: { errors?: { message?: string }[] } })
      .error?.errors?.[0]?.message;
    if (status === 409) {
      this.saveConflict.set(true);
      return (message ?? 'A newer revision exists.')
        + ' Load the latest revision to continue.';
    }
    if (status === 422) {
      return message ?? 'The plan violates hard constraints and was not saved.';
    }
    if (status === 0 || status === 502) {
      return 'Evaluation service is unreachable. Your edits are kept; retry or discard.';
    }
    return message ?? 'Could not save the revision. Retry or discard.';
  }
}
