import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:latlong2/latlong.dart';

import '../../core/api/api_error.dart';
import '../../l10n/strings_vi.dart';
import '../../theme.dart';
import '../../widgets/common.dart';
import '../guidance/guidance_panel.dart';
import '../location/location_service.dart';
import '../map/route_map.dart';
import 'phone_caller.dart';
import 'route_models.dart';
import 'routes_controller.dart';
import 'routes_screen.dart' show LogoutButton;

/// Initial bottom-sheet size (ui-design §8.2).
const double _sheetInitial = 0.45;

/// Route detail (DA-R03…R07, design §5.3, ui-design §4.4 and §8.2).
class RouteDetailScreen extends ConsumerWidget {
  const RouteDetailScreen({super.key, required this.sendId});

  final int sendId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final detail = ref.watch(routeDetailProvider(sendId));
    return Scaffold(
      appBar: AppBar(
        title: Text(detail.value?.vehicleLicensePlate ?? ''),
        actions: const [LogoutButton()],
      ),
      body: switch (detail) {
        AsyncData(:final value) => _DetailBody(route: value),
        AsyncError(:final error) => _DetailError(error: asApiError(error), sendId: sendId),
        _ => const ColoredBox(
            color: Color(0xFFE0E0E0),
            child: Center(child: CircularProgressIndicator()),
          ),
      },
    );
  }
}

class _DetailError extends ConsumerWidget {
  const _DetailError({required this.error, required this.sendId});

  final ApiError error;
  final int sendId;

  @override
  Widget build(BuildContext context, WidgetRef ref) => switch (error) {
        ForbiddenError() || NotFoundError() => MessageView(
            message: error.message,
            icon: Icons.block,
            actionLabel: S.back,
            onAction: () => Navigator.of(context).maybePop(),
          ),
        // A 401 is handled by AuthGate, which returns to Login.
        UnauthorizedError() => const SizedBox.shrink(),
        _ => RetryView(
            message: error.message,
            onRetry: () => ref.invalidate(routeDetailProvider(sendId)),
          ),
      };
}

class _DetailBody extends ConsumerStatefulWidget {
  const _DetailBody({required this.route});

  final DriverRoute route;

  @override
  ConsumerState<_DetailBody> createState() => _DetailBodyState();
}

class _DetailBodyState extends ConsumerState<_DetailBody> {
  final _mapController = MapController();
  StreamSubscription<LatLng>? _positionSub;
  LatLng? _myPosition;
  bool _followMe = false;
  double _sheetExtent = _sheetInitial;

  @override
  void dispose() {
    _positionSub?.cancel();
    _mapController.dispose();
    super.dispose();
  }

  void _moveTo(LatLng point) => _mapController.move(point, _mapController.camera.zoom);

  void _stopFollowing() {
    if (_followMe) setState(() => _followMe = false);
  }

  Future<void> _toggleFollow() async {
    if (_followMe) {
      setState(() => _followMe = false);
      return;
    }
    if (_positionSub == null && !await _startLocation()) return;
    if (!mounted) return;
    setState(() => _followMe = true);
    if (_myPosition != null) _moveTo(_myPosition!);
  }

  /// Explanation first, then the OS prompt (ui-design §4.5).
  Future<bool> _startLocation() async {
    if (!ref.read(locationExplainedProvider)) {
      final accepted = await showLocationExplanation(context);
      if (!accepted || !mounted) return false;
      ref.read(locationExplainedProvider.notifier).accept();
    }
    final service = ref.read(locationServiceProvider);
    final access = await service.requestAccess();
    if (!mounted) return false;
    if (access != LocationAccess.granted) {
      _showLocationDenied(permanent: access == LocationAccess.deniedForever);
      return false;
    }
    _positionSub = service.positions().listen(
      (p) {
        if (!mounted) return;
        setState(() => _myPosition = p);
        if (_followMe) _moveTo(p);
      },
      onError: (Object _) {
        _positionSub?.cancel();
        _positionSub = null;
        if (!mounted) return;
        setState(() => _followMe = false);
        _showLocationDenied(permanent: false);
      },
    );
    return true;
  }

  void _showLocationDenied({required bool permanent}) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: const Text(S.locationDenied),
        action: permanent
            ? SnackBarAction(
                label: S.openSettings,
                onPressed: () => ref.read(locationServiceProvider).openAppSettings(),
              )
            : null,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final route = widget.route;
    final stops = route.stops;
    final rawLeg = ref.watch(legIndexProvider(route.sendId));
    final legIndex = stops.isEmpty ? 0 : rawLeg.clamp(0, stops.length - 1);

