import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import '../../../core/websocket/availability_socket.dart';
import '../domain/spot_model.dart';
import 'spot_repository.dart';

// Fallback centre used when location permission is denied.
// Matches the coordinates seeded into the spot-detection-service.
const _defaultLat = -6.2088;
const _defaultLng = 106.8456;

/// Resolves device GPS once. Returns null if permission is denied or
/// location services are off — callers fall back to [_defaultLat]/[_defaultLng].
final userLocationProvider = FutureProvider<Position?>((ref) async {
  if (!await Geolocator.isLocationServiceEnabled()) return null;

  var permission = await Geolocator.checkPermission();
  if (permission == LocationPermission.denied) {
    permission = await Geolocator.requestPermission();
  }
  if (permission == LocationPermission.denied ||
      permission == LocationPermission.deniedForever) {
    return null;
  }

  return Geolocator.getCurrentPosition(
    locationSettings: const LocationSettings(accuracy: LocationAccuracy.high),
  );
});

final spotsNotifierProvider =
    AsyncNotifierProvider<SpotsNotifier, List<SpotModel>>(SpotsNotifier.new);

class SpotsNotifier extends AsyncNotifier<List<SpotModel>> {
  @override
  Future<List<SpotModel>> build() async {
    final position = await ref.read(userLocationProvider.future);
    final lat = position?.latitude ?? _defaultLat;
    final lng = position?.longitude ?? _defaultLng;

    // Keep spot state in sync as WebSocket events arrive.
    // The listener is cancelled automatically when this notifier is disposed.
    ref.listen(availabilitySocketProvider, (_, next) {
      next.whenData(_applyStateUpdate);
    });

    return ref
        .read(spotRepositoryProvider)
        .getAvailableSpots(lat: lat, lng: lng);
  }

  void _applyStateUpdate(SpotStateEvent event) {
    state.whenData((spots) {
      state = AsyncData(
        spots
            .map((s) =>
                s.id == event.spotId ? s.copyWith(state: event.state) : s)
            .toList(),
      );
    });
  }

  Future<void> refresh() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final position = await ref.read(userLocationProvider.future);
      final lat = position?.latitude ?? _defaultLat;
      final lng = position?.longitude ?? _defaultLng;
      return ref
          .read(spotRepositoryProvider)
          .getAvailableSpots(lat: lat, lng: lng);
    });
  }
}
