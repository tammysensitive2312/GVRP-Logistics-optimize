import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  input,
  OnDestroy,
  output,
  PLATFORM_ID,
  Renderer2,
  signal,
  viewChild
} from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ParetoCandidateDTO, ParetoFrontierDTO, SolutionDTO } from '@core/models';
import { FrontierStatus } from '@core/models';
import { ApiService } from '@core/services/api.service';
import { SolutionStore } from '@core/services/solution.store';
import { ConfirmService } from '@shared/services/confirm.service';
import { ToastService } from '@shared/services/toast.service';
import {
  ChartWindow,
  compactVnd,
  deltaPct,
  frontierPath,
  fullWindow as makeFullWindow,
  isFullWindow,
  MAX_CHART_ZOOM,
  niceTicks,
  panWindow,
  scaleLinear,
  windowSpan,
  zoomWindow
} from '@shared/utils/pareto-chart.utils';
import { formatDuration, formatVndAmount } from '@shared/utils/solution-view.utils';

const VIEW_W = 560;
const VIEW_H = 300;
const PLOT_LEFT = 56;
const PLOT_RIGHT = 16;
const PLOT_TOP = 24;
const PLOT_BOTTOM = 40;
const PLOT_W = VIEW_W - PLOT_LEFT - PLOT_RIGHT;
const PLOT_H = VIEW_H - PLOT_TOP - PLOT_BOTTOM;

/** Ratio applied per wheel notch / control-strip click (design paragraph 5). */
const WHEEL_STEP = 1.25;
/** Pointer travel (client px) before a press counts as a drag and suppresses the click. */
const DRAG_THRESHOLD_PX = 4;

interface ChartPointVM {
  candidate: ParetoCandidateDTO;
  x: number;
  y: number;
}

interface ChartTickVM {
  value: number;
  pos: number;
  label: string;
}

interface ChartVM {
  points: ChartPointVM[];
  xTicks: ChartTickVM[];
  yTicks: ChartTickVM[];
  path: string;
}

interface DeltaVM {
  text: string;
  direction: 'up' | 'down' | 'flat';
}

interface CandidateRowVM {
  candidate: ParetoCandidateDTO;
  isActive: boolean;
  isSelected: boolean;
  weightCostPct: number;
  weightCo2Pct: number;
  servedText: string;
  servedLow: boolean;
  deltaCost: DeltaVM | null;
  deltaCo2: DeltaVM | null;
}

interface ComparisonRowVM {
  label: string;
  deltaText: string;
  direction: 'up' | 'down' | 'flat';
}

/**
 * Pareto tab (PARETO-002): trade-off chart, candidate list and apply flow.
 *
 * Scientific note (AGENTS.md §9): when the run is clustered the frontier is
 * computed on the clustered problem, an approximation of the original problem.
 * The chip states this explicitly; no equal-quality claim is made.
 */
