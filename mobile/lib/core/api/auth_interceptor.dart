import 'package:dio/dio.dart';
import '../auth/token_storage.dart';

class AuthInterceptor extends Interceptor {
  AuthInterceptor(this._tokenStorage, this._refreshDio);

  final TokenStorage _tokenStorage;
  // Separate Dio instance with no interceptors — used only for token refresh
  // to avoid infinite recursion when the main Dio gets a 401.
  final Dio _refreshDio;

  @override
  Future<void> onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    final token = await _tokenStorage.getAccessToken();
    if (token != null) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }

  @override
  Future<void> onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    final is401 = err.response?.statusCode == 401;
    final alreadyRetried = err.requestOptions.extra['_retried'] == true;
    final isAuthEndpoint = err.requestOptions.path.contains('/auth/');

    if (is401 && !alreadyRetried && !isAuthEndpoint) {
      final refreshToken = await _tokenStorage.getRefreshToken();
      if (refreshToken != null) {
        try {
          final response = await _refreshDio.post(
            '/auth/refresh',
            data: {'refreshToken': refreshToken},
          );
          final newAccess = response.data['accessToken'] as String;
          final newRefresh = response.data['refreshToken'] as String;
          await _tokenStorage.saveTokens(newAccess, newRefresh);

          final retryOptions = err.requestOptions
            ..headers['Authorization'] = 'Bearer $newAccess'
            ..extra['_retried'] = true;

          final retryResponse = await _refreshDio.fetch(retryOptions);
          return handler.resolve(retryResponse);
        } catch (_) {
          await _tokenStorage.clear();
        }
      } else {
        await _tokenStorage.clear();
      }
    }
    handler.next(err);
  }
}
