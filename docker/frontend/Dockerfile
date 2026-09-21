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

# Flutter SDK 与 pub 依赖的下载源。默认官方源；中国大陆构建可换官方国内镜像提速：
#   --build-arg FLUTTER_STORAGE_BASE_URL=https://storage.flutter-io.cn
#   --build-arg PUB_HOSTED_URL=https://pub.flutter-io.cn
# 实测（2026-09-22）国内直连 storage.googleapis.com 会掉到 ~125KB/s，1GB 的 SDK
# 要下数小时；换镜像后恢复满速。
ARG FLUTTER_STORAGE_BASE_URL=https://storage.googleapis.com
ARG PUB_HOSTED_URL=https://pub.dev

# apt 源镜像。默认官方 deb.debian.org；中国大陆构建建议换镜像：
#   --build-arg APT_MIRROR=mirrors.tencentyun.com   (腾讯云内网，实测秒开)
#   --build-arg APT_MIRROR=mirrors.cloud.tencent.com (公网)
# 实测（2026-09-22，腾讯云广州）官方源在本步骤卡了十余分钟仍未完成 apt-get update。
ARG APT_MIRROR=

ENV FLUTTER_HOME=/opt/flutter
ENV PATH="${FLUTTER_HOME}/bin:${PATH}"
ENV PUB_CACHE=/root/.pub-cache
ENV FLUTTER_STORAGE_BASE_URL="${FLUTTER_STORAGE_BASE_URL}"
ENV PUB_HOSTED_URL="${PUB_HOSTED_URL}"

RUN if [ -n "${APT_MIRROR}" ]; then \
        for f in /etc/apt/sources.list /etc/apt/sources.list.d/debian.sources; do \
            if [ -f "$f" ]; then \
                sed -i "s|deb.debian.org|${APT_MIRROR}|g; s|security.debian.org|${APT_MIRROR}|g" "$f"; \
            fi; \
        done; \
    fi && \
    apt-get -o Acquire::Retries=3 update && \
    apt-get install -y --no-install-recommends \
        curl git unzip xz-utils ca-certificates && \
    rm -rf /var/lib/apt/lists/* && \
    curl -fsSL --retry 3 \
        "${FLUTTER_STORAGE_BASE_URL}/flutter_infra_release/releases/stable/linux/flutter_linux_${FLUTTER_VERSION}-stable.tar.xz" \
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

# ------------------------------------------------------------------------------
# 构建期 dart-define 参数
#
# 刻意声明在 SDK 安装层之后：改这些值只会重跑"编译 Dart"这一步，
# 不会让上面 ~1GB 的 SDK 下载层失效（缓存语义是按指令链计算的）。
# ------------------------------------------------------------------------------

# 运行环境标识 (Stage 10-K)。
#
# 必须显式注入 prod：EnvConfig 里生产分支的地址推导依赖它，此前 currentEnvironment
# 是写死的 dev 常量，导致 prod 分支永不生效、生产构建实际指向 http://localhost:8080。
ARG APP_ENV=prod

# 生产环境的 API 根地址 (Stage 10-K)。
#
# 默认留空 —— 留空时前端在运行时从**页面 origin** 推导接口地址
# （https://<你的域名>/api/v1），因此不需要在构建时知道最终域名、换域名无需重建。
# 仅当前后端**跨域**部署时才注入绝对地址：
#   --build-arg API_BASE_URL=https://api.example.com/api/v1
ARG API_BASE_URL=

# 登录页"答辩演示快捷登录"面板 (可选)。
# 生产默认关闭：公开的 JS 产物里不该出现任何账号口令。演示部署打开
# SHOW_DEMO_ACCOUNTS 并把 DEMO_* 指向该环境真实存在的账号即可；
# 用户名为空的角色会被整行隐藏（例如只展示学生/馆员，不公开管理员口令）。
ARG SHOW_DEMO_ACCOUNTS=false
ARG DEMO_STUDENT_USERNAME=
ARG DEMO_STUDENT_PASSWORD=
ARG DEMO_LIBRARIAN_USERNAME=
ARG DEMO_LIBRARIAN_PASSWORD=
ARG DEMO_ADMIN_USERNAME=
ARG DEMO_ADMIN_PASSWORD=

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
        --dart-define=API_BASE_URL="${API_BASE_URL}" \
        --dart-define=SHOW_DEMO_ACCOUNTS="${SHOW_DEMO_ACCOUNTS}" \
        --dart-define=DEMO_STUDENT_USERNAME="${DEMO_STUDENT_USERNAME}" \
        --dart-define=DEMO_STUDENT_PASSWORD="${DEMO_STUDENT_PASSWORD}" \
        --dart-define=DEMO_LIBRARIAN_USERNAME="${DEMO_LIBRARIAN_USERNAME}" \
        --dart-define=DEMO_LIBRARIAN_PASSWORD="${DEMO_LIBRARIAN_PASSWORD}" \
        --dart-define=DEMO_ADMIN_USERNAME="${DEMO_ADMIN_USERNAME}" \
        --dart-define=DEMO_ADMIN_PASSWORD="${DEMO_ADMIN_PASSWORD}"

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

# 必须用 127.0.0.1 而不是 localhost：容器内 /etc/hosts 里 localhost 同时映射到
# ::1 与 127.0.0.1，而 nginx 的 `listen 80` 只绑定 IPv4，wget 先试 ::1 会拿到
# Connection refused，导致健康检查恒为 unhealthy（实测 2026-09-21 腾讯云部署）。
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD wget -q --spider http://127.0.0.1/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
