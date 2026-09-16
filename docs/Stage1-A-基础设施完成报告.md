# 《校园图书借阅系统》Stage 1-A：项目基础设施初始化完成报告

**文档版本**：v1.0.0  
**报告日期**：2026-09-16  
**阶段名称**：Stage 1-A 项目基础设施初始化 (Project Infrastructure Initialization)  
**阶段状态**：✅ **已完成 (COMPLETED & PASSED)**  

---

## 一、阶段目标达成概述

根据《Stage 0 需求设计》、《Stage 0.5 Design Revision》及 Stage 1-A 执行规范，本项目已成功建立完整、标准化、可扩展且已验证运行的工程基础设施。

### 严格边界执行证明
在 Stage 1-A 执行过程中，严格遵循边界规范：
- ❌ **未创建** 任何用户业务接口（无登录、注册、个人中心代码）
- ❌ **未创建** 任何图书业务 CRUD、借阅业务、预约业务
- ❌ **未创建** 任何 AI 模块、Excel 导入或统计报表逻辑
- ❌ **未创建** 任何业务领域实体表（User/Book/BorrowRecord/Reservation/Fine 等）
- ✅ **仅创建** 基础设施骨架、通用响应模型、全局异常处理、MDC 分布式追踪过滤、安全框架基线、Flyway 基线验证表、容器编排配置及前后端自动化测试套件。

---

## 二、开发与运行环境检查结果

| 检查项 | 规约要求 | 实际检测版本 / 路径 | 判定 |
| :--- | :--- | :--- | :---: |
| **操作系统** | Windows 10/11 x64 | Windows 11 Enterprise (64-bit) | ✅ 通过 |
| **JDK 版本** | Java 17 LTS | OpenJDK 64-Bit Server VM 17.0.2 (`D:\jdk17\jdk-17.0.2`) | ✅ 通过 |
| **构建工具 (Maven)** | Maven 3.8+ | Apache Maven 3.9.9 (`D:\yp3\.tools\maven\bin\mvn.cmd`) | ✅ 通过 |
| **前端环境 (Flutter)** | Flutter 3.x / Dart 3.x | Flutter 3.47.2 / Dart 3.13.2 (`D:\flutter_sdk\flutter\bin\flutter.bat`) | ✅ 通过 |
| **容器运行时 (Docker)**| Docker 20+ / Compose v2 | Docker Engine 29.1.3 / Docker Compose v2.40.3-desktop.1 | ✅ 通过 |
| **版本管理 (Git)** | Git 2.30+ | Git 2.45.2.windows.1 | ✅ 通过 |

---

## 三、技术选型与依赖确认

### 3.1 后端技术栈 (Spring Boot 3.3.4)
- **核心框架**：Spring Boot `3.3.4` (Spring Framework 6.1.x)
- **持久层技术**：Spring Data JPA (Hibernate 6.5.x)
- **数据库驱动**：PostgreSQL JDBC Driver `42.7.4`
- **数据库迁移**：Flyway `10.10.0` + `flyway-database-postgresql`
- **缓存框架**：Spring Data Redis (`Lettuce` 驱动，配置 Jackson2 JSON 序列化)
- **安全框架**：Spring Security 6.3.x (无状态 Session、CORS、CSRF 禁用、BCrypt 密码哈希 Cost=12)
- **监控与运维**：Spring Boot Actuator (`/actuator/health` 暴露 DB & Redis 就绪检查)
- **日志体系**：Logback-spring + SLF4J + MDC (统一注入 `traceId`，生成标准结构化日志)
- **工具与辅助**：Lombok、Hibernate Validator 8.0.x

### 3.2 前端技术栈 (Flutter 3.47.2)
- **状态管理**：`flutter_riverpod: ^2.5.1` (声明式、编译期安全、易测试)
- **HTTP 通信**：`dio: ^5.7.0` (配置 TraceId 拦截器、全局 BaseUrl、超时与统一响应解析)
- **路由管理**：`go_router: ^14.3.0` (声明式路由、底栏导航 ShellRoute 支持)
- **UI 规范**：Material Design 3 (支持浅色 / 深色双主题，标准设计色彩规范)

### 3.3 基础镜像服务 (Docker Compose)
- **主数据库**：`postgres:17` (PostgreSQL 17.11-1.pgdg120+1, 官方镜像，配置 UTF-8 编码)
- **缓存与状态**：`redis:8` (Redis 8.0-M03-alpine/bookworm, 官方镜像)

