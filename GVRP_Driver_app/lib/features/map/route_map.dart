import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:latlong2/latlong.dart';

import '../../l10n/strings_vi.dart';
import '../../theme.dart';
import '../routes/route_models.dart';

/// One leg on the map: `stops[leg]` → `stops[leg + 1]`.
class LegLine {
  const LegLine({required this.leg, required this.points, required this.fromServer});

  final int leg;
  final List<LatLng> points;

  /// True when [points] is the server's decoded `geometry_to_next`; false for
  /// the straight illustrative fallback (DA-R04).
  final bool fromServer;
}

/// Server geometry where present, otherwise a straight line between the two
/// stops. No router is ever called (design §1.2).
List<LegLine> buildLegLines(List<DriverStop> stops) => [
      for (var i = 0; i + 1 < stops.length; i++)
        switch (stops[i].decodedGeometryToNext) {
          final decoded when decoded.length >= 2 => LegLine(leg: i, points: decoded, fromServer: true),
          _ => LegLine(leg: i, points: [stops[i].point, stops[i + 1].point], fromServer: false),
        },
    ];

/// Lets widget tests run without network tiles.
final mapTilesEnabledProvider = Provider<bool>((ref) => true);

/// Same tile source as the web client (`driver-view.component.ts`).
/// Development and testing only: ui-design §8.5 (release blocker).
const String googleTileUrl = 'https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}';

const double minMapZoom = 3;
const double maxMapZoom = 20;

/// Default view when a route has no stops (never happens for a valid send).
const LatLng _fallbackCenter = LatLng(21.0285, 105.8542);

/// The route map (design §5.3, ui-design §8.3).
class RouteMap extends ConsumerWidget {
  const RouteMap({
    super.key,
    required this.route,
    required this.legIndex,
    required this.controller,
    required this.followMe,
    required this.onFollowPressed,
    required this.onUserGesture,
    this.myPosition,
    this.bottomInset = 0,
  });

  final DriverRoute route;
  final int legIndex;
  final MapController controller;
  final bool followMe;
  final VoidCallback onFollowPressed;

  /// Called on any manual pan or zoom gesture and on "fit" (turns follow-me off).
  final VoidCallback onUserGesture;

  /// Driver position; only shown, never sent or logged (DA-R07).
  final LatLng? myPosition;

  /// Height covered by the bottom sheet, kept clear when fitting the stops.
  final double bottomInset;

  List<LatLng> get _stopPoints => [for (final s in route.stops) s.point];

  CameraFit _fitAll() => CameraFit.coordinates(
        coordinates: _stopPoints,
        padding: EdgeInsets.fromLTRB(48, 72, 72, bottomInset + 48),
        maxZoom: 17,
      );

  void _zoomBy(double delta) {
    final camera = controller.camera;
    controller.move(camera.center, (camera.zoom + delta).clamp(minMapZoom, maxMapZoom));
  }

