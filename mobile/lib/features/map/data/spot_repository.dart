import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/api/api_client.dart';
import '../../../core/exceptions/app_exception.dart';
import '../domain/spot_model.dart';

final spotRepositoryProvider = Provider<SpotRepository>((ref) {
  final client = ref.read(apiClientProvider);
  return SpotRepository(client.dio);
});

class SpotRepository {
  SpotRepository(this._dio);

  final Dio _dio;

  Future<List<SpotModel>> getAvailableSpots({
    required double lat,
    required double lng,
    double radius = 500,
  }) async {
    try {
      final response = await _dio.get(
        '/spots/available',
        queryParameters: {'lat': lat, 'lng': lng, 'radius': radius},
      );
      final list = response.data as List<dynamic>;
      return list
          .map((e) => SpotModel.fromJson(e as Map<String, dynamic>))
          .toList();
    } on DioException catch (e) {
      throw AppException.fromDio(e);
    }
  }
}
