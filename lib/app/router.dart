import 'package:deaf_less/features/monitoring/presentation/pages/home_page.dart';
import 'package:deaf_less/features/settings/presentation/pages/settings_page.dart';
import 'package:go_router/go_router.dart';

GoRouter createRouter() {
  return GoRouter(
    routes: <RouteBase>[
      GoRoute(
        path: '/',
        name: 'home',
        pageBuilder: (context, state) =>
            const NoTransitionPage(child: HomePage()),
      ),
      GoRoute(
        path: '/settings',
        name: 'settings',
        pageBuilder: (context, state) =>
            const NoTransitionPage(child: SettingsPage()),
      ),
    ],
  );
}