    return LayoutBuilder(
      builder: (context, constraints) => Stack(
        children: [
          Positioned.fill(
            child: RouteMap(
              route: route,
              legIndex: legIndex,
              controller: _mapController,
              followMe: _followMe,
              onFollowPressed: _toggleFollow,
              onUserGesture: _stopFollowing,
              myPosition: _myPosition,
              // Follows the sheet, so "fit" and the attribution use the part
              // of the map the sheet does not cover.
              bottomInset: constraints.maxHeight * _sheetExtent,
            ),
          ),
          NotificationListener<DraggableScrollableNotification>(
            onNotification: (n) {
              if ((n.extent - _sheetExtent).abs() > 0.005) setState(() => _sheetExtent = n.extent);
              return false;
            },
            child: DraggableScrollableSheet(
              initialChildSize: _sheetInitial,
              minChildSize: 0.2,
              maxChildSize: 0.9,
              builder: (context, scrollController) => Material(
                elevation: 12,
                color: Colors.white,
                shadowColor: Colors.black54,
                borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
                child: _SheetContent(
                  route: route,
                  legIndex: legIndex,
                  scrollController: scrollController,
                  onStopTap: (stop) {
                    _stopFollowing();
                    _moveTo(stop.point);
                  },
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _SheetContent extends ConsumerWidget {
  const _SheetContent({
    required this.route,
    required this.legIndex,
    required this.scrollController,
    required this.onStopTap,
  });

  final DriverRoute route;
  final int legIndex;
  final ScrollController scrollController;
  final void Function(DriverStop stop) onStopTap;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final stops = route.stops;
    final superseded = route.isSuperseded;
    final nextIndex = legIndex + 1 < stops.length ? legIndex + 1 : null;
    final legs = stops.isEmpty ? 0 : stops.length - 1;
    final legController = ref.read(legIndexProvider(route.sendId).notifier);

    return ListView(
      controller: scrollController,
      // Keep the last row above the system navigation bar.
      padding: EdgeInsets.fromLTRB(16, 8, 16, 24 + MediaQuery.viewPaddingOf(context).bottom),
      children: [
        Center(
          child: Container(
            width: 32,
            height: 4,
            margin: const EdgeInsets.only(bottom: 12),
            decoration: BoxDecoration(
              color: AppColors.cardBorder,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
        ),
        if (superseded) ...[
          const StatusBanner.danger(key: Key('detail.supersededBanner'), text: S.supersededBanner),
          const SizedBox(height: 12),
        ],
        if (route.hasZones) ...[
          const StatusBanner.warning(key: Key('detail.zoneBanner'), text: S.zoneBanner),
          const SizedBox(height: 12),
        ],
        // Superseded mode: the guidance card is not built at all (DT-07).
        if (!superseded && nextIndex != null) ...[
          GuidanceCard(key: const Key('detail.guidance'), instructions: stops[legIndex].instructionsToNext),
          const SizedBox(height: 16),
        ],
        if (stops.isNotEmpty)
          _NextStopCard(
            stop: nextIndex == null ? null : stops[nextIndex],
            position: nextIndex ?? legs,
            legs: legs,
            showLegButtons: !superseded,
            canGoBack: legIndex > 0,
            onArrived: () => legController.advance(stops.length),
            onBack: legController.back,
          ),
        const SizedBox(height: 8),
        ExpansionTile(
          key: const Key('detail.allStops'),
          tilePadding: EdgeInsets.zero,
          childrenPadding: const EdgeInsets.only(bottom: 8),
          leading: const Icon(Icons.format_list_numbered, color: AppColors.primary),
          title: Text(S.allStops(stops.length), style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
          children: [
            for (var i = 0; i < stops.length; i++)
              _StopRow(
                index: i,
                stop: stops[i],
                passed: i <= legIndex,
                isNext: i == nextIndex,
                onTap: () => onStopTap(stops[i]),
              ),
          ],
        ),
      ],
    );
  }
}

class _NextStopCard extends ConsumerWidget {
  const _NextStopCard({
    required this.stop,
    required this.position,
    required this.legs,
    required this.showLegButtons,
    required this.canGoBack,
    required this.onArrived,
    required this.onBack,
  });

  /// Null once the last stop has been reached.
  final DriverStop? stop;
  final int position;
  final int legs;
  final bool showLegButtons;
  final bool canGoBack;
  final VoidCallback onArrived;
  final VoidCallback onBack;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = stop;
    final phone = s?.customerPhone?.trim() ?? '';

    return Container(
      key: const Key('detail.nextStop'),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.nextStopBackground,
        border: Border.all(color: AppColors.nextStopBorder),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (s == null)
            const Row(
              children: [
                Icon(Icons.flag_circle, color: AppColors.depot, size: 28),
                SizedBox(width: 8),
                Expanded(
                  child: Text(S.routeFinished, style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
                ),
              ],
            )
          else ...[
            Text(
              '${S.nextStop} · $position/$legs'.toUpperCase(),
              style: const TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w700,
                letterSpacing: 0.5,
                color: AppColors.primary,
              ),
            ),
            const SizedBox(height: 6),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Text(
                    s.locationName,
                    style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700, color: AppColors.text),
                  ),
                ),
                const SizedBox(width: 8),
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: s.isDepot
                      ? const LabelBadge.depot(text: S.depotChip)
                      : const LabelBadge(text: S.orderBadge),
                ),
              ],
            ),
            const SizedBox(height: 6),
            if (s.arrivalTime != null) MetaLine(icon: Icons.schedule, text: formatClock(s.arrivalTime)),
            if (s.address != null && s.address!.isNotEmpty) MetaLine(icon: Icons.place_outlined, text: s.address!),
            if (!s.isDepot) ...[
              if (s.customerName != null && s.customerName!.isNotEmpty)
                MetaLine(icon: Icons.person_outline, text: s.customerName!),
              if (phone.isNotEmpty)
                InkWell(
                  key: const Key('detail.phone'),
                  borderRadius: BorderRadius.circular(8),
                  onTap: () => ref.read(phoneCallerProvider).call(phone),
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(minHeight: 48),
                    child: Row(
                      children: [
                        const Icon(Icons.phone, size: 20, color: AppColors.primary),
                        const SizedBox(width: 8),
                        Text(
                          phone,
                          style: const TextStyle(
                            color: AppColors.primary,
                            fontSize: 16,
                            fontWeight: FontWeight.w600,
                            decoration: TextDecoration.underline,
                            decorationColor: AppColors.primary,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              if (s.deliveryNotes != null && s.deliveryNotes!.isNotEmpty)
                MetaLine(icon: Icons.sticky_note_2_outlined, text: '${S.notesPrefix} ${s.deliveryNotes!}'),
            ],
            if (s.zoneNames.isNotEmpty) ...[
              const SizedBox(height: 6),
              _ZoneChip(names: s.zoneNames),
            ],
          ],
          if (showLegButtons) ...[
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    key: const Key('detail.back'),
                    style: OutlinedButton.styleFrom(minimumSize: const Size(48, 56), backgroundColor: Colors.white),
                    onPressed: canGoBack ? onBack : null,
                    child: const Text(S.back),
                  ),
                ),
                if (s != null) ...[
                  const SizedBox(width: 12),
                  Expanded(
                    flex: 2,
                    child: FilledButton.icon(
                      key: const Key('detail.arrived'),
                      onPressed: onArrived,
                      icon: const Icon(Icons.check),
                      label: const Text(S.arrived, textAlign: TextAlign.center),
                    ),
                  ),
                ],
              ],
            ),
          ],
        ],
      ),
    );
  }
}

class _ZoneChip extends StatelessWidget {
  const _ZoneChip({required this.names});

  final List<String> names;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        decoration: BoxDecoration(
          color: AppColors.warningBackground,
          borderRadius: BorderRadius.circular(6),
          border: Border.all(color: AppColors.zoneRing.withValues(alpha: 0.4)),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.shield_outlined, size: 16, color: AppColors.warningText),
            const SizedBox(width: 4),
            Flexible(
              child: Text(
                S.zoneChip(names.join(', ')),
                style: const TextStyle(fontSize: 13, color: AppColors.warningText, fontWeight: FontWeight.w500),
              ),
            ),
          ],
        ),
      );
}

