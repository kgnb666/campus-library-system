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
# 注意: 此处不得使用 `|| true` 掩盖失败。依赖预取一旦失败必须让构建立即显式中断，
# 否则问题会推迟到下一阶段，以更难定位的报错形式暴露。
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

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

# 拷贝构建产物并直接指定属主 (Stage 10-J)
# 注意: 这里必须用 --chown 而不是先 COPY 再 RUN chown ——
# "COPY + RUN chown" 会把同一个 jar 写进两个层（实测各 92.3MB），
# 镜像凭空多出一份等价于 jar 体积的冗余层。--chown 在拷贝时就设定属主，只产生一层。
COPY --from=builder --chown=appuser:appgroup /build/target/*.jar app.jar

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
