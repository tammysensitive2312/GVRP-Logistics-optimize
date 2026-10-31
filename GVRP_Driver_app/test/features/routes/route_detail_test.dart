import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gvrp_driver/features/map/route_map.dart';
import 'package:gvrp_driver/l10n/strings_vi.dart';
import 'package:gvrp_driver/theme.dart';

import '../../support/test_app.dart';

/// Opens the detail of send 12 whose body is [route].
Future<TestApp> openRoute(WidgetTester tester, Map<String, Object?> route, {String status = 'CURRENT'}) async {
  final app = TestApp(store: FakeTokenStore(fakeToken));
  app.backend.json('GET', '/api/v1/driver/routes', [sendJson(id: 12, status: status)]);
  app.backend.json('GET', '/api/v1/driver/routes/12', route);
  await app.pump(tester);
  await openDetail(tester, app, 12);
  return app;
}

List<Map<String, Object?>> threeStops({List<Map<String, Object?>>? depotInstructions, List<String>? zones}) => [
      depotJson(lat: 10.0, lon: 106.0)..['instructions_to_next'] = depotInstructions,
      orderJson(seq: 1, lat: 10.01, lon: 106.01, zones: zones),
      orderJson(seq: 2, lat: 10.02, lon: 106.0, phone: '0900000002'),
      depotJson(lat: 10.0, lon: 106.0),
    ];

