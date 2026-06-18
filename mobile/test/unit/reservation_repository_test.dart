import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';
import 'package:smart_parking/core/exceptions/app_exception.dart';
import 'package:smart_parking/features/reservation/data/reservation_repository.dart';
import 'package:smart_parking/features/reservation/domain/reservation_model.dart';

import 'reservation_repository_test.mocks.dart';

@GenerateMocks([Dio])
void main() {
  late MockDio mockDio;
  late ReservationRepository repository;

  setUp(() {
    mockDio = MockDio();
    repository = ReservationRepository(mockDio);
  });

  RequestOptions opts(String path) => RequestOptions(path: path);

  final _now = DateTime.utc(2024, 6, 1, 10, 0);
  final _rawReservation = {
    'id': 'res-uuid',
    'spotId': 'spot-uuid',
    'spotNumber': 'A1',
    'vehiclePlate': 'ABC-001',
    'reservedFrom': '2024-06-01T10:00:00Z',
    'reservedUntil': '2024-06-01T11:00:00Z',
    'checkedInAt': null,
    'status': 'PENDING',
    'totalAmount': null,
    'createdAt': '2024-06-01T09:55:00Z',
  };

  group('ReservationRepository.create', () {
    test('returns ReservationModel on 201', () async {
      when(mockDio.post('/reservations', data: anyNamed('data'))).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/reservations'),
          statusCode: 201,
          data: _rawReservation,
        ),
      );

      final result = await repository.create(
        spotId: 'spot-uuid',
        vehiclePlate: 'ABC-001',
        reservedFrom: _now,
        reservedUntil: _now.add(const Duration(hours: 1)),
      );

      expect(result, isA<ReservationModel>());
      expect(result.status, ReservationStatus.pending);
      expect(result.vehiclePlate, 'ABC-001');
    });

    test('throws AppException on 409 spot locked', () async {
      when(mockDio.post('/reservations', data: anyNamed('data'))).thenThrow(
        DioException(
          requestOptions: opts('/reservations'),
          response: Response(
            requestOptions: opts('/reservations'),
            statusCode: 409,
            data: {
              'status': 409,
              'title': 'Conflict',
              'detail': 'Spot is currently locked',
            },
          ),
          type: DioExceptionType.badResponse,
        ),
      );

      expect(
        () => repository.create(
          spotId: 'spot-uuid',
          vehiclePlate: 'ABC-001',
          reservedFrom: _now,
          reservedUntil: _now.add(const Duration(hours: 1)),
        ),
        throwsA(
          isA<AppException>()
              .having((e) => e.status, 'status', 409)
              .having((e) => e.detail, 'detail', 'Spot is currently locked'),
        ),
      );
    });
  });

  group('ReservationRepository.getById', () {
    test('returns ReservationModel on 200', () async {
      when(mockDio.get('/reservations/res-uuid')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/reservations/res-uuid'),
          statusCode: 200,
          data: _rawReservation,
        ),
      );

      final result = await repository.getById('res-uuid');
      expect(result.id, 'res-uuid');
      expect(result.spotNumber, 'A1');
    });

    test('throws AppException on 404', () async {
      when(mockDio.get('/reservations/bad-id')).thenThrow(
        DioException(
          requestOptions: opts('/reservations/bad-id'),
          response: Response(
            requestOptions: opts('/reservations/bad-id'),
            statusCode: 404,
            data: {
              'status': 404,
              'title': 'Not Found',
              'detail': 'Reservation not found',
            },
          ),
          type: DioExceptionType.badResponse,
        ),
      );

      expect(
        () => repository.getById('bad-id'),
        throwsA(isA<AppException>().having((e) => e.status, 'status', 404)),
      );
    });
  });

  group('ReservationRepository.getMyReservations', () {
    test('returns list of reservations', () async {
      when(mockDio.get('/reservations/user/me')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/reservations/user/me'),
          statusCode: 200,
          data: [_rawReservation, {..._rawReservation, 'id': 'res-uuid-2'}],
        ),
      );

      final result = await repository.getMyReservations();
      expect(result, hasLength(2));
    });

    test('returns empty list when user has no reservations', () async {
      when(mockDio.get('/reservations/user/me')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/reservations/user/me'),
          statusCode: 200,
          data: <dynamic>[],
        ),
      );

      final result = await repository.getMyReservations();
      expect(result, isEmpty);
    });
  });

  group('ReservationRepository actions', () {
    final _activeRaw = {..._rawReservation, 'status': 'ACTIVE'};
    final _completedRaw = {
      ..._rawReservation,
      'status': 'COMPLETED',
      'checkedInAt': '2024-06-01T10:05:00Z',
      'totalAmount': 2.0,
    };

    test('checkIn returns ACTIVE reservation with checkedInAt set', () async {
      when(mockDio.post('/reservations/res-uuid/checkin')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/reservations/res-uuid/checkin'),
          statusCode: 200,
          data: {..._activeRaw, 'checkedInAt': '2024-06-01T10:05:00Z'},
        ),
      );

      final result = await repository.checkIn('res-uuid');
      expect(result.status, ReservationStatus.active);
      expect(result.checkedInAt, isNotNull);
      expect(result.isCheckedIn, isTrue);
    });

    test('checkOut returns COMPLETED reservation with totalAmount', () async {
      when(mockDio.post('/reservations/res-uuid/checkout')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/reservations/res-uuid/checkout'),
          statusCode: 200,
          data: _completedRaw,
        ),
      );

      final result = await repository.checkOut('res-uuid');
      expect(result.status, ReservationStatus.completed);
      expect(result.totalAmount, 2.0);
    });

    test('cancel returns CANCELLED reservation', () async {
      when(mockDio.post('/reservations/res-uuid/cancel')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/reservations/res-uuid/cancel'),
          statusCode: 200,
          data: {..._rawReservation, 'status': 'CANCELLED'},
        ),
      );

      final result = await repository.cancel('res-uuid');
      expect(result.status, ReservationStatus.cancelled);
    });
  });

  group('ReservationModel', () {
    test('isCheckedIn is false when checkedInAt is null', () {
      final model = ReservationModel.fromJson(_rawReservation);
      expect(model.isCheckedIn, isFalse);
    });

    test('isCheckedIn is true when ACTIVE and checkedInAt is set', () {
      final model = ReservationModel.fromJson({
        ..._rawReservation,
        'status': 'ACTIVE',
        'checkedInAt': '2024-06-01T10:05:00Z',
      });
      expect(model.isCheckedIn, isTrue);
    });

    test('isCheckedIn is false when COMPLETED even with checkedInAt', () {
      final model = ReservationModel.fromJson({
        ..._rawReservation,
        'status': 'COMPLETED',
        'checkedInAt': '2024-06-01T10:05:00Z',
      });
      expect(model.isCheckedIn, isFalse);
    });
  });
}
