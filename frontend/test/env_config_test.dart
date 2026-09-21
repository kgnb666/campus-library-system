import 'package:campus_library_frontend/core/config/env_config.dart';
import 'package:flutter_test/flutter_test.dart';

/// EnvConfig API 根地址解析测试
///
/// 背景：dev 下 baseUrl 原先硬编码 `http://localhost:8080`，而 8080 常被其它项目占用，
/// 前端无法指到本机其它端口的后端；生产下则完全无法指定地址（产物退化成占位域名）。
/// 现在支持 `--dart-define=API_BASE_URL=...` 注入，并且在生产环境默认
/// **从页面 origin 推导**同源绝对地址（Stage 10-K）。
void main() {
  group('EnvConfig.parseEnvironment', () {
    test('识别 prod / test，其它一律按 dev 处理（不静默当成生产）', () {
      expect(EnvConfig.parseEnvironment('prod'), AppEnvironment.prod);
      expect(EnvConfig.parseEnvironment('PRODUCTION'), AppEnvironment.prod);
      expect(EnvConfig.parseEnvironment(' prod '), AppEnvironment.prod);
      expect(EnvConfig.parseEnvironment('test'), AppEnvironment.test);
      expect(EnvConfig.parseEnvironment('dev'), AppEnvironment.dev);
      expect(EnvConfig.parseEnvironment(''), AppEnvironment.dev);
      expect(EnvConfig.parseEnvironment('prd'), AppEnvironment.dev);
    });
  });

  group('EnvConfig.resolveBaseUrl', () {
    test('未注入覆盖值时按环境取默认地址', () {
      expect(EnvConfig.resolveBaseUrl('', AppEnvironment.dev),
          'http://localhost:8080/api/v1');
      expect(EnvConfig.resolveBaseUrl('', AppEnvironment.test),
          'http://test.campus.edu.cn/api/v1');
    });

    test('生产环境从页面 origin 推导同源绝对地址（无需构建期知道域名）', () {
      expect(
        EnvConfig.resolveBaseUrl('', AppEnvironment.prod,
            pageBase: Uri.parse('https://lib.example.edu/index.html')),
        'https://lib.example.edu/api/v1',
      );
      // 页面带端口时端口必须保留
      expect(
        EnvConfig.resolveBaseUrl('', AppEnvironment.prod,
            pageBase: Uri.parse('http://192.168.1.10:8080/#/login')),
        'http://192.168.1.10:8080/api/v1',
      );
      // 深链页面同样只取 origin
      expect(
        EnvConfig.resolveBaseUrl('', AppEnvironment.prod,
            pageBase: Uri.parse('https://lib.example.edu/#/books/12')),
        'https://lib.example.edu/api/v1',
      );
    });

    test('非 http(s) 页面（如 VM 下的 file://）无法推导，回落兜底值', () {
      expect(
        EnvConfig.resolveBaseUrl('', AppEnvironment.prod,
            pageBase: Uri.parse('file:///D:/repo/frontend/')),
        'https://library.campus.edu.cn/api/v1',
      );
    });

    test('注入值优先于 origin 推导与环境默认值', () {
      expect(
        EnvConfig.resolveBaseUrl('https://api.example.com/api/v1', AppEnvironment.prod,
            pageBase: Uri.parse('https://lib.example.edu/index.html')),
        'https://api.example.com/api/v1',
      );
      expect(
        EnvConfig.resolveBaseUrl('http://localhost:28080/api/v1', AppEnvironment.dev),
        'http://localhost:28080/api/v1',
      );
    });

    test('注入值尾部斜杠被去除，避免与 /auth/login 拼出双斜杠', () {
      expect(
        EnvConfig.resolveBaseUrl('http://localhost:28080/api/v1/', AppEnvironment.dev),
        'http://localhost:28080/api/v1',
      );
    });

    test('注入值两端空白被忽略', () {
      expect(
        EnvConfig.resolveBaseUrl('  http://localhost:28080/api/v1  ', AppEnvironment.dev),
        'http://localhost:28080/api/v1',
      );
    });

    test('相对路径注入被接受（仅 Web 可用，Dio 在非 Web 平台会拒绝）', () {
      expect(EnvConfig.resolveBaseUrl('/api/v1', AppEnvironment.prod), '/api/v1');
      expect(EnvConfig.resolveBaseUrl('/api/v1/', AppEnvironment.prod), '/api/v1');
    });

    test('非法注入值被忽略并回落默认值', () {
      for (final invalid in <String>['localhost:28080/api/v1', 'api/v1', 'ftp://x/api/v1']) {
        expect(
          EnvConfig.resolveBaseUrl(invalid, AppEnvironment.dev),
          'http://localhost:8080/api/v1',
          reason: '非法注入值 "$invalid" 应被忽略',
        );
      }
    });

    test('baseUrl getter 与常量解析路径一致，且随 --dart-define 注入切换', () {
      // 未注入时等于 dev 默认地址；注入了则必须等于注入值（去掉尾部斜杠的规范化形式）。
      // 两种运行方式都要绿：flutter test 与 flutter test --dart-define=API_BASE_URL=...
      expect(
        EnvConfig.baseUrl,
        EnvConfig.resolveBaseUrl(EnvConfig.apiBaseUrlOverride, EnvConfig.currentEnvironment),
      );

      final injected = EnvConfig.apiBaseUrlOverride.trim();
      if (injected.isEmpty) {
        expect(EnvConfig.baseUrl, 'http://localhost:8080/api/v1');
      } else {
        expect(EnvConfig.baseUrl, injected.replaceFirst(RegExp(r'/$'), ''));
      }
    });
  });
}
