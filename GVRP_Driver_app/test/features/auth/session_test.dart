import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gvrp_driver/features/auth/login_screen.dart';
import 'package:gvrp_driver/features/routes/route_detail_screen.dart';
import 'package:gvrp_driver/features/routes/routes_screen.dart';
import 'package:gvrp_driver/l10n/strings_vi.dart';

import '../../support/test_app.dart';

/// DT-05: any 401 clears the token and returns to Login with the expiry
/// message (design §5.4).
void main() {
  testWidgets('DT-05 a 401 on My routes clears the token and shows Login with the expiry message',
      (tester) async {
    final app = TestApp(store: FakeTokenStore(fakeToken));
    app.backend.json('GET', '/api/v1/driver/routes', {'status': 401, 'error': 'Unauthorized'}, status: 401);
    await app.pump(tester);

    expect(app.store.token, isNull);
    expect(app.store.clears, greaterThanOrEqualTo(1));
    expect(find.byType(LoginScreen), findsOneWidget);
    expect(find.byKey(const Key('login.expiredBanner')), findsOneWidget);
    expect(find.text(S.sessionExpired), findsOneWidget);
    expect(find.byType(RoutesScreen), findsNothing);
  });

  testWidgets('DT-05 a 401 on a pushed route detail returns to Login', (tester) async {
    final app = TestApp(store: FakeTokenStore(fakeToken));
    app.backend.json('GET', '/api/v1/driver/routes', [sendJson(id: 12)]);
    app.backend.json('GET', '/api/v1/driver/routes/12', {'status': 401}, status: 401);
    await app.pump(tester);

    await openDetail(tester, app, 12);

    expect(app.store.token, isNull);
    expect(find.byType(RouteDetailScreen), findsNothing);
    expect(find.byType(LoginScreen), findsOneWidget);
    expect(find.text(S.sessionExpired), findsOneWidget);
  });

  testWidgets('logout clears the token and shows Login without the expiry message', (tester) async {
    final app = TestApp(store: FakeTokenStore(fakeToken));
    app.backend.json('GET', '/api/v1/driver/routes', []);
    await app.pump(tester);

    await tester.tap(find.byKey(const Key('appbar.logout')));
    await tester.pumpAndSettle();

    expect(app.store.token, isNull);
    expect(find.byType(LoginScreen), findsOneWidget);
    expect(find.text(S.sessionExpired), findsNothing);
  });
}
