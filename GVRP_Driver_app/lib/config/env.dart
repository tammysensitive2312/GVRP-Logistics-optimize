import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Emulator address of the host's `localhost:8080` (context §4).
const String debugDefaultApiBaseUrl = 'http://10.0.2.2:8080';

const String _definedApiBaseUrl = String.fromEnvironment('API_BASE_URL');

/// Resolves the API base URL (design §6).
///
/// A value from `--dart-define=API_BASE_URL` always wins. Debug and profile
/// builds fall back to the emulator host. A release build without the define
/// returns null: the app then shows the configuration error screen and makes
/// no network call.
String? resolveApiBaseUrl({required String defined, required bool release}) {
  final trimmed = defined.trim();
  if (trimmed.isNotEmpty) {
    return trimmed.endsWith('/') ? trimmed.substring(0, trimmed.length - 1) : trimmed;
  }
  return release ? null : debugDefaultApiBaseUrl;
}

final apiBaseUrlProvider = Provider<String?>(
  (ref) => resolveApiBaseUrl(defined: _definedApiBaseUrl, release: kReleaseMode),
);
