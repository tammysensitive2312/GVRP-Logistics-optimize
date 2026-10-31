import 'package:flutter_test/flutter_test.dart';
import 'package:gvrp_driver/core/geo/polyline.dart';
import 'package:latlong2/latlong.dart';

/// DT-01: Google encoded polyline decode, precision 1e5, `[lat, lon]`.
void main() {
  // Tolerance far below the 1e-5 resolution of the format.
  const eps = 1e-9;

  void expectPoints(List<LatLng> actual, List<List<double>> expected) {
    expect(actual, hasLength(expected.length));
    for (var i = 0; i < expected.length; i++) {
      expect(actual[i].latitude, closeTo(expected[i][0], eps), reason: 'lat[$i]');
      expect(actual[i].longitude, closeTo(expected[i][1], eps), reason: 'lon[$i]');
    }
  }

  test('DT-01 decodes the published Google example exactly', () {
    expectPoints(decodePolyline('_p~iF~ps|U_ulLnnqC_mqNvxq`@'), [
      [38.5, -120.2],
      [40.7, -120.95],
      [43.252, -126.453],
    ]);
  });

  test('DT-01 decodes a single point at the origin', () {
    expectPoints(decodePolyline('??'), [
      [0.0, 0.0],
    ]);
  });

  test('DT-01 returns an empty list for null or empty input', () {
    expect(decodePolyline(null), isEmpty);
    expect(decodePolyline(''), isEmpty);
  });

  test('DT-01 returns an empty list instead of throwing for malformed input', () {
    // Truncated: the last number never ends (every chunk has the 0x20 bit).
    expect(() => decodePolyline('_p~iF~ps|U_'), returnsNormally);
    expect(decodePolyline('_p~iF~ps|U_'), isEmpty);
  });
}
