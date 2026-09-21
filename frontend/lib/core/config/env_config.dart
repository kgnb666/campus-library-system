enum AppEnvironment { dev, test, prod }

/// 全局多环境网络与应用配置
///
/// API 根地址的解析优先级见 [EnvConfig.resolveBaseUrl]。多数场景不需要配置：
/// * 本地开发：默认指向 `http://localhost:8080/api/v1`；
///   后端换端口时用 `--dart-define` 覆盖：
///   ```bash
///   flutter run -d chrome --dart-define=API_BASE_URL=http://localhost:28080/api/v1
///   ```
/// * 生产（网关同源部署）：不注入，运行时从页面 origin 自动推导 `https://<你的域名>/api/v1`；
/// * 生产（跨域部署）：构建时注入绝对地址：
///   ```bash
///   flutter build web --release --dart-define=API_BASE_URL=https://api.example.com/api/v1
///   ```
class EnvConfig {
  /// 当前环境，支持构建期注入：`--dart-define=APP_ENV=prod`。
  ///
  /// 此前它是写死的 `const AppEnvironment.dev`，导致 test/prod 分支**永远不生效** ——
  /// 生产构建实际走的是 dev 分支（接口指向 http://localhost:8080）。
  /// 生产镜像已固定注入 `APP_ENV=prod`（见 docker/frontend/Dockerfile）。
  static const String _rawEnvironment = String.fromEnvironment('APP_ENV', defaultValue: 'dev');

  static AppEnvironment get currentEnvironment => parseEnvironment(_rawEnvironment);

  /// 环境名解析（独立成纯函数以便单测；非法值一律按 dev 处理，不静默当成生产）
  static AppEnvironment parseEnvironment(String raw) {
    switch (raw.trim().toLowerCase()) {
      case 'prod':
      case 'production':
        return AppEnvironment.prod;
      case 'test':
        return AppEnvironment.test;
      default:
        return AppEnvironment.dev;
    }
  }

  /// 编译期注入的 API 根地址；未注入时为空字符串，交由 [resolveBaseUrl] 解析。
  static const String apiBaseUrlOverride = String.fromEnvironment('API_BASE_URL');

  static String get baseUrl => resolveBaseUrl(apiBaseUrlOverride, currentEnvironment);

  /// 解析最终 API 根地址，按优先级：
  ///
  /// 1. **显式注入值**（`--dart-define=API_BASE_URL=...`）—— 绝对地址或 Web 端相对路径；
  /// 2. **生产环境从页面 origin 推导**：生产网关把前端与 `/api/` 挂同一域名，
  ///    于是 `https://lib.example.edu/...` 页面推出 `https://lib.example.edu/api/v1`。
  ///    这样既不需要构建时知道域名（换域名无需重新构建），又得到的是**绝对地址**，
  ///    不依赖"Dio 是否接受相对 baseUrl"这一平台差异；
  /// 3. **各环境默认值**（本地开发用）。
  ///
  /// ## 为什么不把相对路径当默认值
  ///
  /// Dio 在**非 Web 平台会直接拒绝相对 baseUrl**
  /// （`Invalid argument (baseUrl): Must be a valid URL on platforms other than Web.`），
  /// 于是"相对路径"这条默认值在单元测试（VM）里根本无法被验证 —— 一个测不到的默认值
  /// 就是部署事故的温床。改为从页面 origin 推导绝对地址后，逻辑变成纯函数、可完整单测。
  ///
  /// 抽成纯函数的原因：`String.fromEnvironment` 是编译期常量，运行期无法伪造注入值。
  static String resolveBaseUrl(String override, AppEnvironment environment, {Uri? pageBase}) {
    final candidate = override.trim();
    if (candidate.startsWith('http://') || candidate.startsWith('https://')) {
      return _stripTrailingSlash(candidate);
    }
    // 相对路径仅 Web 端可用（Dio 平台限制），仍接受，但不作为默认值
    if (candidate.startsWith('/')) {
      return _stripTrailingSlash(candidate);
    }

    // 生产环境：优先从当前页面 origin 推导（同源部署形态）
    if (environment == AppEnvironment.prod) {
      final origin = _httpOriginOf(pageBase ?? Uri.base);
      if (origin != null) {
        return '$origin/api/v1';
      }
    }

    switch (environment) {
      case AppEnvironment.dev:
        return 'http://localhost:8080/api/v1';
      case AppEnvironment.test:
        return 'http://test.campus.edu.cn/api/v1';
      case AppEnvironment.prod:
        // 兜底：页面 origin 不可用（例如非 Web 端跑 prod 配置）且未注入时的占位值。
        // 生产镜像构建默认注入空值并依赖 origin 推导；跨域部署需显式注入绝对地址。
        return 'https://library.campus.edu.cn/api/v1';
    }
  }

  /// 取出 http/https 页面的 origin（含端口），非 http(s) 页面返回 null
  static String? _httpOriginOf(Uri page) {
    final scheme = page.scheme.toLowerCase();
    if ((scheme != 'http' && scheme != 'https') || page.host.isEmpty) {
      return null;
    }
    final port = page.hasPort ? ':${page.port}' : '';
    return '$scheme://${page.host}$port';
  }

  static String _stripTrailingSlash(String value) =>
      value.endsWith('/') ? value.substring(0, value.length - 1) : value;

  static const String appName = '校园图书借阅系统';
  static const int connectTimeoutMs = 15000;
  static const int receiveTimeoutMs = 15000;

  // ---------------------------------------------------------------------------
  // 登录页"答辩演示快捷登录"面板 (Stage 7-A / 部署可配)
  //
  // 此前该面板写死为 `if (kReleaseMode) return SizedBox.shrink();` —— 于是
  // 任何 release 构建（也就是所有部署形态）都看不到它，演示时只能手敲账号。
  //
  // 现在改为显式开关：默认关闭（生产 release 不应把任何账号口令写进公开的
  // JS 产物），演示部署用构建参数打开：
  //   --dart-define=SHOW_DEMO_ACCOUNTS=true
  //   --dart-define=DEMO_STUDENT_USERNAME=... --dart-define=DEMO_STUDENT_PASSWORD=...
  // 用户名留空表示隐藏该行（例如只展示学生与馆员、不公开管理员口令）。
  // ---------------------------------------------------------------------------

  /// 是否展示演示快捷登录面板（Debug 构建始终展示，Release 需显式打开）
  static const bool showDemoAccounts =
      bool.fromEnvironment('SHOW_DEMO_ACCOUNTS', defaultValue: false);

  static const String demoStudentUsername =
      String.fromEnvironment('DEMO_STUDENT_USERNAME', defaultValue: 'student_demo');
  static const String demoStudentPassword =
      String.fromEnvironment('DEMO_STUDENT_PASSWORD', defaultValue: '123456');

  static const String demoLibrarianUsername =
      String.fromEnvironment('DEMO_LIBRARIAN_USERNAME', defaultValue: 'librarian_demo');
  static const String demoLibrarianPassword =
      String.fromEnvironment('DEMO_LIBRARIAN_PASSWORD', defaultValue: '123456');

  static const String demoAdminUsername =
      String.fromEnvironment('DEMO_ADMIN_USERNAME', defaultValue: 'admin_demo');
  static const String demoAdminPassword =
      String.fromEnvironment('DEMO_ADMIN_PASSWORD', defaultValue: '123456');
}
