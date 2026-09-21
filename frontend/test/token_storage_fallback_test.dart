import 'package:campus_library_frontend/core/storage/token_storage.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';

/// TokenStorage 在非安全上下文下的降级行为测试 (Stage 10-I)
///
/// 背景：`flutter_secure_storage` 的 Web 实现直接调用 `crypto.subtle`，
/// 该 API 只在 secure context（https 或 http 下的 localhost）可用。
/// 用 `http://192.168.x.x` 打开前端时读写会抛异常，而 `ApiClient` 的 onRequest
/// 恰好会读 access token —— 于是每个请求都失败，且错误与 HTTP 无关，
/// 用户只看到"操作失败，请稍后重试"。
///
/// 这里守住两条底线：
/// 1. 存储不可用时**不抛异常**，降级为内存存储，并通过 isEphemeral 暴露状态；
/// 2. 存储层抛出的异常必须被就地消化，不能升级成"所有请求都失败"。
void main() {
  group('TokenStorage 降级 (Stage 10-I)', () {
    test('非安全上下文：构造即降级为内存存储，读写不抛异常且会话内可用', () async {
      final storage = TokenStorage(
        forceEphemeral: true,
        storage: _ThrowingSecureStorage(),
      );

      expect(storage.isEphemeral, isTrue, reason: '降级状态必须可被调用方感知');

      await storage.saveTokens(accessToken: 'a-token', refreshToken: 'r-token');
      expect(await storage.getAccessToken(), 'a-token');
      expect(await storage.getRefreshToken(), 'r-token');

      await storage.saveUserProfile('{"id":1}');
      expect(await storage.getUserProfile(), '{"id":1}');

      await storage.clear();
      expect(await storage.getAccessToken(), isNull);
      expect(await storage.getRefreshToken(), isNull);
      expect(await storage.getUserProfile(), isNull);
    });

    test('底层存储抛异常（crypto.subtle 不可用）：就地降级，异常不外泄', () async {
      final storage = TokenStorage(storage: _ThrowingSecureStorage());
      expect(storage.isEphemeral, isFalse, reason: '非 Web 平台默认不降级');

      // 写入触发底层异常 -> 捕获后降级，值进内存
      await storage.saveTokens(accessToken: 'a2', refreshToken: 'r2');
      expect(storage.isEphemeral, isTrue);
      expect(await storage.getAccessToken(), 'a2');

      // 读取路径同样不得抛出
      expect(await storage.getRefreshToken(), 'r2');
      // 清理路径同样不得抛出
      await storage.clear();
      expect(await storage.getAccessToken(), isNull);
    });
  });
}

/// 模拟"Web 非安全上下文"：底层任何操作都抛出原生层错误
class _ThrowingSecureStorage extends FlutterSecureStorage {
  const _ThrowingSecureStorage();

  @override
  Future<void> write({
    required String key,
    required String? value,
    IOSOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    throw StateError('crypto.subtle is undefined (insecure context)');
  }

  @override
  Future<String?> read({
    required String key,
    IOSOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    throw StateError('crypto.subtle is undefined (insecure context)');
  }

  @override
  Future<void> delete({
    required String key,
    IOSOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    throw StateError('crypto.subtle is undefined (insecure context)');
  }
}
