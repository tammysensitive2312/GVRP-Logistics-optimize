import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gvrp_driver/features/routes/routes_screen.dart';
import 'package:gvrp_driver/l10n/strings_vi.dart';

import '../../support/test_app.dart';

Future<void> fillLogin(WidgetTester tester) async {
  await tester.enterText(find.byKey(const Key('login.branch')), testBranch);
  await tester.enterText(find.byKey(const Key('login.username')), testUsername);
  await tester.enterText(find.byKey(const Key('login.password')), testPassword);
  await tester.pump();
}

void main() {
  testWidgets('DT-03 login with role PLANNER is refused and nothing is stored', (tester) async {
    final app = TestApp();
    app.backend.json('POST', '/api/v1/auth/login', {
      'access_token': fakeToken,
      'user_id': 1,
      'username': testUsername,
      'branch_id': 1,
      'branch_name': testBranch,
      'role': 'PLANNER',
    });
    await app.pump(tester);

    await fillLogin(tester);
    await tester.tap(find.byKey(const Key('login.submit')));
    await tester.pumpAndSettle();

    expect(find.text(S.notDriver), findsOneWidget);
    expect(app.store.writes, 0);
    expect(app.store.token, isNull);
    expect(find.byType(RoutesScreen), findsNothing);
    // The request used the snake_case contract.
    final body = jsonDecode(app.backend.requests.single.body) as Map<String, Object?>;
    expect(body.keys, unorderedEquals(['branch_name', 'username', 'password']));
    expect(app.backend.requests.single.headers['Authorization'], isNull);
  });

  testWidgets('DT-04 login with role DRIVER stores the token and opens My routes', (tester) async {
    final app = TestApp();
    app.backend.json('POST', '/api/v1/auth/login', {'access_token': fakeToken, 'role': 'DRIVER'});
    app.backend.json('GET', '/api/v1/driver/routes', []);
    await app.pump(tester);

    await fillLogin(tester);
    await tester.tap(find.byKey(const Key('login.submit')));
    await tester.pumpAndSettle();

    expect(app.store.writes, 1);
    expect(app.store.token, fakeToken);
    expect(find.byType(RoutesScreen), findsOneWidget);
    expect(find.text(S.myRoutes), findsOneWidget);
    final routesCall = app.backend.requests.last;
    expect(routesCall.url.path, '/api/v1/driver/routes');
    expect(routesCall.headers['Authorization'], 'Bearer $fakeToken');
  });

  testWidgets('login 400 shows the credentials message and clears only the password', (tester) async {
    final app = TestApp();
    app.backend.json('POST', '/api/v1/auth/login', errorBody('0040402'), status: 400);
    await app.pump(tester);

    await fillLogin(tester);
    await tester.tap(find.byKey(const Key('login.submit')));
    await tester.pumpAndSettle();

    expect(find.text(S.badCredentials), findsOneWidget);
    expect(app.store.writes, 0);
    expect(find.text(testBranch), findsOneWidget);
    expect(find.text(testUsername), findsOneWidget);
    expect(find.text(testPassword), findsNothing);
  });

  testWidgets('login 500 shows the connection message', (tester) async {
    final app = TestApp();
    app.backend.json('POST', '/api/v1/auth/login', errorBody('0050001'), status: 500);
    await app.pump(tester);

    await fillLogin(tester);
    await tester.tap(find.byKey(const Key('login.submit')));
    await tester.pumpAndSettle();

    expect(find.text(S.network), findsOneWidget);
    expect(app.store.writes, 0);
  });

  testWidgets('the login button stays disabled until all fields are filled', (tester) async {
    final app = TestApp();
    await app.pump(tester);
    FilledButton button() => tester.widget<FilledButton>(find.byKey(const Key('login.submit')));

    expect(button().onPressed, isNull);
    await tester.enterText(find.byKey(const Key('login.branch')), testBranch);
    await tester.enterText(find.byKey(const Key('login.username')), '   ');
    await tester.enterText(find.byKey(const Key('login.password')), testPassword);
    await tester.pump();
    expect(button().onPressed, isNull);
    await tester.enterText(find.byKey(const Key('login.username')), testUsername);
    await tester.pump();
    expect(button().onPressed, isNotNull);
  });
}
