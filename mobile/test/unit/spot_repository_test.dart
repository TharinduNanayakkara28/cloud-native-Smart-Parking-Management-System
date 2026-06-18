import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';
import 'package:smart_parking/core/exceptions/app_exception.dart';
import 'package:smart_parking/features/map/data/spot_repository.dart';
import 'package:smart_parking/features/map/domain/spot_model.dart';

import 'spot_repository_test.mocks.dart';

@GenerateMocks([Dio])
void main() {
  late MockDio mockDio;
  late SpotRepository repository;

  setUp(() {
    mockDio = MockDio();
    repository = SpotRepository(mockDio);
  });

  RequestOptions opts(String path) => RequestOptions(path: path);

  final rawSpot = {
    'id': 'spot-uuid-1',
    'spotNumber': 'A1',
    'floor': 'G',
    'latitude': -6.2088,
    'longitude': 106.8456,
    'state': 'FREE',
    'lastUpdated': '2024-01-01T10:00:00Z',
  };

  group('SpotRepository.getAvailableSpots', () {
    test('returns parsed list on 200', () async {
      when(
        mockDio.get(
          '/spots/available',
          queryParameters: anyNamed('queryParameters'),
        ),
      ).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/spots/available'),
          statusCode: 200,
          data: [rawSpot],
        ),
      );

      final result = await repository.getAvailableSpots(
        lat: -6.2088,
        lng: 106.8456,
      );

      expect(result, hasLength(1));
      expect(result.first, isA<SpotModel>());
      expect(result.first.spotNumber, 'A1');
      expect(result.first.state, SpotState.free);
      expect(result.first.floor, 'G');
    });

    test('returns empty list when no spots found', () async {
      when(
        mockDio.get(
          '/spots/available',
          queryParameters: anyNamed('queryParameters'),
        ),
      ).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/spots/available'),
          statusCode: 200,
          data: <dynamic>[],
        ),
      );

      final result = await repository.getAvailableSpots(
        lat: -6.2088,
        lng: 106.8456,
      );

      expect(result, isEmpty);
    });

    test('passes correct query parameters to API', () async {
      when(
        mockDio.get(
          '/spots/available',
          queryParameters: anyNamed('queryParameters'),
        ),
      ).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/spots/available'),
          statusCode: 200,
          data: <dynamic>[],
        ),
      );

      await repository.getAvailableSpots(lat: -6.5, lng: 107.0, radius: 1000);

      verify(
        mockDio.get(
          '/spots/available',
          queryParameters: {'lat': -6.5, 'lng': 107.0, 'radius': 1000.0},
        ),
      ).called(1);
    });

    test('throws AppException on network error', () async {
      when(
        mockDio.get(
          '/spots/available',
          queryParameters: anyNamed('queryParameters'),
        ),
      ).thenThrow(
        DioException(
          requestOptions: opts('/spots/available'),
          response: Response(
            requestOptions: opts('/spots/available'),
            statusCode: 503,
            data: {
              'status': 503,
              'title': 'Service Unavailable',
              'detail': 'availability-service is down',
            },
          ),
          type: DioExceptionType.badResponse,
        ),
      );

      expect(
        () => repository.getAvailableSpots(lat: -6.2088, lng: 106.8456),
        throwsA(
          isA<AppException>()
              .having((e) => e.status, 'status', 503)
              .having((e) => e.detail, 'detail', 'availability-service is down'),
        ),
      );
    });
  });

  group('SpotModel.fromJson', () {
    test('parses FREE state correctly', () {
      final spot = SpotModel.fromJson({...rawSpot, 'state': 'FREE'});
      expect(spot.state, SpotState.free);
    });

    test('parses RESERVED state correctly', () {
      final spot = SpotModel.fromJson({...rawSpot, 'state': 'RESERVED'});
      expect(spot.state, SpotState.reserved);
    });

    test('parses OCCUPIED state correctly', () {
      final spot = SpotModel.fromJson({...rawSpot, 'state': 'OCCUPIED'});
      expect(spot.state, SpotState.occupied);
    });

    test('handles null floor gracefully', () {
      final spot = SpotModel.fromJson({...rawSpot, 'floor': null});
      expect(spot.floor, isNull);
    });

    test('copyWith updates state and preserves other fields', () {
      final original = SpotModel.fromJson(rawSpot);
      final updated = original.copyWith(state: SpotState.occupied);

      expect(updated.state, SpotState.occupied);
      expect(updated.id, original.id);
      expect(updated.spotNumber, original.spotNumber);
      expect(updated.latLng, original.latLng);
    });
  });
}
