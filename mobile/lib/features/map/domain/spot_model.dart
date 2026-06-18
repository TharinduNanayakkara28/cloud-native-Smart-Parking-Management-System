import 'package:latlong2/latlong.dart';

enum SpotState { free, reserved, occupied }

extension SpotStateX on SpotState {
  static SpotState fromString(String value) => switch (value.toUpperCase()) {
        'FREE' => SpotState.free,
        'RESERVED' => SpotState.reserved,
        'OCCUPIED' => SpotState.occupied,
        _ => SpotState.free,
      };

  String get label => switch (this) {
        SpotState.free => 'Free',
        SpotState.reserved => 'Reserved',
        SpotState.occupied => 'Occupied',
      };
}

class SpotModel {
  const SpotModel({
    required this.id,
    required this.spotNumber,
    required this.floor,
    required this.latLng,
    required this.state,
    this.lastUpdated,
  });

  final String id;
  final String spotNumber;
  final String? floor;
  final LatLng latLng;
  final SpotState state;
  final DateTime? lastUpdated;

  factory SpotModel.fromJson(Map<String, dynamic> json) => SpotModel(
        id: json['id'] as String,
        spotNumber: json['spotNumber'] as String,
        floor: json['floor'] as String?,
        latLng: LatLng(
          (json['latitude'] as num).toDouble(),
          (json['longitude'] as num).toDouble(),
        ),
        state: SpotStateX.fromString(json['state'] as String? ?? 'FREE'),
        lastUpdated: json['lastUpdated'] != null
            ? DateTime.parse(json['lastUpdated'] as String)
            : null,
      );

  SpotModel copyWith({SpotState? state}) => SpotModel(
        id: id,
        spotNumber: spotNumber,
        floor: floor,
        latLng: latLng,
        state: state ?? this.state,
        lastUpdated: lastUpdated,
      );
}

class SpotStateEvent {
  const SpotStateEvent({required this.spotId, required this.state});

  final String spotId;
  final SpotState state;

  factory SpotStateEvent.fromJson(Map<String, dynamic> json) => SpotStateEvent(
        spotId: json['spotId'] as String,
        state: SpotStateX.fromString(json['state'] as String),
      );
}
