# ==============================================================================
# Frontend Dockerfile - Campus Library Borrowing System
# Multi-stage build: Flutter Web SDK build -> Lightweight Nginx Alpine server
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Flutter Web Compilation
# ------------------------------------------------------------------------------
FROM ghcr.io/cirruslabs/flutter:stable AS builder

WORKDIR /build

# 拷贝依赖描述并预拉取依赖
COPY pubspec.yaml pubspec.lock ./
RUN flutter pub get

# 拷贝源码并编译 Web 生产产物
COPY . ./
RUN flutter build web --release --no-tree-shake-icons

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
