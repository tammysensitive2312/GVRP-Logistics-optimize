import 'package:flutter/material.dart';

/// Visual tokens, `ui-design.md` §8.1 (revision 2: web-client style).
abstract final class AppColors {
  static const primary = Color(0xFF1976D2);
  static const primaryLight = Color(0xFFE3F2FD);
  static const primaryDark = Color(0xFF0D47A1);
  static const nextStopBackground = Color(0xFFF5F9FF);
  static const nextStopBorder = Color(0xFFBBDEFB);
  static const outlineAccent = Color(0xFF4A90E2);
  static const headerGradientStart = Color(0xFF4A90E2);
  static const headerGradientEnd = Color(0xFF357ABD);
  static const depot = Color(0xFF0B8043);
  static const depotLight = Color(0xFFE6F4EA);
  static const background = Color(0xFFF8F9FA);
  static const inputBorder = Color(0xFFE5E5E5);
  static const cardBorder = Color(0xFFDDDDDD);
  static const text = Color(0xFF333333);
  static const textSecondary = Color(0xFF666666);
  static const danger = Color(0xFFB42318);
  static const dangerBackground = Color(0xFFFDECEA);

  // Unchanged from ui-design §3.
  static const warningBackground = Color(0xFFFAEEDA);
  static const warningText = Color(0xFF633806);
  static const myPosition = Color(0xFF185FA5);
  static const zoneRing = Color(0xFFA32D2D);
}

ThemeData buildAppTheme() {
  final scheme = ColorScheme.fromSeed(seedColor: AppColors.primary).copyWith(
    primary: AppColors.primary,
    onPrimary: Colors.white,
    surface: Colors.white,
    onSurface: AppColors.text,
    onSurfaceVariant: AppColors.textSecondary,
    outlineVariant: AppColors.inputBorder,
  );
  const radius8 = BorderRadius.all(Radius.circular(8));
  OutlineInputBorder inputBorder(Color color) => OutlineInputBorder(
        borderRadius: radius8,
        borderSide: BorderSide(color: color, width: 1.5),
      );

  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: AppColors.background,
    textTheme: const TextTheme(
      bodyLarge: TextStyle(fontSize: 16, color: AppColors.text),
      bodyMedium: TextStyle(fontSize: 16, color: AppColors.text),
    ),
    appBarTheme: const AppBarTheme(
      backgroundColor: Colors.white,
      foregroundColor: AppColors.text,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      scrolledUnderElevation: 0,
      titleSpacing: 16,
      titleTextStyle: TextStyle(fontSize: 18, fontWeight: FontWeight.w600, color: AppColors.text),
      shape: Border(bottom: BorderSide(color: AppColors.inputBorder)),
    ),
    cardTheme: const CardThemeData(
      color: Colors.white,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(
        borderRadius: radius8,
        side: BorderSide(color: AppColors.cardBorder),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: AppColors.background,
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 16),
      border: inputBorder(AppColors.inputBorder),
      enabledBorder: inputBorder(AppColors.inputBorder),
      focusedBorder: inputBorder(AppColors.primary),
      prefixIconColor: AppColors.textSecondary,
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size.fromHeight(56),
        shape: const RoundedRectangleBorder(borderRadius: radius8),
        textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        minimumSize: const Size(48, 48),
        foregroundColor: AppColors.outlineAccent,
        side: const BorderSide(color: AppColors.outlineAccent),
        shape: const RoundedRectangleBorder(borderRadius: radius8),
        textStyle: const TextStyle(fontSize: 15, fontWeight: FontWeight.w500),
      ),
    ),
    expansionTileTheme: const ExpansionTileThemeData(
      shape: Border(),
      collapsedShape: Border(),
      iconColor: AppColors.primary,
      collapsedIconColor: AppColors.textSecondary,
    ),
    snackBarTheme: const SnackBarThemeData(behavior: SnackBarBehavior.floating),
  );
}
