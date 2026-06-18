import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../data/reservation_repository.dart';
import '../domain/reservation_model.dart';
import '../../../shared/widgets/status_badge.dart';

class MyReservationsScreen extends ConsumerWidget {
  const MyReservationsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final reservationsAsync = ref.watch(myReservationsProvider);

    return DefaultTabController(
      length: 3,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('My Reservations'),
          bottom: const TabBar(
            tabs: [
              Tab(text: 'Active'),
              Tab(text: 'Upcoming'),
              Tab(text: 'Past'),
            ],
          ),
        ),
        body: reservationsAsync.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.error_outline, size: 48, color: Colors.red),
                const SizedBox(height: 12),
                Text(e.toString(),
                    textAlign: TextAlign.center,
                    style: const TextStyle(color: Colors.red)),
                const SizedBox(height: 16),
                FilledButton(
                  onPressed: () => ref.invalidate(myReservationsProvider),
                  child: const Text('Retry'),
                ),
              ],
            ),
          ),
          data: (all) {
            final active = all
                .where((r) => r.status == ReservationStatus.active)
                .toList();
            final upcoming = all
                .where((r) => r.status == ReservationStatus.pending)
                .toList();
            final past = all
                .where((r) => [
                      ReservationStatus.completed,
                      ReservationStatus.cancelled,
                      ReservationStatus.expired,
                    ].contains(r.status))
                .toList();

            return TabBarView(
              children: [
                _ReservationList(
                  reservations: active,
                  emptyMessage: 'No active reservations',
                  onRefresh: () =>
                      ref.refresh(myReservationsProvider.future),
                ),
                _ReservationList(
                  reservations: upcoming,
                  emptyMessage: 'No upcoming reservations',
                  onRefresh: () =>
                      ref.refresh(myReservationsProvider.future),
                ),
                _ReservationList(
                  reservations: past,
                  emptyMessage: 'No past reservations',
                  onRefresh: () =>
                      ref.refresh(myReservationsProvider.future),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _ReservationList extends StatelessWidget {
  const _ReservationList({
    required this.reservations,
    required this.emptyMessage,
    required this.onRefresh,
  });

  final List<ReservationModel> reservations;
  final String emptyMessage;
  final Future<void> Function() onRefresh;

  @override
  Widget build(BuildContext context) {
    if (reservations.isEmpty) {
      return RefreshIndicator(
        onRefresh: onRefresh,
        child: CustomScrollView(
          slivers: [
            SliverFillRemaining(
              child: Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.bookmark_border_outlined,
                        size: 56,
                        color: Theme.of(context).colorScheme.outlineVariant),
                    const SizedBox(height: 12),
                    Text(emptyMessage,
                        style: TextStyle(
                            color: Theme.of(context)
                                .colorScheme
                                .onSurfaceVariant)),
                  ],
                ),
              ),
            ),
          ],
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView.separated(
        padding: const EdgeInsets.all(16),
        itemCount: reservations.length,
        separatorBuilder: (_, __) => const SizedBox(height: 8),
        itemBuilder: (context, i) =>
            _ReservationTile(reservation: reservations[i]),
      ),
    );
  }
}

class _ReservationTile extends StatelessWidget {
  const _ReservationTile({required this.reservation});

  final ReservationModel reservation;

  BadgeVariant _variantFor(ReservationStatus s) => switch (s) {
        ReservationStatus.pending => BadgeVariant.warning,
        ReservationStatus.active => BadgeVariant.success,
        ReservationStatus.completed => BadgeVariant.info,
        ReservationStatus.expired => BadgeVariant.neutral,
        ReservationStatus.cancelled => BadgeVariant.error,
      };

  @override
  Widget build(BuildContext context) {
    final r = reservation;
    final cs = Theme.of(context).colorScheme;
    final dateFmt = DateFormat('d MMM • HH:mm');

    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () => context.go('/reservation/${r.id}'),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(Icons.local_parking_rounded,
                      color: cs.primary, size: 20),
                  const SizedBox(width: 8),
                  Text(
                    r.spotNumber != null ? 'Spot ${r.spotNumber}' : 'Parking',
                    style: const TextStyle(fontWeight: FontWeight.bold),
                  ),
                  const Spacer(),
                  StatusBadge(
                    label: r.status.label,
                    variant: _variantFor(r.status),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Icon(Icons.directions_car_outlined,
                      size: 14, color: cs.onSurfaceVariant),
                  const SizedBox(width: 4),
                  Text(r.vehiclePlate,
                      style: TextStyle(
                          fontSize: 13, color: cs.onSurfaceVariant)),
                  const Spacer(),
                  Icon(Icons.schedule_outlined,
                      size: 14, color: cs.onSurfaceVariant),
                  const SizedBox(width: 4),
                  Text(
                    '${dateFmt.format(r.reservedFrom.toLocal())} – ${DateFormat('HH:mm').format(r.reservedUntil.toLocal())}',
                    style: TextStyle(
                        fontSize: 12, color: cs.onSurfaceVariant),
                  ),
                ],
              ),
              if (r.totalAmount != null) ...[
                const SizedBox(height: 4),
                Text(
                  '\$${r.totalAmount!.toStringAsFixed(2)}',
                  style: TextStyle(
                      fontWeight: FontWeight.w600, color: cs.primary),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
