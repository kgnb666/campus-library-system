import 'package:campus_library_frontend/core/network/api_client.dart';
import 'package:campus_library_frontend/core/storage/token_storage.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

/// API 客户端拼装请求地址的行为测试 (Stage 10-K)。
///
/// 生产部署形态是"网关同源"：前端与 `/api/` 挂同一域名，运行时从页面 origin
/// 推导出绝对地址 `https://<域名>/api/v1`。这条链路上有两个必须锁死的点：
///   1. Dio 必须接受该绝对地址并正确拼接路径（不能出现双斜杠或缺段）；
///   2. 相对 baseUrl 在**非 Web 平台会被 Dio 直接拒绝** —— 因此它只能是
///      "显式注入时的可选项"，绝不能作为默认值。第 2 条在本文件用断言的
///      方式固化下来，避免以后有人图省事把默认值改成相对路径。
void main() {
  group('API 客户端地址拼装 (Stage 10-K)', () {
    late List<RequestOptions> captured;
    late ProviderContainer container;

    ProviderContainer buildContainer(String baseUrl) {
      final c = ProviderContainer(overrides: [
        apiBaseUrlProvider.overrideWithValue(baseUrl),
        tokenStorageProvider.overrideWithValue(_FakeTokenStorage()),
      ]);
      c.read(apiClientProvider).httpClientAdapter = _RecordingAdapter(
        onRequest: (options) => captured.add(options),
      );
      return c;
    }

    setUp(() => captured = <RequestOptions>[]);
    tearDown(() => container.dispose());

    test('同源绝对地址（生产形态）拼出的请求路径正确', () async {
      container = buildContainer('https://lib.example.edu/api/v1');
      final dio = container.read(apiClientProvider);

      await dio.request<dynamic>('/auth/login', data: {'username': 'x'});

      final uri = captured.single.uri;
      expect(uri.toString(), 'https://lib.example.edu/api/v1/auth/login');
      expect(uri.host, 'lib.example.edu');
    });

    test('本地开发用的带端口绝对地址同样正确', () async {
      container = buildContainer('http://localhost:28080/api/v1');
      final dio = container.read(apiClientProvider);

      await dio.request<dynamic>('/books?page=0&size=10');

      expect(captured.single.uri.toString(),
          'http://localhost:28080/api/v1/books?page=0&size=10');
    });

    test('请求路径缺少前导斜杠时不会拼出畸形地址（不出现双斜杠）', () async {
      container = buildContainer('https://lib.example.edu/api/v1');
      final dio = container.read(apiClientProvider);

      await dio.request<dynamic>('books');

      // 断言的是"拼接结果不含双斜杠"这一实质要求，而不是 Dio 内部的补斜杠规则：
      // 真实调用点全部使用 '/xxx' 形式，这里只兜住手误漏写斜杠的情况。
      final path = captured.single.uri.path;
      expect(path, isNot(contains('//')));
      expect(path, contains('api/v1'));
      expect(path, contains('books'));
    });

    test('相对 baseUrl 在非 Web 平台被 Dio 拒绝 —— 故不可作为默认值', () {
      // 这不是"我们想要的"行为，而是 Dio 的平台约束，必须显式记录：
      // 一旦把生产默认值写成 /api/v1，单元测试与所有非 Web 端会立刻炸在这里。
      expect(
        () => BaseOptions(baseUrl: '/api/v1'),
        throwsA(isA<ArgumentError>()),
      );
    });
  });
}

class _FakeTokenStorage implements TokenStorage {
  @override
  Future<String?> getAccessToken() async => null;

  @override
  Future<String?> getRefreshToken() async => null;

  @override
  Future<void> saveTokens({required String accessToken, required String refreshToken}) async {}

  @override
  Future<void> saveUserProfile(String userJson) async {}

  @override
  Future<String?> getUserProfile() async => null;

  @override
  Future<void> clear() async {}

  @override
  bool get isEphemeral => false;
}

/// 只记录、不回真实网络：用于观察 Dio 最终拼出的请求
class _RecordingAdapter implements HttpClientAdapter {
  _RecordingAdapter({required this.onRequest});

  final void Function(RequestOptions options) onRequest;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<List<int>>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    onRequest(options);
    return ResponseBody.fromString(
      '{"code":"SUCCESS","message":"ok","data":null}',
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
