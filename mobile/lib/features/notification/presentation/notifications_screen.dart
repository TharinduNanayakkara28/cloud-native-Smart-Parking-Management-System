import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../domain/notification_model.dart';
import '../data/notification_repository.dart';
import '../../../core/notifications/local_notification_service.dart';

class NotificationsScreen extends ConsumerWidget {
  const NotificationsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final notificationsAsync = ref.watch(notificationsProvider);

    // Fire local push for the first unread penalty when data first arrives.
    ref.listen<AsyncValue<List<NotificationModel>>>(
      notificationsProvider,
      (previous, next) {
        if (previous?.isLoading == true && next.hasValue) {
          final penalty = next.value!.where(
            (n) => !n.read && n.type.toUpperCase().contains('PENALTY'),
          );
          if (penalty.isNotEmpty) {
            LocalNotificationService.showPenaltyAlert(
              title: penalty.first.title,
              body: penalty.first.message,
            );
          }
        }
      },
    );

    return Scaffold(
      appBar: AppBar(
        title: const Text('Notifications'),
        actions: [
          IconButton(
            icon: const Icon(Icons.done_all_outlined),
            tooltip: 'Mark all read',
            onPressed: () async {
              try {
                await ref
                    .read(notificationRepositoryProvider)
                    .markAllRead();
                ref.invalidate(notificationsProvider);
                ref.invalidate(unreadCountProvider);
              } catch (_) {}
            },
          ),
        ],
      ),
      body: notificationsAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => _ErrorState(
          message: e.toString(),
          onRetry: () => ref.invalidate(notificationsProvider),
        ),
        data: (notifications) => notifications.isEmpty
            ? const _EmptyState()
            : RefreshIndicator(
                onRefresh: () => ref.refresh(notificationsProvider.future),
                child: ListView.separated(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  itemCount: notifications.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 1),
                  itemBuilder: (context, index) {
                    final n = notifications[index];
                    return _NotificationTile(
                      notification: n,
                      onTap: () async {
                        if (!n.read) {
                          try {
                            await ref
                                .read(notificationRepositoryProvider)
                                .markAsRead(n.id);
                          } catch (_) {}
                          ref.invalidate(notificationsProvider);
                          ref.invalidate(unreadCountProvider);
                        }
                        final route = _routeFor(n);
                        if (route != null && context.mounted) {
                          context.push(route);
                        }
                      },
                    );
                  },
                ),
              ),
      ),
    );
  }

  String? _routeFor(NotificationModel n) {
    final type = n.type.toUpperCase();
    if (type.contains('RESERVATION') && n.entityId != null) {
      return '/reservation/${n.entityId}';
    }
    if (type.contains('PAYMENT') && n.entityId != null) {
      return '/payment/${n.entityId}';
    }
    if (type.contains('PENALTY')) return '/penalties';
    return null;
  }
}

class _NotificationTile extends StatelessWidget {
  const _NotificationTile({
    required this.notification,
    required this.onTap,
  });

  final NotificationModel notification;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;
    final unread = !notification.read;

    return InkWell(
      onTap: onTap,
      child: Container(
        decoration: BoxDecoration(
          color: unread ? scheme.primaryContainer.withOpacity(0.25) : null,
          border: unread
              ? Border(
                  left: BorderSide(color: scheme.primary, width: 3),
                )
              : null,
        ),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _TypeAvatar(type: notification.type, scheme: scheme),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        child: Text(
                          notification.title,
                          style: textTheme.bodyLarge?.copyWith(
                            fontWeight:
                                unread ? FontWeight.w600 : FontWeight.normal,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        _timeLabel(notification.createdAt),
                        style: textTheme.bodySmall?.copyWith(
                          color: scheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 2),
                  Text(
                    notification.message,
                    style: textTheme.bodyMedium
                        ?.copyWith(color: scheme.onSurfaceVariant),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _timeLabel(DateTime dt) {
    final diff = DateTime.now().difference(dt);
    if (diff.inMinutes < 1) return 'just now';
    if (diff.inHours < 1) return '${diff.inMinutes}m ago';
    if (diff.inDays < 1) return '${diff.inHours}h ago';
    if (diff.inDays < 7) return '${diff.inDays}d ago';
    return DateFormat('d MMM').format(dt.toLocal());
  }
}

class _TypeAvatar extends StatelessWidget {
  const _TypeAvatar({required this.type, required this.scheme});

  final String type;
  final ColorScheme scheme;

  @override
  Widget build(BuildContext context) {
    final upType = type.toUpperCase();

    if (upType.contains('PENALTY')) {
      return CircleAvatar(
        radius: 20,
        backgroundColor: scheme.errorContainer,
        child: Icon(Icons.warning_amber_rounded,
            size: 20, color: scheme.onErrorContainer),
      );
    }
    if (upType.contains('PAYMENT')) {
      return CircleAvatar(
        radius: 20,
        backgroundColor: scheme.secondaryContainer,
        child: Icon(Icons.payment_outlined,
            size: 20, color: scheme.onSecondaryContainer),
      );
    }
    return CircleAvatar(
      radius: 20,
      backgroundColor: scheme.primaryContainer,
      child: Icon(Icons.local_parking_outlined,
          size: 20, color: scheme.onPrimaryContainer),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.notifications_none_outlined,
            size: 64,
            color: Theme.of(context).colorScheme.outline,
          ),
          const SizedBox(height: 16),
          Text(
            'No notifications yet',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 4),
          Text(
            'Reservation updates will appear here',
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
          ),
        ],
      ),
    );
  }
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.cloud_off_outlined, size: 48),
            const SizedBox(height: 16),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            FilledButton(onPressed: onRetry, child: const Text('Retry')),
          ],
        ),
      ),
    );
  }
}
