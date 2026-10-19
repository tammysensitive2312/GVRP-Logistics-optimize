import { FrontierStatus } from '@core/models';

/**
 * Pure helpers for the inline-SVG Pareto trade-off chart (PARETO-002).
 * No Angular or DOM dependency so they are unit-testable in isolation.
 */

/** Point on the chart: SVG coordinates plus the data used for ordering/labels. */
export interface ChartPointInput {
  total_cost: number;
  total_co2: number;
  frontier_status: FrontierStatus;
  x: number;
  y: number;
}

/**
 * "Nice" axis ticks covering [min, max] with roughly `count` steps.
 * Returns [min] for a zero-width or non-finite range.
 */
export function niceTicks(min: number, max: number, count = 4): number[] {
  if (!Number.isFinite(min) || !Number.isFinite(max) || max <= min) {
    return Number.isFinite(min) ? [min] : [];
  }

  const step = niceStep((max - min) / count);
  const start = Math.ceil(min / step) * step;
  const ticks: number[] = [];
  // Guard the loop against floating point drift with a relative epsilon.
  for (let value = start; value <= max + step * 1e-9; value += step) {
    ticks.push(roundToStep(value, step));
  }
  return ticks;
}

/** Domain -> range linear scale. A zero-width domain maps everything to the range midpoint. */
export function scaleLinear(
  domain: [number, number],
  range: [number, number]
): (value: number) => number {
  const [d0, d1] = domain;
  const [r0, r1] = range;

  if (d1 === d0) {
    const mid = (r0 + r1) / 2;
    return () => mid;
  }

  return (value: number) => r0 + ((value - d0) / (d1 - d0)) * (r1 - r0);
}

/**
 * SVG `d` string joining the FRONTIER points in ascending cost order.
 * Returns '' when there is no frontier point.
 */
