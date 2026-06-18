enum ReservationStatus { pending, active, completed, expired, cancelled }

extension ReservationStatusX on ReservationStatus {
  static ReservationStatus fromString(String value) =>
      switch (value.toUpperCase()) {
        'PENDING' => ReservationStatus.pending,
        'ACTIVE' => ReservationStatus.active,
        'COMPLETED' => ReservationStatus.completed,
        'EXPIRED' => ReservationStatus.expired,
        'CANCELLED' => ReservationStatus.cancelled,
        _ => ReservationStatus.pending,
      };

  String get label => switch (this) {
        ReservationStatus.pending => 'Pending',
        ReservationStatus.active => 'Active',
        ReservationStatus.completed => 'Completed',
        ReservationStatus.expired => 'Expired',
        ReservationStatus.cancelled => 'Cancelled',
      };
}

class ReservationModel {
  const ReservationModel({
    required this.id,
    required this.spotId,
    this.spotNumber,
    required this.vehiclePlate,
    required this.reservedFrom,
    required this.reservedUntil,
    this.checkedInAt,
    required this.status,
    this.totalAmount,
    required this.createdAt,
  });

  final String id;
  final String spotId;
  final String? spotNumber;
  final String vehiclePlate;
  final DateTime reservedFrom;
  final DateTime reservedUntil;
  final DateTime? checkedInAt;
  final ReservationStatus status;
  final double? totalAmount;
  final DateTime createdAt;

  /// True when ACTIVE and the driver has tapped Check In.
  bool get isCheckedIn =>
      checkedInAt != null && status == ReservationStatus.active;

  factory ReservationModel.fromJson(Map<String, dynamic> json) =>
      ReservationModel(
        id: json['id'] as String,
        spotId: json['spotId'] as String,
        spotNumber: json['spotNumber'] as String?,
        vehiclePlate: json['vehiclePlate'] as String,
        reservedFrom: DateTime.parse(json['reservedFrom'] as String),
        reservedUntil: DateTime.parse(json['reservedUntil'] as String),
        checkedInAt: json['checkedInAt'] != null
            ? DateTime.parse(json['checkedInAt'] as String)
            : null,
        status: ReservationStatusX.fromString(json['status'] as String),
        totalAmount: json['totalAmount'] != null
            ? (json['totalAmount'] as num).toDouble()
            : null,
        createdAt: DateTime.parse(json['createdAt'] as String),
      );
}