class _StopRow extends StatelessWidget {
  const _StopRow({
    required this.index,
    required this.stop,
    required this.passed,
    required this.isNext,
    required this.onTap,
  });

  final int index;
  final DriverStop stop;
  final bool passed;
  final bool isNext;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Opacity(
      opacity: passed ? 0.55 : 1,
      child: Material(
        color: isNext ? AppColors.primaryLight : Colors.transparent,
        borderRadius: BorderRadius.circular(8),
        child: ListTile(
          key: Key('detail.stopRow.$index'),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
          contentPadding: const EdgeInsets.symmetric(horizontal: 8),
          onTap: onTap,
          leading: SizedBox(
            width: 30,
            child: passed
                ? const Icon(Icons.check_circle, color: AppColors.depot)
                : StopMarker(stop: stop, label: '${stop.sequenceNumber ?? index}', passed: false, size: 28),
          ),
          title: Text(
            stop.locationName,
            style: TextStyle(fontSize: 15, fontWeight: isNext ? FontWeight.w700 : FontWeight.w500),
          ),
          subtitle: stop.zoneNames.isEmpty
              ? null
              : Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Align(alignment: Alignment.centerLeft, child: _ZoneChip(names: stop.zoneNames)),
                ),
          trailing: stop.arrivalTime == null
              ? null
              : Text(
                  formatClock(stop.arrivalTime),
                  style: const TextStyle(fontSize: 14, color: AppColors.textSecondary),
                ),
        ),
      ),
    );
  }
}
