import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gvrp_driver/core/api/api_client.dart';
import 'package:gvrp_driver/core/api/api_error.dart';
import 'package:gvrp_driver/core/log/app_logger.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import '../../support/test_app.dart';

/// DT-13: logs written during API errors hold only method, path template and
/// status: no body, token, password, name, phone or coordinate.
void main() {
  test('DT-13 API errors log no body, token or customer field', () async {
    const sendId = '4242';
    final password = testPassword;
    const customerName = 'Khách hàng Bí Mật';
    const customerPhone = '0912345678';
    const latitude = '10.123456';
    const longitude = '106.654321';
    final sensitiveBody = {
      'errors': [
        {'code': '0050001', 'message': 'boom $customerName $customerPhone $latitude,$longitude $fakeToken'},
      ],
      'stops': [
        {'customer_name': customerName, 'customer_phone': customerPhone, 'latitude': 10.123456, 'longitude': 106.654321},
      ],
    };

    final responses = <http.Response Function()>[
      () => jsonResponse(sensitiveBody, status: 500),
      () => jsonResponse(sensitiveBody, status: 400),
      () => jsonResponse(sensitiveBody, status: 403),
      () => jsonResponse(sensitiveBody, status: 401),
      () => throw http.ClientException('failed: $customerPhone $fakeToken'),
      () => jsonResponse(sensitiveBody, status: 400),
    ];
    var call = 0;
    final mock = MockClient((_) async => responses[call++]());

    final logs = <String>[];
    final printed = <String>[];
    final previousDebugPrint = debugPrint;
    debugPrint = (String? message, {int? wrapWidth}) => printed.add(message ?? '');
    addTearDown(() => debugPrint = previousDebugPrint);

    final store = FakeTokenStore(fakeToken);
    final api = ApiClient(
      httpClient: mock,
      baseUrl: testBaseUrl,
      tokenStore: store,
      logger: AppLogger(sink: logs.add),
      onUnauthorized: () {},
    );

    await runZoned(
      () async {
        for (var i = 0; i < 5; i++) {
          store.token ??= fakeToken;
          await expectLater(
            api.getJson('/api/v1/driver/routes/{sendId}', params: {'sendId': sendId}),
            throwsA(isA<ApiError>()),
          );
        }
        await expectLater(
          api.postJsonPublic('/api/v1/auth/login', {
            'branch_name': testBranch,
            'username': testUsername,
            'password': password,
          }),
          throwsA(isA<ApiError>()),
        );
      },
      zoneSpecification: ZoneSpecification(print: (_, _, _, line) => printed.add(line)),
    );

    expect(logs, hasLength(6));
    final allowedLine = RegExp(r'^HTTP (GET|POST) /api/v1/[A-Za-z/{}]+ -> [0-9a-z-]+$');
    for (final line in logs) {
      expect(line, matches(allowedLine));
    }
    expect(logs.first, 'HTTP GET /api/v1/driver/routes/{sendId} -> 500');
    expect(logs[4], 'HTTP GET /api/v1/driver/routes/{sendId} -> network');
    expect(logs.last, 'HTTP POST /api/v1/auth/login -> 400');

    final everything = [...logs, ...printed].join('\n');
    for (final secret in [
      fakeToken,
      'Bearer',
      password,
      customerName,
      customerPhone,
      latitude,
      longitude,
      sendId,
      testUsername,
      testBranch,
      'boom',
      'errors',
    ]) {
      expect(everything, isNot(contains(secret)), reason: 'log leaked "$secret"');
    }
  });
}
