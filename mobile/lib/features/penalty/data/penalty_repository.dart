import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/api/api_client.dart';
import '../../../core/exceptions/app_exception.dart';
import '../domain/penalty_model.dart';

final penaltyRepositoryProvider = Provider<PenaltyRepository>((ref) {
  return PenaltyRepository(ref.read(apiClientProvider).dio);
});

final myPenaltiesProvider =
    FutureProvider.autoDispose<List<PenaltyModel>>((ref) async {
  final list = await ref.read(penaltyRepositoryProvider).getMyPenalties();
  // newest first, ISSUED before PAID/WAIVED
  list.sort((a, b) {
    if (a.status == PenaltyStatus.issued && b.status != PenaltyStatus.issued) {
      return -1;
    }
    if (b.status == PenaltyStatus.issued && a.status != PenaltyStatus.issued) {
      return 1;
    }
    return b.issuedAt.compareTo(a.issuedAt);
  });
  return list;
});

class PenaltyRepository {
  PenaltyRepository(this._dio);

  final Dio _dio;

  Future<List<PenaltyModel>> getMyPenalties() async {
    try {
      final response = await _dio.get('/penalties/user/me');
      return (response.data as List)
          .map((e) => PenaltyModel.fromJson(e as Map<String, dynamic>))
          .toList();
    } on DioException catch (e) {
      throw AppException.fromDio(e);
    }
  }

  Future<PenaltyModel> pay(String id) async {
    try {
      final response = await _dio.post('/penalties/$id/pay');
      return PenaltyModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw AppException.fromDio(e);
    }
  }
}
