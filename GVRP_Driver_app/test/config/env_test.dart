import 'package:flutter_test/flutter_test.dart';
import 'package:gvrp_driver/app.dart';
import 'package:gvrp_driver/config/env.dart';
import 'package:gvrp_driver/l10n/strings_vi.dart';

import '../support/test_app.dart';

/// Design §6: API_BASE_URL from --dart-define; no default in release.
void main() {
  test('a defined API_BASE_URL wins and loses a trailing slash', () {
    expect(resolveApiBaseUrl(defined: 'http://192.168.0.2:8080/', release: true), 'http://192.168.0.2:8080');
    expect(resolveApiBaseUrl(defined: 'http://10.0.2.2:8080', release: false), 'http://10.0.2.2:8080');
  });

  test('debug falls back to the emulator host; release has no default', () {
    expect(resolveApiBaseUrl(defined: '', release: false), debugDefaultApiBaseUrl);
    expect(resolveApiBaseUrl(defined: '  ', release: true), isNull);
  });

  testWidgets('without a base URL the configuration error screen is shown and no call is made', (tester) async {
    final app = TestApp(store: FakeTokenStore(fakeToken), baseUrl: null);
    await app.pump(tester);

    expect(find.byType(ConfigErrorScreen), findsOneWidget);
    expect(find.text(S.configError), findsOneWidget);
    expect(app.backend.requests, isEmpty);
  });
}
