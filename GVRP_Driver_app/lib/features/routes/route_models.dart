import 'package:latlong2/latlong.dart';

import '../../core/geo/polyline.dart';

/// Client models (design §4). They mirror the Entry API's snake_case JSON,
/// ignore unknown fields and accept the absence of the LEZ-001 P1b fields
/// (`geometry_to_next`, `instructions_to_next`, `zone_names`).
///
/// Times are kept as the server's local wall-clock text and never converted
/// between time zones (`sent_at` is a `LocalDateTime`, `arrival_time` a
/// `LocalTime`).

enum SendStatus {
  current,
  superseded,
  unknown;

  static SendStatus parse(Object? value) => switch (value) {
        'CURRENT' => SendStatus.current,
        'SUPERSEDED' => SendStatus.superseded,
        _ => SendStatus.unknown,
      };
}

enum StopType {
  order,
  depot;

  static StopType parse(Object? value) => value == 'DEPOT' ? StopType.depot : StopType.order;
}

/// Thrown when a required field is missing or has the wrong type.
class ModelFormatException implements Exception {
  const ModelFormatException(this.field);
  final String field;
  @override
  String toString() => 'ModelFormatException($field)';
}

/// One item of `GET /api/v1/driver/routes`.
class RouteSend {
  const RouteSend({
    required this.id,
    required this.solutionId,
    required this.routeId,
    required this.vehicleLicensePlate,
    required this.driverName,
    required this.senderName,
    required this.sentAt,
    required this.status,
  });

  final int id;
  final int? solutionId;
  final int? routeId;
  final String vehicleLicensePlate;
  final String? driverName;
  final String? senderName;

  /// Raw ISO local date-time from the server, for example `2026-10-27T08:05:00`.
  final String? sentAt;
  final SendStatus status;

  bool get isSuperseded => status == SendStatus.superseded;

  factory RouteSend.fromJson(Map<String, Object?> json) => RouteSend(
        id: _reqInt(json, 'id'),
        solutionId: _optInt(json, 'solution_id'),
        routeId: _optInt(json, 'route_id'),
        vehicleLicensePlate: _optString(json, 'vehicle_license_plate') ?? '',
        driverName: _optString(json, 'driver_name'),
        senderName: _optString(json, 'sender_name'),
        sentAt: _optString(json, 'sent_at'),
        status: SendStatus.parse(json['status']),
      );
}

/// `GET /api/v1/driver/routes/{sendId}`.
class DriverRoute {
  const DriverRoute({
    required this.sendId,
    required this.solutionId,
    required this.routeId,
    required this.vehicleLicensePlate,
    required this.status,
    required this.stops,
  });

  final int sendId;
  final int? solutionId;
  final int? routeId;
  final String vehicleLicensePlate;
  final SendStatus status;

  /// Plan order: start depot first, end depot last.
  final List<DriverStop> stops;

  bool get isSuperseded => status == SendStatus.superseded;

  bool get hasZones => stops.any((s) => s.zoneNames.isNotEmpty);

  factory DriverRoute.fromJson(Map<String, Object?> json) {
    final rawStops = json['stops'];
    if (rawStops != null && rawStops is! List) throw const ModelFormatException('stops');
    return DriverRoute(
      sendId: _reqInt(json, 'send_id'),
      solutionId: _optInt(json, 'solution_id'),
      routeId: _optInt(json, 'route_id'),
      vehicleLicensePlate: _optString(json, 'vehicle_license_plate') ?? '',
      status: SendStatus.parse(json['status']),
      stops: [
        for (final s in (rawStops as List?) ?? const [])
          if (s is Map) DriverStop.fromJson(s.cast<String, Object?>()) else throw const ModelFormatException('stops[]'),
      ],
    );
  }
}

class DriverStop {
  const DriverStop({
    this.sequenceNumber,
    required this.type,
    this.orderId,
    required this.locationName,
    this.address,
    required this.latitude,
    required this.longitude,
    this.arrivalTime,
    this.departureTime,
    this.customerName,
    this.customerPhone,
    this.deliveryNotes,
    this.demand,
    this.geometryToNext,
    this.instructionsToNext = const [],
    this.zoneNames = const [],
  });

  final int? sequenceNumber;
  final StopType type;
  final int? orderId;
  final String locationName;
  final String? address;
  final double latitude;
  final double longitude;

