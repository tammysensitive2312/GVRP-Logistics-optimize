import {
  ChartWindow,
  clampWindow,
  fullWindow,
  isFullWindow,
  MAX_CHART_ZOOM,
  panWindow,
  windowSpan,
  zoomWindow
} from './pareto-chart.utils';

/** Z-T1..Z-T5: pure zoom/pan window mathematics (PARETO-003, design paragraph 8). */
describe('pareto-chart.utils zoom window', () => {
  const full: ChartWindow = { costMin: 0, costMax: 100, co2Min: 0, co2Max: 200 };

  const fractionX = (w: ChartWindow, value: number): number =>
    (value - w.costMin) / (w.costMax - w.costMin);
  const fractionY = (w: ChartWindow, value: number): number =>
    (value - w.co2Min) / (w.co2Max - w.co2Min);

  describe('fullWindow / windowSpan', () => {
    it('builds a window from the padded domains and reports its span', () => {
      const w = fullWindow([10, 20], [30, 50]);
      expect(w).toEqual({ costMin: 10, costMax: 20, co2Min: 30, co2Max: 50 });
      expect(windowSpan(w)).toEqual({ x: 10, y: 20 });
    });

    it('exposes the documented maximum magnification', () => {
      expect(MAX_CHART_ZOOM).toBe(8);
    });
  });

  describe('zoomWindow', () => {
    it('keeps the anchored data value under the pointer on both axes', () => {
      const next = zoomWindow(full, full, 0.25, 0.75, 0.5);

      expect(fractionX(next, 25)).toBeCloseTo(0.25, 9);
      expect(fractionY(next, 150)).toBeCloseTo(0.75, 9);
    });

    it('is the identity for factor 1', () => {
      const w: ChartWindow = { costMin: 20, costMax: 60, co2Min: 40, co2Max: 120 };
      const next = zoomWindow(w, full, 0.3, 0.7, 1);

      expect(next.costMin).toBeCloseTo(w.costMin, 9);
      expect(next.costMax).toBeCloseTo(w.costMax, 9);
      expect(next.co2Min).toBeCloseTo(w.co2Min, 9);
      expect(next.co2Max).toBeCloseTo(w.co2Max, 9);
    });

    it('clamps the span to the full window when zooming out', () => {
      const next = zoomWindow(full, full, 0.5, 0.5, 2);

      expect(windowSpan(next)).toEqual(windowSpan(full));
      expect(next).toEqual(full);
    });

    it('clamps the span to fullSpan / MAX_CHART_ZOOM when zooming in', () => {
      const next = zoomWindow(full, full, 0.5, 0.5, 1e-6);

      expect(windowSpan(next).x).toBeCloseTo(100 / MAX_CHART_ZOOM, 9);
      expect(windowSpan(next).y).toBeCloseTo(200 / MAX_CHART_ZOOM, 9);
    });

    it('keeps the window inside the full window when zooming out at an edge', () => {
      const w: ChartWindow = { costMin: 80, costMax: 100, co2Min: 0, co2Max: 40 };
      const next = zoomWindow(w, full, 0.5, 0.5, 2);

      expect(next.costMin).toBeGreaterThanOrEqual(full.costMin);
      expect(next.costMax).toBeLessThanOrEqual(full.costMax);
      expect(next.co2Min).toBeGreaterThanOrEqual(full.co2Min);
      expect(next.co2Max).toBeLessThanOrEqual(full.co2Max);
      expect(next.costMax).toBeCloseTo(100, 9);
    });

    it('falls back to the full window for a degenerate input', () => {
      const degenerate: ChartWindow = { costMin: 5, costMax: 5, co2Min: 1, co2Max: 1 };
      expect(zoomWindow(degenerate, full, 0.5, 0.5, 0.5)).toEqual(full);
    });

    it('does not mutate its inputs', () => {
      const w: ChartWindow = { costMin: 20, costMax: 60, co2Min: 40, co2Max: 120 };
      const before = { ...w };
      zoomWindow(w, full, 0.5, 0.5, 0.5);
      expect(w).toEqual(before);
    });
  });

  describe('panWindow', () => {
    it('moves the window towards higher cost for a positive dxFraction', () => {
      const w: ChartWindow = { costMin: 20, costMax: 60, co2Min: 0, co2Max: 100 };
      const next = panWindow(w, full, 0.25, 0); // span.x = 40 -> shift +10

      expect(next.costMin).toBeCloseTo(30, 9);
      expect(next.costMax).toBeCloseTo(70, 9);
    });

    it('flips the screen-down dyFraction into a data-up shift', () => {
      const w: ChartWindow = { costMin: 0, costMax: 100, co2Min: 0, co2Max: 100 };
      const next = panWindow(w, full, 0, -0.25); // drag up -> higher CO2

      expect(next.co2Min).toBeCloseTo(25, 9);
      expect(next.co2Max).toBeCloseTo(125, 9);
    });

    it('clamps the pan inside the full window', () => {
      const w: ChartWindow = { costMin: 0, costMax: 50, co2Min: 0, co2Max: 100 };

      const left = panWindow(w, full, -1, 0);
      expect(left.costMin).toBe(0);
      expect(left.costMax).toBe(50);

      const right = panWindow(w, full, 10, 0);
      expect(right.costMin).toBe(50);
      expect(right.costMax).toBe(100);
    });

    it('does not mutate its inputs', () => {
      const w: ChartWindow = { costMin: 20, costMax: 60, co2Min: 20, co2Max: 120 };
      const before = { ...w };
      panWindow(w, full, 0.1, 0.1);
      expect(w).toEqual(before);
    });
  });

  describe('clampWindow', () => {
    it('falls back to the full window for a degenerate or non-finite window', () => {
      expect(clampWindow({ costMin: 5, costMax: 5, co2Min: 1, co2Max: 1 }, full)).toEqual(full);
      expect(clampWindow({ costMin: NaN, costMax: 10, co2Min: 0, co2Max: 10 }, full)).toEqual(full);
    });

    it('keeps the window inside the full window', () => {
      const next = clampWindow({ costMin: -50, costMax: 50, co2Min: 150, co2Max: 250 }, full);

      expect(next.costMin).toBeGreaterThanOrEqual(full.costMin);
      expect(next.costMax).toBeLessThanOrEqual(full.costMax);
      expect(next.co2Min).toBeGreaterThanOrEqual(full.co2Min);
      expect(next.co2Max).toBeLessThanOrEqual(full.co2Max);
    });

    it('re-applies the maximum zoom span limit', () => {
      const next = clampWindow({ costMin: 45, costMax: 50, co2Min: 90, co2Max: 100 }, full);

      expect(windowSpan(next).x).toBeCloseTo(12.5, 9);
      expect(windowSpan(next).y).toBeCloseTo(25, 9);
    });
  });

  describe('isFullWindow', () => {
    it('is true only within the tolerance', () => {
      expect(isFullWindow(full, full)).toBeTrue();
      expect(isFullWindow({ ...full, costMin: full.costMin + 1e-12 }, full)).toBeTrue();
      expect(isFullWindow({ ...full, costMin: full.costMin + 1 }, full)).toBeFalse();
      expect(isFullWindow({ ...full, costMax: full.costMax - 1e-12 }, full, 1e-6)).toBeTrue();
      expect(isFullWindow({ ...full, costMax: full.costMax - 1e-3 }, full, 1e-6)).toBeFalse();
    });
  });
});
