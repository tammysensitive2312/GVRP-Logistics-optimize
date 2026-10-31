import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// DT-12 (DA-R08): no external navigation or map hand-off anywhere in `lib/`.
void main() {
  // `flutter test` runs with the package root as the working directory.
  final libFiles = Directory('lib')
      .listSync(recursive: true)
      .whereType<File>()
      .where((f) => f.path.endsWith('.dart'))
      .toList();

  const forbidden = ['geo:', 'google.navigation', 'maps.google', 'comgooglemaps', 'maps.app.goo.gl'];

  test('DT-12 lib/ holds Dart sources to scan', () {
    expect(libFiles, isNotEmpty);
  });

  test('DT-12 no navigation intent or Maps URL string in lib/', () {
    final hits = <String>[];
    for (final file in libFiles) {
      final lines = file.readAsLinesSync();
      for (var i = 0; i < lines.length; i++) {
        final lower = lines[i].toLowerCase();
        for (final needle in forbidden) {
          if (lower.contains(needle)) hits.add('${file.path}:${i + 1} contains "$needle"');
        }
      }
    }
    expect(hits, isEmpty, reason: hits.join('\n'));
  });

  test('DT-12 url_launcher is used only by the tel: phone caller', () {
    final users = [
      for (final file in libFiles)
        if (file.readAsStringSync().contains('package:url_launcher/')) file.path.replaceAll(r'\', '/'),
    ];
    expect(users, ['lib/features/routes/phone_caller.dart']);
    final source = File('lib/features/routes/phone_caller.dart').readAsStringSync();
    expect(RegExp(r'_launch\(').allMatches(source), hasLength(1));
    expect(source, contains("_launch(Uri(scheme: 'tel', path: number))"));
  });
}
