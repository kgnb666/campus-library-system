import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// 安全存储提供者
final tokenStorageProvider = Provider<TokenStorage>((ref) {
  return TokenStorage();
});

/// 基于 flutter_secure_storage 的安全凭据存储服务 (Stage 1-B)
class TokenStorage {
  static const _keyAccessToken = 'auth_access_token';
  static const _keyRefreshToken = 'auth_refresh_token';
  static const _keyUserProfile = 'auth_user_profile';

  final FlutterSecureStorage _storage;

  TokenStorage({FlutterSecureStorage? storage})
      : _storage = storage ??
            const FlutterSecureStorage(
              aOptions: AndroidOptions(encryptedSharedPreferences: true),
              iOptions: IOSOptions(accessibility: KeychainAccessibility.first_unlock),
            );

  /// 保存双 Token
  Future<void> saveTokens({
    required String accessToken,
    required String refreshToken,
  }) async {
    await _storage.write(key: _keyAccessToken, value: accessToken);
    await _storage.write(key: _keyRefreshToken, value: refreshToken);
  }

  /// 获取短期 Access Token
  Future<String?> getAccessToken() async {
    return await _storage.read(key: _keyAccessToken);
  }

  /// 获取长期 Refresh Token
  Future<String?> getRefreshToken() async {
    return await _storage.read(key: _keyRefreshToken);
  }

  /// 缓存用户资料 JSON
  Future<void> saveUserProfile(String userJson) async {
    await _storage.write(key: _keyUserProfile, value: userJson);
  }

  /// 读取用户资料 JSON
  Future<String?> getUserProfile() async {
    return await _storage.read(key: _keyUserProfile);
  }

  /// 清除所有认证凭据 (登出/失效)
  Future<void> clear() async {
    await _storage.delete(key: _keyAccessToken);
    await _storage.delete(key: _keyRefreshToken);
    await _storage.delete(key: _keyUserProfile);
  }
}
