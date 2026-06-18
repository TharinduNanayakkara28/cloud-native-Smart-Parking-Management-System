enum PenaltyTier { warning, fine, escalated }

extension PenaltyTierX on PenaltyTier {
  static PenaltyTier fromString(String s) => switch (s.toUpperCase()) {
        'WARNING' || 'WARNING_TIER_1' || 'TIER_1' => PenaltyTier.warning,
        'FINE' || 'FINE_TIER_2' || 'TIER_2' => PenaltyTier.fine,
        'ESCALATED' || 'ESCALATED_TIER_3' || 'TIER_3' => PenaltyTier.escalated,
        _ => PenaltyTier.fine,
      };

  String get label => switch (this) {
        PenaltyTier.warning => 'Warning',
        PenaltyTier.fine => 'Fine',
        PenaltyTier.escalated => 'Escalated',
      };

  String get badgeVariant => switch (this) {
        PenaltyTier.warning => 'neutral',
        PenaltyTier.fine => 'warning',
        PenaltyTier.escalated => 'error',
      };
}

enum PenaltyStatus { issued, paid, waived }

extension PenaltyStatusX on PenaltyStatus {
  static PenaltyStatus fromString(String s) => switch (s.toUpperCase()) {
        'ISSUED' => PenaltyStatus.issued,
        'PAID' => PenaltyStatus.paid,
        'WAIVED' => PenaltyStatus.waived,
        _ => PenaltyStatus.issued,
      };

  String get label => switch (this) {
        PenaltyStatus.issued => 'Issued',
        PenaltyStatus.paid => 'Paid',
        PenaltyStatus.waived => 'Waived',
      };
}

class PenaltyModel {
  const PenaltyModel({
    required this.id,
    required this.userId,
    required this.reservationId,
    required this.vehiclePlate,
    required this.tier,
    required this.status,
    required this.fineAmount,
    required this.issuedAt,
    this.dueDate,
    this.paidAt,
    this.notes,
  });

  final String id;
  final String userId;
  final String reservationId;
  final String vehiclePlate;
  final PenaltyTier tier;
  final PenaltyStatus status;
  final double fineAmount;
  final DateTime issuedAt;
  final DateTime? dueDate;
  final DateTime? paidAt;
  final String? notes;

  bool get isOverdue =>
      status == PenaltyStatus.issued &&
      dueDate != null &&
      DateTime.now().isAfter(dueDate!);

  factory PenaltyModel.fromJson(Map<String, dynamic> json) => PenaltyModel(
        id: json['id'] as String,
        userId: json['userId'] as String,
        reservationId: json['reservationId'] as String,
        vehiclePlate: json['vehiclePlate'] as String,
        tier: PenaltyTierX.fromString(json['tier'] as String),
        status: PenaltyStatusX.fromString(json['status'] as String),
        fineAmount: (json['fineAmount'] as num).toDouble(),
        issuedAt: DateTime.parse(json['issuedAt'] as String),
        dueDate: json['dueDate'] != null
            ? DateTime.parse(json['dueDate'] as String)
            : null,
        paidAt: json['paidAt'] != null
            ? DateTime.parse(json['paidAt'] as String)
            : null,
        notes: json['notes'] as String?,
      );
}
