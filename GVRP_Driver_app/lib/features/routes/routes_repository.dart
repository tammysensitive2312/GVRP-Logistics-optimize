import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/api_client.dart';
import '../../core/api/api_error.dart';
import 'route_models.dart';

/// Read-only access to the driver endpoints (context §1). Nothing is cached
/// or persisted: every call goes to the server (design §1.4).
class RoutesRepository {
  RoutesRepository(this._api);

  final ApiClient _api;

  /// The caller's sends, in server order (CURRENT first).
  Future<List<RouteSend>> fetchRoutes() async {
    final json = await _api.getJson('/api/v1/driver/routes');
    if (json is! List) throw const ServerError(null);
    return _parse(() => [
          for (final item in json)
            if (item is Map) RouteSend.fromJson(item.cast<String, Object?>()) else throw const ModelFormatException('routes[]'),
        ]);
  }

  Future<DriverRoute> fetchRoute(int sendId) async {
    final json = await _api.getJson('/api/v1/driver/routes/{sendId}', params: {'sendId': '$sendId'});
    if (json is! Map) throw const ServerError(null);
    return _parse(() => DriverRoute.fromJson(json.cast<String, Object?>()));
  }

  /// A body the app cannot read is reported like a server failure.
  static T _parse<T>(T Function() parse) {
    try {
      return parse();
    } on ModelFormatException {
      throw const ServerError(null);
    }
  }
}

final routesRepositoryProvider = Provider<RoutesRepository>(
  (ref) => RoutesRepository(ref.watch(apiClientProvider)),
);
