class NotificationModel {
  const NotificationModel({
    required this.id,
    required this.userId,
    required this.type,
    required this.title,
    required this.message,
    required this.channel,
    required this.read,
    required this.createdAt,
    this.readAt,
    this.entityId,
  });

  final String id;
  final String userId;
  final String type;
  final String title;
  final String message;
  final String channel;
  final bool read;
  final DateTime createdAt;
  final DateTime? readAt;
  final String? entityId;

  factory NotificationModel.fromJson(Map<String, dynamic> json) =>
      NotificationModel(
        id: json['id'] as String,
        userId: json['userId'] as String,
        type: json['type'] as String,
        title: json['title'] as String,
        message: json['message'] as String,
        channel: json['channel'] as String? ?? 'IN_APP',
        read: json['read'] as bool? ?? false,
        createdAt: DateTime.parse(json['createdAt'] as String),
        readAt: json['readAt'] != null
            ? DateTime.parse(json['readAt'] as String)
            : null,
        entityId: json['entityId'] as String?,
      );
}
