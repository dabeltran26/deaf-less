import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'router.dart';
import 'theme.dart';

class App extends StatelessWidget {
  const App({super.key});

  @override
  Widget build(BuildContext context) {
    final GoRouter router = createRouter();

    return MaterialApp.router(
      title: 'Deef less',
      theme: buildLightTheme(),
      darkTheme: buildDarkTheme(),
      routerConfig: router,
      debugShowCheckedModeBanner: false,
    );
  }
}
