# ==============================================================================
# Backend Dockerfile - Campus Library Borrowing System
# Multi-stage build: Maven build -> Lightweight Alpine JRE 21 runtime
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build stage
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# 缓存依赖层
COPY pom.xml ./
COPY .mvn/ .mvn/
COPY mvnw ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B || true

# 编译打包应用程序
COPY src/ ./src/
RUN ./mvnw clean package -DskipTests -B

# ------------------------------------------------------------------------------
# Stage 2: Production runtime stage
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runner

LABEL maintainer="Campus Library Dev Team" \
      version="1.0.0" \
      description="Production image for Campus Library Borrowing System Backend"

WORKDIR /app

# 安装 curl 供容器健康检查探针使用
RUN apk add --no-cache curl tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 创建非 root 专用应用运行用户及组 (遵循企业安全规范)
RUN addgroup -S appgroup -g 10001 && \
    adduser -S appuser -u 10001 -G appgroup -h /app -s /sbin/nologin

# 拷贝构建产物并设置属主
COPY --from=builder /build/target/*.jar app.jar
RUN chown -R appuser:appgroup /app

# 切换为非 root 用户
USER appuser

# 暴露后端服务端口
EXPOSE 8080

# 默认 JVM 性能与 GC 调优参数
ENV JAVA_OPTS="-Xms256m -Xmx768m -XX:+UseZGC -XX:+ZGenerational -XX:+ExitOnOutOfMemoryError"

# 健康检查探针
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
