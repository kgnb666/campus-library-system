import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../shared/utils/app_logger.dart';

/// 安全存储提供者
final tokenStorageProvider = Provider<TokenStorage>((ref) {
  return TokenStorage();
});

/// 基于 flutter_secure_storage 的安全凭据存储服务 (Stage 1-B)
///
/// ## Web 端的 secure context 降级 (Stage 10-I)
///
/// `flutter_secure_storage` 的 Web 实现直接调用浏览器 `crypto.subtle`，
/// 而该 API 只在 <b>secure context</b>（https，或 http 下的 localhost / 127.0.0.1）可用。
/// 用 `http://192.168.x.x:8080` 这类局域网地址打开前端时，`crypto.subtle` 为 undefined，
/// 读写会抛出原生 JS 层错误。
///
/// 关键问题在于抛出的位置：`ApiClient` 的 `onRequest` 拦截器里会读 access token，
/// 于是<b>每个请求</b>都会因为存储层异常而失败，且错误与 HTTP 无关，
/// 用户只看到"操作失败，请稍后重试"，排查方向完全被带偏。
///
/// 因此这里做两件事：
/// 1. 先判定是否会遇到非 secure context；是则**不调用** secure storage，
///    改用内存降级存储（会话内可用，刷新页面即失效），并把凭据标记为易失；
/// 2. 任何存储异常都被吞掉并降级，绝不让存储故障升级为"所有请求都失败"。
///    调用方可通过 [isEphemeral] 判断当前是否处于降级状态，从而给出可读提示。
class TokenStorage {
  static const _keyAccessToken = 'auth_access_token';
  static const _keyRefreshToken = 'auth_refresh_token';
  static const _keyUserProfile = 'auth_user_profile';

  final FlutterSecureStorage _storage;

  /// 内存降级存储：仅在 secure context 不可用时使用
  final Map<String, String> _volatileStore = <String, String>{};

  /// 是否已降级为内存存储（凭据不会持久化，刷新页面即丢失）
  bool get isEphemeral => _ephemeral;

  bool _ephemeral;

  TokenStorage({FlutterSecureStorage? storage, bool? forceEphemeral})
      : _storage = storage ??
            const FlutterSecureStorage(
              aOptions: AndroidOptions(encryptedSharedPreferences: true),
              iOptions: IOSOptions(accessibility: KeychainAccessibility.first_unlock),
            ),
        _ephemeral = forceEphemeral ?? !_isSecureContextAvailable();

  /// 浏览器安全上下文判定。
  ///
  /// 与浏览器自身的判定保持一致：https 一律安全；
  /// http 下仅 localhost / 127.0.0.1 / ::1 被视为安全上下文（这是 WHATWG 的既有约定）。
  /// 非 Web 平台（移动端/桌面端）不受此限制。
  static bool _isSecureContextAvailable() {
    if (!kIsWeb) {
      return true;
    }
    final base = Uri.base;
    if (base.scheme == 'https') {
      return true;
    }
    const loopbackHosts = <String>{'localhost', '127.0.0.1', '::1', '[::1]'};
    return loopbackHosts.contains(base.host);
  }

  Future<String?> _read(String key) async {
    if (_ephemeral) {
      return _volatileStore[key];
    }
    try {
      return await _storage.read(key: key);
    } catch (_) {
      _fallbackToEphemeral();
      return _volatileStore[key];
    }
  }

  Future<void> _write(String key, String value) async {
    if (_ephemeral) {
      _volatileStore[key] = value;
      return;
    }
    try {
      await _storage.write(key: key, value: value);
    } catch (_) {
      _fallbackToEphemeral();
      _volatileStore[key] = value;
    }
  }

  Future<void> _delete(String key) async {
    _volatileStore.remove(key);
    if (_ephemeral) {
      return;
    }
    try {
      await _storage.delete(key: key);
    } catch (_) {
      // 删除失败不影响降级后的可用性：内存中的副本已移除
    }
  }

  void _fallbackToEphemeral() {
    _ephemeral = true;
    AppLogger.w('安全存储不可用，已降级为内存存储：凭据不会持久化，刷新页面后需要重新登录');
  }

  /// 保存双 Token
  Future<void> saveTokens({
    required String accessToken,
    required String refreshToken,
  }) async {
    await _write(_keyAccessToken, accessToken);
    await _write(_keyRefreshToken, refreshToken);
  }

  /// 获取短期 Access Token
  Future<String?> getAccessToken() async {
    return await _read(_keyAccessToken);
  }

  /// 获取长期 Refresh Token
  Future<String?> getRefreshToken() async {
    return await _read(_keyRefreshToken);
  }

  /// 缓存用户资料 JSON
  Future<void> saveUserProfile(String userJson) async {
    await _write(_keyUserProfile, userJson);
  }

  /// 读取用户资料 JSON
  Future<String?> getUserProfile() async {
    return await _read(_keyUserProfile);
  }

  /// 清除所有认证凭据 (登出/失效)
  Future<void> clear() async {
    await _delete(_keyAccessToken);
    await _delete(_keyRefreshToken);
    await _delete(_keyUserProfile);
  }
}
