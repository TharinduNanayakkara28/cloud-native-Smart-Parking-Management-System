enum PaymentStatus { preAuthorized, captured, refunded, failed }

extension PaymentStatusX on PaymentStatus {
  static PaymentStatus fromString(String s) => switch (s.toUpperCase()) {
        'PRE_AUTHORIZED' => PaymentStatus.preAuthorized,
        'CAPTURED' => PaymentStatus.captured,
        'REFUNDED' => PaymentStatus.refunded,
        'FAILED' => PaymentStatus.failed,
        _ => PaymentStatus.preAuthorized,
      };

  String get label => switch (this) {
        PaymentStatus.preAuthorized => 'Pending',
        PaymentStatus.captured => 'Paid',
        PaymentStatus.refunded => 'Refunded',
        PaymentStatus.failed => 'Failed',
      };
}

class PaymentModel {
  const PaymentModel({
    required this.id,
    required this.reservationId,
    required this.status,
    required this.amount,
    required this.currency,
    required this.createdAt,
    this.capturedAt,
    this.refundedAt,
    this.refundedAmount,
  });

  final String id;
  final String reservationId;
  final PaymentStatus status;
  final double amount;
  final String currency;
  final DateTime createdAt;
  final DateTime? capturedAt;
  final DateTime? refundedAt;
  final double? refundedAmount;

  factory PaymentModel.fromJson(Map<String, dynamic> json) => PaymentModel(
        id: json['id'] as String,
        reservationId: json['reservationId'] as String,
        status: PaymentStatusX.fromString(json['status'] as String),
        amount: (json['amount'] as num).toDouble(),
        currency: json['currency'] as String? ?? 'USD',
        createdAt: DateTime.parse(json['createdAt'] as String),
        capturedAt: json['capturedAt'] != null
            ? DateTime.parse(json['capturedAt'] as String)
            : null,
        refundedAt: json['refundedAt'] != null
            ? DateTime.parse(json['refundedAt'] as String)
            : null,
        refundedAmount: json['refundedAmount'] != null
            ? (json['refundedAmount'] as num).toDouble()
            : null,
      );
}
