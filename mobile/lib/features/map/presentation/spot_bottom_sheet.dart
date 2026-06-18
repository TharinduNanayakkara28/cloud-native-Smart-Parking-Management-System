import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../domain/spot_model.dart';

class SpotBottomSheet extends StatelessWidget {
  const SpotBottomSheet({super.key, required this.spot});

  final SpotModel spot;

  @override
  Widget build(BuildContext context) {
    final canReserve = spot.state == SpotState.free;

    return Padding(
      padding: EdgeInsets.fromLTRB(
        24,
        16,
        24,
        MediaQuery.of(context).viewInsets.bottom + 24,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // drag handle
          Center(
            child: Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: Colors.grey[300],
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 20),

          // Spot header
          Row(
            children: [
              _StateCircle(spot.state),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Spot ${spot.spotNumber}',
                      style:
                          Theme.of(context).textTheme.titleLarge?.copyWith(
                                fontWeight: FontWeight.bold,
                              ),
                    ),
                    if (spot.floor != null)
                      Text(
                        'Floor ${spot.floor}',
                        style: Theme.of(context)
                            .textTheme
                            .bodyMedium
                            ?.copyWith(
                              color: Theme.of(context)
                                  .colorScheme
                                  .onSurfaceVariant,
                            ),
                      ),
                  ],
                ),
              ),
              _StateChip(spot.state),
            ],
          ),
          const SizedBox(height: 28),

          FilledButton.icon(
            onPressed: canReserve
                ? () {
                    Navigator.of(context).pop();
                    context.go(
                      '/reservation/new'
                      '?spotId=${spot.id}'
                      '&spotNumber=${spot.spotNumber}',
                    );
                  }
                : null,
            icon: Icon(
                canReserve ? Icons.bookmark_add_outlined : Icons.block_outlined),
            label: Text(canReserve ? 'Reserve This Spot' : 'Spot Unavailable'),
            style:
                FilledButton.styleFrom(minimumSize: const Size.fromHeight(48)),
          ),
          const SizedBox(height: 8),
          OutlinedButton(
            onPressed: () => Navigator.of(context).pop(),
            style: OutlinedButton.styleFrom(
                minimumSize: const Size.fromHeight(44)),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }
}

class _StateCircle extends StatelessWidget {
  const _StateCircle(this.state);

  final SpotState state;

  @override
  Widget build(BuildContext context) {
    final color = _colorFor(state);
    return Container(
      width: 48,
      height: 48,
      decoration: BoxDecoration(
        color: color.withOpacity(0.15),
        shape: BoxShape.circle,
      ),
      child: Icon(Icons.local_parking_rounded, color: color, size: 26),
    );
  }
}

class _StateChip extends StatelessWidget {
  const _StateChip(this.state);

  final SpotState state;

  @override
  Widget build(BuildContext context) {
    final color = _colorFor(state);
    return Chip(
      label: Text(
        state.label,
        style:
            TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.bold),
      ),
      backgroundColor: color.withOpacity(0.1),
      side: BorderSide(color: color.withOpacity(0.3)),
      padding: const EdgeInsets.symmetric(horizontal: 4),
      visualDensity: VisualDensity.compact,
    );
  }
}

Color _colorFor(SpotState state) => switch (state) {
      SpotState.free => Colors.green,
      SpotState.reserved => Colors.amber.shade700,
      SpotState.occupied => Colors.red,
    };
