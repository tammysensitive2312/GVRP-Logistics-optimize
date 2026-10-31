import 'dart:async';
import 'dart:convert';
import 'dart:io' show IOException;

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;

import '../../config/env.dart';
import '../../features/auth/auth_controller.dart';
import '../auth/token_store.dart';
import '../log/app_logger.dart';
import 'api_error.dart';

/// Error code of `RESOURCE_NOT_FOUND` in the Entry API (`util/ErrorCode.java`).
const String resourceNotFoundCode = '0040402';

/// HTTP client for the Entry API (design §6).
///
/// - Paths are passed as templates (`/api/v1/driver/routes/{sendId}`) plus
///   parameters, so only the template is logged.
/// - Authenticated calls send `Authorization: Bearer <token>`.
/// - A 401 on an authenticated call clears the token and calls
///   [onUnauthorized] before throwing [UnauthorizedError].
class ApiClient {
  ApiClient({
    required this._httpClient,
    required this._baseUrl,
    required this._tokenStore,
    required this._logger,
    required this._onUnauthorized,
    this.timeout = const Duration(seconds: 15),
  });

  final http.Client _httpClient;
  final String _baseUrl;
  final TokenStore _tokenStore;
  final AppLogger _logger;
  final void Function() _onUnauthorized;
  final Duration timeout;

  /// GET with the bearer token; returns the decoded JSON body.
  Future<Object?> getJson(String pathTemplate, {Map<String, String> params = const {}}) =>
      _send('GET', pathTemplate, params: params, authenticated: true);

  /// POST without a token (login); returns the decoded JSON body.
  Future<Object?> postJsonPublic(String pathTemplate, Map<String, Object?> body) =>
      _send('POST', pathTemplate, body: body, authenticated: false);

  Future<Object?> _send(
    String method,
    String pathTemplate, {
    Map<String, String> params = const {},
    Map<String, Object?>? body,
    required bool authenticated,
  }) async {
    final uri = Uri.parse(_baseUrl + _expand(pathTemplate, params));
    final headers = <String, String>{'Accept': 'application/json'};
    if (body != null) headers['Content-Type'] = 'application/json; charset=utf-8';
    if (authenticated) {
      final token = await _tokenStore.read();
      if (token == null) {
        _logger.http(method, pathTemplate, errorKind: 'no-token');
        await _expire();
        throw const UnauthorizedError();
      }
      headers['Authorization'] = 'Bearer $token';
    }

    final http.Response response;
    try {
      final request = http.Request(method, uri)..headers.addAll(headers);
      if (body != null) request.body = jsonEncode(body);
      response = await http.Response.fromStream(await _httpClient.send(request).timeout(timeout))
          .timeout(timeout);
    } on TimeoutException {
      _logger.http(method, pathTemplate, errorKind: 'timeout');
      throw const NetworkError();
    } on http.ClientException {
      _logger.http(method, pathTemplate, errorKind: 'network');
      throw const NetworkError();
    } on IOException {
      _logger.http(method, pathTemplate, errorKind: 'network');
      throw const NetworkError();
    }

    final status = response.statusCode;
    _logger.http(method, pathTemplate, status: status);

    if (status == 401) {
      if (authenticated) await _expire();
      throw const UnauthorizedError();
    }
    if (status == 403) throw const ForbiddenError();
    if (status >= 400 && status < 500) {
      if (status == 400 && _hasErrorCode(response, resourceNotFoundCode)) {
        throw const NotFoundError();
      }
      throw ClientError(status);
    }
    if (status >= 500 || status < 200 || status >= 300) throw ServerError(status);

    if (response.bodyBytes.isEmpty) return null;
    try {
      return jsonDecode(utf8.decode(response.bodyBytes));
    } on FormatException {
      throw ServerError(status);
    }
  }

  Future<void> _expire() async {
    await _tokenStore.clear();
    _onUnauthorized();
  }

  static String _expand(String template, Map<String, String> params) =>
      template.replaceAllMapped(RegExp(r'\{(\w+)\}'), (m) {
        final value = params[m.group(1)];
        if (value == null) throw ArgumentError('Missing path parameter ${m.group(1)}');
        return Uri.encodeComponent(value);
      });

  /// True when the Entry API error body (`{"errors":[{"code":...}]}`) holds [code].
  static bool _hasErrorCode(http.Response response, String code) {
    try {
      final decoded = jsonDecode(utf8.decode(response.bodyBytes));
      if (decoded is! Map) return false;
      final errors = decoded['errors'];
      return errors is List && errors.any((e) => e is Map && e['code'] == code);
    } on FormatException {
      return false;
    }
  }
}

final httpClientProvider = Provider<http.Client>((ref) {
  final client = http.Client();
  ref.onDispose(client.close);
  return client;
});

final apiClientProvider = Provider<ApiClient>((ref) {
  final baseUrl = ref.watch(apiBaseUrlProvider);
  if (baseUrl == null) {
    // AuthGate shows the configuration error screen before anything asks for
    // the client, so reaching this is a programming error.
    throw StateError('API_BASE_URL is not configured');
  }
  return ApiClient(
    httpClient: ref.watch(httpClientProvider),
    baseUrl: baseUrl,
    tokenStore: ref.watch(tokenStoreProvider),
    logger: ref.watch(appLoggerProvider),
    onUnauthorized: () => ref.read(authControllerProvider.notifier).sessionExpired(),
  );
});
