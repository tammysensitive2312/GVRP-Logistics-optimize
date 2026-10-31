import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gvrp_driver/l10n/strings_vi.dart';

import '../support/test_app.dart';

/// ui-design §3: layouts must not overflow at text scale 1.3 on a small
/// phone (360 x 780 dp). A RenderFlex overflow fails the test.
void main() {
  const small = Size(360, 780);

  setUp(() {
    TestWidgetsFlutterBinding.instance.platformDispatcher.textScaleFactorTestValue = 1.3;
  });
  tearDown(() {
    TestWidgetsFlutterBinding.instance.platformDispatcher.clearTextScaleFactorTestValue();
  });

  testWidgets('login with the expiry banner fits at text scale 1.3', (tester) async {
    final app = TestApp(store: FakeTokenStore(fakeToken));
    app.backend.json('GET', '/api/v1/driver/routes', {'status': 401}, status: 401);
    await app.pump(tester, logicalSize: small);
    expect(find.text(S.sessionExpired), findsOneWidget);
  });

  testWidgets('routes list and route detail fit at text scale 1.3', (tester) async {
    final app = TestApp(store: FakeTokenStore(fakeToken));
    app.backend.json('GET', '/api/v1/driver/routes', [
      sendJson(id: 12),
      sendJson(id: 30, status: 'SUPERSEDED'),
    ]);
    app.backend.json('GET', '/api/v1/driver/routes/12', routeJson(sendId: 12, stops: [
      depotJson(lat: 10.0, lon: 106.0)
        ..['instructions_to_next'] = [instructionJson('Rẽ phải vào phố có tên rất dài để thử xuống dòng')],
      orderJson(seq: 1, lat: 10.01, lon: 106.01, zones: ['Vùng X', 'Vùng Y']),
      depotJson(lat: 10.0, lon: 106.0),
    ]));
    await app.pump(tester, logicalSize: small);
    expect(find.byKey(const Key('routes.newerBanner')), findsOneWidget);

    await openDetail(tester, app, 12);
    // On a small screen the half-open sheet shows the banners and guidance
    // first; the driver scrolls (or drags the sheet) to reach the buttons.
    final sheet = find.descendant(of: find.byType(DraggableScrollableSheet), matching: find.byType(Scrollable)).first;
    await tester.scrollUntilVisible(find.byKey(const Key('detail.arrived')), 150, scrollable: sheet);
    await tester.scrollUntilVisible(find.byKey(const Key('detail.allStops')), 150, scrollable: sheet);
    await tester.tap(find.byKey(const Key('detail.allStops')));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(find.byKey(const Key('detail.stopRow.2')), 150, scrollable: sheet);
  });
}
