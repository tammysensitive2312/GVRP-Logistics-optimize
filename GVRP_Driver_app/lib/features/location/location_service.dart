import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import 'package:latlong2/latlong.dart';

import '../../l10n/strings_vi.dart';

enum LocationAccess { granted, denied, deniedForever, serviceDisabled }

/// Driver location for the map only (DA-R07). Positions are shown on the
/// device and never sent to the server or logged.
abstract interface class LocationService {
  /// Shows the OS prompt when needed.
  Future<LocationAccess> requestAccess();

  Stream<LatLng> positions();

  /// Opens this app's own settings page.
  Future<void> openAppSettings();
}

class GeolocatorLocationService implements LocationService {
  @override
  Future<LocationAccess> requestAccess() async {
    if (!await Geolocator.isLocationServiceEnabled()) return LocationAccess.serviceDisabled;
    var permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
    }
    return switch (permission) {
      LocationPermission.always || LocationPermission.whileInUse => LocationAccess.granted,
      LocationPermission.deniedForever => LocationAccess.deniedForever,
      LocationPermission.denied || LocationPermission.unableToDetermine => LocationAccess.denied,
    };
  }

  @override
  Stream<LatLng> positions() => Geolocator.getPositionStream(
        locationSettings: const LocationSettings(accuracy: LocationAccuracy.high, distanceFilter: 10),
      ).map((p) => LatLng(p.latitude, p.longitude));

  @override
  Future<void> openAppSettings() async {
    await Geolocator.openAppSettings();
  }
}

final locationServiceProvider = Provider<LocationService>((ref) => GeolocatorLocationService());

/// Whether the in-app explanation was accepted in this app session. Memory
/// only, so it is shown again after a restart.
class LocationExplained extends Notifier<bool> {
  @override
  bool build() => false;

  void accept() => state = true;
}

final locationExplainedProvider = NotifierProvider<LocationExplained, bool>(LocationExplained.new);

/// The explanation shown before the OS prompt (ui-design §4.5). Returns true
/// on "Tiếp tục".
Future<bool> showLocationExplanation(BuildContext context) async {
  final result = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      title: const Text(S.locationDialogTitle),
      content: const Text(S.locationExplain),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text(S.later)),
        FilledButton(
          style: FilledButton.styleFrom(minimumSize: const Size(48, 48)),
          onPressed: () => Navigator.of(context).pop(true),
          child: const Text(S.continueLabel),
        ),
      ],
    ),
  );
  return result ?? false;
}