  void _fit() {
    if (_stopPoints.isEmpty) return;
    onUserGesture();
    controller.fitCamera(_fitAll());
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final stops = route.stops;
    final lines = buildLegLines(stops);
    final anyFallback = lines.any((l) => !l.fromServer);
    final points = _stopPoints;

    // Non-current legs first so the current leg is drawn on top.
    final ordered = [
      ...lines.where((l) => l.leg != legIndex),
      ...lines.where((l) => l.leg == legIndex),
    ];

    return Stack(
      children: [
        FlutterMap(
          mapController: controller,
          options: MapOptions(
            initialCenter: points.isEmpty ? _fallbackCenter : points.first,
            initialZoom: 13,
            initialCameraFit: points.isEmpty ? null : _fitAll(),
            minZoom: minMapZoom,
            maxZoom: maxMapZoom,
            interactionOptions: const InteractionOptions(flags: InteractiveFlag.all & ~InteractiveFlag.rotate),
            onPositionChanged: (camera, hasGesture) {
              if (hasGesture) onUserGesture();
            },
          ),
          children: [
            if (ref.watch(mapTilesEnabledProvider))
              TileLayer(
                urlTemplate: googleTileUrl,
                maxZoom: maxMapZoom,
                userAgentPackageName: 'org.truong.gvrp_driver',
              ),
            PolylineLayer(
              polylines: [
                for (final line in ordered) _polyline(line, current: line.leg == legIndex),
              ],
            ),
            MarkerLayer(
              markers: [
                for (final line in lines)
                  if (_arrow(line) case final arrow?)
                    Marker(
                      point: arrow.$1,
                      width: 22,
                      height: 22,
                      child: _DirectionArrow(angle: arrow.$2, current: line.leg == legIndex),
                    ),
              ],
            ),
            MarkerLayer(
              markers: [
                for (var i = 0; i < stops.length; i++)
                  Marker(
                    point: stops[i].point,
                    width: 40,
                    height: 40,
                    child: StopMarker(
                      key: Key('map.stop.$i'),
                      stop: stops[i],
                      label: '${stops[i].sequenceNumber ?? i}',
                      passed: i <= legIndex,
                    ),
                  ),
                if (myPosition != null)
                  Marker(
                    point: myPosition!,
                    width: 40,
                    height: 40,
                    child: const _MyPositionDot(key: Key('map.myPosition')),
                  ),
              ],
            ),
          ],
        ),
        Positioned(
          left: 12,
          top: 12,
          right: 72,
          child: Align(
            alignment: Alignment.topLeft,
            child: anyFallback ? const _FallbackLegend(key: Key('map.fallbackLegend')) : const SizedBox.shrink(),
          ),
        ),
        Positioned(
          right: 12,
          top: 12,
          child: Column(
            children: [
              _ControlGroup(
                children: [
                  _ControlButton(key: const Key('map.zoomIn'), icon: Icons.add, tooltip: S.zoomIn, onPressed: () => _zoomBy(1)),
                  const SizedBox(width: 44, child: Divider(height: 1)),
                  _ControlButton(key: const Key('map.zoomOut'), icon: Icons.remove, tooltip: S.zoomOut, onPressed: () => _zoomBy(-1)),
                ],
              ),
              const SizedBox(height: 10),
              _ControlGroup(
                children: [
                  _ControlButton(key: const Key('map.fitRoute'), icon: Icons.zoom_out_map, tooltip: S.fitRoute, onPressed: _fit),
                ],
              ),
              const SizedBox(height: 10),
              _ControlGroup(
                children: [
                  _ControlButton(
                    key: const Key('map.followMe'),
                    icon: followMe ? Icons.my_location : Icons.location_searching,
                    tooltip: S.followMe,
                    active: followMe,
                    onPressed: onFollowPressed,
                  ),
                ],
              ),
            ],
          ),
        ),
        Positioned(
          left: 8,
          bottom: bottomInset + 4,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
            color: Colors.white.withValues(alpha: 0.8),
            child: const Text(S.googleAttribution, style: TextStyle(fontSize: 11, color: AppColors.text)),
          ),
        ),
      ],
    );
  }

  static Polyline _polyline(LegLine line, {required bool current}) {
    if (line.fromServer) {
      return Polyline(
        points: line.points,
        strokeWidth: current ? 6 : 4,
        color: current ? AppColors.primary : AppColors.primary.withValues(alpha: 0.5),
      );
    }
    return Polyline(
      points: line.points,
      strokeWidth: 4,
      color: AppColors.primary.withValues(alpha: current ? 0.6 : 0.4),
      pattern: StrokePattern.dashed(segments: [10, 10]),
    );
  }

  /// Middle of the leg and its direction in radians clockwise from north
  /// (web `polylineDecorator` arrows). Null for a zero-length leg.
  static (LatLng, double)? _arrow(LegLine line) {
    final pts = line.points;
    final i = (pts.length - 1) ~/ 2;
    final a = pts[i];
    final b = pts[i + 1];
    if (a == b) return null;
    final mid = LatLng((a.latitude + b.latitude) / 2, (a.longitude + b.longitude) / 2);
    // Equirectangular approximation: good enough for an on-screen arrow.
    final dx = (b.longitude - a.longitude) * math.cos(mid.latitude * math.pi / 180);
    final dy = b.latitude - a.latitude;
    return (mid, math.atan2(dx, dy));
  }
}

