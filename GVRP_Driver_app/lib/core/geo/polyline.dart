import 'package:latlong2/latlong.dart';

/// Decodes a Google encoded polyline (precision 1e5) into `[lat, lon]` points.
///
/// The server sends `geometry_to_next` in this format (design §4, context §2).
/// Returns an empty list when [encoded] is null, empty or malformed, so the
/// map falls back to the dashed straight line instead of crashing (design §4:
/// "the decoder never throws on absent data").
///
/// Algorithm (Google "Encoded Polyline Algorithm Format"):
/// 1. Read characters one by one; subtract 63 from each code unit.
/// 2. Each value holds 5 bits of payload (`value & 0x1f`); bit 0x20 means
///    "another chunk follows". Collect chunks, least significant first,
///    shifting each by 5 more bits.
/// 3. When a value without 0x20 ends the number: if the lowest bit is 1 the
///    number is negative, `~(result >> 1)`; otherwise `result >> 1`.
/// 4. Numbers come in pairs (lat, lon) and are deltas: add each to the
///    running lat / lon.
/// 5. Divide the running totals by 1e5 to get degrees.
List<LatLng> decodePolyline(String? encoded) {
  if (encoded == null || encoded.isEmpty) return const [];

  final points = <LatLng>[];
  var index = 0;
  var lat = 0;
  var lon = 0;

  // Reads one signed number starting at [index]. Returns null when the input
  // ends inside a number, holds a character outside the format, or a number
  // is longer than any valid coordinate delta (6 chunks).
  int? readNumber() {
    var result = 0;
    var shift = 0;
    while (true) {
      if (index >= encoded.length || shift > 25) return null;
      final value = encoded.codeUnitAt(index++) - 63;
      if (value < 0 || value > 63) return null;
      result |= (value & 0x1f) << shift;
      shift += 5;
      if (value & 0x20 == 0) break;
    }
    return (result & 1) == 1 ? ~(result >> 1) : result >> 1;
  }

  while (index < encoded.length) {
    final dLat = readNumber();
    final dLon = readNumber();
    if (dLat == null || dLon == null) return const [];
    lat += dLat;
    lon += dLon;
    final latDeg = lat / 1e5;
    final lonDeg = lon / 1e5;
    if (latDeg.abs() > 90 || lonDeg.abs() > 180) return const [];
    points.add(LatLng(latDeg, lonDeg));
  }
  return points;
}
