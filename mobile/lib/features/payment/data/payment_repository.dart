import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/api/api_client.dart';
import '../../../core/exceptions/app_exception.dart';
import '../domain/payment_model.dart';

final paymentRepositoryProvider = Provider<PaymentRepository>((ref) {
  return PaymentRepository(ref.read(apiClientProvider).dio);
});

class PaymentRepository {
  PaymentRepository(this._dio);

  final Dio _dio;

  Future<PaymentModel> getByReservationId(String reservationId) async {
    try {
      final response = await _dio.get('/payments/$reservationId');
      return PaymentModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw AppException.fromDio(e);
    }
  }
}
