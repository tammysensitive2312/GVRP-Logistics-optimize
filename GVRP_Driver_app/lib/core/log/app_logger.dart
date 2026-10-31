import 'dart:developer' as developer;

import 'package:flutter_riverpod/flutter_riverpod.dart';

/// The app's only logger (design §6, AGENTS.md §15).
///
/// Its API accepts nothing but the HTTP method, the path *template* (for
/// example `/api/v1/driver/routes/{sendId}`), the status and an error kind,
/// so bodies, tokens, names, phones and coordinates cannot reach the log.
class AppLogger {
  AppLogger({void Function(String line)? sink}) : _sink = sink ?? _developerSink;

  final void Function(String line) _sink;

  static void _developerSink(String line) => developer.log(line, name: 'gvrp_driver');

  /// [status] is null when no response arrived; [errorKind] then names the
  /// failure class (for example `timeout`), never its message.
  void http(String method, String pathTemplate, {int? status, String? errorKind}) {
    final outcome = status?.toString() ?? errorKind ?? 'no-response';
    _sink('HTTP $method $pathTemplate -> $outcome');
  }
}

final appLoggerProvider = Provider<AppLogger>((ref) => AppLogger());