---

## 四、项目工程完整目录结构

```text
Campus Library Borrowing System/
├── .env                                  # Docker 端口及环境配置文件
├── .gitignore                             # Git 忽略配置
├── docker-compose.yml                     # 基础设施编排 (Postgres 17 + Redis 8)
├── README.md                              # 项目总体说明与开发快速指南
├── docker/
│   └── README.md                          # 容器服务运维手册
├── scripts/                               # 跨平台一键启动/测试脚本
│   ├── start-infra.bat                    # 启动 Docker 基础设施
│   ├── run-backend-test.bat               # 执行后端单元测试
│   └── run-frontend-test.bat              # 执行前端单元测试
├── docs/                                  # 架构与设计归档目录
│   ├── 01-项目需求分析.md
│   ├── 02-总体设计.md
│   ├── 03-功能模块设计.md
│   ├── 04-业务流程设计.md
│   ├── 05-数据库设计.md
│   ├── 06-API设计.md
│   ├── 07-权限设计.md
│   ├── 08-统计模块设计.md
│   ├── 09-AI模块设计.md
│   ├── 10-测试方案.md
│   ├── Stage0-Design-Review.md
│   ├── Stage0.5-Design-Revision.md
│   └── Stage1-A-基础设施完成报告.md       # [本文件]
├── backend/                               # 后端工程 (Spring Boot 3.3.4)
│   ├── pom.xml                            # Maven 依赖清单
│   └── src/
│       ├── main/
│       │   ├── java/com/campus/library/
│       │   │   ├── Application.java       # 启动主类
│       │   │   ├── common/
│       │   │   │   ├── constants/CommonConstants.java
│       │   │   │   └── enums/ResultCode.java
│       │   │   ├── config/
│       │   │   │   ├── RedisConfig.java   # RedisTemplate 序列化配置
│       │   │   │   └── WebMvcConfig.java  # CORS 跨域配置
│       │   │   ├── exception/
│       │   │   │   ├── BusinessException.java
│       │   │   │   └── GlobalExceptionHandler.java  # 全局统一异常拦截
│       │   │   ├── monitoring/
│       │   │   │   └── TraceIdFilter.java # MDC 链路追踪过滤器
│       │   │   ├── response/
│       │   │   │   └── ApiResponse.java   # 标准化统一响应封装
│       │   │   └── security/
│       │   │       └── SecurityConfig.java# Spring Security 骨架配置
│       │   └── resources/
│       │       ├── application.yml        # 基础配置
│       │       ├── application-dev.yml    # 开发环境配置
│       │       ├── application-test.yml   # 测试环境配置
│       │       ├── application-prod.yml   # 生产环境配置
│       │       ├── logback-spring.xml     # 日志策略与 TraceId 输出格式
│       │       └── db/migration/
│       │           └── V1__init_schema.sql# Flyway 迁移基线表 (无业务表)
│       └── test/java/com/campus/library/
│           ├── ApplicationTests.java      # 上下文加载测试
│           ├── HealthCheckTest.java       # Actuator 健康检查测试
│           └── ApiResponseTest.java       # 统一响应与 TraceId 测试
└── frontend/                              # 前端工程 (Flutter 3.47.2)
    ├── pubspec.yaml                       # Flutter 依赖声明
    ├── lib/
    │   ├── main.dart                      # 入口主类
    │   ├── core/
    │   │   ├── config/env_config.dart     # 多环境切换配置 (dev/test/prod)
    │   │   ├── network/api_client.dart    # Dio 封装与 TraceId 注入拦截器
    │   │   ├── router/app_router.dart     # GoRouter 声明式路由与底栏骨架
    │   │   └── theme/app_theme.dart       # Material 3 主题规范
    │   ├── features/                      # 特性模块占位（无业务实体）
    │   │   ├── auth/                      # 认证模块骨架
    │   │   ├── home/                      # 首页骨架
    │   │   ├── books/                     # 检索骨架
    │   │   ├── borrow/                    # 借还骨架
    │   │   └── profile/                   # 个人中心骨架
    │   └── shared/
    │       ├── utils/app_logger.dart      # 统一日志打印
    │       └── widgets/                   # 公共组件
    │           ├── app_empty_view.dart    # 空状态组件
    │           └── app_loading_view.dart  # 加载中组件
    └── test/
        └── widget_test.dart               # 前端基础渲染与导航冒烟测试
```

