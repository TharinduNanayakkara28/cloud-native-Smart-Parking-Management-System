import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../domain/payment_model.dart';
import '../data/payment_repository.dart';
import '../../reservation/domain/reservation_model.dart';
import '../../reservation/data/reservation_repository.dart';
import '../../../shared/widgets/status_badge.dart';

class PaymentReceiptScreen extends ConsumerStatefulWidget {
  const PaymentReceiptScreen({super.key, required this.reservationId});

  final String reservationId;

  @override
  ConsumerState<PaymentReceiptScreen> createState() =>
      _PaymentReceiptScreenState();
}

class _PaymentReceiptScreenState extends ConsumerState<PaymentReceiptScreen> {
  PaymentModel? _payment;
  ReservationModel? _reservation;
  bool _isLoading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });
    try {
      final results = await Future.wait([
        ref
            .read(paymentRepositoryProvider)
            .getByReservationId(widget.reservationId),
        ref
            .read(reservationRepositoryProvider)
            .getById(widget.reservationId),
      ]);
      if (!mounted) return;
      setState(() {
        _payment = results[0] as PaymentModel;
        _reservation = results[1] as ReservationModel;
        _isLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.toString();
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Payment Receipt'),
        leading: BackButton(
          onPressed: () =>
              context.canPop() ? context.pop() : context.go('/home/reservations'),
        ),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? _ErrorView(error: _error!, onRetry: _load)
              : _ReceiptView(
                  payment: _payment!,
                  reservation: _reservation!,
                ),
    );
  }
}

class _ReceiptView extends StatelessWidget {
  const _ReceiptView({required this.payment, required this.reservation});

  final PaymentModel payment;
  final ReservationModel reservation;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;
    final dtFmt = DateFormat('d MMM HH:mm');

    final duration =
        reservation.reservedUntil.difference(reservation.reservedFrom);
    final durationLabel = duration.inMinutes >= 60
        ? '${duration.inHours}h ${duration.inMinutes.remainder(60).toString().padLeft(2, '0')}m'
        : '${duration.inMinutes}m';

    final displayAmount = payment.status == PaymentStatus.refunded
        ? payment.refundedAmount ?? payment.amount
        : payment.amount;

    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        children: [
          // Amount hero card
          Card(
            color: _statusBackground(scheme, payment.status),
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 32, horizontal: 24),
              child: SizedBox(
                width: double.infinity,
                child: Column(
                  children: [
                    Icon(_statusIcon(payment.status), size: 52),
                    const SizedBox(height: 12),
                    Text(
                      payment.status == PaymentStatus.refunded
                          ? '-\$${displayAmount.toStringAsFixed(2)}'
                          : '\$${displayAmount.toStringAsFixed(2)}',
                      style: textTheme.displaySmall
                          ?.copyWith(fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 8),
                    StatusBadge(
                      label: payment.status.label,
                      variant: _badgeVariant(payment.status),
                    ),
                  ],
                ),
              ),
            ),
          ),
          const SizedBox(height: 16),
          // Details
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  _InfoRow(
                    label: 'Reference',
                    value: '#${payment.id.substring(0, 8).toUpperCase()}',
                  ),
                  const Divider(height: 24),
                  _InfoRow(
                    label: 'Spot',
                    value: reservation.spotNumber ?? reservation.spotId,
                  ),
                  _InfoRow(label: 'Plate', value: reservation.vehiclePlate),
                  _InfoRow(label: 'Duration', value: durationLabel),
                  _InfoRow(
                    label: 'Time',
                    value:
                        '${dtFmt.format(reservation.reservedFrom.toLocal())} → ${dtFmt.format(reservation.reservedUntil.toLocal())}',
                  ),
                  const Divider(height: 24),
                  _InfoRow(
                    label: 'Authorised',
                    value: dtFmt.format(payment.createdAt.toLocal()),
                  ),
                  if (payment.capturedAt != null)
                    _InfoRow(
                      label: 'Captured',
                      value: dtFmt.format(payment.capturedAt!.toLocal()),
                    ),
                  if (payment.refundedAt != null)
                    _InfoRow(
                      label: 'Refunded',
                      value: dtFmt.format(payment.refundedAt!.toLocal()),
                    ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Color _statusBackground(ColorScheme s, PaymentStatus status) =>
      switch (status) {
        PaymentStatus.captured => s.primaryContainer,
        PaymentStatus.refunded => s.tertiaryContainer,
        PaymentStatus.failed => s.errorContainer,
        PaymentStatus.preAuthorized => s.surfaceContainerHighest,
      };

  IconData _statusIcon(PaymentStatus status) => switch (status) {
        PaymentStatus.captured => Icons.check_circle_outline,
        PaymentStatus.refunded => Icons.replay_outlined,
        PaymentStatus.failed => Icons.cancel_outlined,
        PaymentStatus.preAuthorized => Icons.hourglass_empty_outlined,
      };

  String _badgeVariant(PaymentStatus status) => switch (status) {
        PaymentStatus.captured => 'success',
        PaymentStatus.refunded => 'info',
        PaymentStatus.failed => 'error',
        PaymentStatus.preAuthorized => 'neutral',
      };
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Text(
            label,
            style: Theme.of(context)
                .textTheme
                .bodyMedium
                ?.copyWith(color: scheme.onSurfaceVariant),
          ),
          const Spacer(),
          Flexible(
            child: Text(
              value,
              textAlign: TextAlign.right,
              style: const TextStyle(fontWeight: FontWeight.w500),
            ),
          ),
        ],
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.error, required this.onRetry});

  final String error;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.receipt_long_outlined, size: 48),
            const SizedBox(height: 16),
            Text(error, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            FilledButton(onPressed: onRetry, child: const Text('Retry')),
          ],
        ),
      ),
    );
  }
}
