import 'package:flutter/material.dart';

import '../l10n/strings_vi.dart';
import '../theme.dart';

enum _BannerKind { info, warning, danger }

/// A banner that always carries text next to its colour (ui-design §1.3).
class StatusBanner extends StatelessWidget {
  const StatusBanner.info({super.key, required this.text, this.icon = Icons.info_outline}) : _kind = _BannerKind.info;
  const StatusBanner.warning({super.key, required this.text, this.icon = Icons.shield_outlined})
      : _kind = _BannerKind.warning;
  const StatusBanner.danger({super.key, required this.text, this.icon = Icons.warning_amber_rounded})
      : _kind = _BannerKind.danger;

  final String text;
  final IconData icon;
  final _BannerKind _kind;

  @override
  Widget build(BuildContext context) {
    final (bg, fg) = switch (_kind) {
      _BannerKind.info => (AppColors.primaryLight, AppColors.primaryDark),
      _BannerKind.warning => (AppColors.warningBackground, AppColors.warningText),
      _BannerKind.danger => (AppColors.dangerBackground, AppColors.danger),
    };
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(8)),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: fg, size: 20),
          const SizedBox(width: 10),
          Expanded(child: Text(text, style: TextStyle(color: fg, fontSize: 14, fontWeight: FontWeight.w500))),
        ],
      ),
    );
  }
}

/// Small rounded label, like the web client's `.driver-badge`.
class LabelBadge extends StatelessWidget {
  const LabelBadge({super.key, required this.text, this.background = AppColors.primaryLight, this.foreground = AppColors.primary});

  const LabelBadge.depot({super.key, required this.text})
      : background = AppColors.depotLight,
        foreground = AppColors.depot;

  const LabelBadge.danger({super.key, required this.text})
      : background = AppColors.dangerBackground,
        foreground = AppColors.danger;

  final String text;
  final Color background;
  final Color foreground;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
        decoration: BoxDecoration(color: background, borderRadius: BorderRadius.circular(4)),
        child: Text(text, style: TextStyle(color: foreground, fontSize: 12, fontWeight: FontWeight.w600)),
      );
}

/// Icon plus one line of secondary text (web `.driver-stop-meta` lines).
class MetaLine extends StatelessWidget {
  const MetaLine({super.key, required this.icon, required this.text, this.color, this.fontSize = 14});

  final IconData icon;
  final String text;
  final Color? color;
  final double fontSize;

  @override
  Widget build(BuildContext context) {
    final c = color ?? AppColors.textSecondary;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(padding: const EdgeInsets.only(top: 2), child: Icon(icon, size: 16, color: c)),
          const SizedBox(width: 8),
          Expanded(child: Text(text, style: TextStyle(fontSize: fontSize, color: c))),
        ],
      ),
    );
  }
}

/// Section title with an icon and an optional action on the right
/// (web `.guide-head` / `.stops-head`).
class SectionHeader extends StatelessWidget {
  const SectionHeader({super.key, required this.icon, required this.title, this.trailing});

  final IconData icon;
  final String title;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Row(
          children: [
            Icon(icon, size: 20, color: AppColors.primary),
            const SizedBox(width: 8),
            Expanded(
              child: Text(title, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
            ),
            ?trailing,
          ],
        ),
      );
}

/// Message plus one action, shown where the content would be (ui-design §1.4).
class MessageView extends StatelessWidget {
  const MessageView({super.key, required this.message, this.icon = Icons.cloud_off, this.actionLabel, this.onAction});

  final String message;
  final IconData icon;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 72,
              height: 72,
              decoration: const BoxDecoration(color: AppColors.primaryLight, shape: BoxShape.circle),
              child: Icon(icon, size: 36, color: AppColors.primary),
            ),
            const SizedBox(height: 16),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 16, color: AppColors.text),
            ),
            if (actionLabel != null && onAction != null) ...[
              const SizedBox(height: 16),
              OutlinedButton(onPressed: onAction, child: Text(actionLabel!)),
            ],
          ],
        ),
      ),
    );
  }
}

/// Retry view for network, server and generic client errors.
class RetryView extends StatelessWidget {
  const RetryView({super.key, required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) =>
      MessageView(message: message, actionLabel: S.retry, onAction: onRetry);
}