  /// `HH:mm:ss` local time, as sent.
  final String? arrivalTime;
  final String? departureTime;
  final String? customerName;
  final String? customerPhone;
  final String? deliveryNotes;
  final num? demand;

  /// [p1b] Encoded polyline (1e5, `[lat, lon]`) of the leg to the next stop.
  final String? geometryToNext;

  /// [p1b] Turn instructions of the leg to the next stop; empty when absent.
  final List<Instruction> instructionsToNext;

  /// [p1b] Names of restricted zones at this stop; empty when absent.
  final List<String> zoneNames;

  bool get isDepot => type == StopType.depot;

  LatLng get point => LatLng(latitude, longitude);

  /// Decoded [geometryToNext]; empty when absent or malformed.
  List<LatLng> get decodedGeometryToNext => decodePolyline(geometryToNext);

  factory DriverStop.fromJson(Map<String, Object?> json) {
    final instructions = json['instructions_to_next'];
    final zones = json['zone_names'];
    return DriverStop(
      sequenceNumber: _optInt(json, 'sequence_number'),
      type: StopType.parse(json['type']),
      orderId: _optInt(json, 'order_id'),
      locationName: _optString(json, 'location_name') ?? '',
      address: _optString(json, 'address'),
      latitude: _reqDouble(json, 'latitude'),
      longitude: _reqDouble(json, 'longitude'),
      arrivalTime: _optString(json, 'arrival_time'),
      departureTime: _optString(json, 'departure_time'),
      customerName: _optString(json, 'customer_name'),
      customerPhone: _optString(json, 'customer_phone'),
      deliveryNotes: _optString(json, 'delivery_notes'),
      demand: json['demand'] is num ? json['demand'] as num : null,
      geometryToNext: _optString(json, 'geometry_to_next'),
      instructionsToNext: [
        if (instructions is List)
          for (final i in instructions)
            if (i is Map) Instruction.fromJson(i.cast<String, Object?>()),
      ],
      zoneNames: [
        if (zones is List)
          for (final z in zones)
            if (z is String && z.trim().isNotEmpty) z,
      ],
    );
  }
}

/// [p1b] One step of `instructions_to_next`.
class Instruction {
  const Instruction({
    required this.text,
    this.distanceM,
    this.timeMs,
    this.sign,
    this.streetName,
  });

  final String text;

  /// Metres.
  final double? distanceM;

  /// Milliseconds.
  final int? timeMs;
  final int? sign;
  final String? streetName;

  factory Instruction.fromJson(Map<String, Object?> json) => Instruction(
        text: _optString(json, 'text') ?? '',
        distanceM: (json['distance_m'] is num) ? (json['distance_m'] as num).toDouble() : null,
        timeMs: _optInt(json, 'time_ms'),
        sign: _optInt(json, 'sign'),
        streetName: _optString(json, 'street_name'),
      );
}

int _reqInt(Map<String, Object?> json, String key) =>
    _optInt(json, key) ?? (throw ModelFormatException(key));

int? _optInt(Map<String, Object?> json, String key) {
  final v = json[key];
  if (v is int) return v;
  if (v is num && v == v.roundToDouble()) return v.toInt();
  return null;
}

double _reqDouble(Map<String, Object?> json, String key) {
  final v = json[key];
  if (v is num && v.isFinite) return v.toDouble();
  throw ModelFormatException(key);
}

String? _optString(Map<String, Object?> json, String key) {
  final v = json[key];
  return v is String ? v : null;
}

/// `2026-10-27T08:05:00[.fff]` → `27/10/2026 08:05`, as-is, without any
/// time-zone conversion (ui-design §4.3). Unknown formats are returned raw.
String formatSentAt(String? iso) {
  if (iso == null) return '';
  final m = RegExp(r'^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})').firstMatch(iso);
  if (m == null) return iso;
  return '${m[3]}/${m[2]}/${m[1]} ${m[4]}:${m[5]}';
}

/// `08:05:00` → `08:05`. Unknown formats are returned raw.
String formatClock(String? time) {
  if (time == null) return '';
  final m = RegExp(r'^(\d{2}):(\d{2})').firstMatch(time);
  return m == null ? time : '${m[1]}:${m[2]}';
}

/// Under 1000 m: `{m} m`; otherwise `{km:0.0} km` (ui-design §4.4).
String formatDistance(double? metres) {
  if (metres == null) return '';
  if (metres < 1000) return '${metres.round()} m';
  return '${(metres / 1000).toStringAsFixed(1)} km';
}
