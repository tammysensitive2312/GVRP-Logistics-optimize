import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

/// The only use of `url_launcher` in the app: a `tel:` link (DA-R03, DA-R08).
/// No map, navigation or web URL is ever launched.
class PhoneCaller {
  PhoneCaller([Future<bool> Function(Uri uri)? launcher]) : _launch = launcher ?? launchUrl;

  final Future<bool> Function(Uri uri) _launch;

  Future<void> call(String phone) async {
    final number = phone.trim();
    if (number.isEmpty) return;
    await _launch(Uri(scheme: 'tel', path: number));
  }
}

final phoneCallerProvider = Provider<PhoneCaller>((ref) => PhoneCaller());
