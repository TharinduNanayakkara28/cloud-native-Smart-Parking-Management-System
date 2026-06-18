import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../data/reservation_repository.dart';
import '../domain/reservation_model.dart';
import '../../../shared/widgets/error_banner.dart';
import '../../../shared/widgets/loading_button.dart';
import '../../../shared/widgets/status_badge.dart';

class ReservationDetailScreen extends ConsumerStatefulWidget {
  const ReservationDetailScreen({super.key, required this.reservationId});

  final String reservationId;

  @override
  ConsumerState<ReservationDetailScreen> createState() =>
      _ReservationDetailScreenState();
}

class _ReservationDetailScreenState
    extends ConsumerState<ReservationDetailScreen> {
  ReservationModel? _reservation;
  bool _isLoading = true;
  String? _loadError;
  String? _actionError;
  bool _actionLoading = false;

  // Single timer drives both countdown display (every 1s) and polling.
  Timer? _timer;
  int _ticks = 0;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final r = await ref
          .read(reservationRepositoryProvider)
          .getById(widget.reservationId);
      if (!mounted) return;
      setState(() {
        _reservation = r;
        _isLoading = false;
      });
      _manageTimer(r);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _loadError = e.toString();
        _isLoading = false;
      });
    }
  }

  /// Quiet refresh — does not show loading indicator.
  Future<void> _refresh() async {
    try {
      final r = await ref
          .read(reservationRepositoryProvider)
          .getById(widget.reservationId);
      if (!mounted) return;
      setState(() => _reservation = r);
      _manageTimer(r);
    } catch (_) {}
  }

  void _manageTimer(ReservationModel r) {
    _timer?.cancel();
    if (r.status == ReservationStatus.completed ||
        r.status == ReservationStatus.cancelled ||
        r.status == ReservationStatus.expired) {
      return; // terminal state — no timer needed
    }
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      setState(() => _ticks++);
      // Poll API: every 3s while PENDING, every 30s while ACTIVE
      final pollEvery =
          _reservation?.status == ReservationStatus.pending ? 3 : 30;
      if (_ticks % pollEvery == 0) _refresh();
    });
  }

  // ── actions ──────────────────────────────────────────────────────────────

  Future<void> _doAction(Future<ReservationModel> Function() action) async {
    setState(() {
      _actionLoading = true;
      _actionError = null;
    });
    try {
      final updated = await action();
      if (!mounted) return;
      setState(() => _reservation = updated);
      _manageTimer(updated);
    } catch (e) {
      if (!mounted) return;
      setState(() => _actionError = e.toString());
    } finally {
      if (mounted) setState(() => _actionLoading = false);
    }
  }

  Future<void> _cancel() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Cancel Reservation'),
        content: const Text(
            'Are you sure you want to cancel? A refund will be issued.'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Keep')),
          FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Cancel Reservation')),
        ],
      ),
    );
    if (confirmed != true) return;
    await _doAction(() =>
        ref.read(reservationRepositoryProvider).cancel(widget.reservationId));
  }

  Future<void> _checkIn() => _doAction(() =>
      ref.read(reservationRepositoryProvider).checkIn(widget.reservationId));

  Future<void> _checkOut() => _doAction(() =>
      ref.read(reservationRepositoryProvider).checkOut(widget.reservationId));

  // ── helpers ───────────────────────────────────────────────────────────────

  static String _fmt(DateTime dt) =>
      DateFormat('EEE, d MMM  HH:mm').format(dt.toLocal());

  static String _formatCountdown(Duration d) {
    if (d.isNegative) return 'Overdue';
    final h = d.inHours;
    final m = d.inMinutes.remainder(60);
    final s = d.inSeconds.remainder(60);
    if (h > 0) {
      return '${h}h ${m.toString().padLeft(2, '0')}m';
    }
    return '${m}m ${s.toString().padLeft(2, '0')}s';
  }

  BadgeVariant _variantFor(ReservationStatus s) => switch (s) {
        ReservationStatus.pending => BadgeVariant.warning,
        ReservationStatus.active => BadgeVariant.success,
        ReservationStatus.completed => BadgeVariant.info,
        ReservationStatus.expired => BadgeVariant.neutral,
        ReservationStatus.cancelled => BadgeVariant.error,
      };

  // ── build ─────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return Scaffold(
        appBar: AppBar(title: const Text('Reservation')),
        body: const Center(child: CircularProgressIndicator()),
      );
    }
    if (_loadError != null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Reservation')),
        body: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            children: [
              ErrorBanner(message: _loadError!),
              const SizedBox(height: 16),
              FilledButton(onPressed: _load, child: const Text('Retry')),
            ],
          ),
        ),
      );
    }

    final r = _reservation!;
    final cs = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Reservation Details'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_outlined),
            onPressed: _refresh,
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Status header
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  children: [
                    Icon(Icons.local_parking_rounded,
                        size: 36, color: cs.primary),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            r.spotNumber != null
                                ? 'Spot ${r.spotNumber}'
                                : 'Parking Spot',
                            style: Theme.of(context)
                                .textTheme
                                .titleMedium
                                ?.copyWith(fontWeight: FontWeight.bold),
                          ),
                          Text(
                            r.vehiclePlate,
                            style: Theme.of(context)
                                .textTheme
                                .bodySmall
                                ?.copyWith(color: cs.onSurfaceVariant),
                          ),
                        ],
                      ),
                    ),
                    StatusBadge(
                      label: r.status.label,
                      variant: _variantFor(r.status),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 12),

            // Time details
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  children: [
                    _DetailRow(
                        icon: Icons.login_outlined,
                        label: 'From',
                        value: _fmt(r.reservedFrom)),
                    const Divider(height: 20),
                    _DetailRow(
                        icon: Icons.logout_outlined,
                        label: 'Until',
                        value: _fmt(r.reservedUntil)),
                    if (r.checkedInAt != null) ...[
                      const Divider(height: 20),
                      _DetailRow(
                          icon: Icons.check_circle_outline,
                          label: 'Checked In',
                          value: _fmt(r.checkedInAt!)),
                    ],
                    if (r.totalAmount != null) ...[
                      const Divider(height: 20),
                      _DetailRow(
                          icon: Icons.payments_outlined,
                          label: 'Amount',
                          value: '\$${r.totalAmount!.toStringAsFixed(2)}'),
                    ],
                  ],
                ),
              ),
            ),
            const SizedBox(height: 12),

            // Countdown / elapsed / pending indicator
            _StatusWidget(reservation: r, formatCountdown: _formatCountdown),
            const SizedBox(height: 20),

            // Action error
            if (_actionError != null) ...[
              ErrorBanner(message: _actionError!),
              const SizedBox(height: 12),
            ],

            // Action buttons
            _ActionButtons(
              reservation: r,
              isLoading: _actionLoading,
              onCheckIn: _checkIn,
              onCheckOut: _checkOut,
              onCancel: _cancel,
              onViewReceipt: () =>
                  context.go('/payment/${r.id}'),
            ),
          ],
        ),
      ),
    );
  }
}

