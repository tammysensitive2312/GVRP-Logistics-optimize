import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gvrp_driver/features/map/route_map.dart';
import 'package:gvrp_driver/features/routes/route_models.dart';
import 'package:gvrp_driver/l10n/strings_vi.dart';

import '../../support/test_app.dart';

/// DT-08: server geometry is drawn solid; a leg without it is a dashed
/// straight line with the legend.
void main() {
  final geometryLeg0 = encodePolyline([
    [10.0, 106.0],
    [10.004, 106.008],
    [10.01, 106.01],
  ]);

  Map<String, Object?> route({String? leg1Geometry}) => routeJson(sendId: 12, stops: [
        depotJson(lat: 10.0, lon: 106.0, geometry: geometryLeg0),
        orderJson(seq: 1, lat: 10.01, lon: 106.01, geometry: leg1Geometry),
        orderJson(seq: 2, lat: 10.02, lon: 106.0),
      ]);

  test('DT-08 buildLegLines: leg 0 from the server, leg 1 straight fallback', () {
    final stops = DriverRoute.fromJson(route()).stops;
    final lines = buildLegLines(stops);

    expect(lines, hasLength(2));
    expect(lines[0].fromServer, isTrue);
    expect(lines[0].points, hasLength(3));
    expect(lines[0].points[1].latitude, closeTo(10.004, 1e-9));
    expect(lines[1].fromServer, isFalse);
    expect(lines[1].points, [stops[1].point, stops[2].point]);
  });

  test('DT-08 malformed geometry falls back to the straight line', () {
    final stops = DriverRoute.fromJson(route(leg1Geometry: '_p~iF~ps|U_')).stops;
    expect(buildLegLines(stops)[1].fromServer, isFalse);
  });

  testWidgets('DT-08 map draws one solid and one dashed polyline with the legend', (tester) async {
    final app = TestApp(store: FakeTokenStore(fakeToken));
    app.backend.json('GET', '/api/v1/driver/routes', [sendJson(id: 12)]);
    app.backend.json('GET', '/api/v1/driver/routes/12', route());
    await app.pump(tester);
    await openDetail(tester, app, 12);

    final polylines = tester.widget<PolylineLayer>(find.byType(PolylineLayer)).polylines;
    expect(polylines, hasLength(2));
    final solid = polylines.where((p) => p.pattern == const StrokePattern.solid()).toList();
    final dashed = polylines.where((p) => p.pattern != const StrokePattern.solid()).toList();
    expect(solid, hasLength(1));
    expect(dashed, hasLength(1));
    expect(solid.single.points, hasLength(3));
    expect(dashed.single.points, hasLength(2));
    // Leg 0 is the current leg: drawn wider (ui-design §8.3: 6 dp).
    expect(solid.single.strokeWidth, 6);

    expect(find.byKey(const Key('map.fallbackLegend')), findsOneWidget);
    expect(find.text(S.fallbackLegend), findsOneWidget);
  });

  testWidgets('DT-08 every leg from the server: no dashed line and no legend', (tester) async {
    final app = TestApp(store: FakeTokenStore(fakeToken));
    app.backend.json('GET', '/api/v1/driver/routes', [sendJson(id: 12)]);
    app.backend.json(
      'GET',
      '/api/v1/driver/routes/12',
      route(leg1Geometry: encodePolyline([
        [10.01, 106.01],
        [10.02, 106.0],
      ])),
    );
    await app.pump(tester);
    await openDetail(tester, app, 12);

    final polylines = tester.widget<PolylineLayer>(find.byType(PolylineLayer)).polylines;
    expect(polylines.every((p) => p.pattern == const StrokePattern.solid()), isTrue);
    expect(find.byKey(const Key('map.fallbackLegend')), findsNothing);
  });

  testWidgets('zoom buttons change the zoom by one level; fit shows all stops again', (tester) async {
    final app = TestApp(store: FakeTokenStore(fakeToken));
    app.backend.json('GET', '/api/v1/driver/routes', [sendJson(id: 12)]);
    app.backend.json('GET', '/api/v1/driver/routes/12', route());
    await app.pump(tester);
    await openDetail(tester, app, 12);
    MapCamera camera() => MapCamera.of(tester.element(find.byType(PolylineLayer)));

    final start = camera().zoom;
    await tester.tap(find.byKey(const Key('map.zoomIn')));
    await tester.pumpAndSettle();
    expect(camera().zoom, closeTo(start + 1, 1e-9));

    await tester.tap(find.byKey(const Key('map.zoomOut')));
    await tester.tap(find.byKey(const Key('map.zoomOut')));
    await tester.pumpAndSettle();
    expect(camera().zoom, closeTo(start - 1, 1e-9));

    await tester.tap(find.byKey(const Key('map.fitRoute')));
    await tester.pumpAndSettle();
    expect(camera().zoom, closeTo(start, 1e-6));
  });

  testWidgets('zoom stays within the map limits', (tester) async {
    final app = TestApp(store: FakeTokenStore(fakeToken));
    app.backend.json('GET', '/api/v1/driver/routes', [sendJson(id: 12)]);
    app.backend.json('GET', '/api/v1/driver/routes/12', route());
    await app.pump(tester);
    await openDetail(tester, app, 12);
    MapCamera camera() => MapCamera.of(tester.element(find.byType(PolylineLayer)));

    for (var i = 0; i < 25; i++) {
      await tester.tap(find.byKey(const Key('map.zoomIn')));
    }
    await tester.pumpAndSettle();
    expect(camera().zoom, maxMapZoom);
  });
}