---

## 五、后端初始化详细结果

### 5.1 统一响应格式 (`ApiResponse<T>`)
所有 Controller 将统一通过 `ApiResponse<T>` 输出符合 Stage 0.5 规范的结构：
```json
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "traceId": "9b1deb4d3b7d4bad9bdd2b0d7b3dcb6d",
  "timestamp": 1726478800000
}
```

### 5.2 全局异常处理 (`GlobalExceptionHandler`)
已实现 `@RestControllerAdvice`，覆盖所有主流异常场景并转化为结构化 `ApiResponse`：
- `BusinessException`：返回业务错误码及可读信息
- `MethodArgumentNotValidException` / `ConstraintViolationException`：参数校验异常，捕获所有 FieldError 组装为友好提示，HTTP 400
- `NoHandlerFoundException`：路径不存在，HTTP 404
- `HttpRequestMethodNotSupportedException`：方法不支持，HTTP 405
- `BadCredentialsException`：凭据无效，HTTP 401
- `AccessDeniedException`：权限不足，HTTP 403
- `Exception`：未捕获系统异常，记录 ERROR 堆栈，屏蔽敏感内部细节，返回 HTTP 500

### 5.3 分布式链路追踪 (`TraceIdFilter`)
- 继承 `OncePerRequestFilter`，拦截每一个 HTTP 请求。
- 优先提取请求头中的 `X-Trace-Id`，若不存在则生成 32 字符的 UUID-v4（无短横线）。
- 存入 SLF4J `MDC.put("traceId", traceId)`，并在 HTTP 响应头附带 `X-Trace-Id`。
- 请求完成后在 `finally` 块中调用 `MDC.clear()`，防止线程池复用导致上下文泄露。

### 5.4 结构化日志系统 (`logback-spring.xml`)
- 控制台输出及文件滚动日志统一采用带 TraceId 模式：
  `%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{traceId:-SYSTEM}] - %msg%n`
- 在无请求的系统启动或后台任务中默认显示 `[SYSTEM]`，在请求线程中自动打印对应请求的 TraceId。
- 生产模式下配置了按日滚动的日志文件归档（保留 30 天，单文件上限 50MB）。

### 5.5 基础安全配置骨架 (`SecurityConfig`)
- 基于 Spring Security 6+ 纯 Lambda DSL 风格配置。
- Session 策略设置为 `SessionCreationPolicy.STATELESS`。
- 开启跨域拦截配置 `cors()`，禁用 CSRF。
- 公开放行接口：`/actuator/**`、`/api/v1/auth/**`、`/public/**`。
- 其余请求统一受控：`anyRequest().authenticated()`。
- 注入 `BCryptPasswordEncoder(12)`，满足密码安全性要求。

---

## 六、Flutter 前端初始化详细结果

### 6.1 架构分层
采用 Google 推荐的 Feature-first / Core-Shared 模块化分层：
- `core/`：全局基础设施（配置、网络、主题、路由）
- `features/`：按功能特性拆分的领域表现层（当前仅包含导航骨架）
- `shared/`：全局跨特性共享的 UI 组件与工具方法

### 6.2 多环境配置 (`EnvConfig`)
- 支持 `Environment.dev`、`Environment.test`、`Environment.prod`。
- 开发环境配置 BaseUrl 为 `http://localhost:8080/api/v1`（Android 模拟器自动转换为 `http://10.0.2.2:8080/api/v1`）。
- 连接超时、接收超时统一设定为 15,000ms。

### 6.3 网络层封装 (`ApiClient`)
- 基于 Dio 封装单例客户端，注册 `InterceptorsWrapper`。
- 自动为所有发出的请求添加 `X-Trace-Id` Header（前端生成或沿用）。
- 拦截 HTTP 401、403、404、500 等异常，统一映射为前端业务异常。

### 6.4 响应式路由与导航 (`AppRouter`)
- 基于 `GoRouter` 与 `ShellRoute` 搭建底座骨架。
- 包含首页 (`/`)、找书/检索 (`/books`)、我的借还 (`/borrow`)、个人中心 (`/profile`) 4 个核心导航 Tab。

---

## 七、Docker 编排与数据库/缓存初始化结果

### 7.1 端口隔离与解决冲突
由于开发者宿主机环境已有占用：
- 宿主机 `5432` 端口已被原生 Windows PostgreSQL 服务占用
- 宿主机 `6379` 端口已被其他容器 `campustrade-redis` 占用
- 宿主机 `8080` 端口已有 Java 服务运行

