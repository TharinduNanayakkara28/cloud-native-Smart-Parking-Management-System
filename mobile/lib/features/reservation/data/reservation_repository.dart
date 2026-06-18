import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/api/api_client.dart';
import '../../../core/exceptions/app_exception.dart';
import '../domain/reservation_model.dart';

final reservationRepositoryProvider = Provider<ReservationRepository>((ref) {
  final client = ref.read(apiClientProvider);
  return ReservationRepository(client.dio);
});

final myReservationsProvider =
    FutureProvider.autoDispose<List<ReservationModel>>((ref) async {
  return ref.read(reservationRepositoryProvider).getMyReservations();
});

class ReservationRepository {
  ReservationRepository(this._dio);

  final Dio _dio;

  Future<ReservationModel> create({
    required String spotId,
    required String vehiclePlate,
    required DateTime reservedFrom,
    required DateTime reservedUntil,
  }) async {
    try {
      final response = await _dio.post(
        '/reservations',
        data: {
          'spotId': spotId,
          'vehiclePlate': vehiclePlate,
          'reservedFrom': reservedFrom.toUtc().toIso8601String(),
          'reservedUntil': reservedUntil.toUtc().toIso8601String(),
        },
      );
      return ReservationModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw AppException.fromDio(e);
    }
  }

  Future<ReservationModel> getById(String id) async {
    try {
      final response = await _dio.get('/reservations/$id');
      return ReservationModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw AppException.fromDio(e);
    }
  }

  Future<List<ReservationModel>> getMyReservations() async {
    try {
      final response = await _dio.get('/reservations/user/me');
      final list = response.data as List<dynamic>;
      return list
          .map((e) => ReservationModel.fromJson(e as Map<String, dynamic>))
          .toList();
    } on DioException catch (e) {
      throw AppException.fromDio(e);
    }
  }

  Future<ReservationModel> cancel(String id) async {
    try {
      final response = await _dio.post('/reservations/$id/cancel');
      return ReservationModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw AppException.fromDio(e);
    }
  }

  Future<ReservationModel> checkIn(String id) async {
    try {
      final response = await _dio.post('/reservations/$id/checkin');
      return ReservationModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw AppException.fromDio(e);
    }
  }

  Future<ReservationModel> checkOut(String id) async {
    try {
      final response = await _dio.post('/reservations/$id/checkout');
      return ReservationModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw AppException.fromDio(e);
    }
  }
}
