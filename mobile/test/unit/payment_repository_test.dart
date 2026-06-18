import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';
import 'package:smart_parking/core/exceptions/app_exception.dart';
import 'package:smart_parking/features/payment/data/payment_repository.dart';
import 'package:smart_parking/features/payment/domain/payment_model.dart';

import 'payment_repository_test.mocks.dart';

@GenerateMocks([Dio])
void main() {
  late MockDio mockDio;
  late PaymentRepository repository;

  setUp(() {
    mockDio = MockDio();
    repository = PaymentRepository(mockDio);
  });

  RequestOptions opts(String path) => RequestOptions(path: path);

  const _resId = 'res-uuid';
  final _rawPayment = {
    'id': 'pay-uuid',
    'reservationId': _resId,
    'status': 'CAPTURED',
    'amount': 4.0,
    'currency': 'USD',
    'createdAt': '2024-06-01T09:55:00Z',
    'capturedAt': '2024-06-01T11:05:00Z',
    'refundedAt': null,
    'refundedAmount': null,
  };

  group('PaymentRepository.getByReservationId', () {
    test('returns PaymentModel on 200', () async {
      when(mockDio.get('/payments/$_resId')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/payments/$_resId'),
          statusCode: 200,
          data: _rawPayment,
        ),
      );

      final result = await repository.getByReservationId(_resId);

      expect(result, isA<PaymentModel>());
      expect(result.id, 'pay-uuid');
      expect(result.reservationId, _resId);
    });

    test('status CAPTURED maps to PaymentStatus.captured', () async {
      when(mockDio.get('/payments/$_resId')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/payments/$_resId'),
          statusCode: 200,
          data: _rawPayment,
        ),
      );

      final result = await repository.getByReservationId(_resId);
      expect(result.status, PaymentStatus.captured);
    });

    test('amount parses as double', () async {
      when(mockDio.get('/payments/$_resId')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/payments/$_resId'),
          statusCode: 200,
          data: _rawPayment,
        ),
      );

      final result = await repository.getByReservationId(_resId);
      expect(result.amount, 4.0);
    });

    test('capturedAt parses as DateTime', () async {
      when(mockDio.get('/payments/$_resId')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/payments/$_resId'),
          statusCode: 200,
          data: _rawPayment,
        ),
      );

      final result = await repository.getByReservationId(_resId);
      expect(result.capturedAt, isA<DateTime>());
    });

    test('REFUNDED status includes refundedAmount', () async {
      when(mockDio.get('/payments/$_resId')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/payments/$_resId'),
          statusCode: 200,
          data: {
            ..._rawPayment,
            'status': 'REFUNDED',
            'refundedAt': '2024-06-01T11:10:00Z',
            'refundedAmount': 4.0,
          },
        ),
      );

      final result = await repository.getByReservationId(_resId);
      expect(result.status, PaymentStatus.refunded);
      expect(result.refundedAmount, 4.0);
      expect(result.refundedAt, isA<DateTime>());
    });

    test('throws AppException on 404', () async {
      when(mockDio.get('/payments/bad-id')).thenThrow(
        DioException(
          requestOptions: opts('/payments/bad-id'),
          response: Response(
            requestOptions: opts('/payments/bad-id'),
            statusCode: 404,
            data: {
              'status': 404,
              'title': 'Not Found',
              'detail': 'Payment not found for reservation bad-id',
            },
          ),
          type: DioExceptionType.badResponse,
        ),
      );

      expect(
        () => repository.getByReservationId('bad-id'),
        throwsA(
          isA<AppException>()
              .having((e) => e.status, 'status', 404)
              .having((e) => e.detail, 'detail',
                  'Payment not found for reservation bad-id'),
        ),
      );
    });

    test('PRE_AUTHORIZED maps to PaymentStatus.preAuthorized', () async {
      when(mockDio.get('/payments/$_resId')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/payments/$_resId'),
          statusCode: 200,
          data: {..._rawPayment, 'status': 'PRE_AUTHORIZED', 'capturedAt': null},
        ),
      );

      final result = await repository.getByReservationId(_resId);
      expect(result.status, PaymentStatus.preAuthorized);
      expect(result.capturedAt, isNull);
    });
  });
}
