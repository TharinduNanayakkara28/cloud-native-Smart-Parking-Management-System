import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../domain/penalty_model.dart';
import '../data/penalty_repository.dart';
import '../../../shared/widgets/status_badge.dart';

class MyPenaltiesScreen extends ConsumerWidget {
  const MyPenaltiesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final penaltiesAsync = ref.watch(myPenaltiesProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('My Penalties')),
      body: penaltiesAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => _ErrorState(
          message: e.toString(),
          onRetry: () => ref.invalidate(myPenaltiesProvider),
        ),
        data: (penalties) => penalties.isEmpty
            ? const _EmptyState()
            : RefreshIndicator(
                onRefresh: () => ref.refresh(myPenaltiesProvider.future),
                child: ListView.separated(
                  padding: const EdgeInsets.all(16),
                  itemCount: penalties.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 12),
                  itemBuilder: (context, index) {
                    final penalty = penalties[index];
                    return _PenaltyCard(
                      penalty: penalty,
                      onPay: penalty.status == PenaltyStatus.issued
                          ? () => _handlePay(context, ref, penalty)
                          : null,
                    );
                  },
                ),
              ),
      ),
    );
  }

  Future<void> _handlePay(
    BuildContext context,
    WidgetRef ref,
    PenaltyModel penalty,
  ) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Confirm Payment'),
        content: Text(
          'Pay \$${penalty.fineAmount.toStringAsFixed(2)} for a '
          '${penalty.tier.label.toLowerCase()} issued against ${penalty.vehiclePlate}?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Keep'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Pay Now'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    try {
      await ref.read(penaltyRepositoryProvider).pay(penalty.id);
      ref.invalidate(myPenaltiesProvider);
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(e.toString()),
            backgroundColor: Theme.of(context).colorScheme.error,
          ),
        );
      }
    }
  }
}

class _PenaltyCard extends StatelessWidget {
  const _PenaltyCard({required this.penalty, this.onPay});

  final PenaltyModel penalty;
  final VoidCallback? onPay;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;
    final dtFmt = DateFormat('d MMM yyyy HH:mm');

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header row: tier badge + status
            Row(
              children: [
                StatusBadge(
                  label: penalty.tier.label,
                  variant: penalty.tier.badgeVariant,
                ),
                const Spacer(),
                _StatusChip(status: penalty.status, isOverdue: penalty.isOverdue),
              ],
            ),
            const SizedBox(height: 12),
            // Details
            _DetailRow(
              icon: Icons.directions_car_outlined,
              label: penalty.vehiclePlate,
            ),
            if (penalty.fineAmount > 0) ...[
              const SizedBox(height: 4),
              _DetailRow(
                icon: Icons.attach_money_outlined,
                label: '\$${penalty.fineAmount.toStringAsFixed(2)}',
                emphasized: penalty.status == PenaltyStatus.issued,
              ),
            ],
            const SizedBox(height: 4),
            _DetailRow(
              icon: Icons.calendar_today_outlined,
              label: 'Issued ${dtFmt.format(penalty.issuedAt.toLocal())}',
            ),
            if (penalty.dueDate != null) ...[
              const SizedBox(height: 4),
              _DetailRow(
                icon: Icons.event_outlined,
                label: 'Due ${dtFmt.format(penalty.dueDate!.toLocal())}',
                color: penalty.isOverdue ? scheme.error : null,
              ),
            ],
            if (penalty.paidAt != null) ...[
              const SizedBox(height: 4),
              _DetailRow(
                icon: Icons.check_circle_outline,
                label: 'Paid ${dtFmt.format(penalty.paidAt!.toLocal())}',
                color: Colors.green,
              ),
            ],
            if (penalty.notes != null && penalty.notes!.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(
                penalty.notes!,
                style: textTheme.bodySmall
                    ?.copyWith(color: scheme.onSurfaceVariant),
              ),
            ],
            // Pay button
            if (onPay != null) ...[
              const SizedBox(height: 12),
              Align(
                alignment: Alignment.centerRight,
                child: FilledButton.icon(
                  onPressed: onPay,
                  icon: const Icon(Icons.payment_outlined, size: 18),
                  label: const Text('Pay Now'),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.status, required this.isOverdue});

  final PenaltyStatus status;
  final bool isOverdue;

  @override
  Widget build(BuildContext context) {
    if (status == PenaltyStatus.paid) {
      return StatusBadge(label: status.label, variant: 'success');
    }
    if (status == PenaltyStatus.waived) {
      return StatusBadge(label: status.label, variant: 'neutral');
    }
    // ISSUED
    return StatusBadge(
      label: isOverdue ? 'Overdue' : status.label,
      variant: isOverdue ? 'error' : 'warning',
    );
  }
}

class _DetailRow extends StatelessWidget {
  const _DetailRow({
    required this.icon,
    required this.label,
    this.color,
    this.emphasized = false,
  });

  final IconData icon;
  final String label;
  final Color? color;
  final bool emphasized;

  @override
  Widget build(BuildContext context) {
    final effective =
        color ?? Theme.of(context).colorScheme.onSurfaceVariant;
    return Row(
      children: [
        Icon(icon, size: 16, color: effective),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            label,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: effective,
                  fontWeight:
                      emphasized ? FontWeight.w600 : FontWeight.normal,
                ),
          ),
        ),
      ],
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
            Icons.verified_outlined,
            size: 64,
            color: Theme.of(context).colorScheme.outline,
          ),
          const SizedBox(height: 16),
          Text(
            'No penalties',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 4),
          Text(
            'Keep parking within your reserved time!',
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
