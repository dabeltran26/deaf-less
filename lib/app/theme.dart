import 'package:flutter/material.dart';

ThemeData buildLightTheme() {
  const seed = Color(0xFF7B3AED);
  final scheme = ColorScheme.fromSeed(
    seedColor: seed,
    brightness: Brightness.light,
  );
  return ThemeData(colorScheme: scheme, useMaterial3: true);
}

ThemeData buildDarkTheme() {
  const seed = Color(0xFF7B3AED);
  final scheme = ColorScheme.fromSeed(
    seedColor: seed,
    brightness: Brightness.dark,
  );
  return ThemeData(colorScheme: scheme, useMaterial3: true);
}