void main() {
  testWidgets('DT-07 a SUPERSEDED send shows the red banner and hides guidance', (tester) async {
    await openRoute(
      tester,
      routeJson(
        sendId: 12,
        status: 'SUPERSEDED',
        stops: threeStops(depotInstructions: [instructionJson('Rẽ phải vào phố A')]),
      ),
      status: 'SUPERSEDED',
    );

    expect(find.byKey(const Key('detail.supersededBanner')), findsOneWidget);
    expect(find.text(S.supersededBanner), findsOneWidget);
    expect(find.byKey(const Key('detail.guidance')), findsNothing);
    expect(find.text('Rẽ phải vào phố A'), findsNothing);
    // Superseded mode hides the leg buttons (ui-design §4.4).
    expect(find.byKey(const Key('detail.arrived')), findsNothing);
    expect(find.byKey(const Key('detail.back')), findsNothing);
    // The map and the next-stop card remain.
    expect(find.byType(RouteMap), findsOneWidget);
    expect(find.byKey(const Key('detail.nextStop')), findsOneWidget);
  });

  testWidgets('DT-09 guidance shows leg 0 steps, then the no-guidance message on leg 1', (tester) async {
    await openRoute(
      tester,
      routeJson(
        sendId: 12,
        stops: threeStops(depotInstructions: [
          instructionJson('Rẽ phải vào phố A', distance: 350),
          instructionJson('Đi thẳng 1,2 km', distance: 1200, street: 'đường B'),
        ]),
      ),
    );

    final guidance = find.byKey(const Key('detail.guidance'));
    expect(guidance, findsOneWidget);
    expect(find.descendant(of: guidance, matching: find.text('Rẽ phải vào phố A')), findsOneWidget);
    expect(find.descendant(of: guidance, matching: find.text(S.stepCounter(1, 2))), findsOneWidget);
    expect(find.descendant(of: guidance, matching: find.text('350 m · phố A')), findsOneWidget);
    expect(find.text('${S.nextStop} · 1/3'.toUpperCase()), findsOneWidget);

    // "Xem tất cả bước" lists every step of the leg.
    await tapInSheet(tester, find.byKey(const Key('guidance.allSteps')));
    final sheet = find.byKey(const Key('guidance.stepsSheet'));
    expect(find.descendant(of: sheet, matching: find.text('Đi thẳng 1,2 km')), findsOneWidget);
    expect(find.descendant(of: sheet, matching: find.text('1.2 km · đường B')), findsOneWidget);
    await tester.tapAt(const Offset(200, 40));
    await tester.pumpAndSettle();

    await tapInSheet(tester, find.byKey(const Key('detail.arrived')));
    await scrollSheetToTop(tester);

    expect(find.descendant(of: guidance, matching: find.text(S.noGuidance)), findsOneWidget);
    expect(find.text('Rẽ phải vào phố A'), findsNothing);
    expect(find.text('${S.nextStop} · 2/3'.toUpperCase()), findsOneWidget);

    // "Quay lại" returns to leg 0.
    await tapInSheet(tester, find.byKey(const Key('detail.back')));
    await scrollSheetToTop(tester);
    expect(find.text('Rẽ phải vào phố A'), findsOneWidget);
  });

  testWidgets('leg progression ends at the last stop and Quay lại is disabled at leg 0', (tester) async {
    await openRoute(tester, routeJson(sendId: 12, stops: threeStops()));
    OutlinedButton back() => tester.widget<OutlinedButton>(find.byKey(const Key('detail.back')));

    expect(back().onPressed, isNull);
    for (var i = 0; i < 3; i++) {
      await tapInSheet(tester, find.byKey(const Key('detail.arrived')));
    }
    expect(find.text(S.routeFinished), findsOneWidget);
    expect(find.byKey(const Key('detail.arrived')), findsNothing);
    expect(find.byKey(const Key('detail.guidance')), findsNothing);
    expect(back().onPressed, isNotNull);
  });

  testWidgets('DT-10 a stop with zone_names shows the zone banner and a marked stop', (tester) async {
    await openRoute(tester, routeJson(sendId: 12, stops: threeStops(zones: ['Vùng X'])));

    expect(find.byKey(const Key('detail.zoneBanner')), findsOneWidget);
    expect(find.text(S.zoneBanner), findsOneWidget);

    final zoned = tester.widget<StopMarker>(find.byKey(const Key('map.stop.1')));
    final plain = tester.widget<StopMarker>(find.byKey(const Key('map.stop.2')));
    expect(zoned.hasZone, isTrue);
    expect(plain.hasZone, isFalse);
    expect(_markerBorderColor(tester, 'map.stop.1'), AppColors.zoneRing);
    expect(_markerBorderColor(tester, 'map.stop.2'), isNot(AppColors.zoneRing));
    // Next stop is stop 1: its card carries the zone chip.
    expect(
      find.descendant(of: find.byKey(const Key('detail.nextStop')), matching: find.text(S.zoneChip('Vùng X'))),
      findsOneWidget,
    );
  });

  testWidgets('DT-10 no zone_names: no zone banner', (tester) async {
    await openRoute(tester, routeJson(sendId: 12, stops: threeStops()));
    expect(find.byKey(const Key('detail.zoneBanner')), findsNothing);
  });

  testWidgets('DT-11 tapping the phone launches tel: only', (tester) async {
    final app = await openRoute(tester, routeJson(sendId: 12, stops: threeStops()));

    await tester.tap(find.byKey(const Key('detail.phone')));
    await tester.pumpAndSettle();

    expect(app.launched, [Uri(scheme: 'tel', path: '0900000001')]);
    expect(app.launched.every((u) => u.scheme == 'tel'), isTrue);
  });

  testWidgets('403 shows the forbidden message and Quay lại returns to the list', (tester) async {
    final app = TestApp(store: FakeTokenStore(fakeToken));
    app.backend.json('GET', '/api/v1/driver/routes', [sendJson(id: 12)]);
    app.backend.json('GET', '/api/v1/driver/routes/12', errorBody('0040301'), status: 403);
    await app.pump(tester);
    await openDetail(tester, app, 12);

    expect(find.text(S.forbidden), findsOneWidget);
    await tester.tap(find.text(S.back));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('routes.row.12')), findsOneWidget);
  });

  testWidgets('400 RESOURCE_NOT_FOUND shows the not-found message', (tester) async {
    final app = TestApp(store: FakeTokenStore(fakeToken));
    app.backend.json('GET', '/api/v1/driver/routes', [sendJson(id: 12)]);
    app.backend.json('GET', '/api/v1/driver/routes/12', errorBody('0040402'), status: 400);
    await app.pump(tester);
    await openDetail(tester, app, 12);

    expect(find.text(S.notFound), findsOneWidget);
  });

  testWidgets('follow-me shows the explanation first; a denial keeps the map working', (tester) async {
    final app = await openRoute(tester, routeJson(sendId: 12, stops: threeStops()));

    await tester.tap(find.byKey(const Key('map.followMe')));
    await tester.pumpAndSettle();
    expect(find.text(S.locationExplain), findsOneWidget);
    expect(app.location.requests, 0);

    await tester.tap(find.text(S.continueLabel));
    await tester.pumpAndSettle();
    expect(app.location.requests, 1);
    expect(find.text(S.locationDenied), findsOneWidget);
    expect(find.byKey(const Key('map.myPosition')), findsNothing);
    expect(find.byType(RouteMap), findsOneWidget);
  });
}

Color? _markerBorderColor(WidgetTester tester, String key) {
  final container = tester
      .widgetList<Container>(find.descendant(of: find.byKey(Key(key)), matching: find.byType(Container)))
      .firstWhere((c) => c.decoration is BoxDecoration && (c.decoration! as BoxDecoration).border != null);
  return ((container.decoration! as BoxDecoration).border! as Border).top.color;
}