export function frontierPath(points: readonly ChartPointInput[]): string {
  const frontier = points
    .filter(point => point.frontier_status === 'FRONTIER')
    .sort((a, b) => a.total_cost - b.total_cost);

  return frontier
    .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`)
    .join(' ');
}

/** Compact VND label for axis ticks: 1 234 567 -> "1.23M", 845 000 -> "845K". */
export function compactVnd(value: number): string {
  if (!Number.isFinite(value)) return '—';

  const abs = Math.abs(value);
  const sign = value < 0 ? '-' : '';
  if (abs >= 1_000_000) return `${sign}${(abs / 1_000_000).toFixed(2)}M`;
  if (abs >= 1_000) return `${sign}${Math.round(abs / 1_000)}K`;
  return `${sign}${Math.round(abs)}`;
}

/** Percentage change of `value` relative to `base`; null when `base` is 0. */
export function deltaPct(value: number, base: number): number | null {
  if (!Number.isFinite(value) || !Number.isFinite(base) || base === 0) return null;
  return ((value - base) / base) * 100;
}

/**
 * Visible data window of the Pareto chart (PARETO-003). The SVG geometry is
 * unchanged; zooming changes which `[costMin, costMax] x [co2Min, co2Max]`
 * sub-range the existing `scaleLinear` / `niceTicks` helpers map onto the plot.
 */
export interface ChartWindow {
  costMin: number;
  costMax: number;
  co2Min: number;
  co2Max: number;
}

/** Maximum magnification of the visible data window. */
export const MAX_CHART_ZOOM = 8;

/** Build the default (unzoomed) window from the padded x/y domains. */
export function fullWindow(x: [number, number], y: [number, number]): ChartWindow {
  return { costMin: x[0], costMax: x[1], co2Min: y[0], co2Max: y[1] };
}

/** Span of a window on each axis (always a new object, never negative for a valid window). */
export function windowSpan(w: ChartWindow): { x: number; y: number } {
  return { x: w.costMax - w.costMin, y: w.co2Max - w.co2Min };
}

/**
 * True when `w` covers the same range as `full` within an absolute `eps`
 * tolerance on each bound.
 */
export function isFullWindow(w: ChartWindow, full: ChartWindow, eps = 1e-9): boolean {
  return Math.abs(w.costMin - full.costMin) <= eps
    && Math.abs(w.costMax - full.costMax) <= eps
    && Math.abs(w.co2Min - full.co2Min) <= eps
    && Math.abs(w.co2Max - full.co2Max) <= eps;
}

/**
 * Zoom the window about the point `(fx, fy)` (fractions inside the plot
 * rectangle, `fy` growing upwards) by `factor`: `factor < 1` zooms in,
 * `factor > 1` zooms out. The anchored data value stays under the pointer.
 * Spans are clamped to `[fullSpan / maxZoom, fullSpan]` and the result is kept
 * inside `full`. Returns a new object; a degenerate/non-finite input falls back
 * to `full`.
 */
export function zoomWindow(
  w: ChartWindow,
  full: ChartWindow,
  fx: number,
  fy: number,
  factor: number,
  maxZoom = MAX_CHART_ZOOM
): ChartWindow {
  if (!isFiniteWindow(w) || !isFiniteWindow(full) || !Number.isFinite(factor)) return full;

  const fullSpan = windowSpan(full);
  const span = windowSpan(w);
  if (!(span.x > 0) || !(span.y > 0)) return full;

  const nextSpanX = clampSpan(span.x * factor, fullSpan.x, maxZoom);
  const nextSpanY = clampSpan(span.y * factor, fullSpan.y, maxZoom);

  const anchorFx = clampFraction(fx);
  const anchorFy = clampFraction(fy);
  const anchorX = w.costMin + anchorFx * span.x;
  const anchorY = w.co2Min + anchorFy * span.y;

  const next: ChartWindow = {
    costMin: anchorX - anchorFx * nextSpanX,
    costMax: anchorX - anchorFx * nextSpanX + nextSpanX,
    co2Min: anchorY - anchorFy * nextSpanY,
    co2Max: anchorY - anchorFy * nextSpanY + nextSpanY
  };
  return clampWindow(next, full, maxZoom);
}

/**
 * Pan the window by a fraction of its **current** span. Positive `dxFraction`
 * moves the window towards higher cost (data increases); `dyFraction` is given
 * in screen-down units and is flipped internally, so dragging the plot up shows
 * higher CO2. The result is clamped inside `full`.
 */
export function panWindow(
  w: ChartWindow,
  full: ChartWindow,
  dxFraction: number,
  dyFraction: number
): ChartWindow {
  if (!isFiniteWindow(w) || !isFiniteWindow(full)) return full;

  const span = windowSpan(w);
  const dx = Number.isFinite(dxFraction) ? dxFraction : 0;
  const dy = Number.isFinite(dyFraction) ? dyFraction : 0;

  const next: ChartWindow = {
    costMin: w.costMin + dx * span.x,
    costMax: w.costMax + dx * span.x,
    co2Min: w.co2Min - dy * span.y,
    co2Max: w.co2Max - dy * span.y
  };
  return clampWindow(next, full);
}

/**
 * Clamp `w` inside `full` and re-apply the span limits. A zero-span or
 * non-finite window returns `full` instead of throwing. Returns a new object.
 */
export function clampWindow(
  w: ChartWindow,
  full: ChartWindow,
  maxZoom = MAX_CHART_ZOOM
): ChartWindow {
  if (!isFiniteWindow(w) || !isFiniteWindow(full)) return full;

  const fullSpan = windowSpan(full);
  if (!(fullSpan.x > 0) || !(fullSpan.y > 0)) return full;

  const span = windowSpan(w);
  if (!(span.x > 0) || !(span.y > 0)) return full;

  const width = clampSpan(span.x, fullSpan.x, maxZoom);
  const height = clampSpan(span.y, fullSpan.y, maxZoom);

  let costMin = (w.costMin + w.costMax) / 2 - width / 2;
  let co2Min = (w.co2Min + w.co2Max) / 2 - height / 2;

  if (costMin < full.costMin) costMin = full.costMin;
  if (costMin + width > full.costMax) costMin = full.costMax - width;
  if (co2Min < full.co2Min) co2Min = full.co2Min;
  if (co2Min + height > full.co2Max) co2Min = full.co2Max - height;

  return { costMin, costMax: costMin + width, co2Min, co2Max: co2Min + height };
}

function isFiniteWindow(w: ChartWindow): boolean {
  return Number.isFinite(w.costMin) && Number.isFinite(w.costMax)
    && Number.isFinite(w.co2Min) && Number.isFinite(w.co2Max);
}

function clampFraction(value: number): number {
  if (!Number.isFinite(value)) return 0;
  return Math.max(0, Math.min(1, value));
}

/** Clamp a span to `[fullSpan / maxZoom, fullSpan]`; invalid spans become the full span. */
function clampSpan(span: number, fullSpan: number, maxZoom: number): number {
  if (!Number.isFinite(fullSpan) || fullSpan <= 0) return fullSpan;
  const zoom = Number.isFinite(maxZoom) && maxZoom >= 1 ? maxZoom : MAX_CHART_ZOOM;
  if (!Number.isFinite(span) || span <= 0) return fullSpan;
  return Math.min(Math.max(span, fullSpan / zoom), fullSpan);
}

function niceStep(raw: number): number {
  const exponent = Math.floor(Math.log10(raw));
  const magnitude = Math.pow(10, exponent);
  const normalized = raw / magnitude;

  let nice: number;
  if (normalized <= 1) nice = 1;
  else if (normalized <= 2) nice = 2;
  else if (normalized <= 2.5) nice = 2.5;
  else if (normalized <= 5) nice = 5;
  else nice = 10;

  return nice * magnitude;
}

/** Snap a tick to the step grid so accumulated float error does not leak. */
function roundToStep(value: number, step: number): number {
  const decimals = Math.max(0, -Math.floor(Math.log10(step)) + 1);
  return Number(value.toFixed(Math.min(12, decimals)));
}