// ── subwidgets ────────────────────────────────────────────────────────────────

class _DetailRow extends StatelessWidget {
  const _DetailRow(
      {required this.icon, required this.label, required this.value});

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, size: 18, color: Theme.of(context).colorScheme.primary),
        const SizedBox(width: 8),
        Text(label,
            style: const TextStyle(fontWeight: FontWeight.w500, fontSize: 13)),
        const Spacer(),
        Text(value,
            style: TextStyle(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
                fontSize: 13)),
      ],
    );
  }
}

class _StatusWidget extends StatelessWidget {
  const _StatusWidget(
      {required this.reservation, required this.formatCountdown});

  final ReservationModel reservation;
  final String Function(Duration) formatCountdown;

  @override
  Widget build(BuildContext context) {
    final r = reservation;

    if (r.status == ReservationStatus.pending) {
      return Card(
        color: Colors.amber.shade50,
        child: const Padding(
          padding: EdgeInsets.all(16),
          child: Row(
            children: [
              SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(
                      strokeWidth: 2, color: Colors.amber)),
              SizedBox(width: 12),
              Expanded(
                  child: Text('Awaiting payment confirmation…',
                      style: TextStyle(color: Colors.amber))),
            ],
          ),
        ),
      );
    }

    if (r.status == ReservationStatus.active) {
      if (r.isCheckedIn) {
        final elapsed = DateTime.now().difference(r.checkedInAt!);
        return Card(
          color: Colors.green.shade50,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                const Icon(Icons.timer_outlined, color: Colors.green),
                const SizedBox(width: 12),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Time parked',
                        style: TextStyle(
                            color: Colors.green, fontWeight: FontWeight.bold)),
                    Text(formatCountdown(elapsed),
                        style: const TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                            color: Colors.green)),
                  ],
                ),
              ],
            ),
          ),
        );
      } else {
        final remaining =
            r.reservedUntil.toLocal().difference(DateTime.now());
        return Card(
          color: remaining.isNegative
              ? Colors.red.shade50
              : Colors.blue.shade50,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                Icon(Icons.hourglass_bottom_outlined,
                    color: remaining.isNegative ? Colors.red : Colors.blue),
                const SizedBox(width: 12),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      remaining.isNegative
                          ? 'Reservation overdue'
                          : 'Time remaining',
                      style: TextStyle(
                          color: remaining.isNegative
                              ? Colors.red
                              : Colors.blue,
                          fontWeight: FontWeight.bold),
                    ),
                    Text(
                      formatCountdown(remaining),
                      style: TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.bold,
                          color: remaining.isNegative
                              ? Colors.red
                              : Colors.blue),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      }
    }

    return const SizedBox.shrink();
  }
}

class _ActionButtons extends StatelessWidget {
  const _ActionButtons({
    required this.reservation,
    required this.isLoading,
    required this.onCheckIn,
    required this.onCheckOut,
    required this.onCancel,
    required this.onViewReceipt,
  });

  final ReservationModel reservation;
  final bool isLoading;
  final VoidCallback onCheckIn;
  final VoidCallback onCheckOut;
  final VoidCallback onCancel;
  final VoidCallback onViewReceipt;

  @override
  Widget build(BuildContext context) {
    final r = reservation;

    if (r.status == ReservationStatus.pending) {
      return const SizedBox.shrink();
    }

    if (r.status == ReservationStatus.active && !r.isCheckedIn) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          LoadingButton(
            label: 'Check In',
            isLoading: isLoading,
            onPressed: onCheckIn,
          ),
          const SizedBox(height: 8),
          OutlinedButton(
            onPressed: isLoading ? null : onCancel,
            style: OutlinedButton.styleFrom(
              minimumSize: const Size.fromHeight(44),
              foregroundColor: Colors.red,
              side: const BorderSide(color: Colors.red),
            ),
            child: const Text('Cancel Reservation'),
          ),
        ],
      );
    }

    if (r.status == ReservationStatus.active && r.isCheckedIn) {
      return LoadingButton(
        label: 'Check Out',
        isLoading: isLoading,
        onPressed: onCheckOut,
      );
    }

    if (r.status == ReservationStatus.completed) {
      return FilledButton.tonal(
        onPressed: onViewReceipt,
        style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(48)),
        child: const Text('View Receipt'),
      );
    }

    return const SizedBox.shrink();
  }
}
