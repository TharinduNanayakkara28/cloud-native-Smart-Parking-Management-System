import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';
import 'package:smart_parking/core/exceptions/app_exception.dart';
import 'package:smart_parking/features/notification/data/notification_repository.dart';
import 'package:smart_parking/features/notification/domain/notification_model.dart';

import 'notification_repository_test.mocks.dart';

@GenerateMocks([Dio])
void main() {
  late MockDio mockDio;
  late NotificationRepository repository;

  setUp(() {
    mockDio = MockDio();
    repository = NotificationRepository(mockDio);
  });

  RequestOptions opts(String path) => RequestOptions(path: path);

  final _rawNotification = {
    'id': 'notif-uuid',
    'userId': 'user-uuid',
    'type': 'RESERVATION_ACTIVE',
    'title': 'Reservation Confirmed',
    'message': 'Your spot A1 is ready.',
    'channel': 'IN_APP',
    'read': false,
    'createdAt': '2024-06-01T10:00:00Z',
    'readAt': null,
    'entityId': 'res-uuid',
  };

  group('NotificationRepository.getMyNotifications', () {
    test('returns list of notifications', () async {
      when(mockDio.get('/notifications/user/me')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/notifications/user/me'),
          statusCode: 200,
          data: [_rawNotification, {..._rawNotification, 'id': 'notif-uuid-2'}],
        ),
      );

      final result = await repository.getMyNotifications();
      expect(result, hasLength(2));
      expect(result.first, isA<NotificationModel>());
    });

    test('returns empty list when no notifications', () async {
      when(mockDio.get('/notifications/user/me')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/notifications/user/me'),
          statusCode: 200,
          data: <dynamic>[],
        ),
      );

      final result = await repository.getMyNotifications();
      expect(result, isEmpty);
    });

    test('throws AppException on 500', () async {
      when(mockDio.get('/notifications/user/me')).thenThrow(
        DioException(
          requestOptions: opts('/notifications/user/me'),
          response: Response(
            requestOptions: opts('/notifications/user/me'),
            statusCode: 500,
            data: {
              'status': 500,
              'title': 'Internal Server Error',
              'detail': 'Unexpected error',
            },
          ),
          type: DioExceptionType.badResponse,
        ),
      );

      expect(
        () => repository.getMyNotifications(),
        throwsA(isA<AppException>().having((e) => e.status, 'status', 500)),
      );
    });
  });

  group('NotificationRepository.getUnreadCount', () {
    test('returns unread count as int', () async {
      when(mockDio.get('/notifications/user/me/unread-count')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/notifications/user/me/unread-count'),
          statusCode: 200,
          data: {'unreadCount': 3},
        ),
      );

      final result = await repository.getUnreadCount();
      expect(result, 3);
    });

    test('returns 0 when no unread', () async {
      when(mockDio.get('/notifications/user/me/unread-count')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/notifications/user/me/unread-count'),
          statusCode: 200,
          data: {'unreadCount': 0},
        ),
      );

      final result = await repository.getUnreadCount();
      expect(result, 0);
    });
  });

  group('NotificationRepository.markAsRead', () {
    test('calls correct endpoint', () async {
      when(mockDio.post('/notifications/notif-uuid/read')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/notifications/notif-uuid/read'),
          statusCode: 204,
          data: null,
        ),
      );

      await expectLater(repository.markAsRead('notif-uuid'), completes);
      verify(mockDio.post('/notifications/notif-uuid/read')).called(1);
    });
  });

  group('NotificationRepository.markAllRead', () {
    test('calls correct endpoint', () async {
      when(mockDio.post('/notifications/user/me/read-all')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/notifications/user/me/read-all'),
          statusCode: 204,
          data: null,
        ),
      );

      await expectLater(repository.markAllRead(), completes);
      verify(mockDio.post('/notifications/user/me/read-all')).called(1);
    });
  });

  group('NotificationModel', () {
    test('read defaults to false when absent from JSON', () {
      final model = NotificationModel.fromJson({
        ..._rawNotification,
        'read': null,
      }..remove('read'));
      expect(model.read, isFalse);
    });

    test('entityId is nullable', () {
      final model = NotificationModel.fromJson({
        ..._rawNotification,
        'entityId': null,
      });
      expect(model.entityId, isNull);
    });

    test('readAt is null when notification is unread', () {
      final model = NotificationModel.fromJson(_rawNotification);
      expect(model.readAt, isNull);
    });

    test('readAt parses when notification is read', () {
      final model = NotificationModel.fromJson({
        ..._rawNotification,
        'read': true,
        'readAt': '2024-06-01T10:05:00Z',
      });
      expect(model.read, isTrue);
      expect(model.readAt, isA<DateTime>());
    });
  });
}
