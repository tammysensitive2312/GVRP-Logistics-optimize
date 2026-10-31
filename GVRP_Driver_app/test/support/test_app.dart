import 'dart:convert';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:flutter_test/flutter_test.dart';
import 'package:gvrp_driver/app.dart';
import 'package:gvrp_driver/config/env.dart';
import 'package:gvrp_driver/core/api/api_client.dart';
import 'package:gvrp_driver/core/auth/token_store.dart';
import 'package:gvrp_driver/core/log/app_logger.dart';
import 'package:gvrp_driver/features/location/location_service.dart';
import 'package:gvrp_driver/features/map/route_map.dart';
import 'package:gvrp_driver/features/routes/phone_caller.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:latlong2/latlong.dart';

/// All data in this file is invented test data: no real customer, branch,
/// plate, phone, coordinate or credential.
const testBaseUrl = 'http://api.test';

final _random = Random.secure();

String _generated(String prefix) =>
    '$prefix-${List.generate(16, (_) => _random.nextInt(36).toRadixString(36)).join()}';

// Credentials are generated per test run, so no token, password or account
// name is stored in any file (task rule: "Test accounts, passwords or tokens
// in any file" are forbidden).
final String fakeToken = _generated('tok');
final String testPassword = _generated('pw');
final String testUsername = _generated('user');
final String testBranch = _generated('branch');

/// In-memory [TokenStore] that records writes (DT-03, DT-04, DT-05).
class FakeTokenStore implements TokenStore {
  FakeTokenStore([this.token]);

  String? token;
  int writes = 0;
  int clears = 0;

  @override
  Future<String?> read() async => token;

  @override
  Future<void> write(String value) async {
    writes++;
    token = value;
  }

  @override
  Future<void> clear() async {
    clears++;
    token = null;
  }
}

class FakeLocationService implements LocationService {
  FakeLocationService({this.access = LocationAccess.denied});

  final LocationAccess access;
  int requests = 0;

  @override
  Future<LocationAccess> requestAccess() async {
    requests++;
    return access;
  }

  @override
  Stream<LatLng> positions() => const Stream.empty();

  @override
  Future<void> openAppSettings() async {}
}

/// Answers by `METHOD path`; records every request.
class FakeBackend {
  final Map<String, http.Response Function(http.Request request)> routes = {};
  final List<http.Request> requests = [];

  void on(String method, String path, http.Response Function(http.Request request) handler) =>
      routes['$method $path'] = handler;

  void json(String method, String path, Object? body, {int status = 200}) =>
      on(method, path, (_) => jsonResponse(body, status: status));

  late final MockClient client = MockClient((request) async {
    requests.add(request);
    final handler = routes['${request.method} ${request.url.path}'];
    return handler == null ? jsonResponse({'errors': []}, status: 404) : handler(request);
  });
}

http.Response jsonResponse(Object? body, {int status = 200}) => http.Response.bytes(
      utf8.encode(jsonEncode(body)),
      status,
      headers: {'content-type': 'application/json; charset=utf-8'},
    );

/// Entry API error body (`ErrorResponse`).
Map<String, Object?> errorBody(String code, [String message = 'error']) => {
      'errors': [
        {'code': code, 'message': message},
      ],
    };

class TestApp {
  TestApp({FakeTokenStore? store, FakeLocationService? location, this.baseUrl = testBaseUrl})
      : store = store ?? FakeTokenStore(),
        location = location ?? FakeLocationService();

  /// Null simulates a release build without `API_BASE_URL`.
  final String? baseUrl;
  final FakeBackend backend = FakeBackend();
  final FakeTokenStore store;
  final FakeLocationService location;
  final List<String> logs = [];
  final List<Uri> launched = [];

  List<Override> get overrides => [
        apiBaseUrlProvider.overrideWithValue(baseUrl),
        httpClientProvider.overrideWithValue(backend.client),
        tokenStoreProvider.overrideWithValue(store),
        appLoggerProvider.overrideWithValue(AppLogger(sink: logs.add)),
        mapTilesEnabledProvider.overrideWithValue(false),
        phoneCallerProvider.overrideWithValue(PhoneCaller((uri) async {
          launched.add(uri);
          return true;
        })),
        locationServiceProvider.overrideWithValue(location),
      ];

