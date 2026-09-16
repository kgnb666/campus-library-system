enum AppEnvironment { dev, test, prod }

/// 全局多环境网络与应用配置
class EnvConfig {
  static const AppEnvironment currentEnvironment = AppEnvironment.dev;

  static String get baseUrl {
    switch (currentEnvironment) {
      case AppEnvironment.dev:
        return 'http://localhost:8080/api/v1';
      case AppEnvironment.test:
        return 'http://test.campus.edu.cn/api/v1';
      case AppEnvironment.prod:
        return 'https://library.campus.edu.cn/api/v1';
    }
  }

  static const String appName = '校园图书借阅系统';
  static const int connectTimeoutMs = 15000;
  static const int receiveTimeoutMs = 15000;
}
