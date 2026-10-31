import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gvrp_driver/l10n/strings_vi.dart';

import '../../support/test_app.dart';

/// DT-06: My routes keeps server order and marks superseded sends.
void main() {
  testWidgets('DT-06 CURRENT and SUPERSEDED: order kept, banner and tag shown', (tester) async {
    final app = TestApp(store: FakeTokenStore(fakeToken));
    app.backend.json('GET', '/api/v1/driver/routes', [
      // Server order: CURRENT first. Ids are chosen so that sorting by id
      // would reverse them.
      sendJson(id: 12, sentAt: '2026-10-27T08:05:00'),
      sendJson(id: 30, status: 'SUPERSEDED', sentAt: '2026-10-27T07:40:00'),
    ]);
    await app.pump(tester);

    final current = find.byKey(const Key('routes.row.12'));
    final superseded = find.byKey(const Key('routes.row.30'));
    expect(current, findsOneWidget);
    expect(superseded, findsOneWidget);
    expect(tester.getTopLeft(current).dy, lessThan(tester.getTopLeft(superseded).dy));

    expect(find.byKey(const Key('routes.newerBanner')), findsOneWidget);
    expect(find.text(S.newerVersionBanner), findsOneWidget);
    expect(find.descendant(of: superseded, matching: find.text(S.supersededTag)), findsOneWidget);
    expect(find.descendant(of: current, matching: find.text(S.supersededTag)), findsNothing);

    expect(find.descendant(of: current, matching: find.text('Gửi lúc 27/10/2026 08:05')), findsOneWidget);
    expect(find.descendant(of: current, matching: find.text('Người gửi: Điều phối A')), findsOneWidget);
  });

  testWidgets('DT-06 only CURRENT sends: no banner and no tag', (tester) async {
    final app = TestApp(store: FakeTokenStore(fakeToken));
    app.backend.json('GET', '/api/v1/driver/routes', [sendJson(id: 12)]);
    await app.pump(tester);

    expect(find.byKey(const Key('routes.newerBanner')), findsNothing);
    expect(find.text(S.supersededTag), findsNothing);
  });

  testWidgets('empty list shows the empty message', (tester) async {
    final app = TestApp(store: FakeTokenStore(fakeToken));
    app.backend.json('GET', '/api/v1/driver/routes', []);
    await app.pump(tester);

    expect(find.text(S.noRoutes), findsOneWidget);
  });

  testWidgets('a server error shows the message and retry reloads the list', (tester) async {
    final app = TestApp(store: FakeTokenStore(fakeToken));
    app.backend.json('GET', '/api/v1/driver/routes', errorBody('0050001'), status: 500);
    await app.pump(tester);

    expect(find.text(S.network), findsOneWidget);
    app.backend.json('GET', '/api/v1/driver/routes', [sendJson(id: 12)]);
    await tester.tap(find.text(S.retry));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('routes.row.12')), findsOneWidget);
  });
}
