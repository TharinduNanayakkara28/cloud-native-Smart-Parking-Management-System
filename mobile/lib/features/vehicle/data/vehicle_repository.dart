import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/api/api_client.dart';
import '../../../core/exceptions/app_exception.dart';
import '../domain/vehicle_model.dart';

final vehicleRepositoryProvider = Provider<VehicleRepository>((ref) {
  return VehicleRepository(ref.read(apiClientProvider).dio);
});

final myVehiclesProvider =
    FutureProvider.autoDispose<List<VehicleModel>>((ref) async {
  return ref.read(vehicleRepositoryProvider).getMyVehicles();
});

class VehicleRepository {
  VehicleRepository(this._dio);

  final Dio _dio;

  Future<List<VehicleModel>> getMyVehicles() async {
    try {
      final response = await _dio.get('/users/me/vehicles');
      return (response.data as List)
          .map((e) => VehicleModel.fromJson(e as Map<String, dynamic>))
          .toList();
    } on DioException catch (e) {
      throw AppException.fromDio(e);
    }
  }

  Future<VehicleModel> addVehicle({
    required String plate,
    String? make,
    String? model,
  }) async {
    try {
      final response = await _dio.post(
        '/users/me/vehicles',
        data: {
          'plate': plate,
          if (make != null && make.isNotEmpty) 'make': make,
          if (model != null && model.isNotEmpty) 'model': model,
        },
      );
      return VehicleModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw AppException.fromDio(e);
    }
  }
}
