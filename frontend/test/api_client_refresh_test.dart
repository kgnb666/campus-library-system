import 'dart:convert';
import 'dart:typed_data';

import 'package:campus_library_frontend/core/network/api_client.dart';
import 'package:campus_library_frontend/core/session/session_events.dart';
import 'package:campus_library_frontend/core/storage/token_storage.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

/// 可编排的假传输层：区分"业务请求"与"刷新请求"，并记录调用次数与请求头
class _ScriptedAdapter implements HttpClientAdapter {
  _ScriptedAdapter();

  /// /auth/refresh 的返回状态码与响应体
  int refreshStatus = 200;
  Map<String, dynamic> refreshBody = const {};

  /// 业务请求在"刷新前/刷新后"的返回状态码
  int businessStatusBefore = 401;
  int businessStatusAfter = 200;

  int refreshCalls = 0;
  int businessCalls = 0;
  final List<String?> businessAuthHeaders = <String?>[];
  bool _refreshed = false;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    if (options.path.contains('/auth/refresh')) {
      refreshCalls++;
      _refreshed = true;
      return _json(refreshBody, refreshStatus);
    }

    businessCalls++;
    businessAuthHeaders.add(options.headers['Authorization']?.toString());
    final status = _refreshed ? businessStatusAfter : businessStatusBefore;
    return _json(
      {
        'code': status == 200 ? 'SUCCESS' : 'AUTH_UNAUTHORIZED',
        'data': status == 200 ? {'ok': true} : null,
      },
      status,
    );
  }

  ResponseBody _json(Map<String, dynamic> body, int status) => ResponseBody.fromString(
        jsonEncode(body),
        status,
        headers: {
          Headers.contentTypeHeader: [Headers.jsonContentType],
        },
      );

  @override
  void close({bool force = false}) {}
}

class FakeTokenStorage implements TokenStorage {
  FakeTokenStorage({this.accessToken, this.refreshToken});

  String? accessToken;
  String? refreshToken;

  @override
  Future<void> saveTokens({
    required String accessToken,
    required String refreshToken,
  }) async {
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
  }

  @override
  Future<String?> getAccessToken() async => accessToken;

  @override
  Future<String?> getRefreshToken() async => refreshToken;

  @override
  Future<void> saveUserProfile(String userJson) async {}

  @override
  Future<String?> getUserProfile() async => null;

  @override
  Future<void> clear() async {
    accessToken = null;
    refreshToken = null;
  }

  /// 测试替身始终视为持久化可用（不模拟 Web 非安全上下文降级）
  @override
  bool get isEphemeral => false;
}

void main() {
  group('ApiClient 401 自动刷新链路 (Stage 10-C)', () {
    late FakeTokenStorage storage;
    late ProviderContainer container;
    late _ScriptedAdapter adapter;

    setUp(() {
      storage = FakeTokenStorage(
        accessToken: 'expired-access-token',
        refreshToken: 'old-refresh-token',
      );
      adapter = _ScriptedAdapter();
      container = ProviderContainer(
        overrides: [tokenStorageProvider.overrideWithValue(storage)],
      );
      addTearDown(container.dispose);
      container.read(apiClientProvider).httpClientAdapter = adapter;
    });

    test('刷新返回 code=SUCCESS 时应换发新令牌并重试原请求', () async {
      // 后端成功码是字符串 "SUCCESS"；原实现用 code == 200 判定，恒为 false，
      // 于是每次刷新都被判失败、凭据被清空 —— 这正是本用例锁定的缺陷
      adapter.refreshBody = {
        'code': 'SUCCESS',
        'data': {'accessToken': 'new-access-token', 'refreshToken': 'new-refresh-token'},
      };

      final response = await container.read(apiClientProvider).get<dynamic>('/books');

      expect(response.statusCode, 200);
      expect(adapter.refreshCalls, 1, reason: '应恰好刷新一次');
      expect(adapter.businessCalls, 2, reason: '首次 401 + 换发后重试一次');
      expect(adapter.businessAuthHeaders.first, 'Bearer expired-access-token');
      expect(adapter.businessAuthHeaders.last, 'Bearer new-access-token',
          reason: '重试必须携带新换发的令牌');
      expect(storage.accessToken, 'new-access-token');
      expect(container.read(sessionExpiredProvider), 0, reason: '不应误判为会话过期');
    });

    test('必须保存轮换后的新 Refresh Token（否则下次刷新会因旧令牌已轮换而失败）', () async {
      adapter.refreshBody = {
        'code': 'SUCCESS',
        'data': {'accessToken': 'new-access-token', 'refreshToken': 'new-refresh-token'},
      };

      await container.read(apiClientProvider).get<dynamic>('/books');

      expect(storage.refreshToken, 'new-refresh-token');
      expect(storage.refreshToken, isNot('old-refresh-token'));
    });

    test('刷新成功但业务请求仍持续 401 时只重试一次，不形成无限刷新循环', () async {
      adapter.refreshBody = {
        'code': 'SUCCESS',
        'data': {'accessToken': 'new-access-token', 'refreshToken': 'new-refresh-token'},
      };
      adapter.businessStatusAfter = 401;

      await expectLater(
        container.read(apiClientProvider).get<dynamic>('/books'),
        throwsA(isA<DioException>()),
      );

      expect(adapter.refreshCalls, 1, reason: '不得反复刷新');
      expect(adapter.businessCalls, 2, reason: '首次 + 重试一次后放弃');
    });

    test('刷新接口返回 401 时应清空凭据并广播会话过期事件', () async {
      adapter.refreshStatus = 401;
      adapter.refreshBody = {'code': 'REFRESH_TOKEN_INVALID'};

      await expectLater(
        container.read(apiClientProvider).get<dynamic>('/books'),
        throwsA(isA<DioException>()),
      );

      expect(storage.accessToken, isNull);
      expect(storage.refreshToken, isNull);
      expect(container.read(sessionExpiredProvider), 1,
          reason: '必须广播会话过期，否则路由守卫不会跳登录页');
    });

    test('登录接口自身的 401 不应触发刷新（密码错误不是令牌过期）', () async {
      adapter.businessStatusBefore = 401;

      await expectLater(
        container.read(apiClientProvider).post<dynamic>('/auth/login', data: {}),
        throwsA(isA<DioException>()),
      );

      expect(adapter.refreshCalls, 0);
    });

    test('并发 401 只触发一次刷新（互斥，避免刷新风暴）', () async {
      adapter.refreshBody = {
        'code': 'SUCCESS',
        'data': {'accessToken': 'new-access-token', 'refreshToken': 'new-refresh-token'},
      };

      final dio = container.read(apiClientProvider);
      await Future.wait([
        dio.get<dynamic>('/books'),
        dio.get<dynamic>('/notifications'),
        dio.get<dynamic>('/reservations/my'),
      ]);

      expect(adapter.refreshCalls, 1, reason: '三个并发 401 只应换发一次令牌');
    });
  });
}
