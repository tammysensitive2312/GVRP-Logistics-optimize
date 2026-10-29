import { DriverStopDTO, RouteGeometryDTO } from '@core/models';

import {
  completeDriverLegs,
  completeRoutePolyline,
  driverInstructionSteps,
  storedLegPaths
} from './route-geometry.utils';

/** The published Google example: `(38.5,-120.2) → (40.7,-120.95) → (43.252,-126.453)`. */
const GOOGLE_EXAMPLE = '_p~iF~ps|U_ulLnnqC_mqNvxq`@';

const stop = (partial: Partial<DriverStopDTO>): DriverStopDTO => ({
  sequence_number: 1,
  type: 'ORDER',
  order_id: 1,
  location_name: 'Stop',
  address: null,
  latitude: 21,
  longitude: 105,
  arrival_time: '08:00:00',
  departure_time: null,
  customer_name: null,
  customer_phone: null,
  delivery_notes: null,
  demand: null,
  ...partial
});

/**
 * MP-T1 and DV-T1 decisions: the map source rules of LEZ-001 P1c.
 *
 * The decision is a pure function, so "no OSRM request" means exactly "the
 * function returned a path": `MapService` and the driver view only call
 * `RoutingService` when it returns null. A real Leaflet map needs a laid-out
 * container, so the container-free half of the rule is tested here.
 */
describe('route geometry source rule', () => {
  describe('planner path', () => {
    const geometry = (legs: RouteGeometryDTO['legs']): RouteGeometryDTO => ({
      route_id: 11,
      legs
    });

    it('returns the stored path when every leg has a polyline', () => {
      const path = completeRoutePolyline(geometry([
        { from_seq: 0, to_seq: 1, polyline: GOOGLE_EXAMPLE },
        { from_seq: 1, to_seq: 2, polyline: GOOGLE_EXAMPLE }
      ]));

      expect(path).not.toBeNull();
      expect(path!.length).toBe(5);
      expect(path![0][0]).toBeCloseTo(38.5, 5);
      expect(path![0][1]).toBeCloseTo(-120.2, 5);
      expect(path![path!.length - 1][0]).toBeCloseTo(43.252, 5);
    });

    it('returns null as soon as one leg is not routed, so OSRM is used', () => {
      const path = completeRoutePolyline(geometry([
        { from_seq: 0, to_seq: 1, polyline: GOOGLE_EXAMPLE },
        { from_seq: 1, to_seq: 2, polyline: null }
      ]));

      expect(path).toBeNull();
    });

    it('returns null when the legs arrive out of order and one is missing', () => {
      const path = completeRoutePolyline(geometry([
        { from_seq: 1, to_seq: 2, polyline: null },
        { from_seq: 0, to_seq: 1, polyline: GOOGLE_EXAMPLE }
      ]));

      expect(path).toBeNull();
    });

    it('sorts the legs by plan index before joining them', () => {
      const path = completeRoutePolyline(geometry([
        { from_seq: 1, to_seq: 2, polyline: GOOGLE_EXAMPLE },
        { from_seq: 0, to_seq: 1, polyline: GOOGLE_EXAMPLE }
      ]));

      expect(path).not.toBeNull();
      // Both legs start at the same published point, so the joined path is
      // strictly ordered and the shared vertex is not duplicated.
      expect(path!.length).toBe(5);
    });

    it('returns null for an empty, missing or invalid geometry', () => {
      expect(completeRoutePolyline(null)).toBeNull();
      expect(completeRoutePolyline(undefined)).toBeNull();
      expect(completeRoutePolyline(geometry([]))).toBeNull();
      expect(completeRoutePolyline(geometry([
        { from_seq: 0, to_seq: 1, polyline: '' }
      ]))).toBeNull();
    });

    // FR3-T1 (review-p1c R3): the highlight needs one path per leg, in order.
    it('returns one decoded path per leg in plan order', () => {
      const paths = storedLegPaths(geometry([
        { from_seq: 2, to_seq: 3, polyline: GOOGLE_EXAMPLE },
        { from_seq: 0, to_seq: 1, polyline: '_p~iF~ps|U_ulLnnqC' },
        { from_seq: 1, to_seq: 2, polyline: GOOGLE_EXAMPLE }
      ]));

      expect(paths).not.toBeNull();
      expect(paths!.length).toBe(3);
      // Leg 0 is the two-point prefix, so the returned order is from_seq order
      // and the legs are not joined into one path.
      expect(paths![0].length).toBe(2);
      expect(paths![1].length).toBe(3);
      expect(paths![0][0][0]).toBeCloseTo(38.5, 5);
    });

    it('returns null for the highlight when one leg is not routed', () => {
      expect(storedLegPaths(geometry([
        { from_seq: 0, to_seq: 1, polyline: GOOGLE_EXAMPLE },
        { from_seq: 1, to_seq: 2, polyline: null },
        { from_seq: 2, to_seq: 3, polyline: GOOGLE_EXAMPLE }
      ]))).toBeNull();
      expect(storedLegPaths(null)).toBeNull();
      expect(storedLegPaths(geometry([]))).toBeNull();
    });
  });

  describe('driver path', () => {
    const stops = (): DriverStopDTO[] => [
      stop({ sequence_number: 1, type: 'DEPOT', order_id: null, geometry_to_next: GOOGLE_EXAMPLE }),
      stop({ sequence_number: 2, geometry_to_next: GOOGLE_EXAMPLE }),
      stop({ sequence_number: 3, type: 'DEPOT', order_id: null })
    ];

    it('returns one path per leg when every leg has a polyline', () => {
      const legs = completeDriverLegs(stops());

      expect(legs).not.toBeNull();
      expect(legs!.length).toBe(2);
      expect(legs![0].length).toBe(3);
    });

    it('returns null when a leg has no polyline, so OSRM is used', () => {
      const data = stops();
      data[1].geometry_to_next = null;

      expect(completeDriverLegs(data)).toBeNull();
    });

    it('returns null with fewer than two stops', () => {
      expect(completeDriverLegs([])).toBeNull();
      expect(completeDriverLegs([stop({})])).toBeNull();
    });

    it('uses the stored instructions when geometry and instructions are complete', () => {
      const data = stops();
      data[0].instructions_to_next = [
        { text: 'Rẽ trái vào phố Huế', distance_m: 320 },
        { text: 'Đi thẳng', distance_m: 0 }
      ];
      data[1].instructions_to_next = [{ text: 'Đến nơi', distance_m: 40 }];

      const steps = driverInstructionSteps(data);

      expect(steps).not.toBeNull();
      // The same `distance > 0` filter as today's OSRM path.
      expect(steps!.map(step => step.instruction))
        .toEqual(['Rẽ trái vào phố Huế', 'Đến nơi']);
    });

    it('returns null when no leg carries an instruction, keeping the OSRM list', () => {
      expect(driverInstructionSteps(stops())).toBeNull();
    });

    it('returns null when the geometry is incomplete even with instructions', () => {
      const data = stops();
      data[0].geometry_to_next = null;
      data[0].instructions_to_next = [{ text: 'Rẽ trái', distance_m: 320 }];

      expect(driverInstructionSteps(data)).toBeNull();
    });
  });
});
