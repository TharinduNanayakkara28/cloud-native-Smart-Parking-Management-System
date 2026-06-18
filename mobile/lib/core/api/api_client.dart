import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'endpoints.dart';
import 'auth_interceptor.dart';
import '../auth/token_storage.dart';

final apiClientProvider = Provider<ApiClient>((ref) {
  final tokenStorage = ref.read(tokenStorageProvider);
  return ApiClient(tokenStorage);
});

class ApiClient {
  ApiClient(TokenStorage tokenStorage) {
    // Separate Dio for token refresh — no interceptors to avoid infinite loop
    final refreshDio = Dio(BaseOptions(baseUrl: kBaseUrl));

    dio = Dio(
      BaseOptions(
        baseUrl: kBaseUrl,
        connectTimeout: const Duration(seconds: 10),
        receiveTimeout: const Duration(seconds: 10),
        headers: {'Content-Type': 'application/json'},
      ),
    );
    dio.interceptors.add(AuthInterceptor(tokenStorage, refreshDio));
  }

  late final Dio dio;
}
