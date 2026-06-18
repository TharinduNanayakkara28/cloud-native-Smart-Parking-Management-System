import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';
import 'package:smart_parking/core/auth/auth_notifier.dart';
import 'package:smart_parking/core/auth/token_storage.dart';
import 'package:smart_parking/core/exceptions/app_exception.dart';
import 'package:smart_parking/features/auth/data/auth_repository.dart';
import 'package:smart_parking/features/auth/domain/user_model.dart';

import 'auth_notifier_test.mocks.dart';

@GenerateMocks([TokenStorage, AuthRepository])
void main() {
  late MockTokenStorage mockStorage;
  late MockAuthRepository mockRepo;

  setUp(() {
    mockStorage = MockTokenStorage();
    mockRepo = MockAuthRepository();
  });

  ProviderContainer makeContainer() => ProviderContainer(
        overrides: [
          tokenStorageProvider.overrideWithValue(mockStorage),
          authRepositoryProvider.overrideWithValue(mockRepo),
        ],
      );

  group('AuthNotifier.build', () {
    test('returns null when no token in storage', () async {
      when(mockStorage.getAccessToken()).thenAnswer((_) async => null);

      final container = makeContainer();
      addTearDown(container.dispose);

      final user = await container.read(authNotifierProvider.future);
      expect(user, isNull);
    });

    test('returns UserModel when valid token and profile succeed', () async {
      when(mockStorage.getAccessToken())
          .thenAnswer((_) async => 'valid-token');
      when(mockRepo.getProfile()).thenAnswer(
        (_) async => {
          'id': 'uuid-1',
          'name': 'Alice',
          'email': 'alice@example.com',
          'phone': '0712345678',
        },
      );

      final container = makeContainer();
      addTearDown(container.dispose);

      final user = await container.read(authNotifierProvider.future);
      expect(user, isA<UserModel>());
      expect(user?.name, 'Alice');
    });

    test('clears tokens and returns null when getProfile throws', () async {
      when(mockStorage.getAccessToken())
          .thenAnswer((_) async => 'expired-token');
      when(mockRepo.getProfile()).thenThrow(
        const AppException(status: 401, title: 'Unauthorized', detail: 'Expired'),
      );
      when(mockStorage.clear()).thenAnswer((_) async {});

      final container = makeContainer();
      addTearDown(container.dispose);

      final user = await container.read(authNotifierProvider.future);
      expect(user, isNull);
      verify(mockStorage.clear()).called(1);
    });
  });

  group('AuthNotifier.login', () {
    test('sets user state on success', () async {
      when(mockStorage.getAccessToken()).thenAnswer((_) async => null);
      when(mockRepo.login('a@b.com', 'pass123')).thenAnswer(
        (_) async => {'accessToken': 'acc', 'refreshToken': 'ref'},
      );
      when(mockStorage.saveTokens('acc', 'ref')).thenAnswer((_) async {});
      when(mockRepo.getProfile()).thenAnswer(
        (_) async => {
          'id': 'uuid-1',
          'name': 'Alice',
          'email': 'a@b.com',
        },
      );

      final container = makeContainer();
      addTearDown(container.dispose);
      await container.read(authNotifierProvider.future);

      await container
          .read(authNotifierProvider.notifier)
          .login('a@b.com', 'pass123');

      final state = container.read(authNotifierProvider);
      expect(state.valueOrNull?.name, 'Alice');
    });

    test('sets error state on bad credentials', () async {
      when(mockStorage.getAccessToken()).thenAnswer((_) async => null);
      when(mockRepo.login(any, any)).thenThrow(
        const AppException(
          status: 401,
          title: 'Unauthorized',
          detail: 'Invalid credentials',
        ),
      );

      final container = makeContainer();
      addTearDown(container.dispose);
      await container.read(authNotifierProvider.future);

      await container
          .read(authNotifierProvider.notifier)
          .login('wrong@b.com', 'wrong');

      final state = container.read(authNotifierProvider);
      expect(state.hasError, isTrue);
    });
  });

  group('AuthNotifier.logout', () {
    test('clears state and tokens', () async {
      when(mockStorage.getAccessToken())
          .thenAnswer((_) async => 'valid-token');
      when(mockRepo.getProfile()).thenAnswer(
        (_) async => {'id': 'uuid-1', 'name': 'Alice', 'email': 'a@b.com'},
      );
      when(mockStorage.clear()).thenAnswer((_) async {});

      final container = makeContainer();
      addTearDown(container.dispose);
      await container.read(authNotifierProvider.future);

      await container.read(authNotifierProvider.notifier).logout();

      final state = container.read(authNotifierProvider);
      expect(state.valueOrNull, isNull);
      verify(mockStorage.clear()).called(1);
    });
  });

  group('AuthNotifier.register', () {
    test('sets user state after successful registration', () async {
      when(mockStorage.getAccessToken()).thenAnswer((_) async => null);
      when(
        mockRepo.register(
          name: anyNamed('name'),
          email: anyNamed('email'),
          password: anyNamed('password'),
          phone: anyNamed('phone'),
        ),
      ).thenAnswer(
        (_) async => {'accessToken': 'acc', 'refreshToken': 'ref'},
      );
      when(mockStorage.saveTokens('acc', 'ref')).thenAnswer((_) async {});
      when(mockRepo.getProfile()).thenAnswer(
        (_) async => {
          'id': 'uuid-2',
          'name': 'Bob',
          'email': 'bob@b.com',
          'phone': '0712345679',
        },
      );

      final container = makeContainer();
      addTearDown(container.dispose);
      await container.read(authNotifierProvider.future);

      await container.read(authNotifierProvider.notifier).register(
            name: 'Bob',
            email: 'bob@b.com',
            password: 'secret123',
            phone: '0712345679',
          );

      final state = container.read(authNotifierProvider);
      expect(state.valueOrNull?.name, 'Bob');
    });
  });
}
