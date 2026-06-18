import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../data/reservation_repository.dart';
import '../../../shared/widgets/error_banner.dart';
import '../../../shared/widgets/loading_button.dart';

// Must match PAYMENT_HOURLY_RATE env var in docker-compose.yml
const _kHourlyRate = 2.00;

class ReservationFormScreen extends ConsumerStatefulWidget {
  const ReservationFormScreen({
    super.key,
    required this.spotId,
    required this.spotNumber,
  });

  final String spotId;
  final String spotNumber;

  @override
  ConsumerState<ReservationFormScreen> createState() =>
      _ReservationFormScreenState();
}

class _ReservationFormScreenState
    extends ConsumerState<ReservationFormScreen> {
  final _plateController = TextEditingController();
  DateTime? _from;
  DateTime? _until;
  bool _isSubmitting = false;
  String? _error;

  @override
  void dispose() {
    _plateController.dispose();
    super.dispose();
  }

  double? get _estimatedCost {
    if (_from == null || _until == null) return null;
    final minutes = _until!.difference(_from!).inMinutes;
    if (minutes <= 0) return null;
    return (minutes / 60.0) * _kHourlyRate;
  }

  String? get _durationLabel {
    if (_from == null || _until == null) return null;
    final minutes = _until!.difference(_from!).inMinutes;
    if (minutes <= 0) return null;
    final h = minutes ~/ 60;
    final m = minutes % 60;
    return h > 0 ? '${h}h ${m}m' : '${m}m';
  }

  Future<void> _pickDateTime({required bool isFrom}) async {
    final now = DateTime.now();
    final initial = isFrom
        ? (_from ?? now.add(const Duration(minutes: 5)))
        : (_until ?? (_from ?? now).add(const Duration(hours: 1)));

    final date = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: now.subtract(const Duration(minutes: 1)),
      lastDate: now.add(const Duration(days: 30)),
    );
    if (date == null || !mounted) return;

    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(initial),
    );
    if (time == null || !mounted) return;

    final picked =
        DateTime(date.year, date.month, date.day, time.hour, time.minute);
    setState(() {
      if (isFrom) {
        _from = picked;
        // push Until forward if it's now before From
        if (_until != null && !_until!.isAfter(picked)) {
          _until = picked.add(const Duration(hours: 1));
        }
      } else {
        _until = picked;
      }
      _error = null;
    });
  }

  Future<void> _submit() async {
    final plate = _plateController.text.trim();
    if (plate.isEmpty) {
      setState(() => _error = 'Vehicle plate is required');
      return;
    }
    if (_from == null) {
      setState(() => _error = 'Select a start time');
      return;
    }
    if (_until == null) {
      setState(() => _error = 'Select an end time');
      return;
    }
    if (!_until!.isAfter(_from!)) {
      setState(() => _error = 'End time must be after start time');
      return;
    }
    if (_until!.difference(_from!).inMinutes < 15) {
      setState(() => _error = 'Minimum reservation duration is 15 minutes');
      return;
    }

    setState(() {
      _isSubmitting = true;
      _error = null;
    });

    try {
      final reservation =
          await ref.read(reservationRepositoryProvider).create(
                spotId: widget.spotId,
                vehiclePlate: plate.toUpperCase(),
                reservedFrom: _from!,
                reservedUntil: _until!,
              );
      if (!mounted) return;
      context.go('/reservation/${reservation.id}');
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _isSubmitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(title: Text('Reserve Spot ${widget.spotNumber}')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Spot info card
            Card(
              child: ListTile(
                leading: Icon(Icons.local_parking_rounded, color: cs.primary),
                title: Text(
                  'Spot ${widget.spotNumber}',
                  style: const TextStyle(fontWeight: FontWeight.bold),
                ),
                trailing: Chip(
                  label: const Text('Free',
                      style: TextStyle(
                          color: Colors.green, fontWeight: FontWeight.bold)),
                  backgroundColor: Colors.green.shade50,
                  side: BorderSide(color: Colors.green.shade200),
                  visualDensity: VisualDensity.compact,
                ),
              ),
            ),
            const SizedBox(height: 20),

            // Vehicle plate
            Text('Vehicle Plate',
                style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 8),
            TextField(
              controller: _plateController,
              textCapitalization: TextCapitalization.characters,
              decoration: const InputDecoration(
                hintText: 'e.g. ABC-1234',
                prefixIcon: Icon(Icons.directions_car_outlined),
              ),
              onChanged: (_) => setState(() => _error = null),
            ),
            const SizedBox(height: 20),

            // Duration pickers
            Text('Duration', style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 8),
            _DateTimeButton(
              label: 'From',
              value: _from,
              onTap: () => _pickDateTime(isFrom: true),
            ),
            const SizedBox(height: 8),
            _DateTimeButton(
              label: 'Until',
              value: _until,
              onTap: () => _pickDateTime(isFrom: false),
            ),
            const SizedBox(height: 20),

            // Summary card
            if (_from != null && _until != null && _estimatedCost != null) ...[
              Card(
                color: cs.primaryContainer,
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    children: [
                      _SummaryRow(
                        label: 'Duration',
                        value: _durationLabel ?? '',
                      ),
                      const SizedBox(height: 6),
                      _SummaryRow(
                        label: 'Rate',
                        value: '\$$_kHourlyRate / hour',
                      ),
                      const Divider(height: 16),
                      _SummaryRow(
                        label: 'Estimated Cost',
                        value:
                            '\$${_estimatedCost!.toStringAsFixed(2)}',
                        bold: true,
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 20),
            ],

            if (_error != null) ...[
              ErrorBanner(message: _error!),
              const SizedBox(height: 16),
            ],

            LoadingButton(
              label: 'Confirm Reservation',
              isLoading: _isSubmitting,
              onPressed: _submit,
            ),
            const SizedBox(height: 12),
            Text(
              'Payment will be pre-authorised and captured on check-out.',
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: cs.onSurfaceVariant,
                  ),
            ),
          ],
        ),
      ),
    );
  }
}

class _DateTimeButton extends StatelessWidget {
  const _DateTimeButton({
    required this.label,
    required this.value,
    required this.onTap,
  });

  final String label;
  final DateTime? value;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final fmt = value != null
        ? '${DateFormat('EEE, d MMM').format(value!)}  •  ${DateFormat('HH:mm').format(value!)}'
        : 'Select $label time';

    return OutlinedButton.icon(
      icon: const Icon(Icons.schedule_outlined),
      label: Text(fmt),
      onPressed: onTap,
      style: OutlinedButton.styleFrom(
        minimumSize: const Size(double.infinity, 52),
        alignment: Alignment.centerLeft,
      ),
    );
  }
}

class _SummaryRow extends StatelessWidget {
  const _SummaryRow({
    required this.label,
    required this.value,
    this.bold = false,
  });

  final String label;
  final String value;
  final bool bold;

  @override
  Widget build(BuildContext context) {
    final style = bold
        ? Theme.of(context)
            .textTheme
            .bodyMedium
            ?.copyWith(fontWeight: FontWeight.bold)
        : Theme.of(context).textTheme.bodyMedium;

    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label, style: style),
        Text(value, style: style),
      ],
    );
  }
}
