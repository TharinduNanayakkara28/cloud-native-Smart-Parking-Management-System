import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';
import 'package:smart_parking/core/exceptions/app_exception.dart';
import 'package:smart_parking/features/auth/data/auth_repository.dart';

import 'auth_repository_test.mocks.dart';

@GenerateMocks([Dio])
void main() {
  late MockDio mockDio;
  late AuthRepository repository;

  setUp(() {
    mockDio = MockDio();
    repository = AuthRepository(mockDio);
  });

  RequestOptions opts(String path) => RequestOptions(path: path);

  group('AuthRepository.login', () {
    test('returns tokens on 200', () async {
      when(mockDio.post('/auth/login', data: anyNamed('data'))).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/auth/login'),
          statusCode: 200,
          data: {'accessToken': 'acc', 'refreshToken': 'ref'},
        ),
      );

      final result = await repository.login('a@b.com', 'pass123');

      expect(result['accessToken'], 'acc');
      expect(result['refreshToken'], 'ref');
    });

    test('throws AppException on 401 invalid credentials', () async {
      when(mockDio.post('/auth/login', data: anyNamed('data'))).thenThrow(
        DioException(
          requestOptions: opts('/auth/login'),
          response: Response(
            requestOptions: opts('/auth/login'),
            statusCode: 401,
            data: {
              'status': 401,
              'title': 'Unauthorized',
              'detail': 'Invalid credentials',
            },
          ),
          type: DioExceptionType.badResponse,
        ),
      );

      expect(
        () => repository.login('wrong@b.com', 'wrong'),
        throwsA(
          isA<AppException>().having((e) => e.status, 'status', 401),
        ),
      );
    });
  });

  group('AuthRepository.register', () {
    test('returns tokens on 201', () async {
      when(mockDio.post('/auth/register', data: anyNamed('data'))).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/auth/register'),
          statusCode: 201,
          data: {'accessToken': 'acc', 'refreshToken': 'ref'},
        ),
      );

      final result = await repository.register(
        name: 'Alice',
        email: 'alice@example.com',
        password: 'secret123',
        phone: '0712345678',
      );

      expect(result['accessToken'], 'acc');
    });

    test('throws AppException on 409 email taken', () async {
      when(mockDio.post('/auth/register', data: anyNamed('data'))).thenThrow(
        DioException(
          requestOptions: opts('/auth/register'),
          response: Response(
            requestOptions: opts('/auth/register'),
            statusCode: 409,
            data: {
              'status': 409,
              'title': 'Conflict',
              'detail': 'Email already registered',
            },
          ),
          type: DioExceptionType.badResponse,
        ),
      );

      expect(
        () => repository.register(
          name: 'Alice',
          email: 'alice@example.com',
          password: 'secret123',
          phone: '0712345678',
        ),
        throwsA(
          isA<AppException>()
              .having((e) => e.status, 'status', 409)
              .having((e) => e.detail, 'detail', 'Email already registered'),
        ),
      );
    });
  });

  group('AuthRepository.getProfile', () {
    test('returns user data on 200', () async {
      when(mockDio.get('/users/me')).thenAnswer(
        (_) async => Response(
          requestOptions: opts('/users/me'),
          statusCode: 200,
          data: {
            'id': 'user-uuid',
            'name': 'Alice',
            'email': 'alice@example.com',
            'phone': '0712345678',
          },
        ),
      );

      final result = await repository.getProfile();

      expect(result['name'], 'Alice');
      expect(result['id'], 'user-uuid');
    });

    test('throws AppException on 401 expired token', () async {
      when(mockDio.get('/users/me')).thenThrow(
        DioException(
          requestOptions: opts('/users/me'),
          response: Response(
            requestOptions: opts('/users/me'),
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
        () => repository.getProfile(),
        throwsA(isA<AppException>()),
      );
    });
  });
}