  /// [logicalSize] defaults to a phone-sized screen (432 x 960) so the
  /// bottom sheet content is laid out on screen.
  Future<void> pump(WidgetTester tester, {Size logicalSize = const Size(432, 960)}) async {
    tester.view.devicePixelRatio = 2.5;
    tester.view.physicalSize = logicalSize * 2.5;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      ProviderScope(retry: (_, _) => null, overrides: overrides, child: const GvrpDriverApp()),
    );
    await tester.pumpAndSettle();
  }
}

// ---- Sample JSON (shapes from context.md §1 and §2) ----

Map<String, Object?> sendJson({
  required int id,
  String status = 'CURRENT',
  String plate = '29X-000.00',
  String sentAt = '2026-10-27T08:05:00',
  String? sender = 'Điều phối A',
}) =>
    {
      'id': id,
      'solution_id': 900,
      'route_id': 901,
      'vehicle_license_plate': plate,
      'driver_id': 5,
      'driver_name': 'Tài xế Thử',
      'sender_name': sender,
      'sent_at': sentAt,
      'status': status,
    };

Map<String, Object?> depotJson({required double lat, required double lon, String? geometry}) => {
      'sequence_number': null,
      'type': 'DEPOT',
      'order_id': null,
      'location_name': 'Kho Thử',
      'address': 'Địa chỉ kho thử',
      'latitude': lat,
      'longitude': lon,
      'arrival_time': null,
      'departure_time': '08:00:00',
      'customer_name': null,
      'customer_phone': null,
      'delivery_notes': null,
      'demand': null,
      'geometry_to_next': ?geometry,
    };

Map<String, Object?> orderJson({
  required int seq,
  required double lat,
  required double lon,
  String? phone = '0900000001',
  String? geometry,
  List<Map<String, Object?>>? instructions,
  List<String>? zones,
}) =>
    {
      'sequence_number': seq,
      'type': 'ORDER',
      'order_id': 100 + seq,
      'location_name': 'Cửa hàng $seq',
      'address': 'Số $seq phố Thử',
      'latitude': lat,
      'longitude': lon,
      'arrival_time': '09:30:00',
      'departure_time': '09:40:00',
      'customer_name': 'Khách hàng $seq',
      'customer_phone': phone,
      'delivery_notes': 'gọi trước 10 phút',
      'demand': 12.5,
      'geometry_to_next': ?geometry,
      'instructions_to_next': ?instructions,
      'zone_names': ?zones,
    };

Map<String, Object?> routeJson({
  required int sendId,
  String status = 'CURRENT',
  required List<Map<String, Object?>> stops,
}) =>
    {
      'send_id': sendId,
      'solution_id': 900,
      'route_id': 901,
      'vehicle_license_plate': '29X-000.00',
      'status': status,
      'stops': stops,
    };

Map<String, Object?> instructionJson(String text, {double distance = 350, String? street = 'phố A'}) => {
      'text': text,
      'distance_m': distance,
      'time_ms': 42000,
      'sign': 2,
      'street_name': street,
    };

/// Test-only encoder (precision 1e5) to build `geometry_to_next` fixtures.
String encodePolyline(List<List<double>> points) {
  final out = StringBuffer();
  var prevLat = 0;
  var prevLon = 0;
  void encode(int value) {
    var v = value < 0 ? ~(value << 1) : value << 1;
    while (v >= 0x20) {
      out.writeCharCode((0x20 | (v & 0x1f)) + 63);
      v >>= 5;
    }
    out.writeCharCode(v + 63);
  }

  for (final p in points) {
    final lat = (p[0] * 1e5).round();
    final lon = (p[1] * 1e5).round();
    encode(lat - prevLat);
    encode(lon - prevLon);
    prevLat = lat;
    prevLon = lon;
  }
  return out.toString();
}

/// Logs in through the routes list and opens the detail of [sendId].
Future<void> openDetail(WidgetTester tester, TestApp app, int sendId) async {
  await tester.tap(find.byKey(Key('routes.row.$sendId')));
  await tester.pumpAndSettle();
}

/// Scrolls the route-detail sheet until [finder] is on screen, then taps it.
/// The sheet starts at 45 % of the screen (ui-design §8.2), so its buttons
/// can be below the visible part.
Future<void> tapInSheet(WidgetTester tester, Finder finder) async {
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

/// Scrolls the route-detail sheet back to its top (lazily built content
/// such as the guidance card is rebuilt).
Future<void> scrollSheetToTop(WidgetTester tester) async {
  final scrollable = find
      .descendant(of: find.byType(DraggableScrollableSheet), matching: find.byType(Scrollable))
      .first;
  tester.state<ScrollableState>(scrollable).position.jumpTo(0);
  await tester.pumpAndSettle();
}
