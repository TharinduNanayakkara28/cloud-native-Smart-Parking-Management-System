import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../auth/auth_notifier.dart';
import '../../features/auth/presentation/login_screen.dart';
import '../../features/auth/presentation/register_screen.dart';
import '../../features/map/presentation/map_screen.dart';
import '../../features/reservation/presentation/reservation_form_screen.dart';
import '../../features/reservation/presentation/reservation_detail_screen.dart';
import '../../features/reservation/presentation/my_reservations_screen.dart';
import '../../features/payment/presentation/payment_receipt_screen.dart';
import '../../features/notification/presentation/notifications_screen.dart';
import '../../features/notification/data/notification_repository.dart';
import '../../features/penalty/presentation/my_penalties_screen.dart';
import '../../features/vehicle/presentation/vehicles_screen.dart';
import '../../features/profile/presentation/profile_screen.dart';

final routerProvider = Provider<GoRouter>((ref) {
  final notifier = _RouterNotifier(ref);
  return GoRouter(
    initialLocation: '/login',
    refreshListenable: notifier,
    redirect: notifier.redirect,
    routes: [
      GoRoute(
        path: '/login',
        builder: (_, __) => const LoginScreen(),
      ),
      GoRoute(
        path: '/register',
        builder: (_, __) => const RegisterScreen(),
      ),
      GoRoute(
        path: '/reservation/new',
        builder: (_, state) => ReservationFormScreen(
          spotId: state.uri.queryParameters['spotId'] ?? '',
          spotNumber: state.uri.queryParameters['spotNumber'] ?? '',
        ),
      ),
      GoRoute(
        path: '/reservation/:id',
        builder: (_, state) => ReservationDetailScreen(
          reservationId: state.pathParameters['id'] ?? '',
        ),
      ),
      GoRoute(
        path: '/payment/:reservationId',
        builder: (_, state) => PaymentReceiptScreen(
          reservationId: state.pathParameters['reservationId'] ?? '',
        ),
      ),
      GoRoute(
        path: '/penalties',
        builder: (_, __) => const MyPenaltiesScreen(),
      ),
      GoRoute(
        path: '/vehicles',
        builder: (_, __) => const VehiclesScreen(),
      ),
      ShellRoute(
        builder: (_, __, child) => _HomeShell(child: child),
        routes: [
          GoRoute(
            path: '/home/map',
            builder: (_, __) => const MapScreen(),
          ),
          GoRoute(
            path: '/home/reservations',
            builder: (_, __) => const MyReservationsScreen(),
          ),
          GoRoute(
            path: '/home/notifications',
            builder: (_, __) => const NotificationsScreen(),
          ),
          GoRoute(
            path: '/home/profile',
            builder: (_, __) => const ProfileScreen(),
          ),
        ],
      ),
    ],
  );
});

class _RouterNotifier extends ChangeNotifier {
  _RouterNotifier(this._ref) {
    _ref.listen(authNotifierProvider, (_, __) => notifyListeners());
  }

  final Ref _ref;

  String? redirect(BuildContext context, GoRouterState state) {
    final authState = _ref.read(authNotifierProvider);

    if (authState.isLoading) return null;

    final isAuthenticated = authState.valueOrNull != null;
    final onAuthPage = state.matchedLocation == '/login' ||
        state.matchedLocation == '/register';

    if (!isAuthenticated && !onAuthPage) return '/login';
    if (isAuthenticated && onAuthPage) return '/home/map';
    return null;
  }
}

class _HomeShell extends ConsumerWidget {
  const _HomeShell({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final location = GoRouterState.of(context).matchedLocation;
    final unreadCount = ref.watch(unreadCountProvider).valueOrNull ?? 0;

    int tabIndex() {
      if (location.startsWith('/home/map')) return 0;
      if (location.startsWith('/home/reservations')) return 1;
      if (location.startsWith('/home/notifications')) return 2;
      if (location.startsWith('/home/profile')) return 3;
      return 0;
    }

    return Scaffold(
      body: child,
      bottomNavigationBar: NavigationBar(
        selectedIndex: tabIndex(),
        onDestinationSelected: (i) {
          switch (i) {
            case 0:
              context.go('/home/map');
            case 1:
              context.go('/home/reservations');
            case 2:
              context.go('/home/notifications');
            case 3:
              context.go('/home/profile');
          }
        },
        destinations: [
          const NavigationDestination(
            icon: Icon(Icons.map_outlined),
            selectedIcon: Icon(Icons.map),
            label: 'Map',
          ),
          const NavigationDestination(
            icon: Icon(Icons.bookmark_outlined),
            selectedIcon: Icon(Icons.bookmark),
            label: 'Reservations',
          ),
          NavigationDestination(
            icon: Badge(
              isLabelVisible: unreadCount > 0,
              label: Text(unreadCount > 99 ? '99+' : '$unreadCount'),
              child: const Icon(Icons.notifications_outlined),
            ),
            selectedIcon: Badge(
              isLabelVisible: unreadCount > 0,
              label: Text(unreadCount > 99 ? '99+' : '$unreadCount'),
              child: const Icon(Icons.notifications),
            ),
            label: 'Notifications',
          ),
          const NavigationDestination(
            icon: Icon(Icons.person_outline),
            selectedIcon: Icon(Icons.person),
            label: 'Profile',
          ),
        ],
      ),
    );
  }
}
