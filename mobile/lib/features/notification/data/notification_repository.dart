import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/api/api_client.dart';
import '../../../core/exceptions/app_exception.dart';
import '../domain/notification_model.dart';

final notificationRepositoryProvider = Provider<NotificationRepository>((ref) {
  return NotificationRepository(ref.read(apiClientProvider).dio);
});

final notificationsProvider =
    FutureProvider.autoDispose<List<NotificationModel>>((ref) async {
  final list = await ref
      .read(notificationRepositoryProvider)
      .getMyNotifications();
  // newest first
  list.sort((a, b) => b.createdAt.compareTo(a.createdAt));
  return list;
});

final unreadCountProvider = StreamProvider.autoDispose<int>((ref) async* {
  final repo = ref.read(notificationRepositoryProvider);
  while (true) {
    try {
      yield await repo.getUnreadCount();
    } catch (_) {
      yield 0;
    }
    await Future<void>.delayed(const Duration(seconds: 30));
  }
});

class NotificationRepository {
  NotificationRepository(this._dio);

  final Dio _dio;

  Future<List<NotificationModel>> getMyNotifications() async {
    try {
      final response = await _dio.get('/notifications/user/me');
      return (response.data as List)
          .map((e) => NotificationModel.fromJson(e as Map<String, dynamic>))
          .toList();
    } on DioException catch (e) {
      throw AppException.fromDio(e);
    }
  }

  Future<int> getUnreadCount() async {
    try {
      final response = await _dio.get('/notifications/user/me/unread-count');
      return (response.data['unreadCount'] as num).toInt();
    } on DioException catch (e) {
      throw AppException.fromDio(e);
    }
  }

  Future<void> markAsRead(String id) async {
    try {
      await _dio.post('/notifications/$id/read');
    } on DioException catch (e) {
      throw AppException.fromDio(e);
    }
  }

  Future<void> markAllRead() async {
    try {
      await _dio.post('/notifications/user/me/read-all');
    } on DioException catch (e) {
      throw AppException.fromDio(e);
    }
  }
}
