import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/api_client.dart';
import '../../core/api/api_error.dart';
import '../../core/auth/token_store.dart';

/// Role the driver API requires (SEC-ROLE-001).
const String driverRole = 'DRIVER';

/// Session state shown by `AuthGate` (design §5.4).
class AuthSession {
  const AuthSession.loggedIn()
      : loggedIn = true,
        expired = false;
  const AuthSession.loggedOut({this.expired = false}) : loggedIn = false;

  final bool loggedIn;

  /// True when the session ended because of a 401; Login then shows the
  /// expiry banner.
  final bool expired;
}

enum LoginOutcome { success, notDriver, badCredentials, clientError, network }

/// The fields of `POST /api/v1/auth/login` the app needs. Other fields
/// (`user_id`, `branch_id`, …) are ignored.
class LoginResponse {
  const LoginResponse({required this.accessToken, required this.role});

  final String accessToken;
  final String role;

  /// Null when the body is not a login response.
  static LoginResponse? tryParse(Object? json) {
    if (json is! Map) return null;
    final token = json['access_token'];
    final role = json['role'];
    if (token is! String || token.isEmpty || role is! String) return null;
    return LoginResponse(accessToken: token, role: role);
  }
}

class AuthController extends AsyncNotifier<AuthSession> {
  @override
  Future<AuthSession> build() async {
    final token = await ref.read(tokenStoreProvider).read();
    return token == null ? const AuthSession.loggedOut() : const AuthSession.loggedIn();
  }

  /// Logs in and stores the token only for a DRIVER account (design §5.1).
  Future<LoginOutcome> login({
    required String branchName,
    required String username,
    required String password,
  }) async {
    final Object? json;
    try {
      json = await ref.read(apiClientProvider).postJsonPublic('/api/v1/auth/login', {
        'branch_name': branchName,
        'username': username,
        'password': password,
      });
    } on ApiError catch (e) {
      return switch (e) {
        // A 401 on the public login call means the credentials were refused.
        UnauthorizedError() || NotFoundError() => LoginOutcome.badCredentials,
        ClientError(status: 400) => LoginOutcome.badCredentials,
        ClientError() || ForbiddenError() => LoginOutcome.clientError,
        ServerError() || NetworkError() => LoginOutcome.network,
      };
    }

    final response = LoginResponse.tryParse(json);
    if (response == null) return LoginOutcome.network;
    if (response.role != driverRole) return LoginOutcome.notDriver;

    await ref.read(tokenStoreProvider).write(response.accessToken);
    state = const AsyncData(AuthSession.loggedIn());
    return LoginOutcome.success;
  }

  /// Called by the API client after a 401 has cleared the token.
  void sessionExpired() {
    state = const AsyncData(AuthSession.loggedOut(expired: true));
  }

  Future<void> logout() async {
    await ref.read(tokenStoreProvider).clear();
    state = const AsyncData(AuthSession.loggedOut());
  }
}

final authControllerProvider = AsyncNotifierProvider<AuthController, AuthSession>(AuthController.new);
