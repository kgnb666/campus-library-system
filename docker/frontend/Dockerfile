# ==============================================================================
# Frontend Dockerfile - Campus Library Borrowing System
# Multi-stage build: Flutter Web SDK build -> Lightweight Nginx Alpine server
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Flutter Web Compilation
# ------------------------------------------------------------------------------
# 不使用 ghcr.io/cirruslabs/flutter:stable 这类浮动工具链 tag。
# 实测（2026-09）该 tag 携带 Dart 3.12.0，而本项目 pubspec.yaml 要求 sdk: ^3.13.2，
# 构建必然在 `flutter pub get` 阶段失败（错误提示 "Because campus_library_frontend
# requires SDK version ^3.13.2, version solving failed"）。
# 该镜像仓库当前最高 tag (3.44.x) 同样不满足要求，因此改为按固定版本安装官方 SDK，
# 使容器内 Flutter 与开发基线（Flutter 3.47.2 / Dart 3.13.x）和 pubspec.lock 完全一致。
FROM debian:bookworm-slim AS builder

ARG FLUTTER_VERSION=3.47.2

# 运行环境标识 (Stage 10-K)。
#
# 必须显式注入 prod：EnvConfig 里生产分支的地址推导依赖它，此前 currentEnvironment
# 是写死的 dev 常量，导致 prod 分支永不生效、生产构建实际指向 http://localhost:8080。
ARG APP_ENV=prod

# 生产环境的 API 根地址 (Stage 10-K)。
#
# 默认留空 —— 留空时前端在运行时从**页面 origin** 推导接口地址
# （https://<你的域名>/api/v1），因此：
#   * 不需要在构建时知道最终域名，换域名无需重新构建镜像；
#   * 得到的是绝对地址，不依赖"Dio 是否接受相对 baseUrl"这一平台差异
#     （Dio 在非 Web 平台会直接拒绝相对 baseUrl）。
# 生产网关 (docker/nginx/nginx.conf) 把前端与 /api/ 挂在同一域名下，天然同源。
#
# 仅当前后端**跨域**部署时才需要注入绝对地址：
#   --build-arg API_BASE_URL=https://api.example.com/api/v1
ARG API_BASE_URL=

ENV FLUTTER_HOME=/opt/flutter
ENV PATH="${FLUTTER_HOME}/bin:${PATH}"
ENV PUB_CACHE=/root/.pub-cache

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        curl git unzip xz-utils ca-certificates && \
    rm -rf /var/lib/apt/lists/* && \
    curl -fsSL --retry 3 \
        "https://storage.googleapis.com/flutter_infra_release/releases/stable/linux/flutter_linux_${FLUTTER_VERSION}-stable.tar.xz" \
        -o /tmp/flutter.tar.xz && \
    tar -xf /tmp/flutter.tar.xz -C /opt && \
    rm -f /tmp/flutter.tar.xz && \
    # 官方 tarball 内含 .git，且属主与当前用户不同；不声明 safe.directory 会让
    # flutter 的版本探测直接报 "detected dubious ownership in repository"
    git config --global --add safe.directory "${FLUTTER_HOME}" && \
    flutter --version

WORKDIR /build

# 构建上下文为仓库根目录 (见 docker-compose.prod.yml 中 frontend.build.context)，
# 因此所有 COPY 源路径均以仓库根为基准，而非以 frontend/ 为基准。
# 拷贝依赖描述并预拉取依赖
COPY frontend/pubspec.yaml frontend/pubspec.lock ./
RUN flutter pub get

# 拷贝前端源码并编译 Web 生产产物。
# 此前漏了这几行 COPY：pub get 之后直接执行 flutter build，容器里根本没有 lib/，
# 构建必然以 'Target file "lib/main.dart" not found.' 失败。镜像与 CI 的
# "Production images" 门禁都因此从未真正构建成功过。
COPY frontend/lib ./lib
COPY frontend/web ./web
COPY frontend/assets ./assets
COPY frontend/analysis_options.yaml ./

# 两个 --dart-define 都必须透传：Flutter Web 的这些值是编译期常量，运行期无法覆盖。
#   APP_ENV      决定走哪个环境分支（不注入就退回 dev，接口会指向 localhost）
#   API_BASE_URL 显式指定接口地址（留空则运行时按页面 origin 推导）
RUN flutter build web --release --no-tree-shake-icons \
        --dart-define=APP_ENV="${APP_ENV}" \
        --dart-define=API_BASE_URL="${API_BASE_URL}"

# ------------------------------------------------------------------------------
# Stage 2: Nginx Web Hosting
# ------------------------------------------------------------------------------
FROM nginx:alpine AS runner

LABEL maintainer="Campus Library Dev Team" \
      version="1.0.0" \
      description="Production image for Campus Library Borrowing System Flutter Web"

# 拷贝 Nginx 配置与构建产物
COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /build/build/web /usr/share/nginx/html

# 暴露 Web 端口
EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD wget -q --spider http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
