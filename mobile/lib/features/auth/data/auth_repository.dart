import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/api/api_client.dart';
import '../../../core/exceptions/app_exception.dart';

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  final client = ref.read(apiClientProvider);
  return AuthRepository(client.dio);
});

class AuthRepository {
  AuthRepository(this._dio);

  final Dio _dio;

  Future<Map<String, dynamic>> login(String email, String password) async {
    try {
      final response = await _dio.post(
        '/auth/login',
        data: {'email': email, 'password': password},
      );
      return response.data as Map<String, dynamic>;
    } on DioException catch (e) {
      throw AppException.fromDio(e);
    }
  }

  Future<Map<String, dynamic>> register({
    required String name,
    required String email,
    required String password,
    required String phone,
  }) async {
    try {
      final response = await _dio.post(
        '/auth/register',
        data: {
          'name': name,
          'email': email,
          'password': password,
          'phone': phone,
        },
      );
      return response.data as Map<String, dynamic>;
    } on DioException catch (e) {
      throw AppException.fromDio(e);
    }
  }

  Future<Map<String, dynamic>> getProfile() async {
    try {
      final response = await _dio.get('/users/me');
      return response.data as Map<String, dynamic>;
    } on DioException catch (e) {
      throw AppException.fromDio(e);
    }
  }
}
