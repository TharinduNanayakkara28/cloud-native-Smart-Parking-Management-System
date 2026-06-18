import 'package:dio/dio.dart';

class AppException implements Exception {
  const AppException({
    required this.status,
    required this.title,
    required this.detail,
  });

  final int status;
  final String title;
  final String detail;

  factory AppException.fromDio(DioException e) {
    final data = e.response?.data;
    if (data is Map<String, dynamic>) {
      return AppException(
        status: (data['status'] as int?) ?? e.response?.statusCode ?? 0,
        title: (data['title'] as String?) ?? 'Error',
        detail: (data['detail'] as String?) ?? 'An unexpected error occurred',
      );
    }
    return AppException(
      status: e.response?.statusCode ?? 0,
      title: 'Network Error',
      detail: e.message ?? 'An unexpected error occurred',
    );
  }

  @override
  String toString() => detail;
}
