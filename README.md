# 校园图书借阅系统 (Campus Library Borrowing System)

> 基于 Spring Boot 3.3 + Java 17 + PostgreSQL 17 + Redis 8 + Flutter 3 的工业级校园数字化图书借阅流通平台。

---

## 1. 项目定位与架构简介

本项目为全功能数字化校园图书借阅平台，服务于高校在校师生、图书管理员及系统运维人员，旨在解决传统高校图书馆存在的借还排队效率低、副本物理状态脱节、超借与逾期监管滞后、借阅并发冲突等痛点。

* **严格领域建模**：彻底拆分“书目（Book）”与“物理单册（BookCopy）”，物理副本采用纯物理 6 态流转；预约模型面向 Book 独立排队调度。
* **自顶向下确定性并发控制**：借阅事务统一先锁父级 Book 行，再锁 BookCopy，从拓扑上彻底根除并发死锁，保证零超借、零负库存。
* **分包单体整洁架构**：后端采用 Spring Boot 3.3 单体模块化整洁架构，单进程事务强一致；前端采用 Flutter 3 响应式 Material 3 设计。

---

## 2. 技术栈全景

| 层次 | 选型组件 | 运行版本 | 说明 |
| :--- | :--- | :--- | :--- |
| **后端语言** | Java | OpenJDK 17 LTS | 现代企业级开发基石 |
| **后端框架** | Spring Boot | 3.3.4 | 核心 Web、Security、Data JPA、Actuator |
| **关系型数据库** | PostgreSQL | 17.11 | ACID 强事务、内置 `pg_trgm` 检索 |
| **缓存与辅助** | Redis | 8.0 | 会话 RefreshToken 托管、防刷频控 |
| **数据库迁移** | Flyway | 10.x | 版本化 DDL 变更控制与环境对齐 |
| **前端跨端** | Flutter / Dart | Flutter 3.47.2 / Dart 3.13.2 | 跨平台 Material 3 客户端 |
| **状态管理** | Flutter Riverpod | 2.6+ | 编译期安全响应式状态管理 |
| **网络通信** | Dio + RESTful | 5.x | 拦截器链、自动附加 TraceId、静默刷新 |
| **路由系统** | GoRouter | 14.x | 声明式 URL 路由 |
| **容器化编排** | Docker & Compose | Docker 29.7+ | 容器化一键部署持久化 |

---

## 3. 项目目录结构

```
Campus-Library-System/
├── backend/                  # 后端 Spring Boot 3.3 Maven 工程
│   ├── src/main/java/com/library/
│   │   ├── common/           # 常量定义与返回码枚举
│   │   ├── config/           # WebMvc 跨域、Redis 序列化配置
│   │   ├── exception/        # 全局统一异常处理器与业务异常类
│   │   ├── monitoring/       # 全链路 TraceId MDC 过滤器
│   │   ├── response/         # 全局统一 RESTful ApiResponse 结构
│   │   ├── security/         # Spring Security 6 基础安全架构
│   │   └── Application.java  # 启动入口类
│   ├── src/main/resources/
│   │   ├── db/migration/     # Flyway 数据库迁移脚本
│   │   ├── application.yml   # 主配置
│   │   ├── application-dev.yml  # 开发环境配置
│   │   ├── application-test.yml # 测试环境配置
│   │   ├── application-prod.yml # 生产环境配置
│   │   └── logback-spring.xml   # 结构化日志配置
│   └── pom.xml
├── frontend/                 # 前端 Flutter 3 跨端工程
│   ├── lib/
│   │   ├── core/             # 环境配置、Dio 客户端、GoRouter、AppTheme
│   │   ├── features/         # auth, home, books, borrow, profile 特性模块
│   │   ├── shared/           # 缺省页、加载指示器、通用工具
│   │   └── main.dart         # Flutter 入口类
│   └── pubspec.yaml
├── docker/                   # 容器化环境与配置说明
├── docs/                     # Stage 0 / 0.5 规范设计文档与审查报告
├── scripts/                  # 基础设施启动与自动化测试脚本
├── .env                      # 端口与凭证环境变量
├── .gitignore                # 统一版本忽略配置
├── README.md                 # 项目使用说明
└── docker-compose.yml        # PostgreSQL 17 + Redis 8 编排文件
```

---

## 4. 快速启动与验证指南

### 4.1 启动基础设施容器 (PostgreSQL 17 + Redis 8)
```bash
# 进入根目录
docker compose up -d

# 检查容器运行状态与健康检查
docker compose ps
```

### 4.2 运行后端测试与服务
```bash
cd backend
# 执行全套基础设施单测 (验证上下文启动、Actuator 健康检查、Flyway 基线)
mvn test

# 启动后端应用
mvn spring-boot:run
```
* 访问 Actuator 健康检查：`GET http://localhost:8080/actuator/health`（返回 `{"status":"UP"}`）。

### 4.3 运行前端测试与客户端
```bash
cd frontend
# 获取依赖
flutter pub get

# 执行前端组件测试
flutter test

# 启动 Flutter 应用
flutter run
```
