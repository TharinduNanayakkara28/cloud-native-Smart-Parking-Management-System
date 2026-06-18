import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';
import 'package:smart_parking/core/exceptions/app_exception.dart';
import 'package:smart_parking/features/vehicle/data/vehicle_repository.dart';
import 'package:smart_parking/features/vehicle/domain/vehicle_model.dart';

import 'vehicle_repository_test.mocks.dart';

@GenerateMocks([Dio])
void main() {
  late MockDio mockDio;
  late VehicleRepository repository;

  setUp(() {
    mockDio = MockDio();
    repository = VehicleRepository(mockDio);
  });

  RequestOptions opts(String path) => RequestOptions(path: path);

  final _rawVehicle = {
    'id': 'vehicle-uuid',
    'plate': 'ABC-001',
    'make': 'Toyota',
    'model': 'Camry',
    'isDefault': false,
  };

  group('VehicleRepository.getMyVehicles', () {
    test('returns list of vehicles', () async {
      when(mockDio.get('/users/me/vehicles')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/users/me/vehicles'),
          statusCode: 200,
          data: [_rawVehicle, {..._rawVehicle, 'id': 'vehicle-uuid-2', 'plate': 'XYZ-999'}],
        ),
      );

      final result = await repository.getMyVehicles();
      expect(result, hasLength(2));
      expect(result.first, isA<VehicleModel>());
    });

    test('returns empty list when no vehicles', () async {
      when(mockDio.get('/users/me/vehicles')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/users/me/vehicles'),
          statusCode: 200,
          data: <dynamic>[],
        ),
      );

      final result = await repository.getMyVehicles();
      expect(result, isEmpty);
    });

    test('throws AppException on 401 (token expired)', () async {
      when(mockDio.get('/users/me/vehicles')).thenThrow(
        DioException(
          requestOptions: opts('/users/me/vehicles'),
          response: Response(
            requestOptions: opts('/users/me/vehicles'),
            statusCode: 401,
            data: {
              'status': 401,
              'title': 'Unauthorized',
              'detail': 'Token expired',
            },
          ),
          type: DioExceptionType.badResponse,
        ),
      );

      expect(
        () => repository.getMyVehicles(),
        throwsA(isA<AppException>().having((e) => e.status, 'status', 401)),
      );
    });
  });

  group('VehicleRepository.addVehicle', () {
    test('returns VehicleModel on 201', () async {
      when(
        mockDio.post('/users/me/vehicles', data: anyNamed('data')),
      ).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/users/me/vehicles'),
          statusCode: 201,
          data: _rawVehicle,
        ),
      );

      final result = await repository.addVehicle(
        plate: 'ABC-001',
        make: 'Toyota',
        model: 'Camry',
      );

      expect(result, isA<VehicleModel>());
      expect(result.plate, 'ABC-001');
      expect(result.make, 'Toyota');
    });

    test('throws AppException on 409 duplicate plate', () async {
      when(
        mockDio.post('/users/me/vehicles', data: anyNamed('data')),
      ).thenThrow(
        DioException(
          requestOptions: opts('/users/me/vehicles'),
          response: Response(
            requestOptions: opts('/users/me/vehicles'),
            statusCode: 409,
            data: {
              'status': 409,
              'title': 'Conflict',
              'detail': 'Vehicle with plate ABC-001 already registered',
            },
          ),
          type: DioExceptionType.badResponse,
        ),
      );

      expect(
        () => repository.addVehicle(plate: 'ABC-001'),
        throwsA(
          isA<AppException>()
              .having((e) => e.status, 'status', 409)
              .having(
                (e) => e.detail,
                'detail',
                'Vehicle with plate ABC-001 already registered',
              ),
        ),
      );
    });
  });

  group('VehicleModel', () {
    test('parses all fields from JSON', () {
      final model = VehicleModel.fromJson(_rawVehicle);
      expect(model.id, 'vehicle-uuid');
      expect(model.plate, 'ABC-001');
      expect(model.make, 'Toyota');
      expect(model.model, 'Camry');
      expect(model.isDefault, isFalse);
    });

    test('isDefault defaults to false when absent', () {
      final model = VehicleModel.fromJson({
        ..._rawVehicle,
        'isDefault': null,
      }..remove('isDefault'));
      expect(model.isDefault, isFalse);
    });

    test('make and model default to empty string when null', () {
      final model = VehicleModel.fromJson({
        'id': 'vehicle-uuid',
        'plate': 'ABC-001',
        'make': null,
        'model': null,
      });
      expect(model.make, '');
      expect(model.model, '');
    });

    test('displayName returns plate when make and model are empty', () {
      final model = VehicleModel.fromJson({
        'id': 'vehicle-uuid',
        'plate': 'ABC-001',
        'make': '',
        'model': '',
      });
      expect(model.displayName, 'ABC-001');
    });

    test('displayName returns make + model when both set', () {
      final model = VehicleModel.fromJson(_rawVehicle);
      expect(model.displayName, 'Toyota Camry');
    });
  });
}
