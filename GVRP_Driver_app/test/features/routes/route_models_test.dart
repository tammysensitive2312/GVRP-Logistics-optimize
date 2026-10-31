import 'package:flutter_test/flutter_test.dart';
import 'package:gvrp_driver/features/routes/route_models.dart';

import '../../support/test_app.dart';

/// DT-02: models parse the API JSON with and without the LEZ-001 P1b fields.
void main() {
  test('DT-02 parses a route without P1b fields: optional fields are empty', () {
    final route = DriverRoute.fromJson(routeJson(sendId: 7, stops: [
      depotJson(lat: 10.0, lon: 106.0),
      orderJson(seq: 1, lat: 10.01, lon: 106.01),
      depotJson(lat: 10.0, lon: 106.0),
    ]));

    expect(route.sendId, 7);
    expect(route.status, SendStatus.current);
    expect(route.stops, hasLength(3));
    expect(route.stops.first.isDepot, isTrue);
    expect(route.stops.first.sequenceNumber, isNull);

    final order = route.stops[1];
    expect(order.type, StopType.order);
    expect(order.sequenceNumber, 1);
    expect(order.orderId, 101);
    expect(order.latitude, 10.01);
    expect(order.longitude, 106.01);
    expect(order.arrivalTime, '09:30:00');
    expect(order.customerPhone, '0900000001');
    expect(order.demand, 12.5);
    expect(order.geometryToNext, isNull);
    expect(order.decodedGeometryToNext, isEmpty);
    expect(order.instructionsToNext, isEmpty);
    expect(order.zoneNames, isEmpty);
    expect(route.hasZones, isFalse);
  });

  test('DT-02 parses a route with P1b fields populated', () {
    final geometry = encodePolyline([
      [10.0, 106.0],
      [10.005, 106.004],
      [10.01, 106.01],
    ]);
    final route = DriverRoute.fromJson(routeJson(sendId: 8, status: 'SUPERSEDED', stops: [
      depotJson(lat: 10.0, lon: 106.0, geometry: geometry),
      orderJson(
        seq: 1,
        lat: 10.01,
        lon: 106.01,
        instructions: [instructionJson('Rẽ phải vào phố A')],
        zones: ['Vùng X'],
      ),
    ]));

    expect(route.isSuperseded, isTrue);
    expect(route.hasZones, isTrue);
    final depot = route.stops.first;
    expect(depot.geometryToNext, geometry);
    expect(depot.decodedGeometryToNext, hasLength(3));
    expect(depot.decodedGeometryToNext.last.latitude, closeTo(10.01, 1e-9));
    expect(depot.decodedGeometryToNext.last.longitude, closeTo(106.01, 1e-9));

    final step = route.stops[1].instructionsToNext.single;
    expect(step.text, 'Rẽ phải vào phố A');
    expect(step.distanceM, 350);
    expect(step.timeMs, 42000);
    expect(step.sign, 2);
    expect(step.streetName, 'phố A');
    expect(route.stops[1].zoneNames, ['Vùng X']);
  });

  test('DT-02 ignores unknown fields and tolerates nulls in P1b fields', () {
    final json = orderJson(seq: 2, lat: 10.0, lon: 106.0)
      ..['some_future_field'] = {'x': 1}
      ..['geometry_to_next'] = null
      ..['instructions_to_next'] = null
      ..['zone_names'] = null;
    final stop = DriverStop.fromJson(json);
    expect(stop.geometryToNext, isNull);
    expect(stop.instructionsToNext, isEmpty);
    expect(stop.zoneNames, isEmpty);
  });

  test('DT-02 parses a send list item', () {
    final send = RouteSend.fromJson(sendJson(id: 12, status: 'SUPERSEDED'));
    expect(send.id, 12);
    expect(send.vehicleLicensePlate, '29X-000.00');
    expect(send.senderName, 'Điều phối A');
    expect(send.isSuperseded, isTrue);
    expect(formatSentAt(send.sentAt), '27/10/2026 08:05');
  });

  test('DT-02 rejects a stop without coordinates', () {
    final json = orderJson(seq: 1, lat: 10.0, lon: 106.0)..remove('latitude');
    expect(() => DriverStop.fromJson(json), throwsA(isA<ModelFormatException>()));
  });

  test('formatters keep server wall-clock time and use m / km', () {
    expect(formatSentAt('2026-10-27T23:59:59.123456'), '27/10/2026 23:59');
    expect(formatClock('09:30:00'), '09:30');
    expect(formatDistance(350), '350 m');
    expect(formatDistance(1250), '1.3 km');
  });
}
