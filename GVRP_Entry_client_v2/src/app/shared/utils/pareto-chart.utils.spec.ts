import {
  ChartPointInput,
  compactVnd,
  deltaPct,
  frontierPath,
  niceTicks,
  scaleLinear
} from './pareto-chart.utils';

/** U-T1: pure chart helpers. */
describe('pareto-chart.utils', () => {
  describe('niceTicks', () => {
    it('produces nice ticks covering the range', () => {
      expect(niceTicks(0, 100, 4)).toEqual([0, 25, 50, 75, 100]);
    });

    it('returns a single tick for a zero-width range', () => {
      expect(niceTicks(5, 5)).toEqual([5]);
    });

    it('returns no ticks for non-finite bounds', () => {
      expect(niceTicks(Number.NaN, 10)).toEqual([]);
    });
  });

  describe('scaleLinear', () => {
    it('maps the domain linearly onto the range', () => {
      const scale = scaleLinear([0, 10], [0, 100]);
      expect(scale(0)).toBe(0);
      expect(scale(5)).toBe(50);
      expect(scale(10)).toBe(100);
    });

    it('maps a zero-width domain to the range midpoint', () => {
      const scale = scaleLinear([7, 7], [0, 100]);
      expect(scale(7)).toBe(50);
      expect(scale(999)).toBe(50);
    });
  });

  describe('frontierPath', () => {
    it('joins FRONTIER points in ascending cost order', () => {
      const points: ChartPointInput[] = [
        { total_cost: 300, total_co2: 1, frontier_status: 'FRONTIER', x: 30, y: 1 },
        { total_cost: 100, total_co2: 3, frontier_status: 'FRONTIER', x: 10, y: 3 },
        { total_cost: 200, total_co2: 2, frontier_status: 'DOMINATED', x: 20, y: 2 }
      ];

      expect(frontierPath(points)).toBe('M 10 3 L 30 1');
    });

    it('returns an empty string without frontier points', () => {
      expect(frontierPath([])).toBe('');
    });
  });

  describe('compactVnd', () => {
    it('formats millions and thousands for axis labels', () => {
      expect(compactVnd(1_234_567)).toBe('1.23M');
      expect(compactVnd(845_000)).toBe('845K');
    });
  });

  describe('deltaPct', () => {
    it('returns null when the base is zero', () => {
      expect(deltaPct(100, 0)).toBeNull();
    });

    it('returns the percentage change otherwise', () => {
      expect(deltaPct(110, 100)).toBeCloseTo(10, 6);
      expect(deltaPct(88.7, 100)).toBeCloseTo(-11.3, 6);
    });
  });
});
