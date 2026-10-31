import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'config/env.dart';
import 'features/auth/auth_controller.dart';
import 'features/auth/login_screen.dart';
import 'features/routes/routes_screen.dart';
import 'l10n/strings_vi.dart';
import 'theme.dart';
import 'widgets/common.dart';

class GvrpDriverApp extends StatelessWidget {
  const GvrpDriverApp({super.key});

  @override
  Widget build(BuildContext context) => MaterialApp(
        title: S.appTitle,
        debugShowCheckedModeBanner: false,
        theme: buildAppTheme(),
        home: const AuthGate(),
      );
}

/// Chooses the root screen from the configuration and the session
/// (ui-design §2). When the session ends (logout or 401) every pushed screen,
/// sheet and dialog is closed so Login is visible.
class AuthGate extends ConsumerWidget {
  const AuthGate({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (ref.watch(apiBaseUrlProvider) == null) return const ConfigErrorScreen();

    ref.listen(authControllerProvider, (previous, next) {
      final session = next.value;
      if (session != null && !session.loggedIn) {
        Navigator.of(context).popUntil((route) => route.isFirst);
      }
    });

    return switch (ref.watch(authControllerProvider)) {
      AsyncData(:final value) when value.loggedIn => const RoutesScreen(),
      AsyncData(:final value) => LoginScreen(expired: value.expired),
      // Unreadable secure storage: start logged out.
      AsyncError() => const LoginScreen(),
      _ => const Scaffold(body: Center(child: CircularProgressIndicator())),
    };
  }
}

/// Release build without `API_BASE_URL` (design §6). No network call.
class ConfigErrorScreen extends StatelessWidget {
  const ConfigErrorScreen({super.key});

  @override
  Widget build(BuildContext context) => const Scaffold(
        body: SafeArea(child: MessageView(message: S.configError, icon: Icons.error_outline)),
      );
}
