import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../features/auth/data/auth_repository.dart';
import '../../features/auth/domain/user_model.dart';
import 'token_storage.dart';

final authNotifierProvider =
    AsyncNotifierProvider<AuthNotifier, UserModel?>(AuthNotifier.new);

class AuthNotifier extends AsyncNotifier<UserModel?> {
  @override
  Future<UserModel?> build() async {
    final tokenStorage = ref.read(tokenStorageProvider);
    final token = await tokenStorage.getAccessToken();
    if (token == null) return null;

    try {
      final repo = ref.read(authRepositoryProvider);
      final data = await repo.getProfile();
      return UserModel.fromJson(data);
    } catch (_) {
      await tokenStorage.clear();
      return null;
    }
  }

  Future<void> login(String email, String password) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final repo = ref.read(authRepositoryProvider);
      final tokenStorage = ref.read(tokenStorageProvider);

      final data = await repo.login(email, password);
      await tokenStorage.saveTokens(
        data['accessToken'] as String,
        data['refreshToken'] as String,
      );

      final profileData = await repo.getProfile();
      return UserModel.fromJson(profileData);
    });
  }

  Future<void> register({
    required String name,
    required String email,
    required String password,
    required String phone,
  }) async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final repo = ref.read(authRepositoryProvider);
      final tokenStorage = ref.read(tokenStorageProvider);

      final data = await repo.register(
        name: name,
        email: email,
        password: password,
        phone: phone,
      );
      await tokenStorage.saveTokens(
        data['accessToken'] as String,
        data['refreshToken'] as String,
      );

      final profileData = await repo.getProfile();
      return UserModel.fromJson(profileData);
    });
  }

  Future<void> logout() async {
    final tokenStorage = ref.read(tokenStorageProvider);
    await tokenStorage.clear();
    state = const AsyncData(null);
  }
}
