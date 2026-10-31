import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:gvrp_driver/core/api/api_client.dart';
import 'package:gvrp_driver/core/api/api_error.dart';
import 'package:gvrp_driver/core/log/app_logger.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import '../../support/test_app.dart';

/// Error mapping of design §6, plus the 401 → logout part of DT-05.
void main() {
  late FakeTokenStore store;
  late List<String> logs;
  late int expiredCalls;

  ApiClient client(MockClient http, {Duration timeout = const Duration(seconds: 15)}) => ApiClient(
        httpClient: http,
        baseUrl: testBaseUrl,
        tokenStore: store,
        logger: AppLogger(sink: logs.add),
        onUnauthorized: () => expiredCalls++,
        timeout: timeout,
      );

  MockClient answer(int status, Object? body) => MockClient((_) async => jsonResponse(body, status: status));

  setUp(() {
    store = FakeTokenStore(fakeToken);
    logs = [];
    expiredCalls = 0;
  });

  test('DT-05 a 401 on an authenticated call clears the token and reports expiry', () async {
    final api = client(answer(401, {'status': 401}));
    await expectLater(api.getJson('/api/v1/driver/routes'), throwsA(isA<UnauthorizedError>()));
    expect(store.token, isNull);
    expect(expiredCalls, 1);
  });

  test('a 401 on the public login call does not end a session', () async {
    final api = client(answer(401, {'status': 401}));
    await expectLater(api.postJsonPublic('/api/v1/auth/login', {}), throwsA(isA<UnauthorizedError>()));
    expect(store.token, fakeToken);
    expect(expiredCalls, 0);
  });

  test('an authenticated call without a token is a 401 without a request', () async {
    store.token = null;
    var calls = 0;
    final api = client(MockClient((_) async {
      calls++;
      return jsonResponse([]);
    }));
    await expectLater(api.getJson('/api/v1/driver/routes'), throwsA(isA<UnauthorizedError>()));
    expect(calls, 0);
    expect(expiredCalls, 1);
  });

  test('403 maps to ForbiddenError', () async {
    await expectLater(
      client(answer(403, errorBody('0040301'))).getJson('/api/v1/driver/routes/{sendId}', params: {'sendId': '9'}),
      throwsA(isA<ForbiddenError>()),
    );
  });

  test('400 with RESOURCE_NOT_FOUND maps to NotFoundError, other 400 to ClientError', () async {
    await expectLater(
      client(answer(400, errorBody(resourceNotFoundCode))).getJson('/api/v1/driver/routes/{sendId}', params: {'sendId': '9'}),
      throwsA(isA<NotFoundError>()),
    );
    await expectLater(
      client(answer(400, errorBody('0040001'))).getJson('/api/v1/driver/routes'),
      throwsA(isA<ClientError>().having((e) => e.status, 'status', 400)),
    );
    await expectLater(
      client(answer(409, errorBody('0040901'))).getJson('/api/v1/driver/routes'),
      throwsA(isA<ClientError>().having((e) => e.status, 'status', 409)),
    );
  });

  test('5xx maps to ServerError; socket failure and timeout to NetworkError', () async {
    await expectLater(client(answer(502, {})).getJson('/api/v1/driver/routes'), throwsA(isA<ServerError>()));
    await expectLater(
      client(MockClient((_) async => throw http.ClientException('connection refused'))).getJson('/api/v1/driver/routes'),
      throwsA(isA<NetworkError>()),
    );
    await expectLater(
      client(
        MockClient((_) => Completer<http.Response>().future),
        timeout: const Duration(milliseconds: 10),
      ).getJson('/api/v1/driver/routes'),
      throwsA(isA<NetworkError>()),
    );
  });

  test('a success body that is not JSON maps to ServerError', () async {
    final api = client(MockClient((_) async => http.Response('<html>', 200)));
    await expectLater(api.getJson('/api/v1/driver/routes'), throwsA(isA<ServerError>()));
  });

  test('path parameters are expanded and encoded; the bearer header is sent', () async {
    late http.Request seen;
    final api = client(MockClient((request) async {
      seen = request;
      return jsonResponse({});
    }));
    await api.getJson('/api/v1/driver/routes/{sendId}', params: {'sendId': '12'});
    expect(seen.url.toString(), '$testBaseUrl/api/v1/driver/routes/12');
    expect(seen.headers['Authorization'], 'Bearer $fakeToken');
  });
}