**工程化解决方案**：
在 `.env` 中为本项目划定隔离映射端口，完全避免修改系统已有环境：
- PostgreSQL：容器内 `5432` 映射到宿主机 **`15437`**
- Redis：容器内 `6379` 映射到宿主机 **`16381`**
- 后端服务：支持环境变量 `SERVER_PORT` 动态重写（验证测试运行在 `8082`）

### 7.2 容器运行健康状态
通过 `docker compose ps` 检测：
```text
NAME                      IMAGE        COMMAND                  SERVICE    STATUS
campus-library-postgres   postgres:17  "docker-entrypoint.s…"   postgres   Up (healthy)
campus-library-redis      redis:8      "docker-entrypoint.s…"   redis      Up (healthy)
```
- PostgreSQL 17 就绪探针 `pg_isready -U postgres -d campus_library` 返回正常。
- Redis 8 就绪探针 `redis-cli ping` 返回 `PONG`。

### 7.3 Flyway 迁移基线验证
- 创建 `db/migration/V1__init_schema.sql`，建立了系统基础设施验证表 `system_schema_baseline`：
  ```sql
  CREATE TABLE IF NOT EXISTS system_schema_baseline (
      id VARCHAR(64) PRIMARY KEY,
      version_tag VARCHAR(32) NOT NULL,
      description VARCHAR(255) NOT NULL,
      created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
  );
  INSERT INTO system_schema_baseline (id, version_tag, description)
  VALUES ('init-001', 'V1.0.0', 'Stage 1-A infrastructure initialized successfully')
  ON CONFLICT (id) DO NOTHING;
  ```
- 应用启动时 Flyway 自动检测到 PG 17 并完成 V1 迁移。
- 成功生成 `flyway_schema_history` 表，基线版本写入成功。
- **验证**：无任何业务数据表创建。

---

## 八、测试执行与系统运行验证

### 8.1 后端自动化测试 (`mvn test`)
运行结果：
```text
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.campus.library.ApiResponseTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.284 s -- in com.campus.library.ApiResponseTest
[INFO] Running com.campus.library.ApplicationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.887 s -- in com.campus.library.ApplicationTests
[INFO] Running com.campus.library.HealthCheckTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.448 s -- in com.campus.library.HealthCheckTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] BUILD SUCCESS
```
- `ApplicationTests`：验证 Spring IoC 容器上下文与各配置类装配正常。
- `HealthCheckTest`：通过 MockMvc 请求 `/actuator/health`，验证返回 200。
- `ApiResponseTest`：验证成功返回封装、失败返回封装以及 MDC 中的 TraceId 一致性提取。

### 8.2 动态运行时 Actuator 健康检查验证
使用打包的 JAR 包启动应用并动态请求健康检查接口：
- **请求**：`GET http://localhost:8082/actuator/health`
- **响应 HTTP Code**：`200 OK`
- **响应体 Payload**：
  ```json
  {
    "status": "UP",
    "components": {
      "db": {
        "status": "UP",
        "details": {
          "database": "PostgreSQL",
          "validationQuery": "isValid()"
        }
      },
      "diskSpace": {
        "status": "UP",
        "details": {
          "total": 510931505152,
          "free": 328087797760,
          "threshold": 10485760,
          "path": "D:\\wkk\\Campus Library Borrowing System\\backend\\.",
          "exists": true
        }
      },
      "ping": {
        "status": "UP"
      },
      "redis": {
        "status": "UP",
        "details": {
          "version": "8.10.1"
        }
      }
    }
  }
  ```
**结果表明**：Spring Boot 3.3.4 与宿主机上运行的 Docker PostgreSQL 17 及 Redis 8 建立了真实连接，连接池心跳与健康探测全部为 `UP`。

### 8.3 前端自动化测试 (`flutter test`)
运行结果：
```text
00:03 +1: All tests passed!
```
验证了 `MaterialApp.router` 挂载正常，4 个导航栏 Tab 正确渲染。

---

## 九、遇到的工程问题及解决方案

