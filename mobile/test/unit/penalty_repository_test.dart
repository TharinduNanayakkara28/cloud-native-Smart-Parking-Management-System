import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';
import 'package:smart_parking/core/exceptions/app_exception.dart';
import 'package:smart_parking/features/penalty/data/penalty_repository.dart';
import 'package:smart_parking/features/penalty/domain/penalty_model.dart';

import 'penalty_repository_test.mocks.dart';

@GenerateMocks([Dio])
void main() {
  late MockDio mockDio;
  late PenaltyRepository repository;

  setUp(() {
    mockDio = MockDio();
    repository = PenaltyRepository(mockDio);
  });

  RequestOptions opts(String path) => RequestOptions(path: path);

  final _rawIssuedFine = {
    'id': 'penalty-uuid',
    'userId': 'user-uuid',
    'reservationId': 'res-uuid',
    'vehiclePlate': 'ABC-001',
    'tier': 'FINE',
    'status': 'ISSUED',
    'fineAmount': 25.0,
    'issuedAt': '2024-06-01T13:00:00Z',
    'dueDate': '2024-06-08T13:00:00Z',
    'paidAt': null,
    'notes': 'Overstay detected: 45 minutes',
  };

  final _rawWarning = {
    ..._rawIssuedFine,
    'id': 'warning-uuid',
    'tier': 'WARNING',
    'fineAmount': 0.0,
    'notes': 'First offence — warning issued',
  };

  final _rawPaid = {
    ..._rawIssuedFine,
    'status': 'PAID',
    'paidAt': '2024-06-02T09:00:00Z',
  };

  group('PenaltyRepository.getMyPenalties', () {
    test('returns list of penalties', () async {
      when(mockDio.get('/penalties/user/me')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/penalties/user/me'),
          statusCode: 200,
          data: [_rawIssuedFine, _rawWarning],
        ),
      );

      final result = await repository.getMyPenalties();
      expect(result, hasLength(2));
      expect(result.first, isA<PenaltyModel>());
    });

    test('returns empty list when no penalties', () async {
      when(mockDio.get('/penalties/user/me')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/penalties/user/me'),
          statusCode: 200,
          data: <dynamic>[],
        ),
      );

      final result = await repository.getMyPenalties();
      expect(result, isEmpty);
    });

    test('throws AppException on 500', () async {
      when(mockDio.get('/penalties/user/me')).thenThrow(
        DioException(
          requestOptions: opts('/penalties/user/me'),
          response: Response(
            requestOptions: opts('/penalties/user/me'),
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
        () => repository.getMyPenalties(),
        throwsA(isA<AppException>().having((e) => e.status, 'status', 500)),
      );
    });

    test('FINE tier maps to PenaltyTier.fine', () async {
      when(mockDio.get('/penalties/user/me')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/penalties/user/me'),
          statusCode: 200,
          data: [_rawIssuedFine],
        ),
      );

      final result = await repository.getMyPenalties();
      expect(result.first.tier, PenaltyTier.fine);
    });

    test('WARNING tier maps to PenaltyTier.warning', () async {
      when(mockDio.get('/penalties/user/me')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/penalties/user/me'),
          statusCode: 200,
          data: [_rawWarning],
        ),
      );

      final result = await repository.getMyPenalties();
      expect(result.first.tier, PenaltyTier.warning);
      expect(result.first.fineAmount, 0.0);
    });
  });

  group('PenaltyRepository.pay', () {
    test('returns PAID PenaltyModel on success', () async {
      when(mockDio.post('/penalties/penalty-uuid/pay')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/penalties/penalty-uuid/pay'),
          statusCode: 200,
          data: _rawPaid,
        ),
      );

      final result = await repository.pay('penalty-uuid');
      expect(result.status, PenaltyStatus.paid);
      expect(result.paidAt, isA<DateTime>());
    });

    test('throws AppException on 404 penalty not found', () async {
      when(mockDio.post('/penalties/bad-id/pay')).thenThrow(
        DioException(
          requestOptions: opts('/penalties/bad-id/pay'),
          response: Response(
            requestOptions: opts('/penalties/bad-id/pay'),
            statusCode: 404,
            data: {
              'status': 404,
              'title': 'Not Found',
              'detail': 'Penalty not found',
            },
          ),
          type: DioExceptionType.badResponse,
        ),
      );

      expect(
        () => repository.pay('bad-id'),
        throwsA(isA<AppException>().having((e) => e.status, 'status', 404)),
      );
    });

    test('throws AppException on 409 already paid', () async {
      when(mockDio.post('/penalties/penalty-uuid/pay')).thenThrow(
        DioException(
          requestOptions: opts('/penalties/penalty-uuid/pay'),
          response: Response(
            requestOptions: opts('/penalties/penalty-uuid/pay'),
            statusCode: 409,
            data: {
              'status': 409,
              'title': 'Conflict',
              'detail': 'Penalty is already paid',
            },
          ),
          type: DioExceptionType.badResponse,
        ),
      );

      expect(
        () => repository.pay('penalty-uuid'),
        throwsA(
          isA<AppException>()
              .having((e) => e.status, 'status', 409)
              .having((e) => e.detail, 'detail', 'Penalty is already paid'),
        ),
      );
    });
  });

  group('PenaltyModel', () {
    test('isOverdue is false when status is PAID', () {
      final model = PenaltyModel.fromJson({
        ..._rawPaid,
        'dueDate': DateTime.now()
            .subtract(const Duration(days: 1))
            .toIso8601String(),
      });
      expect(model.isOverdue, isFalse);
    });

    test('isOverdue is true when ISSUED and past dueDate', () {
      final model = PenaltyModel.fromJson({
        ..._rawIssuedFine,
        'dueDate': DateTime.now()
            .subtract(const Duration(days: 1))
            .toIso8601String(),
      });
      expect(model.isOverdue, isTrue);
    });

    test('isOverdue is false when dueDate is in the future', () {
      final model = PenaltyModel.fromJson({
        ..._rawIssuedFine,
        'dueDate': DateTime.now()
            .add(const Duration(days: 7))
            .toIso8601String(),
      });
      expect(model.isOverdue, isFalse);
    });

    test('notes is nullable', () {
      final model = PenaltyModel.fromJson({
        ..._rawIssuedFine,
        'notes': null,
      });
      expect(model.notes, isNull);
    });
  });
}