@Component({
  selector: 'app-pareto-view',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './pareto-view.component.html',
  styleUrl: './pareto-view.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ParetoViewComponent implements OnDestroy {
  readonly solution = input.required<SolutionDTO>();
  readonly frontier = input.required<ParetoFrontierDTO>();

  readonly reload = output<void>();

  private readonly api = inject(ApiService);
  private readonly store = inject(SolutionStore);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly renderer = inject(Renderer2);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly chartSvg = viewChild<ElementRef<SVGSVGElement>>('chartSvg');

  readonly selectedId = signal<number | null>(null);
  readonly applying = signal(false);
  readonly conflict = signal(false);
  readonly dragging = signal(false);

  /** PARETO-003: null means the default (full padded) window. */
  readonly zoom = signal<ChartWindow | null>(null);

  private cleanupFns: (() => void)[] = [];
  private readonly activePointers = new Map<number, { x: number; y: number }>();
  private dragStart: { x: number; y: number } | null = null;
  private lastPan: { x: number; y: number } | null = null;
  private pinchDistance = 0;
  private dragMoved = false;

  constructor() {
    // Reset the zoom whenever the frontier (a new revision, an apply reload or a
    // different job) changes: the padded domain changes with it and a stale
    // window could hide every point. Guarded: the effect reads only `frontier`
    // and writes `zoom`, so it cannot loop.
    effect(() => {
      this.frontier();
      this.zoom.set(null);
    }, { allowSignalWrites: true });
  }

  readonly viewBox = `0 0 ${VIEW_W} ${VIEW_H}`;
  readonly plotLeft = PLOT_LEFT;
  readonly plotRight = PLOT_LEFT + PLOT_W;
  readonly plotTop = PLOT_TOP;
  readonly plotBottom = PLOT_TOP + PLOT_H;

  readonly candidates = computed(() =>
    [...this.frontier().candidates].sort((a, b) => a.index - b.index)
  );

  readonly frontierCount = computed(
    () => this.candidates().filter(c => c.frontier_status === 'FRONTIER').length
  );

  readonly recommended = computed(
    () => this.candidates().find(c => c.engine_selected) ?? null
  );

  readonly activeCandidate = computed(() => {
    const id = this.frontier().active_candidate_id;
    if (id === null) return null;
    return this.candidates().find(c => c.id === id) ?? null;
  });

  readonly selected = computed(() => {
    const id = this.selectedId();
    if (id === null) return null;
    return this.candidates().find(c => c.id === id) ?? null;
  });

  readonly canApply = computed(() => {
    const selected = this.selected();
    return selected !== null && selected.applicable && !this.applying();
  });

  readonly summary = computed(() => {
    const count = this.candidates().length;
    const recommended = this.recommended();
    const suffix = recommended ? ` · recommended ${recommended.label}` : '';
    return `${count} scenarios · ${this.frontierCount()} on the frontier${suffix}`;
  });

  readonly clustered = computed(() => this.frontier().clustered);

  readonly activePlanLine = computed(() => {
    const active = this.activeCandidate();
    if (active) {
      return `Active plan: ${active.label} · cost-weight ${formatWeight(active.cost_weight)}`
        + ` / CO₂-weight ${formatWeight(active.co2_weight)}`;
    }
    return `Active plan: manual edit (rev #${this.frontier().latest_solution_id}) — `
      + 'Pareto points can still be applied when they cover the same orders';
  });

  /** Full padded window, computed from the same inputs PARETO-002 used per chart(). */
  readonly fullWindow = computed<ChartWindow | null>(() => {
    const candidates = this.candidates();
    if (candidates.length === 0) return null;
    const costs = candidates.map(c => c.total_cost);
    const co2s = candidates.map(c => c.total_co2);
    return makeFullWindow(paddedDomain(costs), paddedDomain(co2s));
  });

  /** Visible window fed into the chart: the zoom window when zoomed, the full domain otherwise. */
  readonly window = computed<ChartWindow | null>(() => this.zoom() ?? this.fullWindow());

  readonly atFullWindow = computed(() => {
    const full = this.fullWindow();
    const win = this.window();
    return full === null || win === null || isFullWindow(win, full);
  });

  readonly atMaxZoom = computed(() => {
    const full = this.fullWindow();
    const win = this.window();
    if (!full || !win) return false;
    const fullSpan = windowSpan(full);
    const span = windowSpan(win);
    const eps = 1e-6;
    return span.x <= fullSpan.x / MAX_CHART_ZOOM + eps
      && span.y <= fullSpan.y / MAX_CHART_ZOOM + eps;
  });

  /** Reset is a no-op (and its button disabled) while the view is already at the default. */
  readonly isDefaultView = computed(() => this.zoom() === null || this.atFullWindow());

  readonly chart = computed<ChartVM>(() => {
    const candidates = this.candidates();
    const win = this.window();
    if (candidates.length === 0 || win === null) {
      return { points: [], xTicks: [], yTicks: [], path: '' };
    }

    const xScale = scaleLinear([win.costMin, win.costMax], [PLOT_LEFT, PLOT_LEFT + PLOT_W]);
    const yScale = scaleLinear([win.co2Min, win.co2Max], [PLOT_TOP + PLOT_H, PLOT_TOP]);

    const points: ChartPointVM[] = candidates.map(candidate => ({
      candidate,
      x: xScale(candidate.total_cost),
      y: yScale(candidate.total_co2)
    }));

    const xTicks: ChartTickVM[] = niceTicks(win.costMin, win.costMax).map(value => ({
      value,
      pos: xScale(value),
      label: compactVnd(value)
    }));
    const yTicks: ChartTickVM[] = niceTicks(win.co2Min, win.co2Max).map(value => ({
      value,
      pos: yScale(value),
      label: formatCo2Tick(value)
    }));

    const path = frontierPath(points.map(point => ({
      total_cost: point.candidate.total_cost,
      total_co2: point.candidate.total_co2,
      frontier_status: point.candidate.frontier_status,
      x: point.x,
      y: point.y
    })));

    return { points, xTicks, yTicks, path };
  });

  readonly rows = computed<CandidateRowVM[]>(() => {
    const active = this.activeCandidate();
    const selectedId = this.selectedId();
    const maxServed = Math.max(0, ...this.candidates().map(c => c.served_orders));

    return this.candidates().map(candidate => ({
      candidate,
      isActive: active !== null && active.id === candidate.id,
      isSelected: candidate.id === selectedId,
      weightCostPct: clampPct(candidate.cost_weight * 100),
      weightCo2Pct: clampPct(candidate.co2_weight * 100),
      servedText: `${candidate.served_orders}/${candidate.served_orders + candidate.unserved_orders}`,
      servedLow: candidate.served_orders < maxServed,
      deltaCost: active && active.id !== candidate.id
        ? percentDelta(candidate.total_cost, active.total_cost)
        : null,
      deltaCo2: active && active.id !== candidate.id
        ? percentDelta(candidate.total_co2, active.total_co2)
        : null
    }));
  });

  readonly comparison = computed<ComparisonRowVM[]>(() => {
    const selected = this.selected();
    const active = this.activeCandidate();
    if (!selected || !active || selected.id === active.id) return [];

    const rows: ComparisonRowVM[] = [];
    rows.push(amountRow('Cost', selected.total_cost, active.total_cost, 'VND'));
    rows.push(amountRow('CO₂', selected.total_co2, active.total_co2, 'kg', 1));
    rows.push(amountRow('Distance', selected.total_distance, active.total_distance, 'km', 1));
    rows.push(intRow('Vehicles', selected.total_vehicles_used, active.total_vehicles_used));
    rows.push(timeRow('Time', selected.total_time, active.total_time));
    return rows;
  });

  readonly selectionReason = computed(() => {
    const selected = this.selected();
    return selected ? this.reasonText(selected) : null;
  });

  readonly showSelectionPanel = computed(() => this.selected() !== null);

  select(id: number): void {
    this.selectedId.set(id);
    this.conflict.set(false);
  }

  /**
   * PARETO-003: a press that turned into a pan must not also select the point.
   * `dragMoved` is cleared on the next pointerdown, so a plain click always selects.
   */
  onPointClick(id: number): void {
    if (this.dragMoved) return;
    this.select(id);
  }

  onPointKey(event: KeyboardEvent, id: number): void {
    if (event.key === 'Enter' || event.key === ' ' || event.key === 'Spacebar') {
      event.preventDefault();
      this.select(id);
    }
  }

  /**
   * Wheel zoom about the pointer. Only acts while the pointer is over the plot
   * rectangle, and only then calls `preventDefault()` so the panel can still be
   * scrolled elsewhere (PZU-004). `ctrlKey` (trackpad pinch) takes the same path.
   */
  onWheel(event: WheelEvent): void {
    if (event.deltaY === 0) return;

    const plot = this.toPlotFraction(event.clientX, event.clientY);
    if (!plot) return;

    event.preventDefault();
    const factor = event.deltaY < 0 ? 1 / WHEEL_STEP : WHEEL_STEP;
    this.applyZoom(plot.fx, plot.fy, factor);
  }

  zoomIn(): void {
    this.applyZoom(0.5, 0.5, 1 / WHEEL_STEP);
  }

  zoomOut(): void {
    this.applyZoom(0.5, 0.5, WHEEL_STEP);
  }

  resetZoom(): void {
    this.zoom.set(null);
  }

  onPointerDown(event: PointerEvent): void {
    if (!this.isBrowser) return;

    this.dragMoved = false;
    this.activePointers.set(event.pointerId, { x: event.clientX, y: event.clientY });

    if (this.activePointers.size === 1) {
      this.dragStart = { x: event.clientX, y: event.clientY };
      this.lastPan = { x: event.clientX, y: event.clientY };
      this.pinchDistance = 0;
      this.dragging.set(true);
    } else if (this.activePointers.size === 2) {
      this.pinchDistance = this.pointerDistance();
    }

    if (this.cleanupFns.length === 0) {
      this.cleanupFns = [
        this.renderer.listen('document', 'pointermove', (e: PointerEvent) =>
          this.onDocumentPointerMove(e)
        ),
        this.renderer.listen('document', 'pointerup', (e: PointerEvent) =>
          this.onDocumentPointerUp(e)
        ),
        this.renderer.listen('document', 'pointercancel', (e: PointerEvent) =>
          this.onDocumentPointerUp(e)
        )
      ];
    }
  }

  ngOnDestroy(): void {
    this.releaseListeners();
  }

  ariaLabel(candidate: ParetoCandidateDTO): string {
    const status = candidate.frontier_status === 'FRONTIER'
      ? 'frontier'
      : candidate.frontier_status === 'DOMINATED'
        ? 'dominated'
        : 'serves fewer orders';
    return `${candidate.label}, cost ${compactVnd(candidate.total_cost)} VND, `
      + `CO₂ ${candidate.total_co2.toFixed(1)} kg, ${status}`;
  }

  formatCost(value: number): string {
    return formatVndAmount(value);
  }

  statusLabel(status: FrontierStatus): string {
    switch (status) {
      case 'FRONTIER': return 'Frontier';
      case 'DOMINATED': return 'Dominated';
      default: return 'Serves fewer';
    }
  }

  statusClass(status: FrontierStatus): string {
    return `status-chip status-${status.toLowerCase()}`;
  }

  reasonText(candidate: ParetoCandidateDTO): string | null {
    switch (candidate.not_applicable_reason) {
      case 'ALREADY_ACTIVE':
        return 'This is the active plan';
      case 'NOT_ON_FRONTIER':
        return candidate.frontier_status === 'SERVICE_DOMINATED'
          ? 'Serves fewer orders'
          : 'Dominated — another point is better on both cost and CO₂';
      case 'ORDER_SET_DIFFERS':
        return 'Covers a different set of orders than the active plan';
      case 'NO_PLAN':
        return 'No stored routes for this point';
      default:
        return null;
    }
  }

  apply(): void {
    const candidate = this.selected();
    if (!candidate || this.applying() || !candidate.applicable) return;

    const solutionId = this.solution().id;
    this.conflict.set(false);

    this.confirm.ask({
      title: `Apply plan ${candidate.label}?`,
      message: 'A new revision is created from this plan. The current plan stays in the '
        + 'revision history. Routes already sent to drivers must be sent again.',
      confirmText: 'Apply',
      cancelText: 'Cancel'
    }).subscribe(confirmed => {
      if (!confirmed) return;

      this.applying.set(true);
      this.api.applyParetoCandidate(solutionId, candidate.id).subscribe({
        next: saved => {
          this.applying.set(false);
          this.store.setSolution(saved);
          this.reload.emit();
          this.toast.success(`Plan ${candidate.label} applied (revision #${saved.id})`);
        },
        error: (error: unknown) => {
          this.applying.set(false);
          this.handleApplyError(error);
        }
      });
    });
  }

  loadLatest(): void {
    this.conflict.set(false);
    const solutionId = this.solution().id;
    this.api.latestRevisionId(solutionId).subscribe({
      next: ({ id }) => this.store.loadById(id).subscribe({ error: () => undefined }),
      error: () => this.toast.error('Could not load the latest revision.')
    });
  }

  private handleApplyError(error: unknown): void {
    const status = (error as { status?: number }).status;
    const first = (error as { error?: { errors?: { code?: string; message?: string }[] } })
      .error?.errors?.[0];

    if (status === 409) {
      this.conflict.set(true);
      return;
    }
    if (status === 400 && first?.code === '0040402') {
      this.toast.error('Plan not found');
      this.reload.emit();
      return;
    }
    if (status === 422) {
      this.toast.error(first?.message ?? 'Could not apply the plan.');
      return;
    }
    if (status === 400) {
      this.toast.error(first?.message ?? 'Could not apply the plan.');
      return;
    }
    this.toast.error('Could not apply the plan.');
  }

  private onDocumentPointerMove(event: PointerEvent): void {
    if (!this.activePointers.has(event.pointerId)) return;
    this.activePointers.set(event.pointerId, { x: event.clientX, y: event.clientY });

    if (this.activePointers.size >= 2) {
      this.handlePinch();
      return;
    }

    const start = this.dragStart;
    if (!start) return;

    const totalDx = event.clientX - start.x;
    const totalDy = event.clientY - start.y;
    if (!this.dragMoved && Math.hypot(totalDx, totalDy) < DRAG_THRESHOLD_PX) return;

    this.dragMoved = true;
    const last = this.lastPan ?? start;
    const dx = event.clientX - last.x;
    const dy = event.clientY - last.y;
    this.lastPan = { x: event.clientX, y: event.clientY };
    if (this.panByClient(dx, dy)) {
      event.preventDefault();
    }
  }

  private onDocumentPointerUp(event: PointerEvent): void {
    this.activePointers.delete(event.pointerId);

    if (this.activePointers.size === 1) {
      // One finger of a pinch lifted: rebase the drag so the remaining pointer
      // does not jump, and wait for a fresh movement to pan again.
      const remaining = [...this.activePointers.values()][0];
      this.dragStart = { ...remaining };
      this.lastPan = { ...remaining };
      this.pinchDistance = 0;
      return;
    }
    if (this.activePointers.size === 0) {
      this.endDrag();
    }
  }

  /** Two-pointer pinch: zoom about the midpoint by previous/current distance, no pan. */
  private handlePinch(): void {
    const points = [...this.activePointers.values()];
    if (points.length < 2) return;

    const distance = Math.hypot(points[0].x - points[1].x, points[0].y - points[1].y);
    const midX = (points[0].x + points[1].x) / 2;
    const midY = (points[0].y + points[1].y) / 2;
    const plot = this.toPlotFraction(midX, midY);

    if (plot && this.pinchDistance > 0 && distance > 0) {
      this.applyZoom(plot.fx, plot.fy, this.pinchDistance / distance);
    }
    this.pinchDistance = distance;
  }

  private applyZoom(fx: number, fy: number, factor: number): void {
    const full = this.fullWindow();
    const win = this.window();
    if (!full || !win) return;

    const next = zoomWindow(win, full, fx, fy, factor);
    this.zoom.set(isFullWindow(next, full) ? null : next);
  }

  private panByClient(dxClient: number, dyClient: number): boolean {
    const full = this.fullWindow();
    const win = this.window();
    const geometry = this.plotGeometry();
    if (!full || !win || !geometry) return false;

    const next = panWindow(
      win,
      full,
      dxClient / geometry.renderedW,
      dyClient / geometry.renderedH
    );
    this.zoom.set(isFullWindow(next, full) ? null : next);
    return true;
  }

  private endDrag(): void {
    this.activePointers.clear();
    this.dragStart = null;
    this.lastPan = null;
    this.pinchDistance = 0;
    this.dragging.set(false);
    this.releaseListeners();
  }

  private releaseListeners(): void {
    this.cleanupFns.forEach(cleanup => cleanup());
    this.cleanupFns = [];
  }

  private pointerDistance(): number {
    const points = [...this.activePointers.values()];
    if (points.length < 2) return 0;
    return Math.hypot(points[0].x - points[1].x, points[0].y - points[1].y);
  }

  private plotGeometry(): { renderedW: number; renderedH: number } | null {
    const ctm = this.screenCtm();
    if (!ctm || !ctm.a) return null;
    return { renderedW: PLOT_W * ctm.a, renderedH: PLOT_H * ctm.a };
  }

  /**
   * Client -> viewBox conversion via `getScreenCTM().inverse()`, then the
   * fraction inside the plot rectangle (`fx`: 0 at PLOT_LEFT; `fy`: 0 at the
   * plot bottom, growing upwards). Returns null when the pointer is outside the
   * plot rectangle or the SVG has no layout yet.
   */
  private toPlotFraction(clientX: number, clientY: number): { fx: number; fy: number } | null {
    const svg = this.chartSvg()?.nativeElement;
    const ctm = this.screenCtm();
    if (!svg || !ctm) return null;

    const point = svg.createSVGPoint();
    point.x = clientX;
    point.y = clientY;
    const local = point.matrixTransform(ctm.inverse());

    if (local.x < PLOT_LEFT || local.x > PLOT_LEFT + PLOT_W) return null;
    if (local.y < PLOT_TOP || local.y > PLOT_TOP + PLOT_H) return null;

    return {
      fx: clamp01((local.x - PLOT_LEFT) / PLOT_W),
      fy: clamp01((PLOT_TOP + PLOT_H - local.y) / PLOT_H)
    };
  }

  private screenCtm(): DOMMatrix | null {
    const svg = this.chartSvg()?.nativeElement;
    if (!svg || typeof svg.getScreenCTM !== 'function') return null;
    return svg.getScreenCTM();
  }
}

function paddedDomain(values: readonly number[]): [number, number] {
  const finite = values.filter(Number.isFinite);
  if (finite.length === 0) return [0, 1];

  const min = Math.min(...finite);
  const max = Math.max(...finite);
  if (max === min) {
    const delta = Math.abs(min) * 0.08 || 1;
    return [min - delta, max + delta];
  }
  const span = max - min;
  return [min - span * 0.08, max + span * 0.08];
}

function clampPct(value: number): number {
  if (!Number.isFinite(value)) return 0;
  return Math.max(0, Math.min(100, value));
}

function clamp01(value: number): number {
  if (!Number.isFinite(value)) return 0;
  return Math.max(0, Math.min(1, value));
}

function formatWeight(value: number): string {
  if (!Number.isFinite(value)) return '—';
  return value.toFixed(2);
}

function formatCo2Tick(value: number): string {
  if (!Number.isFinite(value)) return '—';
  return value >= 100 ? value.toFixed(0) : value.toFixed(1);
}

function direction(delta: number): 'up' | 'down' | 'flat' {
  if (delta > 0) return 'up';
  if (delta < 0) return 'down';
  return 'flat';
}

function percentDelta(value: number, base: number): DeltaVM {
  const pct = deltaPct(value, base);
  if (pct === null) return { text: 'n/a', direction: 'flat' };
  const sign = pct > 0 ? '+' : pct < 0 ? '−' : '';
  return { text: `${sign}${Math.abs(pct).toFixed(1)} %`, direction: direction(pct) };
}

function amountRow(
  label: string,
  value: number,
  base: number,
  unit: string,
  decimals = 0
): ComparisonRowVM {
  const delta = value - base;
  const pct = deltaPct(value, base);
  const sign = delta > 0 ? '+' : delta < 0 ? '−' : '';
  const amount = decimals > 0
    ? Math.abs(delta).toFixed(decimals)
    : formatVndAmount(Math.abs(delta));
  const pctText = pct === null ? '' : ` (${sign}${Math.abs(pct).toFixed(1)} %)`;
  return {
    label,
    deltaText: `${sign}${amount} ${unit}${pctText}`,
    direction: direction(delta)
  };
}

function intRow(label: string, value: number, base: number): ComparisonRowVM {
  const delta = value - base;
  const sign = delta > 0 ? '+' : delta < 0 ? '−' : '';
  return { label, deltaText: `${sign}${Math.abs(delta)}`, direction: direction(delta) };
}

function timeRow(label: string, value: number, base: number): ComparisonRowVM {
  const deltaMinutes = (value - base) * 60;
  const sign = deltaMinutes > 0 ? '+' : deltaMinutes < 0 ? '−' : '';
  const text = deltaMinutes === 0 ? '0m' : `${sign}${formatDuration(Math.abs(deltaMinutes))}`;
  return { label, deltaText: text, direction: direction(deltaMinutes) };
}