| 序号 | 遇到的问题 | 根本原因 | 解决方案 | 验证结果 |
| :---: | :--- | :--- | :--- | :---: |
| 1 | 宿主机 5432 端口冲突 | 本机安装有原生 Windows PostgreSQL 服务已运行在 5432 | 在 `.env` 中将 Compose 端口映射调整为 `15437:5432`，不干扰系统服务 | 容器正常绑定 15437，连接畅通 |
| 2 | 宿主机 6379 端口冲突 | 本机已有其他 Docker 容器 `campustrade-redis` 占用 6379 | 在 `.env` 中调整端口映射为 `16381:6379` | 容器正常绑定 16381，Redis 连接畅通 |
| 3 | 宿主机 8080 端口占用 | 本机另有 Java 后端服务占用 8080 | 后端支持 `SERVER_PORT` 动态重写，开发/测试支持 `--server.port=8082` | 服务在 8082 顺利启动并验证 |
| 4 | Flyway 10 + PostgreSQL 兼容性 | Spring Boot 3.3 升级 Flyway 10 后，PG 数据库方言需要独立依赖包支持 | 在 Maven `pom.xml` 中引入 `org.flywaydb:flyway-database-postgresql` | Flyway V1 迁移一次性成功 |

---

## 十、风险与后续 Stage 1-B 准备

### 10.1 风险评估
- **环境依赖风险**：后续在多开发者环境中，开发人员只需确认 `.env` 端口无冲突即可启动，提供有 `start-infra.bat`，上手摩擦力为 0。
- **无状态 JWT 依赖**：Stage 1-B 将引入 JWT 工具库与用户实体，已预留 `SecurityConfig` 扩展点，不会发生架构回退。
- **实体设计风险**：Stage 0.5 已完成全面的实体与表结构设计修订（已固化在 `docs/05-数据库设计.md`），Stage 1-B 可直接对照编写迁移脚本与 JPA 实体。

### 10.2 Stage 1-B 准备就绪项
1. 数据库基线表已建立，Flyway 执行链路已验证，后续只需添加 `V2__init_entities.sql`。
2. 基础测试基类及 Spring IoC 测试框架已就绪。
3. 前端 Dio 网络拦截器与 TraceId 链路已就绪。

---

## 十一、Stage 1-A 门禁检查表（Gate Checklist）

| 序号 | 门禁检查项 | 检查依据 / 指标 | 结论 |
| :---: | :--- | :--- | :---: |
| 1 | **后端工程是否可编译通过？** | `mvn clean test-compile` 编译 0 错误 | ✅ **PASS** |
| 2 | **后端单元测试是否通过？** | `mvn test` 4/4 测试通过，0 failure / 0 error | ✅ **PASS** |
| 3 | **Flutter 工程是否可运行并测试通过？** | `flutter test` 1/1 测试全部通过 | ✅ **PASS** |
| 4 | **PostgreSQL 17 是否正常运行？** | 容器状态为 `healthy`，宿主机端口 `15437` 连通 | ✅ **PASS** |
| 5 | **Redis 8 是否正常运行？** | 容器状态为 `healthy`，宿主机端口 `16381` 连通 | ✅ **PASS** |
| 6 | **Flyway 是否成功执行 baseline migration？** | `system_schema_baseline` 表成功创建并写入 V1.0.0 记录 | ✅ **PASS** |
| 7 | **日志框架是否输出标准格式日志且包含 traceId？** | `logback-spring.xml` 控制台与文件包含 `[%X{traceId:-SYSTEM}]` 占位 | ✅ **PASS** |
| 8 | **统一响应格式是否生效？** | `ApiResponse` 测试用例验证通过，字段符合 Stage 0.5 规范 | ✅ **PASS** |
| 9 | **全局异常处理是否生效？** | `GlobalExceptionHandler` 覆盖 400/401/403/404/405/500 全生命周期 | ✅ **PASS** |
| 10 | **健康检查接口是否返回 UP 且包含 db/redis 状态？** | `/actuator/health` 实测返回 `UP`，`db: UP`，`redis: UP` | ✅ **PASS** |
| 11 | **是否存在任何提前编写的用户/业务代码？** | 严格审计代码库，无任何业务 Entity/Controller/Service | ✅ **PASS (绝对无违规)** |
| 12 | **是否满足进入 Stage 1-B 的条件？** | 基础设施、容器编排、测试工具链与文档 100% 齐备 | ✅ **PASS** |

---

## 十二、最终判定结论

> **判定结果**：🎉 **Stage 1-A 基础设施初始化阶段：全面达标，正式通过（PASS）！**  
> **后续动作**：等待评审确认，**严禁擅自进入 Stage 1-B**。
