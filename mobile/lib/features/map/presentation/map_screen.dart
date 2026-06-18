import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:latlong2/latlong.dart';
import '../data/spots_notifier.dart';
import '../domain/spot_model.dart';
import 'spot_bottom_sheet.dart';

const _defaultCenter = LatLng(-6.2088, 106.8456);

class MapScreen extends ConsumerWidget {
  const MapScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final spotsAsync = ref.watch(spotsNotifierProvider);
    final locationAsync = ref.watch(userLocationProvider);

    final mapCenter = locationAsync.whenOrNull(
          data: (pos) =>
              pos != null ? LatLng(pos.latitude, pos.longitude) : null,
        ) ??
        _defaultCenter;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Find Parking'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_outlined),
            tooltip: 'Refresh spots',
            onPressed: () =>
                ref.read(spotsNotifierProvider.notifier).refresh(),
          ),
        ],
      ),
      body: Stack(
        children: [
          FlutterMap(
            options: MapOptions(
              initialCenter: mapCenter,
              initialZoom: 16.0,
            ),
            children: [
              TileLayer(
                urlTemplate:
                    'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                userAgentPackageName: 'com.smartparking.mobile',
              ),
              MarkerLayer(
                markers: spotsAsync.valueOrNull
                        ?.map((spot) => _spotMarker(context, spot))
                        .toList() ??
                    [],
              ),
            ],
          ),

          // Loading overlay
          if (spotsAsync.isLoading)
            Positioned(
              top: 16,
              left: 0,
              right: 0,
              child: Center(
                child: Card(
                  elevation: 4,
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 16, vertical: 8),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const SizedBox(
                          width: 14,
                          height: 14,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        ),
                        const SizedBox(width: 10),
                        Text(
                          'Loading spots…',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),

          // Error banner
          if (spotsAsync.hasError)
            Positioned(
              bottom: 16,
              left: 16,
              right: 16,
              child: Material(
                borderRadius: BorderRadius.circular(8),
                color: Theme.of(context).colorScheme.errorContainer,
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Row(
                    children: [
                      Icon(Icons.error_outline,
                          color: Theme.of(context).colorScheme.error),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'Could not load spots. Tap refresh to retry.',
                          style: TextStyle(
                              color: Theme.of(context)
                                  .colorScheme
                                  .onErrorContainer),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),

          // Legend
          Positioned(
            bottom: 16,
            right: 16,
            child: _Legend(),
          ),
        ],
      ),
    );
  }

  Marker _spotMarker(BuildContext context, SpotModel spot) {
    final color = switch (spot.state) {
      SpotState.free => Colors.green,
      SpotState.reserved => Colors.amber.shade700,
      SpotState.occupied => Colors.red,
    };

    return Marker(
      point: spot.latLng,
      width: 40,
      height: 40,
      child: GestureDetector(
        onTap: () => _showSheet(context, spot),
        child: Container(
          decoration: BoxDecoration(
            color: color,
            shape: BoxShape.circle,
            border: Border.all(color: Colors.white, width: 2.5),
            boxShadow: const [
              BoxShadow(
                  color: Colors.black26, blurRadius: 4, offset: Offset(0, 2)),
            ],
          ),
          child: Center(
            child: Text(
              spot.spotNumber,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 9,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
        ),
      ),
    );
  }

  void _showSheet(BuildContext context, SpotModel spot) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => SpotBottomSheet(spot: spot),
    );
  }
}

class _Legend extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: 4,
      child: Padding(
        padding: const EdgeInsets.all(10),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: const [
            _LegendItem(color: Colors.green, label: 'Free'),
            SizedBox(height: 4),
            _LegendItem(color: Colors.amber, label: 'Reserved'),
            SizedBox(height: 4),
            _LegendItem(color: Colors.red, label: 'Occupied'),
          ],
        ),
      ),
    );
  }
}

class _LegendItem extends StatelessWidget {
  const _LegendItem({required this.color, required this.label});

  final Color color;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 12,
          height: 12,
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
        const SizedBox(width: 6),
        Text(label, style: Theme.of(context).textTheme.bodySmall),
      ],
    );
  }
}
