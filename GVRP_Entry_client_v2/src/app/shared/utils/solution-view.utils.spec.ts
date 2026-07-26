import { RouteDTO, StopDTO } from '@core/models';

import {
  calculateTimeBounds,
  formatDuration,
  formatVndAmount,
  minutesToTime,
  orderStops,
  routeColor,
  ROUTE_COLORS,
  timeToMinutes
} from './solution-view.utils';

const stop = (partial: Partial<StopDTO>): StopDTO => ({
  type: 'ORDER',
  location_name: 'Stop',
  latitude: 0,
  longitude: 0,
  arrival_time: '08:00:00',
  load_after: 0,
  wait_time: 0,
  ...partial
});

const route = (partial: Partial<RouteDTO>): RouteDTO => ({
  vehicle_id: 1,
  vehicle_license_plate: '29A-12345',
  start_time: '08:00:00',
  end_time: '12:00:00',
  distance: 10,
  service_time: 1,
  order_count: 2,
  load_utilization: 50,
  stops: [],
  ...partial
});

describe('solution-view.utils', () => {
  describe('timeToMinutes', () => {
    it('converts HH:mm:ss including seconds', () => {
      expect(timeToMinutes('08:30:00')).toBe(510);
      expect(timeToMinutes('08:30:30')).toBe(510.5);
    });

    it('returns NaN for missing or malformed input', () => {
      expect(Number.isNaN(timeToMinutes(null))).toBeTrue();
      expect(Number.isNaN(timeToMinutes(''))).toBeTrue();
      expect(Number.isNaN(timeToMinutes('abc'))).toBeTrue();
    });
  });

  describe('minutesToTime', () => {
    it('pads hours and minutes', () => {
      expect(minutesToTime(510)).toBe('08:30');
      expect(minutesToTime(0)).toBe('00:00');
      expect(minutesToTime(1439)).toBe('23:59');
    });
  });

  describe('formatDuration', () => {
    it('matches V1 formatting', () => {
      expect(formatDuration(45)).toBe('45m');
      expect(formatDuration(135)).toBe('2h 15m');
      expect(formatDuration(120)).toBe('2h 0m');
    });

    it('degrades gracefully on non-finite input', () => {
      expect(formatDuration(Number.NaN)).toBe('—');
    });
  });

  describe('routeColor', () => {
    it('cycles through the V1 palette', () => {
      expect(routeColor(0)).toBe(ROUTE_COLORS[0]);
      expect(routeColor(ROUTE_COLORS.length)).toBe(ROUTE_COLORS[0]);
      expect(routeColor(ROUTE_COLORS.length + 2)).toBe(ROUTE_COLORS[2]);
    });
  });

  describe('orderStops', () => {
    it('moves the closing depot (no departure_time) to the end', () => {
      const stops = [
        stop({ type: 'DEPOT', location_name: 'Start', departure_time: '08:00:00' }),
        stop({ type: 'DEPOT', location_name: 'End', departure_time: undefined }),
        stop({ location_name: 'Order A', departure_time: '09:00:00' })
      ];

      const ordered = orderStops(stops);

      expect(ordered.map(s => s.location_name)).toEqual(['Start', 'Order A', 'End']);
    });

    it('leaves the list untouched when the closing depot is already last', () => {
      const stops = [
        stop({ location_name: 'Order A', departure_time: '09:00:00' }),
        stop({ type: 'DEPOT', location_name: 'End' })
      ];

      expect(orderStops(stops).map(s => s.location_name)).toEqual(['Order A', 'End']);
    });

    it('does not mutate the input array', () => {
      const stops = [
        stop({ type: 'DEPOT', location_name: 'End' }),
        stop({ location_name: 'Order A', departure_time: '09:00:00' })
      ];

      orderStops(stops);

      expect(stops.map(s => s.location_name)).toEqual(['End', 'Order A']);
    });
  });

  describe('calculateTimeBounds', () => {
    it('spans the earliest start and latest end', () => {
      const bounds = calculateTimeBounds([
        route({ start_time: '09:00:00', end_time: '11:00:00' }),
        route({ start_time: '08:15:00', end_time: '12:30:00' })
      ]);

      expect(bounds.startTime).toBe('08:15:00');
      expect(bounds.endTime).toBe('12:30:00');
      expect(bounds.durationMinutes).toBe(255);
    });

    it('falls back to 08:00-18:00 when times are missing', () => {
      const bounds = calculateTimeBounds([route({ start_time: '', end_time: '' })]);

      expect(bounds.startTime).toBe('08:00:00');
      expect(bounds.endTime).toBe('18:00:00');
    });

    it('widens windows shorter than 60 minutes', () => {
      const bounds = calculateTimeBounds([
        route({ start_time: '08:00:00', end_time: '08:20:00' })
      ]);

      expect(bounds.durationMinutes).toBe(60);
      expect(bounds.endTime).toBe('09:00');
    });

    it('handles an empty route list', () => {
      const bounds = calculateTimeBounds([]);

      expect(bounds.startTime).toBe('08:00:00');
      expect(bounds.durationMinutes).toBe(600);
    });
  });

  describe('formatVndAmount', () => {
    it('formats with vi-VN grouping', () => {
      expect(formatVndAmount(1234567)).toBe((1234567).toLocaleString('vi-VN'));
    });

    it('returns a dash for missing values', () => {
      expect(formatVndAmount(null)).toBe('—');
      expect(formatVndAmount(undefined)).toBe('—');
    });
  });
});
