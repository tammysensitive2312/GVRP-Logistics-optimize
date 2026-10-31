import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app.dart';

void main() {
  // Riverpod 3 retries failed providers by default. The app shows errors with
  // an explicit "Thử lại" instead (design §1.3), so retrying is disabled.
  runApp(ProviderScope(retry: (_, _) => null, child: const GvrpDriverApp()));
}
