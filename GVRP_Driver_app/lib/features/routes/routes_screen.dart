import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/api_error.dart';
import '../../l10n/strings_vi.dart';
import '../../theme.dart';
import '../../widgets/common.dart';
import '../auth/auth_controller.dart';
import 'route_detail_screen.dart';
import 'route_models.dart';
import 'routes_controller.dart';

/// My routes (DA-R02, design §5.2, ui-design §4.3 and §8.2).
class RoutesScreen extends ConsumerWidget {
  const RoutesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final routes = ref.watch(routesControllerProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Row(
          children: [
            Icon(Icons.local_shipping_outlined, color: AppColors.primary),
            SizedBox(width: 8),
            Flexible(child: Text(S.myRoutes, overflow: TextOverflow.ellipsis)),
          ],
        ),
        actions: const [LogoutButton()],
      ),
      body: switch (routes) {
        AsyncData(:final value) => _RoutesList(routes: value),
        AsyncError(:final error) => RetryView(
            message: asApiError(error).message,
            onRetry: () => ref.invalidate(routesControllerProvider),
          ),
        _ => const Center(child: CircularProgressIndicator()),
      },
    );
  }
}

class _RoutesList extends ConsumerWidget {
  const _RoutesList({required this.routes});

  final List<RouteSend> routes;

  Future<void> _refresh(BuildContext context, WidgetRef ref) async {
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref.read(routesControllerProvider.notifier).refresh();
    } catch (e) {
      final error = asApiError(e);
      if (error is! UnauthorizedError) {
        messenger.showSnackBar(SnackBar(content: Text(error.message)));
      }
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final hasSuperseded = routes.any((r) => r.isSuperseded);
    return RefreshIndicator(
      onRefresh: () => _refresh(context, ref),
      child: routes.isEmpty
          ? ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: const [
                SizedBox(height: 120),
                MessageView(message: S.noRoutes, icon: Icons.route_outlined),
              ],
            )
          : ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.all(12),
              children: [
                if (hasSuperseded) ...[
                  const StatusBanner.info(key: Key('routes.newerBanner'), text: S.newerVersionBanner),
                  const SizedBox(height: 12),
                ],
                // Server order, never re-sorted (CURRENT first).
                for (final route in routes) ...[
                  _RouteRow(route: route),
                  const SizedBox(height: 8),
                ],
              ],
            ),
    );
  }
}

class _RouteRow extends StatelessWidget {
  const _RouteRow({required this.route});

  final RouteSend route;

  @override
  Widget build(BuildContext context) {
    final muted = route.isSuperseded;
    return Card(
      key: Key('routes.row.${route.id}'),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => Navigator.of(context).push(
          MaterialPageRoute<void>(builder: (_) => RouteDetailScreen(sendId: route.id)),
        ),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: muted ? AppColors.background : AppColors.primaryLight,
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  Icons.local_shipping_outlined,
                  color: muted ? AppColors.textSecondary : AppColors.primary,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Opacity(
                  opacity: muted ? 0.75 : 1,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Wrap(
                        spacing: 8,
                        runSpacing: 4,
                        crossAxisAlignment: WrapCrossAlignment.center,
                        children: [
                          Text(
                            route.vehicleLicensePlate,
                            style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700, color: AppColors.text),
                          ),
                          if (muted) const LabelBadge.danger(key: Key('routes.supersededTag'), text: S.supersededTag),
                        ],
                      ),
                      const SizedBox(height: 6),
                      MetaLine(icon: Icons.schedule, text: S.sentAt(formatSentAt(route.sentAt))),
                      if (route.senderName != null && route.senderName!.isNotEmpty)
                        MetaLine(icon: Icons.person_outline, text: S.sender(route.senderName!)),
                    ],
                  ),
                ),
              ),
              const Padding(
                padding: EdgeInsets.only(top: 10),
                child: Icon(Icons.chevron_right, color: AppColors.textSecondary),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// App-bar logout action (design §5.4), an outlined button like the web
/// client's "Log out".
class LogoutButton extends ConsumerWidget {
  const LogoutButton({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) => Padding(
        padding: const EdgeInsets.only(right: 12),
        child: OutlinedButton.icon(
          key: const Key('appbar.logout'),
          style: OutlinedButton.styleFrom(
            minimumSize: const Size(48, 40),
            padding: const EdgeInsets.symmetric(horizontal: 12),
            visualDensity: VisualDensity.compact,
          ),
          onPressed: () => ref.read(authControllerProvider.notifier).logout(),
          icon: const Icon(Icons.logout, size: 18),
          label: const Text(S.logout),
        ),
      );
}
