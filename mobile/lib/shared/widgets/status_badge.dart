import 'package:flutter/material.dart';

enum BadgeVariant { success, warning, error, info, neutral }

class StatusBadge extends StatelessWidget {
  const StatusBadge({super.key, required this.label, required this.variant});

  final String label;
  final BadgeVariant variant;

  @override
  Widget build(BuildContext context) {
    final (bg, fg) = switch (variant) {
      BadgeVariant.success => (Colors.green.shade50, Colors.green.shade700),
      BadgeVariant.warning => (Colors.amber.shade50, Colors.amber.shade800),
      BadgeVariant.error => (Colors.red.shade50, Colors.red.shade700),
      BadgeVariant.info => (Colors.blue.shade50, Colors.blue.shade700),
      BadgeVariant.neutral => (Colors.grey.shade100, Colors.grey.shade700),
    };

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: fg,
          fontSize: 12,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}
