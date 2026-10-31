import 'package:flutter_riverpod/misc.dart' show ProviderException;

import '../../l10n/strings_vi.dart';

/// Typed API errors (design §6).
sealed class ApiError implements Exception {
  const ApiError();

  /// The Vietnamese message the UI shows for this error.
  String get message;
}

/// 401: the token is missing or expired. The client has already cleared it.
final class UnauthorizedError extends ApiError {
  const UnauthorizedError();
  @override
  String get message => S.sessionExpired;
}

/// 403: for example another driver's send.
final class ForbiddenError extends ApiError {
  const ForbiddenError();
  @override
  String get message => S.forbidden;
}

/// 400 with error code `RESOURCE_NOT_FOUND` (`0040402`).
final class NotFoundError extends ApiError {
  const NotFoundError();
  @override
  String get message => S.notFound;
}

/// Any other 4xx.
final class ClientError extends ApiError {
  const ClientError(this.status);
  final int status;
  @override
  String get message => S.clientError;
}

/// 5xx, or a response the app cannot read.
final class ServerError extends ApiError {
  const ServerError(this.status);
  final int? status;
  @override
  String get message => S.network;
}

/// Timeout, socket or TLS failure: no response arrived.
final class NetworkError extends ApiError {
  const NetworkError();
  @override
  String get message => S.network;
}

/// Unwraps Riverpod's [ProviderException] and maps anything unknown to a
/// [ServerError], so screens can always show a Vietnamese message.
ApiError asApiError(Object error) {
  var e = error;
  while (e is ProviderException) {
    e = e.exception;
  }
  return e is ApiError ? e : const ServerError(null);
}