class _ControlGroup extends StatelessWidget {
  const _ControlGroup({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) => Material(
        color: Colors.white,
        elevation: 3,
        shadowColor: Colors.black38,
        borderRadius: BorderRadius.circular(8),
        clipBehavior: Clip.antiAlias,
        child: Column(mainAxisSize: MainAxisSize.min, children: children),
      );
}

class _ControlButton extends StatelessWidget {
  const _ControlButton({super.key, required this.icon, required this.tooltip, required this.onPressed, this.active = false});

  final IconData icon;
  final String tooltip;
  final VoidCallback onPressed;
  final bool active;

  @override
  Widget build(BuildContext context) => Tooltip(
        message: tooltip,
        child: InkWell(
          onTap: onPressed,
          child: Ink(
            width: 44,
            height: 44,
            color: active ? AppColors.primary : Colors.white,
            child: Icon(icon, color: active ? Colors.white : AppColors.text, size: 22),
          ),
        ),
      );
}

class _DirectionArrow extends StatelessWidget {
  const _DirectionArrow({required this.angle, required this.current});

  final double angle;
  final bool current;

  @override
  Widget build(BuildContext context) => Transform.rotate(
        angle: angle,
        child: Icon(
          Icons.navigation,
          size: 18,
          color: current ? AppColors.primary : AppColors.primary.withValues(alpha: 0.6),
          shadows: const [Shadow(color: Colors.white, blurRadius: 3)],
        ),
      );
}

/// Numbered blue circle for an order stop, green rounded square with a
/// warehouse icon for a depot (shape differs, ui-design §1.3); a red ring
/// marks a stop with `zone_names` (ui-design §8.3).
class StopMarker extends StatelessWidget {
  const StopMarker({super.key, required this.stop, required this.label, required this.passed, this.size = 34});

  final DriverStop stop;
  final String label;
  final bool passed;
  final double size;

  bool get hasZone => stop.zoneNames.isNotEmpty;

  @override
  Widget build(BuildContext context) {
    final border = Border.all(color: hasZone ? AppColors.zoneRing : Colors.white, width: 3);
    const shadow = [BoxShadow(color: Colors.black38, blurRadius: 5, offset: Offset(0, 1))];
    final Widget body = Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: stop.isDepot
          ? BoxDecoration(
              color: AppColors.depot,
              borderRadius: BorderRadius.circular(size * 0.25),
              border: border,
              boxShadow: shadow,
            )
          : BoxDecoration(color: AppColors.primary, shape: BoxShape.circle, border: border, boxShadow: shadow),
      child: stop.isDepot
          ? Icon(Icons.warehouse, size: size * 0.5, color: Colors.white)
          : Text(
              label,
              style: TextStyle(color: Colors.white, fontSize: size * 0.42, fontWeight: FontWeight.w700),
              textScaler: TextScaler.noScaling,
            ),
    );
    return Center(child: Opacity(opacity: passed ? 0.5 : 1, child: body));
  }
}

class _MyPositionDot extends StatelessWidget {
  const _MyPositionDot({super.key});

  @override
  Widget build(BuildContext context) => Center(
        child: Container(
          width: 40,
          height: 40,
          alignment: Alignment.center,
          decoration: BoxDecoration(color: AppColors.myPosition.withValues(alpha: 0.2), shape: BoxShape.circle),
          child: Container(
            width: 14,
            height: 14,
            decoration: BoxDecoration(
              color: AppColors.myPosition,
              shape: BoxShape.circle,
              border: Border.all(color: Colors.white, width: 2),
            ),
          ),
        ),
      );
}

class _FallbackLegend extends StatelessWidget {
  const _FallbackLegend({super.key});

  @override
  Widget build(BuildContext context) => Material(
        color: Colors.white.withValues(alpha: 0.95),
        borderRadius: BorderRadius.circular(8),
        elevation: 2,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text('- - -', style: TextStyle(color: AppColors.primary, fontWeight: FontWeight.w700)),
              const SizedBox(width: 8),
              Flexible(
                child: Text(S.fallbackLegend, style: const TextStyle(fontSize: 12, color: AppColors.text)),
              ),
            ],
          ),
        ),
      );
}
