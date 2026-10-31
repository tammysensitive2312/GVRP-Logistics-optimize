import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'route_models.dart';
import 'routes_repository.dart';

/// My routes list (design §5.2).
class RoutesController extends AsyncNotifier<List<RouteSend>> {
  @override
  Future<List<RouteSend>> build() => ref.read(routesRepositoryProvider).fetchRoutes();

  /// Pull to refresh. On failure the old list stays and the error is
  /// rethrown so the screen can show it in a SnackBar (ui-design §4.3).
  Future<void> refresh() async {
    try {
      final routes = await ref.read(routesRepositoryProvider).fetchRoutes();
      if (ref.mounted) state = AsyncData(routes);
    } catch (e, st) {
      if (ref.mounted && !state.hasValue) state = AsyncError(e, st);
      rethrow;
    }
  }
}

final routesControllerProvider =
    AsyncNotifierProvider.autoDispose<RoutesController, List<RouteSend>>(RoutesController.new);

/// One route detail, fetched again on every open (ui-design §2).
final routeDetailProvider = FutureProvider.autoDispose.family<DriverRoute, int>(
  (ref, sendId) => ref.read(routesRepositoryProvider).fetchRoute(sendId),
);

/// Local leg progress (design §5.3): leg `i` goes from `stops[i]` to
/// `stops[i + 1]`. Not persisted; reopening a route starts at 0.
class LegController extends Notifier<int> {
  LegController(this.sendId);

  final int sendId;

  @override
  int build() => 0;

  /// "Đã đến điểm này". [stopCount] bounds the index to the last stop.
  void advance(int stopCount) {
    if (state < stopCount - 1) state = state + 1;
  }

  /// "Quay lại".
  void back() {
    if (state > 0) state = state - 1;
  }
}

final legIndexProvider = NotifierProvider.autoDispose.family<LegController, int, int>(LegController.new);
