import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/strings_vi.dart';
import '../../theme.dart';
import '../../widgets/common.dart';
import 'auth_controller.dart';

/// Login (DA-R01, design §5.1, ui-design §4.2).
class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key, this.expired = false});

  /// Shows the session-expired banner (design §5.4).
  final bool expired;

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _branch = TextEditingController();
  final _username = TextEditingController();
  final _password = TextEditingController();
  final _usernameFocus = FocusNode();
  final _passwordFocus = FocusNode();

  bool _submitting = false;
  bool _obscure = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    for (final c in [_branch, _username, _password]) {
      c.addListener(_onChanged);
    }
  }

  @override
  void dispose() {
    for (final c in [_branch, _username, _password]) {
      c.dispose();
    }
    _usernameFocus.dispose();
    _passwordFocus.dispose();
    super.dispose();
  }

  void _onChanged() => setState(() {});

  bool get _canSubmit =>
      !_submitting &&
      _branch.text.trim().isNotEmpty &&
      _username.text.trim().isNotEmpty &&
      _password.text.trim().isNotEmpty;

  Future<void> _submit() async {
    if (!_canSubmit) return;
    FocusScope.of(context).unfocus();
    setState(() {
      _submitting = true;
      _error = null;
    });
    final outcome = await ref.read(authControllerProvider.notifier).login(
          branchName: _branch.text.trim(),
          username: _username.text.trim(),
          password: _password.text,
        );
    if (!mounted || outcome == LoginOutcome.success) return;
    setState(() {
      _submitting = false;
      _password.clear();
      _error = switch (outcome) {
        LoginOutcome.notDriver => S.notDriver,
        LoginOutcome.badCredentials => S.badCredentials,
        LoginOutcome.clientError => S.clientError,
        LoginOutcome.network => S.network,
        LoginOutcome.success => null,
      };
    });
  }

  @override
  Widget build(BuildContext context) {
    final topInset = MediaQuery.paddingOf(context).top;
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle.light,
      child: Scaffold(
        body: ListView(
          padding: EdgeInsets.zero,
          children: [
            _Header(topInset: topInset),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.all(20),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      if (widget.expired) ...[
                        const StatusBanner.danger(
                          key: Key('login.expiredBanner'),
                          text: S.sessionExpired,
                          icon: Icons.lock_clock_outlined,
                        ),
                        const SizedBox(height: 16),
                      ],
                      const Text(
                        S.loginTitle,
                        style: TextStyle(fontSize: 22, fontWeight: FontWeight.w600, color: AppColors.text),
                      ),
                      const SizedBox(height: 16),
                      TextField(
                        key: const Key('login.branch'),
                        controller: _branch,
                        readOnly: _submitting,
                        autocorrect: false,
                        enableSuggestions: false,
                        textInputAction: TextInputAction.next,
                        onSubmitted: (_) => _usernameFocus.requestFocus(),
                        decoration: const InputDecoration(
                          labelText: S.branchName,
                          prefixIcon: Icon(Icons.business_outlined),
                        ),
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        key: const Key('login.username'),
                        controller: _username,
                        focusNode: _usernameFocus,
                        readOnly: _submitting,
                        autocorrect: false,
                        enableSuggestions: false,
                        textInputAction: TextInputAction.next,
                        onSubmitted: (_) => _passwordFocus.requestFocus(),
                        decoration: const InputDecoration(
                          labelText: S.username,
                          prefixIcon: Icon(Icons.person_outline),
                        ),
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        key: const Key('login.password'),
                        controller: _password,
                        focusNode: _passwordFocus,
                        readOnly: _submitting,
                        obscureText: _obscure,
                        autocorrect: false,
                        enableSuggestions: false,
                        textInputAction: TextInputAction.done,
                        onSubmitted: (_) => _submit(),
                        decoration: InputDecoration(
                          labelText: S.password,
                          prefixIcon: const Icon(Icons.lock_outline),
                          suffixIcon: IconButton(
                            tooltip: _obscure ? S.showPassword : S.hidePassword,
                            icon: Icon(_obscure ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                            onPressed: () => setState(() => _obscure = !_obscure),
                          ),
                        ),
                      ),
                      if (_error != null) ...[
                        const SizedBox(height: 12),
                        Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Icon(Icons.error_outline, size: 18, color: AppColors.danger),
                            const SizedBox(width: 6),
                            Expanded(
                              child: Text(
                                _error!,
                                key: const Key('login.error'),
                                style: const TextStyle(color: AppColors.danger, fontSize: 14),
                              ),
                            ),
                          ],
                        ),
                      ],
                      const SizedBox(height: 24),
                      FilledButton(
                        key: const Key('login.submit'),
                        onPressed: _canSubmit ? _submit : null,
                        child: _submitting
                            ? const SizedBox.square(
                                dimension: 20,
                                child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                              )
                            : const Text(S.loginButton),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Blue gradient header, like the web login's branding panel.
class _Header extends StatelessWidget {
  const _Header({required this.topInset});

  final double topInset;

  @override
  Widget build(BuildContext context) => Container(
        padding: EdgeInsets.fromLTRB(24, topInset + 36, 24, 32),
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [AppColors.headerGradientStart, AppColors.headerGradientEnd],
          ),
        ),
        child: Column(
          children: [
            Container(
              width: 72,
              height: 72,
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.2),
                borderRadius: BorderRadius.circular(16),
              ),
              child: const Icon(Icons.local_shipping_outlined, size: 40, color: Colors.white),
            ),
            const SizedBox(height: 12),
            const Text(
              S.appTitle,
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 26, fontWeight: FontWeight.w700, color: Colors.white),
            ),
            const SizedBox(height: 4),
            Text(
              S.loginSubtitle,
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 15, color: Colors.white.withValues(alpha: 0.9)),
            ),
          ],
        ),
      );
}
