# 《校园图书借阅系统》第二轮深度审计：分阶段修复提示词库

> 本文档是 `docs/REPAIR-PROMPTS.md`（阶段一 ~ 阶段五 = Stage 9-A ~ 9-E）的**续篇**，编号从 **阶段六（Stage 10-A）** 开始，不覆盖、不修改上一轮已交付的内容。
>
> 本文档只做两件事：**① 给出问题定位与证据**；**② 给出每个阶段可直接复制粘贴给 AI 编程助手的修复提示词**。

---

## 0. 使用说明与全局约束

### 0.1 本轮审计的事实基础

| 标记 | 含义 |
| :--- | :--- |
| 🔴 **实测复现** | 审计中已通过真实 HTTP 请求 / 数据库查询 / `git` 命令复现，结论确定 |
| 🟡 **代码证据** | 静态代码可确证，未构造运行时复现场景 |
| ⚪ **疑似** | 需在修复前进一步验证，提示词中已包含"先验证再修"要求 |

本轮共发现 **66 项问题**（C 类 11 项、H 类 26 项、M 类 22 项、L 类 7 项），
其中 🔴 实测复现 **15 项**、🟡 代码证据 **48 项**、⚪ 疑似 **3 项**（M8、M9、M22）。

### 0.2 复现环境（所有提示词通用）

```bash
项目根目录：D:\wkk\Campus Library Borrowing System
后端：localhost:8080（JDK21 = D:\yp3\.tools\jdk21，Maven = D:\yp3\.tools\maven）
数据库：docker exec campus-library-postgres psql -U library -d library_system
Redis：localhost:16381
演示账号：student_demo / librarian_demo / admin_demo，密码均为 123456
```

取 Token 的标准姿势（几乎每个阶段的验收都要用）：

```bash
BASE=http://localhost:8080/api/v1
TOKEN=$(curl -s -X POST $BASE/auth/login -H "Content-Type: application/json" \
  -d '{"username":"student_demo","password":"123456"}' \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
curl -s -H "Authorization: Bearer $TOKEN" "$BASE/books?page=0&size=3"
```

### 0.3 给所有修复提示词追加的公共约束（建议每次粘贴时带上）

```text
【公共约束】
1. 只修复本条提示词列出的问题，不要顺手重构无关代码，不要改变对外接口的既有语义。
2. 修复前后端契约类问题时，必须先用 curl 打真实接口确认响应结构，禁止只看代码推断。
3. 每个任务完成后必须执行验证：
   - 后端：cd backend && mvn -q test（基线为 165 个 @Test 全绿，不得减少用例数）
   - 前端：cd frontend && flutter analyze && flutter test（基线为 42 个用例）
   若某条基线在当前环境无法跑通（例如 Linux 上 pom.xml 的 C:\Temp 问题），
   先说明原因再继续，不要静默跳过测试。
4. 数据库结构变更一律新增 Flyway 迁移脚本（V11 起），禁止修改 V1~V10 已发布脚本。
5. 报告中必须给出：改了哪些文件、每个文件改了什么、用什么命令验证的、验证输出是什么。
6. 遇到与提示词描述不一致的实际情况，先停下来报告，不要按猜测强行修改。
```

### 0.4 阶段依赖关系（必须按顺序推进）

```
阶段六（密钥止血）──┐
                    ├──> 阶段十三（权限与安全加固）
阶段七（交付链路）──┘
                          ┌──> 阶段十一（事务与懒加载）──> 阶段十二（并发不变式）
阶段八（会话链路）────────┤
                          └──> 阶段九（前后端契约）──> 阶段十（数据与异常语义）
                                                          │
阶段十四（性能与可观测性）<────────────────────────────────┘
阶段十五（文档与答辩一致性）依赖以上全部完成，最后做
```

**必须先做阶段六**：JWT 默认密钥已随仓库公开，在做任何其他修复前项目事实上处于"任何人可伪造管理员身份"的状态。

---

## 1. 问题总览（按严重度 → 阶段映射）

| 编号 | 问题 | 证据位置 | 严重度 | 阶段 |
| :--- | :--- | :--- | :--- | :--- |
| C1 | `.env` 被 git 跟踪，JWT 密钥 + 数据库口令明文入库且进入历史 | `.env:9,19`、`git ls-files` | 🔴 严重 | 六 |
| C2 | JWT 默认密钥硬编码 4 处，生产 compose 静默回退该公开密钥 | `application.yml:33`、`JwtProperties.java:20`、`docker-compose.prod.yml:70`、`.env.example:33` | 🔴 严重 | 六 |
| C3 | V9 种子在生产库写入 `admin_demo/123456` 超级管理员 | `V9__seed_demo_data.sql:14-21` | 🟡 严重 | 六 |
| C4 | 后端镜像构建必失败：Dockerfile 引用不存在的 mvnw/.mvn | `docker/backend/Dockerfile:14-17,21` | 🔴 严重 | 七 |
| C5 | 前端镜像构建必失败：COPY 路径在构建上下文外 | `docker/frontend/Dockerfile:31` + `docker-compose.prod.yml:95-97` | 🔴 严重 | 七 |
| C6 | Excel 导入被 Spring 默认 1MB 上限击穿（Nginx 已放开 50M） | 无 `spring.servlet.multipart` 配置 | 🔴 严重 | 七 |
| C7 | AI 推荐响应字段全面错位，解析必然抛 TypeError | `ai_model.dart:39` vs `RecommendedBookResponse.java:17-30` | 🔴 严重 | 九 |
| C8 | 阅读画像响应字段错位，四项指标恒为 0/空 | `statistics_model.dart:26-42` vs `MyReadingStatisticsResponse` | 🔴 严重 | 九 |
| C9 | 401 刷新成功判定用 `code == 200`，后端返回 `"SUCCESS"` → 每次刷新都被判失败并清空 token | `api_client.dart:90-92` | 🔴 严重 | 八 |
| C10 | 冷启动路由守卫漏 `initial` 态 → 首页裸发请求 → 401 刷屏 | `app_router.dart:25-48` | 🔴 严重 | 八 |
| C11 | 学生首页"热门借阅榜"调用馆员专属接口，恒 403 | `home_screen.dart:60` + `StatisticsController.java:44` | 🔴 严重 | 十三 |
| H1 | 通知列表 500：库中存在枚举外值 `BORROW_SUCCESS` / `SYSTEM` | `V9__seed_demo_data.sql:509,530`、`V8` 无 CHECK | 🔴 严重 | 十 |
| H2 | AI 导读首次生成必 500：事务外访问懒加载 `Book.category` | `AiInsightServiceImpl.java:39-71`、`RuleBasedMockAiProvider.java:19` | 🔴 严重 | 十一 |
| H3 | 临期/逾期催还定时任务事务自调用失效 + 懒加载异常被吞 → 通知永不发出 | `BorrowDueCheckScheduler.java:39,46-47,68,104` | 🟡 高 | 十一 |
| H4 | 馆藏副本数 read-modify-write 无锁无版本 → 丢失更新 | `BookCopyServiceImpl.java:417-421,445-458,478-483` | 🟡 高 | 十一 |
| H5 | 预约晋升无库存守卫：33 条 READY 记录对应 0 在架库存 | `ReservationServiceImpl.java:396-433` | 🔴 高 | 十二 |
| H6 | 异常兜底把 400/409/413/415 全变 500 | `GlobalExceptionHandler.java:98-106` | 🟡 高 | 十 |
| H7 | 分页 `size` 无上限，可 OOM | `NotificationController.java:40` 等 3 处 | 🟡 高 | 十 |
| H8 | 刷新失败不清 authState → 用户卡死满屏 401 且不跳登录页 | `api_client.dart:103,106,111` | 🟡 高 | 八 |
| H9 | `_refreshTokenCompleter` 顶层变量 + 无重试上限 → 跨容器串扰与潜在无限刷新 | `api_client.dart:8,56,125` | 🟡 高 | 八 |
| H10 | 登出不清理业务 Provider → 换账号后串号显示上一用户数据 | `auth_provider.dart:93-101` | 🟡 高 | 八 |
| H11 | 前端 4 处接口路径/参数与后端不匹配 → 404 | `ai_repository.dart:44,51`、`statistics_repository.dart:58,37-39` | 🔴 高 | 九 |
| H12 | 前端静默降级与假数据上传掩盖真实失败 | `book_provider.dart:197`、`excel_import_dialog.dart:29` | 🟡 高 | 九 |
| H13 | 原始 `DioException` 英文文本直接上屏并泄露 baseUrl | 13 处，如 `home_screen.dart:302-305` | 🟡 高 | 九 |
| H14 | Access Token 不可吊销、Refresh Token 不轮换可重放 7 天 | `JwtAuthenticationFilter.java:38-39`、`AuthServiceImpl.java:149-156` | 🟡 高 | 八 |
| H15 | 登录无频控、注册全开放、密码策略仅 6 位 | `AuthServiceImpl.java:86-105`、`RegisterRequest.java:31-33` | 🟡 高 | 十三 |
| H16 | CORS 通配来源 + 允许携带凭证 | `WebMvcConfig.java:14-22` | 🟡 高 | 十三 |
| H17 | Actuator metrics 任意登录用户可读 + 生产 Swagger 匿名开放 | `application.yml:19-28`、`SecurityConfig.java:59-60` | 🟡 高 | 十三 |
| H18 | `borrow:renew` 未授予馆员/管理员，代客续借分支成死代码 | `BorrowRecordController.java:62` vs `V5:96-110` | 🟡 中 | 十三 |
| H19 | 3 个分类查询端点无 `@PreAuthorize` | `CategoryController.java:32,39,46` | 🟡 中 | 十三 |
| H20 | `1001L` fail-open 兜底用户 ID | `NotificationController.java:39` 等 7 处 | 🟡 中 | 十 |
| H21 | 备份方案不可运行：脚本挂进 postgres 容器却调用宿主机 `docker exec`，且无调度、无加密、无校验 | `docker-compose.prod.yml:21-22`、`backup.sh:25` | 🟡 高 | 七 |
| H22 | `stop-all` 按窗口标题杀进程，实际杀不掉，却无条件报成功 | `stop-all.ps1:19-21` | 🟡 高 | 七 |
| H23 | 启动脚本硬编码 `D:\yp3\...` 三套绝对路径，不可移植；测试脚本还指向 JDK 17 | `scripts/start-backend.ps1:16-17,26`、`run-backend-test.bat:5` | 🟡 高 | 七 |
| H24 | README 徽章与正文测试数量失真（161→实际 165；38→实际 42） | `README.md:14,15,186,190,199-200` | 🟡 高 | 十五 |
| H25 | README 的 6 张"系统截图"全部是 `via.placeholder.com` 占位图（该域名实测无法连接，HTTP 000，渲染为破图） | `README.md:48,49,56,57,64,65` | 🟡 高 | 十五 |
| H26 | README 引用的 LICENSE 文件不存在（Apache 2.0 声明不成立） | `README.md:7,255` | 🔴 高 | 六 |
| M1 | N+1 查询：借阅/预约列表逐行懒加载 | `BorrowRecordResponse.java:110-125`、`ReservationResponse` | 🟡 中 | 十四 |
| M2 | 公告广播 `userRepository.findAll()` 无界全表读（实测 4717 用户） | `NotificationServiceImpl.java:129-144` | 🟡 中 | 十四 |
| M3 | 模糊检索用 `lower(col)` 而索引是原始列 trgm，走顺序扫描 | `BookServiceImpl.java:162-164` vs `V3:51-53` | 🟡 中 | 十四 |
| M4 | 未配置 `hibernate.jdbc.batch_size`，批量写退化为逐条 | 全部 yml | 🟡 中 | 十四 |
| M5 | 定时任务无分布式锁、调度线程池默认单线程、无界扫描 | `scheduler/**` | 🟡 中 | 十二 |
| M6 | `bookLocks` 锁移除竞态 + 锁内执行 5s 网络调用 | `AiInsightServiceImpl.java:37,51-52,68,88` | 🟡 中 | 十一 |
| M7 | 定时任务 cron 与 DeepSeek 配置项在 yml 中不存在，运维不可调 | `application*.yml` | 🟡 中 | 十一 |
| M8 | 队列位次与「同书多 READY」不变式在代码与 DB 层面均无保护 | `V6:51-52`、`ReservationServiceImpl.java:267,413` | ⚪ 疑似 | 十二 |
| M9 | 锁拓扑不统一（还书路径起点为 BorrowRecord） | `BorrowCirculationServiceImpl.java:197,238,241` | ⚪ 疑似 | 十二 |
| M10 | V10 唯一约束维度（copy）与代码校验维度（user,book）不一致 | `V10:6-8` vs `BorrowCirculationServiceImpl.java:109-113` | 🟡 中 | 十二 |
| M11 | 刷新 Token 明文写入日志且默认 DEBUG 级别 | `RefreshTokenService.java:88`、`logback-spring.xml:22` | 🟡 中 | 十三 |
| M12 | `EnvConfig` 是编译期常量，test/prod 分支为死配置 | `env_config.dart:5,10-14` | 🟡 中 | 十四 |
| M13 | 测试夹具伪造后端契约，掩盖字段错位；无任何契约测试 | `ai_recommendation_test.dart:13-31` | 🟡 中 | 九 |
| M14 | 分页追加无去重、无请求序号守卫，快速操作会旧响应覆盖新结果 | `book_provider.dart:208`、`borrow_provider.dart:56,149` | 🟡 中 | 九 |
| M15 | 登出后搜索历史等持久化状态跨账号共享 | `searchHistoryProvider`（SharedPreferences） | 🟡 中 | 八 |
| M16 | Dockerfile 中 `\| true` 掩盖依赖预取失败；无 `.dockerignore` | `docker/backend/Dockerfile:17` | 🟡 中 | 七 |
| M17 | 镜像 tag 全部浮动（`postgres:17`、`nginx:alpine`、`flutter:stable`），构建不可复现 | 多个 Dockerfile / compose | 🟡 中 | 七 |
| M18 | 无资源限制、Redis 口令进容器 cmdline | `docker-compose.prod.yml:38,42` | 🟡 中 | 七 |
| M19 | 无 CI 配置，构建错误与测试漂移无人发现 | `.github/` 不存在 | 🟡 中 | 十五 |
| M20 | `.mimosa/` 未被忽略且落入源码树；12 个启动脚本未纳入版本控制 | `.gitignore:43-51`、`git status` | 🟡 中 | 七 |
| M21 | README 技术声明与代码不符：Flyway 写 V1~V9（实际 V1~V10）、JDK 21（pom 为 17）、镜像体积无实测依据、目录树漏 `scripts/` | `README.md:100,202,221,9,139,208-240` | 🟡 中 | 十五 |
| M22 | 馆员大盘/概览等 4 个前端模型字段错位（当前无调用点，属潜伏缺陷） | `statistics_model.dart:84-95` | ⚪ 疑似 | 九 |
| L1 | `/api/v1/public/**` 为空放行规则，未来新增接口默认免鉴权 | `SecurityConfig.java:58` | 🟡 低 | 十三 |
| L2 | `TraceIdFilter` 未校验外部传入的 traceId，可日志注入 | `TraceIdFilter.java:27-38` | 🟡 低 | 十三 |
| L3 | 注册接口回显用户名/邮箱占用，存在用户枚举 | `AuthServiceImpl.java:50-57` | 🟡 低 | 十三 |
| L4 | `TextEditingController` 在编目页未 dispose（6 处） | `catalog_manage_screen.dart:263-268,613-616` | 🟡 低 | 十四 |
| L5 | 通知筛选 Tab 缺 `RESERVATION_EXPIRED`；`BORROW_RECORD` 类型点击无跳转 | `notification_center_screen.dart:18-25,255-261` | 🟡 低 | 十四 |
| L6 | `AppLogger` 工具类全项目零调用；release 下仍 debugPrint | `app_logger.dart:11-20` | 🟡 低 | 十四 |
| L7 | 未使用依赖、死代码 Provider/方法约 10 处 | `cupertino_icons`、`libraryOverviewProvider` 等 | 🟡 低 | 十四 |

---

# 阶段六（Stage 10-A）：密钥泄露止血与凭证轮换 🔴 最高优先

## 目标

消除"任何人读完仓库即可伪造管理员身份"和"生产库自带已知口令超管"两个致命风险，并把密钥管理切换到强制显式注入模式。

**本阶段必须最先执行，且其中的密钥轮换必须在密钥从代码中移除之前完成，否则会造成新密钥同样泄露。**

## 问题清单

| 编号 | 问题 | 证据 |
| :--- | :--- | :--- |
| C1 | `.env` 已被 git 跟踪，含 `POSTGRES_PASSWORD=library_password`、`JWT_SECRET=<base64>` | `git ls-files` 输出包含 `.env`；历史提交 `783967e`、`6aba87f` 已引入 |
| C2 | 同一个 JWT 密钥硬编码在 4 个位置，且都用 `:-` 默认值语法静默回退 | `application.yml:33`、`JwtProperties.java:20`、`docker-compose.prod.yml:70`、`.env.example:33` |
| C3 | 生产库由 Flyway 种子写入 `admin_demo` / `123456` | `V9__seed_demo_data.sql:14-21`，prod 与 dev 共用同一 migration locations（`application-prod.yml:31-34`） |
| H26 | README 声明 Apache 2.0 但 LICENSE 文件不存在 | `README.md:7,255`，`ls LICENSE` 无此文件 |

## 验收标准

1. `git ls-files | grep -E "^\.env$"` 无输出，且 `git check-ignore -v .env` 命中 `.gitignore` 规则。
2. 全仓库检索旧密钥 **零命中**（源码、配置、脚本、文档全部清理）。
   为避免把泄露值本身又写进本文档，请用"从 Git 历史动态提取模式"的方式检索：

   ```bash
   OLD=$(git show HEAD:.env | sed -n 's/^JWT_SECRET=//p')
   grep -rn "$OLD" . --exclude-dir=.git --exclude-dir=.mimosa
   ```
3. 故意不设置 `JWT_SECRET` 启动后端，**必须启动失败并给出中文明确提示**，而不是回退到默认密钥。
4. 使用旧密钥签发的 token 请求 `/api/v1/auth/me` 返回 401（证明密钥已真正轮换）。
5. 生产 profile 下启动，`users` 表中不存在 `admin_demo`（或该账号为 DISABLED 且密码需首次强制修改）。
6. `ls LICENSE` 存在且内容为 Apache-2.0 全文（或 README 中相关声明已被删除）。

## 修复提示词

```text
【任务】Stage 10-A：消除密钥泄露并完成凭证轮换

【背景】
本项目 .env 已被 git 跟踪，其中 JWT_SECRET 的 Base64 解码结果为可读明文
"secure-campus-library-borrowing-system-secret-key-2026-09-16-for-jpa-spring-security"，
且同一字符串硬编码在 application.yml:33、JwtProperties.java:20、
docker-compose.prod.yml:70、.env.example:33 四处，均使用 `:-` 默认值语法。
任何克隆本仓库的人都可以用它离线签发任意用户（含 ADMIN）的 JWT。
同时 V9__seed_demo_data.sql:14-21 会在生产库写入 admin_demo/123456 超级管理员。

【任务 1：生成并落地新密钥（先做这一步）】
1. 用 openssl rand -base64 48 生成一个新的强随机 JWT_SECRET。
2. 同步轮换 PostgreSQL 密码：更新 .env 中 POSTGRES_PASSWORD，并给出
   在已运行容器上执行 ALTER USER library WITH PASSWORD '...' 的具体命令。
3. 明确告知用户：需要手动执行哪些命令完成真实环境的密码/密钥更新
   （这一步不能由你代做，因为它涉及生产凭据）。

【任务 2：从代码中移除一切默认密钥】
1. application.yml:33 改为 secret: ${JWT_SECRET}（无默认值）。
2. JwtProperties.java:20 删除硬编码默认值，改为没有默认值的字段。
3. docker-compose.prod.yml:70 改为 JWT_SECRET: ${JWT_SECRET:?JWT_SECRET is required}。
4. .env.example:33 改为占位符，如 JWT_SECRET=CHANGE_ME_RUN_openssl_rand_base64_48。
5. 在 JwtProperties（或启动校验 Bean）中增加启动期校验：
   - 密钥为空 → 启动失败并输出中文提示"未配置 JWT_SECRET，拒绝启动"
   - 密钥长度小于 32 字节 → 启动失败
   - 密钥等于本文档中的旧值 → 启动失败并提示必须轮换
   用中文错误信息，便于答辩现场排障。

【任务 3：清理版本控制中的泄露】
1. .gitignore 增加 `.env`（当前只有 .env.local / .env.*.local / *.pem / *.key）。
2. 执行 git rm --cached .env（保留本地文件），确保 .env 不再被跟踪。
3. 用 `git log --all --oneline -- .env` 与 `git log --all -S "JWT_SECRET" --oneline`
   输出证明历史中确实存在该密钥，并把结果写进报告。
4. 【重要】不要自行执行 git filter-repo / BFG 重写历史（会破坏他人克隆）。
   在报告中明确写出建议的历史清洗命令，交由用户决定是否执行。

【任务 4：隔离演示数据】
1. 新增 V11 迁移（不要改 V9），在生产环境禁用或删除演示账号。
   推荐方案：新增 classpath:db/seed 目录存放演示数据，application-dev.yml 的
   flyway.locations 追加该目录，application-prod.yml 不包含它。
   过渡方案（若不想动 seeds）：V11 中把 admin_demo/librarian_demo/student_demo
   在 prod 下置为 DISABLED，并由环境变量开关控制（不要写死删除）。
2. 说明该变更对已有 dev 环境（demo 账号需继续可用）的影响与验证方式。

【任务 5：补齐 LICENSE】
1. 在仓库根目录创建 LICENSE，内容为 Apache License 2.0 完整全文，
   版权行使用 "Copyright 2026 Campus Library Borrowing System"。
2. 若用户不打算开源，则改为删除 README.md:7 的徽章与 README.md:255 的声明段落，
   二选一，不要留下悬空链接。

【验收】
- git ls-files | grep -E "^\.env$" 无输出
- 旧密钥全仓库零命中（检索模式从 Git 历史动态提取，避免把泄露值写进本文件）：
  OLD=$(git show HEAD:.env | sed -n 's/^JWT_SECRET=//p'); grep -rn "$OLD" . --exclude-dir=.git --exclude-dir=.mimosa
- 不设 JWT_SECRET 启动后端 → 启动失败且提示可读
- 设置新密钥启动 → /actuator/health 为 UP，用 student_demo 登录成功
- 用旧密钥手写一个 JWT 请求 /api/v1/auth/me → 401

【产出】
修改文件清单 + 需要用户手工执行的命令清单 + 历史清洗建议命令 + 验证输出。
```

---

# 阶段七（Stage 10-B）：恢复生产交付链路

## 目标

让 README 首推的 `docker compose -f docker-compose.prod.yml up -d --build` 真正可用，并让本地一键启动脚本在任何机器上可移植。

## 问题清单

| 编号 | 问题 | 证据 |
| :--- | :--- | :--- |
| C4 | 后端镜像必失败：`COPY .mvn/ .mvn/` 与 `COPY mvnw ./` 引用的文件不存在 | `docker/backend/Dockerfile:14-17,21`；`ls backend/mvnw` → No such file |
| C5 | 前端镜像必失败：构建上下文为 `./frontend`，Dockerfile 却 `COPY docker/frontend/nginx.conf` | `docker-compose.prod.yml:95-97` + `docker/frontend/Dockerfile:31` |
| C6 | 未配置 multipart 上限，Excel 导入被 1MB 默认值击穿（Nginx 已放开 50M） | 全部 yml 无 `spring.servlet.multipart`；`docker/nginx/nginx.conf:36` |
| H21 | 备份脚本挂载进 postgres 容器却执行宿主机 `docker exec`，且无调度/加密/校验 | `docker-compose.prod.yml:21-22`、`docker/scripts/backup.sh:25` |
| H22 | `stop-all` 按窗口标题匹配进程，PowerShell 宿主标题 ≠ java/dart 进程标题 | `stop-all.ps1:19-21`、`scripts/start-backend.ps1:3` |
| H23 | 启动脚本硬编码 `D:\yp3\.tools\jdk21` 等路径；`run-backend-test.bat:5` 还指向 JDK 17 | `scripts/start-backend.ps1:16-17,26`、`scripts/start-frontend.ps1:17,25,29`、`run-backend-test.bat:5` |
| M16 | `./mvnw dependency:go-offline \|\| true` 掩盖依赖预取失败 | `docker/backend/Dockerfile:17` |
| M17 | 镜像 tag 全浮动，构建不可复现 | `postgres:17`、`redis:8`、`nginx:alpine`、`flutter:stable` |
| M18 | prod 无资源限制；Redis 口令出现在容器命令行 | `docker-compose.prod.yml:38,42` |
| M20 | `.mimosa/` 未忽略且落入源码树；12 个启动脚本全部 untracked | `.gitignore:43-51`、`git status --short` |
| — | surefire 硬编码 `C:\Temp`，Linux/CI 上测试不可移植 | `backend/pom.xml:163-169` |

## 验收标准

1. `docker compose -f docker-compose.prod.yml build` 两个镜像均构建成功（或明确给出失败原因与修复）。
2. `docker compose -f docker-compose.prod.yml up -d` 后 `http://localhost/actuator/health` 返回 `UP`，前端首页可打开。
3. 通过 Nginx 上传一个 **>1MB 的真实 xlsx** 到 `/api/v1/books/import/excel`，返回非 413/500。
4. `scripts/stop-all.ps1` 执行后 `netstat` 确认 8080 无监听，且未找到进程时输出警告而非"成功"。
5. 在**未安装过本项目**的路径下（例如把仓库复制到 `C:\tmp\cl`）执行 `start-all.bat`，脚本能给出可读的中文前置依赖提示，而不是 PowerShell 原始报错。
6. `git status --short` 中 12 个启动脚本不再显示为 untracked。

## 修复提示词

```text
【任务】Stage 10-B：修复生产交付链路与启动脚本可移植性

【背景】
README 首推的一键部署命令当前必然失败，已确认两处硬错误：
(a) docker/backend/Dockerfile:14-17 与 :21 引用 mvnw / .mvn/，但 backend/ 下
    这两个文件都不存在（未生成 Maven Wrapper），COPY 阶段直接报 not found。
(b) docker-compose.prod.yml:95-97 把 frontend 的构建上下文设为 ./frontend，
    而 docker/frontend/Dockerfile:31 执行 COPY docker/frontend/nginx.conf ...，
    该路径按上下文解析为 frontend/docker/frontend/nginx.conf，不存在。

【任务 1：修复后端镜像构建】
方案 A（推荐）：cd backend && mvn -N wrapper:wrapper 生成 mvnw、mvnw.cmd、.mvn/wrapper/，
  并把这三个产物纳入版本控制（同时确认 .gitignore 没有忽略 mvnw）。
方案 B：把 Dockerfile 改为 FROM maven:3.9-eclipse-temurin-21 AS builder 并直接调用 mvn。
选择其中一个并说明理由。同时删除 Dockerfile:17 的 `|| true`，让依赖解析失败显式暴露。

【任务 2：修复前端镜像构建】
把 docker-compose.prod.yml 中 frontend 的构建上下文改为仓库根（context: .），
dockerfile 改为 docker/frontend/Dockerfile，并确保 Dockerfile 内所有 COPY 路径
都相对仓库根解析；或把 nginx.conf 复制进 frontend/ 目录内并同步修改 COPY。
改完必须实测构建成功，不能只改配置就宣布完成。

【任务 3：放开 Excel 上传上限（功能缺陷，非仅配置）】
后端未配置 multipart 上限，走 Spring Boot 默认 max-file-size=1MB，
导致 README 宣传的"SAX 大规模 Excel 编目导入"必然失败（Nginx 侧已放开 50M）。
在 application.yml 增加：
  spring.servlet.multipart.max-file-size: 50MB
  spring.servlet.multipart.max-request-size: 60MB
并补 MaxUploadSizeExceededException 的异常处理（返回 413 与中文提示），
该异常处理的具体写法在 Stage 10-E 阶段会统一规范，此处先保证上限放开。
用真实 >1MB 的 xlsx 实测验证。

【任务 4：修复测试可移植性】
backend/pom.xml:163-169 的 surefire argLine 硬编码了 Windows 路径
-Djdk.net.unixdomain.tmpdir=C:\Temp -Djava.io.tmpdir=C:\Temp，
在 Linux/CI 上会指向无效路径。改为按平台注入或直接删除该配置，
并说明原问题（Lettuce AF_UNIX 长路径）在删除后如何规避。

【任务 5：修复备份可用性】
docker/scripts/backup.sh:25 在容器内调用宿主机 `docker exec`，
但 docker-compose.prod.yml:21-22 把脚本挂载进了 postgres:17-alpine 容器，
该镜像内没有 docker CLI，必然 docker: not found。
请二选一并实现：
  A. 新增独立 backup 服务（postgres:17-alpine + while true 循环 sleep 86400 调用脚本），
     脚本改为容器内直接 pg_dump（不经过 docker exec）。
  B. 保留宿主机执行方式，删除 compose 中的挂载，改用宿主机 cron/systemd timer，
     并在文档中给出完整 crontab 行。
无论哪种方案都必须补：备份文件 gzip -t 完整性校验、备份目录挂载到宿主机具名卷
（当前 /var/backups/campus_library 未被任何 volume 挂载，容器重建即丢失）、
以及可选的 gpg/age 加密。最后实测"备份 → 删库 → 用 restore.sh 恢复 → 校验行数"闭环。

【任务 6：修复停止脚本】
stop-all.ps1:19-21 用 Get-Process 的 MainWindowTitle 匹配 "*Campus Library*"，
但窗口标题设在 PowerShell 宿主进程上（scripts/start-backend.ps1:3），
java/dart 子进程没有该标题，实际杀不掉任何进程却打印"[成功] 所有服务已安全退出"。
改为按端口定位：
  Get-NetTCPConnection -LocalPort 8080 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
并同时处理 Flutter/dart 进程（可按 CommandLine 匹配 'flutter' 或 'frontend'）。
未找到任何进程时必须输出警告而非成功。改完实测：启动 → 停止 → netstat 确认端口释放。

【任务 7：修复脚本可移植性】
scripts/start-backend.ps1、scripts/start-frontend.ps1、scripts/run-backend-test.bat、
scripts/run-frontend-test.bat 硬编码了 D:\yp3\.tools\jdk21、D:\yp3\.tools\maven、
D:\flutter_sdk\flutter 三套绝对路径，且 run-backend-test.bat:5 指向 JDK 17
（与 README/Dockerfile 宣称的 JDK 21 自相矛盾，请一并统一口径）。
改为：优先读取 JAVA_HOME / MAVEN_HOME / FLUTTER_ROOT 环境变量，
再用 Get-Command java / mvn / flutter 探测，
全部失败时输出可读的中文提示（例如"未检测到 Flutter SDK，请设置 FLUTTER_ROOT 环境变量"），
保留本机路径作为最后的兜底但不要作为唯一来源。

【任务 8：仓库卫生】
1. .gitignore 增加 .mimosa/ 与 **/.mimosa/，并删除源码树内的
   backend/src/main/java/com/library/.mimosa/ 残留目录。
2. 把 12 个启动脚本（scripts/*.ps1、scripts/*.bat、根目录 start-all.* / stop-all.*）
   纳入版本控制——README 的快速启动指南引用了它们，但克隆者根本拿不到。
3. 补 backend/.dockerignore 与 frontend/.dockerignore，排除 target/、build/、
   .dart_tool/、.git/、*.log、.env*，避免构建上下文污染与敏感文件入镜像。
4. 修复 docker-compose.yml 与 prod compose 中 Redis 口令进 cmdline 的问题
   （改用 environment + sh -c 'exec redis-server --requirepass "$$REDIS_PASSWORD"'），
   并去掉 `:-library_redis_pwd` 这类弱口令回退。为各服务补 deploy.resources.limits。

【验收】按"验收标准"逐条执行并贴出真实输出。特别地：
docker compose -f docker-compose.prod.yml build 必须两个镜像都成功。
```

---

# 阶段八（Stage 10-C）：让登录态真正可用（会话链路修复）

## 目标

修复"token 一过期整个 App 直接不可用""冷启动 401 刷屏""刷新失败卡死""换账号串号"这一组会话链路缺陷，这是用户截图里最直观的问题来源。

## 问题清单

| 编号 | 问题 | 证据 |
| :--- | :--- | :--- |
| C9 | 刷新成功判定 `refreshResponse.data['code'] == 200`，而后端 `ApiResponse.code` 是字符串 `"SUCCESS"` → 恒 false → 每次刷新都 `tokenStorage.clear()` 且不重试 | `api_client.dart:90-92`；后端实际响应 `{"code":"SUCCESS",...}`（已实测） |
| C10 | 路由守卫只处理 `unauthenticated`，`initial`/`loading`/`error` 三态直接放行 → 首页在状态未就绪时发请求 | `app_router.dart:25-48` |
| H8 | 刷新失败清 token 但不通知 `authStateProvider` → 守卫不跳登录页，用户卡在满屏 401 | `api_client.dart:103,106,111` |
| H9 | `_refreshTokenCompleter` 是库级顶层变量（跨 ProviderContainer 共享）；重试路径无次数上限，理论可无限刷新 | `api_client.dart:8,56,125` |
| H10 | 登出只清 TokenStorage + 置 unauthenticated，不 invalidate 任何业务 Provider | `auth_provider.dart:93-101` |
| H14 | Access Token 无黑名单（登出后仍有效 30 分钟）；Refresh Token 不轮换、7 天可重放；logout 不在 permitAll，token 过期后无法登出 | `JwtAuthenticationFilter.java:38-39`、`AuthServiceImpl.java:149-159,162-168`、`SecurityConfig.java:56` |
| M15 | 搜索历史持久化在 SharedPreferences，跨账号共享 | `book_provider.dart` 的 `searchHistoryProvider` |

## 验收标准

1. 登录后手工把本地 access token 改成过期值，下拉刷新页面 → **不会**被登出、不需要重新登录（自动刷新成功）。
2. 清空浏览器 LocalStorage 后打开首页 → 直接落在 `/login`，**不发出任何业务请求**（Network 面板可验证）。
3. 在后端把 Redis 中的该 refresh token 删掉，再让前端触发一次 401 → 前端自动跳转登录页并提示"登录已过期，请重新登录"，而不是停在错误页。
4. 用 A 账号登录 → 退出 → 用 B 账号登录，首页/我的借阅/预约/通知**不显示** A 的任何数据。
5. `curl` 登录拿到 refreshToken → 调 `/auth/refresh` → 返回的新 refreshToken 与旧值**不同**；再用旧值调一次 → 返回 401（重放被拒）。

## 修复提示词

```text
【任务】Stage 10-C：修复认证会话链路

【背景：这条 bug 链的完整因果，请先读完再动手】
1. app_router.dart:25-48 的 redirect 只处理 AuthStatus.unauthenticated 和 authenticated。
   App 冷启动时状态是 AuthStatus.initial，redirect 返回 null，
   于是 initialLocation '/' 立即渲染 MainNavigationScreen → HomeScreen，
   HomeScreen 在 build 里 watch 了 aiRecommendationsProvider 与
   popularBooksRankingProvider，前者在 notifier 构造函数里就发起请求
   → 两个请求在 token 尚未从存储读出（或根本未登录）时裸发
   → 后端 anyRequest().authenticated() 返回 401
   → 这就是"首页一打开就两片红字"的确切成因。
2. 更严重的是 api_client.dart:90-92 把刷新成功判定写成了
     refreshResponse.data['code'] == 200
   而后端 ApiResponse.code 是字符串（"SUCCESS"/"RESOURCE_NOT_FOUND"/...），
   所以 refresh 永远被判为失败 → 走 else 分支 tokenStorage.clear()
   → newAccessToken 保持 null → 原始 401 继续上抛且不重试
   → 表现为"access token 一过期，整个应用不可用"。

【任务 1：修正刷新成功判定】
api_client.dart:90-92 改为基于后端真实的 code 契约判断，
建议抽成常量（如 ApiCodes.SUCCESS = 'SUCCESS'）而不是裸字符串比较，
同时保留对 HTTP 状态码的兜底判断。改完写一个单元测试覆盖
"refresh 返回 {"code":"SUCCESS"} 时应视为成功并重试原请求"。

【任务 2：补齐路由守卫三态】
app_router.dart 的 redirect 需要覆盖全部五种 AuthStatus：
- initial / loading → 返回一个独立的启动页（如 SplashScreen）或返回 null 但
  要求 MainNavigationScreen 在非 authenticated 时不构建业务页；
  推荐做法：新增 '/splash' 路由，initial/loading 重定向到它，避免业务页提前构建。
- unauthenticated → '/login'
- error → '/login'（并携带错误提示）
- authenticated 且访问 /login → '/'
同时给 GoRouter 补 errorBuilder，避免未匹配路由落到 go_router 默认英文红屏
（当前 ai_recommendation_screen.dart:82 的 context.go('/books') 就是一个不存在路由）。
/admin/** 的守卫要处理 user 为 null 的情况（当前 initial 态 roles 为空，
管理员深链接会被静默打回 '/' 且无任何提示）。

【任务 3：刷新失败要通知认证状态】
api_client.dart 中三处 tokenStorage.clear() 之后必须让 authStateProvider 转为
unauthenticated，否则守卫不会跳转，用户卡在满屏 401 的页面上。
推荐实现：定义一个会话过期事件 Provider（如 sessionExpiredProvider），
由 api_client 在清 token 后触发，AuthNotifier 监听后 setState(unauthenticated) 并跳登录页。
注意 Provider 之间不要形成循环依赖。
用户可见提示统一为"登录已过期，请重新登录"。

【任务 4：修复刷新互斥与重试上限】
1. 把 api_client.dart:8 的顶层 _refreshTokenCompleter 移入 apiClientProvider 闭包内，
   避免跨 ProviderContainer / 跨测试用例共享（当前测试里多个 ProviderScope 会串扰）。
   并在 ref.onDispose 中兜底 complete，避免等待者永久挂起。
2. 给重试请求打标：error.requestOptions.extra['_retryAttempt']，
   最多重试 1 次。当前 :56 与 :125 的重试请求会再次进入 onError，
   而此时 completer 已被置 null，该请求会自己成为新的领头请求再次刷新，
   在"刷新出来的 token 仍被业务接口判 401"（如账号被禁用）时会形成无限刷新循环。
3. 刷新用的 tokenDio 目前每次新建，改为复用单例。

【任务 5：登出清理业务状态（隐私问题）】
auth_provider.dart:93-101 的 logout 需要额外 invalidate 所有业务 Provider：
activeBorrowsProvider、borrowHistoryProvider、myReservationsProvider、
notificationProvider、aiRecommendationsProvider、bookListProvider、
bookDetailProvider、searchHistoryProvider（后者还需清空 SharedPreferences 中的持久化历史）。
当前这些 Provider 全部是非 autoDispose 的全局实例，A 用户登出后 B 用户登录，
首帧会直接看到 A 的在借图书、借阅历史、预约与通知。
验证方式：A 登录 → 看首页 → 登出 → B 登录 → 首页不得出现 A 的任何书名与记录。

【任务 6：后端补齐令牌生命周期（可选但强烈建议）】
1. Access Token 增加 jti 声明，登出时写入 Redis 黑名单（TTL = 剩余有效期），
   JwtAuthenticationFilter 在 validateToken 后校验黑名单。
2. 刷新时轮换 refresh token（删旧发新）；检测到旧 token 被二次使用时，
   撤销该用户全部 refresh token（重放检测）。
3. SecurityConfig:56 放行 /api/v1/auth/logout，
   使 access token 过期后仍能凭 refresh token 正常登出。
   （注意：放行前必须确保 logout 只允许吊销自己传入的那个 refresh token，
   不能变成任意人可吊销他人会话的接口。）
4. 为每个任务补集成测试。

【验收】按"验收标准"5 条逐项实测并贴出 Network 截图或 curl 输出。
```

---

# 阶段九（Stage 10-D）：前后端契约对齐（让首页/详情/画像真正有数据）

## 目标

把前端模型与后端响应结构逐字段对齐。这一阶段是"用户截图里什么都没有"的**根本原因**：即使登录成功，AI 推荐与阅读画像也解析不出数据。

## 问题清单

| 编号 | 问题 | 证据（已实测） |
| :--- | :--- | :--- |
| C7 | AI 推荐字段全面错位，`json['id'] as int` 对 `null` 强转 → 每条都抛 TypeError → 整表失败 | 后端实测返回 `{"recommendationLogId":87,"bookId":498,...,"score":72.0,"reason":"...","feedback":null}`；前端 `ai_model.dart:39` 读 `json['id'] as int`，`:47` 读 `recommendationScore`，`:49` 读 `recommendationReason`，`:50` 读 `logId`，`:52-53` 读 `canBorrow/canReserve`（后端不存在） |
| C8 | 阅读画像字段错位，四项指标恒 0/空 | 后端实测返回 `{"activeBorrowingCount":1,"estimatedSavedMoney":127.5,"categoryPreferences":[{"categoryName":..,"count":..,"percentage":..}],"monthlyTrends":[{"month":..}]}`；前端 `statistics_model.dart:26-42` 读 `activeBorrowedCount`、`estimatedMoneySaved`、`categoryDistribution`(**Map**)、`monthlyBorrowTrend`(**Map**)、`readerLevel`（后端无此字段） |
| H11 | 4 处接口路径/参数不匹配 | ① `ai_repository.dart:44` `/ai/insights/books/{id}` → 后端 `/ai/books/{bookId}/insight`（实测 404 `目标请求路径不存在`）② `ai_repository.dart:51` refresh 同错 ③ `statistics_repository.dart:58` `/statistics/recommendations` → 后端 `/statistics/recommendation-metrics`（实测 404）④ `statistics_repository.dart:37-39` 传 `days` 参数后端不接收（静默忽略） |
| H12 | 静默降级与假数据掩盖失败 | `book_provider.dart:197` 搜索失败后静默降级到 getBooks；`excel_import_dialog.dart:29` 上传写死的 4 字节假文件（`[0x50,0x4B,0x03,0x04]`），pubspec 未声明 file_picker |
| H13 | 原始 DioException 英文文本上屏并泄露 baseUrl | 13 处，`home_screen.dart:302-305`、`book_detail_screen.dart:37,346,401`、`book_list_screen.dart:291` 等 |
| M13 | 测试夹具伪造后端契约 | `ai_recommendation_test.dart:13-31` 用 `id/recommendationScore/logId/canBorrow` 造夹具，恰好与前端错误模型一致，所以测试全绿却掩盖了真实错位 |
| M14 | 分页无去重、无请求序号守卫 | `book_provider.dart:208` 纯追加；`loadInitial` 无并发守卫，快速切分类会旧响应覆盖新结果 |
| M22 | 其余 3 个统计模型字段错位（当前无调用点，潜伏） | `statistics_model.dart:84-95` 读 `totalBooks/totalCopies/...`，后端实际为 `totalBookTitles/totalBookCopies/...` |

## 验收标准

1. 用 student_demo 登录后打开首页，"精选 AI 推荐"显示真实书目卡片（含书名/作者/在架册数/推荐理由），**不是**错误文案。
2. 打开 `/ai/recommendations` 整页，卡片上的"一键借阅/预约排队"按钮可见可点；点一次后 `ai_recommendation_logs` 表能看到对应 `feedback` 或点击埋点记录。
3. 打开"阅读画像"，"当前在借"显示真实数字（当前应为 1）、"累计节省购书支出"显示 ¥127.50、分类偏好与近月趋势有图表数据（当前全为空）。
4. 打开任意图书详情页，AI 导读能正常显示（依赖阶段十一的 500 修复，本阶段至少保证路径不再 404）。
5. 故意让后端返回 500，前端显示中文可读文案，**不得**出现 `DioException [bad response]` 或 `http://localhost:8080` 字样。
6. `grep -rn "as int" frontend/lib` 中所有对可能为 null 的字段的强转都被消除（改为 `as int?` + 默认值或显式校验）。

## 修复提示词

```text
【任务】Stage 10-D：前后端契约全面对齐

【背景：这是"页面什么都看不到"的根本原因】
即使成功登录，AI 推荐与阅读画像也解析不出数据，因为前端读的 JSON 键名与后端返回的
完全不一致。后端 /ai/recommendations 实测返回：
  {"recommendationLogId":87,"bookId":498,"title":"深度学习","author":"Ian Goodfellow 等",
   "availableCopies":3,"totalCopies":4,"score":72.0,
   "recommendationSource":"CONTENT_BASED","sourceDescription":"内容特征匹配",
   "reason":"因您在「计算机科学与技术」领域的阅读偏好...","feedback":null}
而 ai_model.dart:39 执行 json['id'] as int —— 对 null 做 as int 会抛
TypeError: type 'Null' is not a subtype of type 'int' in type cast，
导致 map() 整体失败，首页显示"推荐暂时不可用: type 'Null' is not a subtype of type 'int'"。

【任务 1：对齐 AI 推荐模型】
修 frontend/lib/features/ai/domain/ai_model.dart 的 RecommendedBookModel.fromJson：
  id                    → bookId
  recommendationScore   → score
  recommendationReason  → reason
  logId                 → recommendationLogId
  userFeedback          → feedback
  canBorrow/canReserve  → 后端没有这两个字段，改为由 availableCopies > 0 推导，
                          或推动后端补字段（二选一并说明选择理由）
所有可能为 null 的字段禁止裸 `as int`，必须 `as int?` + 默认值或显式抛可读异常。
同时检查 ai_recommendation_screen.dart:131 的 clamp(50,99) —— 后端 score 是
0~100 的真实分值，把它强行抬到 50 起会失真，请一并修正为按真实分值展示。

【任务 2：对齐阅读画像模型】
修 statistics_model.dart 的 MyReadingStatisticsModel.fromJson：
  activeBorrowedCount     → activeBorrowingCount
  estimatedMoneySaved     → estimatedSavedMoney
  categoryDistribution(Map) → categoryPreferences(List<{categoryName,count,percentage}>)
  monthlyBorrowTrend(Map)  → monthlyTrends(List<{month,...}>)
  readerLevel             → 后端无此字段，删除或改为前端本地推导
注意 categoryDistribution 与 monthlyBorrowTrend 的类型从 Map 改成 List，
读取处（阅读画像页面的图表组件）必须同步改造，不能只改模型导致编译错误堆积。

【任务 3：修正全部路径与参数不匹配】
① ai_repository.dart:44,51：/ai/insights/books/{id} 与 /refresh
   → 改为 /ai/books/{id}/insight 与 /ai/books/{id}/insight/refresh
② statistics_repository.dart:58：/statistics/recommendations
   → 改为 /statistics/recommendation-metrics
③ statistics_repository.dart:37-39：days 参数后端不接收，要么删除，
   要么在 StatisticsController 的 /books/ranking 上补 days 参数并实现，
   二选一并说明（如果实现，注意 @PreAuthorize 与 limit 的 clamp 已有，沿用）。
④ 逐条复核 reservation_repository.getReservationDetail 的响应结构
   （后端返回 ReservationDetailResponse，含 events 时间线，与 ReservationResponse 不同）。
⑤ statistics_model.dart:84-95 的 LibraryOverviewStatisticsModel 字段
   （totalBooks/totalCopies/totalBorrowRecords/...）与后端
   LibraryOverviewStatisticsResponse（totalBookTitles/totalBookCopies/...）也不一致，
   虽然当前无调用点，请一并修正避免留下地雷。
完成后请把"前端调用 → 后端实际 → 是否匹配"的对照表写进报告，确保无遗漏。

【任务 4：消除静默降级与假数据】
1. book_provider.dart:197 的 catch 静默降级到 getBooks 必须删除或改为
   仅在明确识别到"接口不存在(404)"时降级，其他错误（403/500/解析异常）正常上抛。
   当前它把 403/500/解析错误全部吞成"没有结果的列表"，正是它掩盖了本阶段的问题。
2. excel_import_dialog.dart:29 上传写死的 4 字节假文件必须删除。
   引入 file_picker 依赖让用户选择真实文件，或在 file_picker 不可用时
   明确提示"当前环境不支持文件选择"，禁止继续上传伪造数据。
   （这一条同时影响答辩可信度，属于必须修的项。）

【任务 5：统一错误文案】
新增一个 ApiErrorMapper（放在 core/network/ 下），把 DioException 按类型映射为中文文案：
- 连接失败/超时 → "网络连接失败，请检查网络后重试"
- 401 → "登录已过期，请重新登录"
- 403 → "您没有权限执行此操作"
- 404 → "请求的资源不存在"
- 5xx → "服务暂时不可用，请稍后重试"
- 优先取后端响应体中的 message 字段（后端已返回中文 message）
然后把 13 处直接渲染 e.toString() 的位置全部替换（home_screen.dart:302-305、
book_detail_screen.dart:37,346,401、book_list_screen.dart:291、
borrow_circulation_screen.dart:87,343、reservation_screen.dart:97、
reading_statistics_screen.dart:44、librarian_dashboard_screen.dart:52、
ai_recommendation_screen.dart:57、catalog_manage_screen.dart:52,160,384,425,669、
excel_import_dialog.dart:41）。
参照 auth_provider.dart:79-89 已有的 DioException 处理模式，把它下沉复用。
验收：任意错误场景下界面不得出现 "DioException"、"[bad response]"、
"http://localhost:8080" 等字样。

【任务 6：分页与竞态】
1. book_provider.dart:208 与 borrow_provider.dart:56,149 的列表追加改为按 id 去重。
2. loadInitial / loadRecords 增加请求序号（requestId），只有最新请求的响应可以写状态，
   避免快速切换分类时旧响应覆盖新结果。
3. borrow_circulation_screen 的滚动加载在 hasMore == false 时必须停止递增 page
   （当前会持续请求空页）。

【任务 7：修正测试夹具（关键）】
ai_recommendation_test.dart:13-31 名为"能够正确解析后端 JSON"的用例，
其夹具使用的键（id/recommendationScore/logId/canBorrow）后端根本不会返回，
因此测试全绿却掩盖了真实错位。
请把夹具改为真实后端响应（可从 curl 输出复制），并补充：
① 至少 3 个契约测试，直接断言前端模型能解析真实响应结构；
② 一个"字段缺失时给出可读错误而非 TypeError"的用例。
这是防止同类问题复发的关键，不要省略。
```

---

# 阶段十（Stage 10-E）：数据一致性与异常语义

## 目标

消除"库里多一个字符串就让接口 500"这类数据脆弱性，并让异常处理恢复正确的 HTTP 语义。

## 问题清单

| 编号 | 问题 | 证据（已实测/数据库核对） |
| :--- | :--- | :--- |
| H1 | `notifications` 存在枚举外值：`type='BORROW_SUCCESS'`(1 行)、`related_entity_type='SYSTEM'`(1 行)，且两条都归属 student_demo、都在默认第一页 | `V9__seed_demo_data.sql:509,530`；实测 `GET /notifications` 学生 500、馆员 200；DB 查询确认脏数据 2 行 |
| — | V8 是全库唯一没有 CHECK 约束的枚举列建表脚本，导致脏数据可入库 | `V8__create_notifications_table.sql:11,13`（全文无 CHECK，对比 V2/V3/V5/V6/V7 均有） |
| — | 其它种子数据错误：`related_entity_id=1` 指向他人已 EXPIRED 的预约；`related_entity_id=0` 非法主键；为不存在的 TEACHER 角色授权（INSERT...SELECT 匹配 0 行） | `V9:525-526,530`、`V6:86-91`、`V7:82-87`；DB 确认 roles 只有 ADMIN/LIBRARIAN/STUDENT |
| H6 | `GlobalExceptionHandler` 的 `Exception` 兜底先于 Spring 内置解析器，把 400/409/413/415 全变 500 | `GlobalExceptionHandler.java:98-106`；实测 `GET /notifications?type=BORROW_SUCCESS` → 500 |
| H7 | 分页 size 无上限，`?size=10000000` 可 OOM | `NotificationController.java:40`、`BorrowRecordController.java:110-113`、`ReservationQueryParam.java:36`（对比 `BookServiceImpl.java:149` 有 `Math.min(100,...)` 保护） |
| H20 | `1001L` fail-open 兜底用户 ID（当前 users 表无 id=1001，一旦触发即脏数据） | `NotificationController.java:39,50,61,71`、`StatisticsController.java:29`、`AiRecommendController.java:32,43,55` |
| — | 枚举值无常量写入路径：`ABNORMAL_LOST`/`ABNORMAL_DAMAGED`/`SCRAPPED`/`OFF_SHELF` 等只有读没有写，"图书遗失/破损/下架"业务未实现 | 全仓库 grep 确认 |

## 验收标准

1. `curl -H "Authorization: Bearer $STUDENT_TOKEN" .../notifications` 返回 200，且能列出 4 条通知（含那条系统公告）。
2. `curl ".../notifications?type=BORROW_SUCCESS"`（模拟前端回传库里原值）返回 **400 + 中文 message**，而不是 500。
3. `curl ".../notifications?size=999999999"` 时后端实际最多返回 100 条（响应中体现 pageSize 上限）。
4. 传畸形 JSON、错误 Content-Type、超大上传文件分别返回 400/415/413，而非 500。
5. `psql` 复查 `notifications` 两列取值全部落在枚举内，且 V11 之后新插入的非法值被 CHECK 拒绝。
6. 人为制造一次 `uk_reservations_active_user_book` 冲突，接口返回 409 + 中文提示，而非 500。

## 修复提示词

```text
【任务】Stage 10-E：修复数据一致性与异常语义

【背景：这一条已实测复现】
V9__seed_demo_data.sql:509 写入 type='BORROW_SUCCESS'、:530 写入 related_entity_type='SYSTEM'，
但 NotificationType 与 RelatedEntityType 两个枚举都没有这两个值。
实测 GET /api/v1/notifications：student_demo 返回 500（
{"code":"SYSTEM_INTERNAL_ERROR","message":"系统繁忙，请稍后重试"}，
后端日志为 IllegalArgumentException: No enum constant com.library.domain.enums.RelatedEntityType.SYSTEM），
librarian_demo / admin_demo 返回 200。原因是那两条脏数据恰好归属 student_demo
且都在默认第一页内。
根因是 V8__create_notifications_table.sql 是全库唯一没有给枚举列加 CHECK 约束的建表脚本。

【任务 1：新增 V11 数据修复迁移（禁止修改 V1~V10）】
V11__fix_notification_enum_data.sql：
  - UPDATE notifications SET type='SYSTEM_ANNOUNCEMENT' WHERE type NOT IN (枚举合法值...);
    并同时校正 related_entity_id（当前为 0，非法主键）
  - UPDATE notifications SET related_entity_type='NONE', related_entity_id=NULL
    WHERE related_entity_type NOT IN ('BOOK','RESERVATION','BORROW_RECORD','NONE');
  - 对 notifications.type 与 notifications.related_entity_type 补 CHECK 约束，
    取值与两个 Java 枚举严格一致（注意 PostgreSQL 添加 CHECK 时先加 NOT VALID 再 VALIDATE，
    避免大表锁）。
  - 修正 V9 遗留的其它脏数据：related_entity_id=1 的通知（实测指向他人已 EXPIRED 的预约
    resv_a_9e78e971），改为按业务语义定位正确实体或用子查询取正确 id。
  请顺带审查 V9 全文中所有枚举语义列（status/type/role/related_entity_type/event_type/
  recommendation_source）的取值是否都在对应 Java 枚举内，
  以及 V6/V7 中针对不存在的 TEACHER 角色的授权语句（roles 表实际只有
  STUDENT/LIBRARIAN/ADMIN，那批 INSERT...SELECT 静默匹配 0 行），
  请补齐 TEACHER 角色定义或删除无效授权，不要留静默空操作。

【任务 2：给枚举列加容错读写】
为避免"库里多一个字符串就 500"，为 NotificationType 与 RelatedEntityType
实现 JPA AttributeConverter（或统一的自定义 Enum 转换器）：
遇到未知值时不抛异常，降级为 NONE / SYSTEM_ANNOUNCEMENT 并打 WARN 日志
（日志需包含原始值，便于发现新脏数据）。
请评估该方案与"强制 CHECK 抛异常"的取舍并说明理由——
推荐：CHECK 保证新数据干净，Converter 保证读到历史脏数据不炸，两者并存。

【任务 3：补全异常处理器（让 HTTP 语义恢复正确）】
GlobalExceptionHandler.java 当前只有 8 个 handler，且 Exception 兜底
先于 Spring 内置的 DefaultHandlerExceptionResolver 生效，
导致所有 MVC 标准异常都变成 500。请补：
  - MethodArgumentTypeMismatchException → 400（典型触发：
    NotificationController.java:38 的 @RequestParam NotificationType type 收到非法枚举值，
    ReservationQueryParam.status 同理）
  - HttpMessageNotReadableException → 400（畸形 JSON）
  - MissingServletRequestParameterException → 400
  - HttpMediaTypeNotSupportedException → 415
  - MaxUploadSizeExceededException → 413
  - DataIntegrityViolationException → 409，并解析约束名映射为中文业务提示
    （至少覆盖 uk_reservations_active_user_book、uk_borrow_records_active_copy）
  - PessimisticLockingFailureException / CannotAcquireLockException → 409 或 503
    （提示"系统繁忙，请稍后重试"），可选加一次自动重试
  - LazyInitializationException → 保留 500 但必须打 ERROR 并带完整堆栈
    （根因在 Stage 10-F 修复，此处只保证可观测）
建议改为继承 ResponseEntityExceptionHandler 或显式声明上述 handler。
验收：用 curl 构造 5 种错误请求，确认返回码分别为 400/415/413/409 而非统一 500。

【任务 4：分页参数统一上限】
为 NotificationController.java:36,40、BorrowRecordController.java:110-113、
ReservationQueryParam.java:36（以及其它直接透传 size 的端点）
统一加上限，参照 BookServiceImpl.java:149 已有的 Math.min(100, ...) 写法，
或在 DTO 层加 @Min(1) @Max(100) 并让 @Validated 生效。
注意修改后 /statistics/books/ranking 的 limit clamp 已存在，不要重复实现。

【任务 5：删除 fail-open 的 1001L 魔法值】
NotificationController.java:39,50,61,71、StatisticsController.java:29、
AiRecommendController.java:32,43,55 中的
  currentUser != null ? currentUser.getId() : 1001L
全部改为 principal 为 null 时抛 401（参照 AuthController.java:60-63 已有的正确写法）。
当前 users 表中不存在 id=1001，一旦触发就是静默读写错误用户的数据。
请同时检查是否还有其它硬编码兜底值。

【验收】按"验收标准"6 条执行，其中第 1、2 条必须给出 curl 的完整输出。
```

---

# 阶段十一（Stage 10-F）：事务边界与懒加载

## 目标

修复"AI 导读必然 500""催还通知永不发出""库存计数丢失更新"三个由事务边界/懒加载引发的确定性缺陷。

## 问题清单

| 编号 | 问题 | 证据 |
| :--- | :--- | :--- |
| H2 | AI 导读首次生成必 500：`getBookInsight` 无 `@Transactional`（刻意在事务外调外部 AI），而 `Book.category` 是 `@ManyToOne(LAZY)`，本地降级 Provider 访问 `book.getCategory().getName()` 时 Session 已关 | `AiInsightServiceImpl.java:39-71`（`:62` 调用）、`RuleBasedMockAiProvider.java:19`、`DeepSeekAiProvider.java:118`（降级分支再抛一次）、`Book.java:56`；`open-in-view: false`（`application-dev.yml:19`）；实测 500，DB 中 666 本书只有 12 条缓存 |
| H3 | 催还/逾期定时任务：`@Transactional` 被自调用绕过 → 无事务 → 懒加载异常 → 被 per-record catch 吞成 WARN → 通知永不发出 | `BorrowDueCheckScheduler.java:39`（自调用）、`:46-47`（失效的 @Transactional）、`:68,104`（懒加载访问）、`:79-81,115-117`（吞异常） |
| H4 | `total_copies`/`available_copies` 的 read-modify-write 无锁无版本 → 丢失更新 | `BookCopyServiceImpl.java:417-421,445-458,478-483`、`BookImportListener.java:110-112`；全仓库无 `@Version` |
| M6 | `bookLocks` 移除时机竞态（A 释放后 C 拿到新锁对象与 B 同时进入临界区）+ 锁内做 5s 网络调用 | `AiInsightServiceImpl.java:37,51-52,68,88` |
| M7 | `app.borrow.due-check-cron`、`ai.deepseek.*` 在 yml 中均不存在，运维不可调 | `application*.yml` |

## 验收标准

1. 用 `curl -H "Authorization: Bearer $TOKEN" .../ai/books/853/insight` 请求一本**未缓存**的书（如 id=853），返回 200 且含 `summary/keyTopics/targetReader/readingGuide`。
2. 连续并发请求同一本未缓存书（如 50 并发），后端日志中"调用 AI 生成导读"只出现 **1 次**（验证防重与锁生效）。
3. 把某条借阅记录的应还日期改为明天，手工触发催还任务（或临时把 cron 改到 1 分钟后），确认 `notifications` 表新增对应的临期提醒记录（**这是当前完全失效的功能**）。
4. 并发 20 个请求同时给同一本书新增副本，结束后 `total_copies` 等于实际副本数（无丢失更新）。
5. `mvn test` 仍全绿，且新增至少 3 个测试覆盖上述场景。

## 修复提示词

```text
【任务】Stage 10-F：修复事务边界与懒加载缺陷

【背景：两个确定性缺陷，均已复现】
(1) AI 导读必 500
    AiInsightServiceImpl.getBookInsight 没有 @Transactional（这是有意的设计：
    避免外部 AI 网络调用长期占用数据库连接）。它先用 bookRepository.findById 取 Book，
    该调用自带的只读事务结束后实体已 detach；随后在事务外调用
    aiProvider.generateInsight(book)。未配置 DEEPSEEK_API_KEY 时会降级到
    RuleBasedMockAiProvider，而它第 19 行执行 book.getCategory().getName()，
    Book.category 是 @ManyToOne(fetch = LAZY)（Book.java:56），
    且 open-in-view: false → LazyInitializationException → 500。
    实测 /ai/books/853/insight 返回 500；数据库中 666 本书只有 12 条
    ai_book_insights 缓存，所以 98% 的书目必然失败。
    DeepSeekAiProvider.java:118 的降级分支还会在 fallback 内部再抛一次，兜底形同虚设。

(2) 催还通知永不发出
    BorrowDueCheckScheduler.java:39 在本类内直接调用
    scanAndProcessOverdueAndReminders()，不经过 Spring AOP 代理，
    因此 :46-47 的 @Transactional 完全不生效；方法内 :68 与 :104 访问
    record.getBook().getTitle() 时 Session 已关，抛 LazyInitializationException，
    又被 :79-81 与 :115-117 的 per-record catch 吞成一条 WARN 日志。
    结果是临期催还与逾期告警功能形式上存在、实际完全不可用。

【任务 1：修复 AI 导读的懒加载（推荐结构性修复）】
不要让 Provider 直接接收 JPA 实体。请：
  1. 定义 BookInsightContext（纯 DTO：bookId、title、author、categoryName、
     description、isbn 等 Provider 需要的全部字段）。
  2. 在持有事务的边界内（新增一个 @Transactional(readOnly = true) 的方法或
     使用 @EntityGraph / JOIN FETCH 查询）把实体转成该 DTO，
     确保 categoryName 被真实读取。
  3. AiProvider 接口签名改为接收 BookInsightContext，
     RuleBasedMockAiProvider 与 DeepSeekAiProvider 同步改造。
  4. 这样既保留了"外部 I/O 在事务外执行"的设计意图，又消除了懒加载越界。
  5. DeepSeekAiProvider 的降级分支必须独立 try/catch，确保降级本身不再抛异常。
  请为"未缓存书目的首次生成"补一个集成测试（不要用 Mock 打桩 repository，
  要真正走一次 Session 关闭后的路径），当前这个 bug 之所以逃过 165 个测试，
  正是因为相关测试全部用 Mockito 打桩绕过了真实 Session。

【任务 2：修复定时任务事务失效】
  1. 把任务执行体拆到独立的 @Component（如 BorrowDueCheckExecutor），
     或把 @Transactional 方法放到接口上并注入自身代理调用，
     确保注解真正生效。
  2. 仓储查询改为 JOIN FETCH r.book / r.user 或返回投影 DTO，
     从根上避免事务内的懒加载风险。
  3. 异常不得再被 per-record catch 静默吞掉：保留 per-record 隔离（避免一条坏数据
     中断整批），但必须 ERROR 级别 + 完整堆栈 + 记录 recordId，
     并在任务结束时汇总"成功 N 条 / 失败 M 条"。
  4. 通知幂等：当前靠"先查后写"，而 idx_notifications_dedup 是非唯一索引
     （V8:40-41），多实例会重复推送。改为唯一索引 + ON CONFLICT DO NOTHING，
     或加分布式锁。
  5. 补 spring.task.scheduling.pool.size（当前默认单线程，
     每日 08:00 的 due-check 会挤掉每分钟的预约过期扫描）。
  6. 把 app.borrow.due-check-cron 与 app.reservation.expire-cron 写入 yml 并可被环境变量覆盖，
     不要在代码里写死默认值。
  验证方式：把借阅记录的应还日期改为明天，触发任务后确认 notifications 表新增记录。

【任务 3：修复库存计数丢失更新】
BookCopyServiceImpl.java:417-421(createCopy)、:445-458(updateCopy)、:478-483(deleteCopy)
与 BookImportListener.java:110-112 中的
  book.setTotalCopies(book.getTotalCopies() + 1)
等 read-modify-write 全部改为先取悲观锁
（bookRepository.findByIdForUpdate(bookId)，该方法已存在并被借阅路径使用），
保证与借阅/归还路径的加锁拓扑一致（Book → BookCopy）。
注意 updateCopy 当前既改副本状态又改 Book 计数且完全不加锁，
与 borrowBook 的 Book→BookCopy 顺序理论上可构成环，请一并纳入统一拓扑。

【任务 4：修复 bookLocks 竞态】
AiInsightServiceImpl.java:37 的 ConcurrentHashMap 用法在
"A 持锁 → B 等待 → A 结束 remove → C 到达建出新锁对象"
这一时序下会让 B 与 C 同时进入临界区，双检失效，同一书可能调用两次外部模型。
请改为引用计数方案，或使用 Guava Striped / ReentrantLock 配合 tryLock(timeout)，
并把 provider 调用移出锁（用 ai_book_insights 的 book_id 唯一键冲突兜底，
或 @Cacheable）。当前锁内还包含最长 5s 的网络调用，
同一书目的后续请求会全部阻塞 Tomcat 工作线程。

【任务 5：DeepSeek 客户端健壮性】
  1. DeepSeekAiProvider.java:82-98 使用 SimpleClientHttpRequestFactory（HttpURLConnection），
     无连接池、无重试、无熔断。改为带连接池的 RestClient
     （HttpComponents/Jetty），并加超时/重试/熔断（如 Resilience4j）。
  2. ai.deepseek.api-key / base-url / model 全部落入 yml，支持环境变量注入。
  3. :75-80 的 getRestClient() 是 synchronized 懒加载并读取可变字段，
     setApiKey 会置 null 重建，运行期非线程安全，改为构造期初始化或双重检查 + volatile。

【验收】按"验收标准"5 条逐项执行，第 1、2、3 条必须给出真实输出/日志。
```

---

# 阶段十二（Stage 10-G）：并发不变式与预约闭环

## 目标

修复"读者收到取书通知却无书可借"这类业务不变式破坏，并把锁拓扑与数据库约束彻底统一。

## 问题清单

| 编号 | 问题 | 证据（数据库实测） |
| :--- | :--- | :--- |
| H5 | 预约晋升无库存守卫 | `ReservationServiceImpl.java:396-433`（`:406-410` 无条件置 READY）；实测 `SELECT count(*) FROM reservations r JOIN books b ON b.id=r.book_id WHERE r.status='READY' AND b.available_copies=0` → **33 行**；触发源 `BookCopyServiceImpl.java:445-458,478-483` 下调库存时不联动 READY 预约 |
| M10 | V10 唯一约束维度（copy_id）与代码校验维度（user_id,book_id）不一致，冲突时抛 `DataIntegrityViolationException` 无处理器 → 500 | `V10:6-8` vs `BorrowCirculationServiceImpl.java:109-113` |
| M9 | 锁拓扑不统一：还书路径起点为 BorrowRecord（`BorrowRecord → Book → BookCopy`）；cancelReservation / scanAndExpire 不加 Book 锁 | `BorrowCirculationServiceImpl.java:197,238,241`、`ReservationServiceImpl.java:229,267,270,365` |
| M8 | 同书多 READY、队列位次无唯一约束保护 | `V6:51-52` 非唯一索引；实测同书最多 8 个同时 READY |
| M5 | 定时任务无分布式锁、无界扫描、无查询超时 | `scheduler/**` |

## 验收标准

1. 写一个测试：把某书唯一在架副本改为 DAMAGED，然后触发一次还书/取消流程，断言**不会**再产生"READY 但 available_copies=0"的记录（当前会产生）。
2. `SELECT count(*) FROM reservations r JOIN books b ON b.id=r.book_id WHERE r.status='READY' AND b.available_copies=0` 在修复后运行数据清理脚本，结果为 0，且此后不再增长。
3. 并发压测：50 线程借还 + 50 线程预约交叉执行，无死锁、无超借、无负库存、无"READY 无库存"。
4. 人为制造单册重复借出，接口返回 409 + 中文提示（依赖阶段十的异常处理器），而非 500。
5. 同书 WAITING 队列位次无重复（加约束后 DB 层拒绝重复）。

## 修复提示词

```text
【任务】Stage 10-G：修复并发不变式与预约闭环

【背景：已实测的业务不变式破坏】
ReservationServiceImpl.promoteNextWaitingReservation(:396-433) 晋升队首时
从不检查 book.availableCopies，直接在 :406-410 置 READY 并写 48 小时保留期。
而 BookCopyServiceImpl.updateCopy(:445-458) / deleteCopy(:478-483) 下调
availableCopies 时完全不联动任何 READY 预约。
数据库实测：status='READY' 且对应图书 available_copies=0 的记录有 33 条，
例如 book_id=613「分布式系统设计 (无库存)」。
后果：读者收到"图书已到馆请取书"的通知，到馆后 fulfillReservation 以
BOOK_NO_AVAILABLE_COPY (409) 失败。这是核心业务闭环上的确定性缺陷。

【任务 1：晋升加库存守卫】
promoteNextWaitingReservation 在置 READY 前必须：
  1. 先取 Book 悲观锁（与 createReservation 一致：bookRepository.findByIdForUpdate）；
  2. 计算该书的"可用库存是否足以支撑下一个 READY"——
     即为同一本书已有的 READY 数量设上限（建议上限 = available_copies，
     或按业务确定的比例），当前已存在同书 8 个 READY 的失配状态；
  3. 库存不足时**不晋升**，保持 WAITING，并记录明确日志。
同时修复存量数据：新增 Flyway 迁移，把"READY 但无库存"的记录置回 WAITING
并重排队列位次（33 行），或按业务确认后置为 EXPIRED。

【任务 2：库存变更联动预约】
updateCopy / deleteCopy 在同一事务内，若因副本下架/损坏导致 availableCopies 减少，
必须检查该书是否存在 READY 预约：
  - 若已无库存支撑，把相应 READY 预约回退为 WAITING（并按位次重新排到队首）
    或触发过期处理；
  - 同时撤销/更新已发出的"到馆取书"通知（避免用户白跑一趟）。
请把这一步纳入与借阅路径统一的加锁顺序中（Book → BookCopy），
不要引入新的锁逆序。

【任务 3：统一唯一约束与校验维度】
V10 的约束是 UNIQUE (copy_id) WHERE status IN ('BORROWING','OVERDUE')，
而代码的前置校验只查 (user_id, book_id)，两者语义不同。
请：
  1. 代码补 existsByCopyIdAndStatusIn 前置校验（真正防"同一单册重复借出"）；
  2. 为 reservations 补 WAITING 位次唯一约束：
     UNIQUE (book_id, queue_position) WHERE status='WAITING'；
  3. 为"同书 READY 数量不超过可用库存"提供 DB 层兜底
     （若 PostgreSQL 难以直接表达，用触发器或改为串行化队列变更 + 应用层强校验，
     并说明取舍）。
  4. 新增迁移，不改 V1~V10。

【任务 4：统一锁拓扑并文档化】
已核实：borrowBook 严格 Book→BookCopy（正确）；fulfillReservation 为 Book→Reservation（正确）；
但 returnBook 起点是 BorrowRecord（BorrowRecord→Book→BookCopy），
cancelReservation 与 scanAndExpireReservations 拿 Reservation 锁而不拿 Book 锁。
请推导并写出一份**全局确定性锁顺序表**（实体级偏序），
然后把所有涉及多实体写入的路径统一到该顺序，
特别是让你在任务 2 中新增的"库存变更联动预约"也遵守同一顺序。
要求：
  - 给出改动前后的锁获取顺序对照表；
  - 写一个并发交叉压力测试（借还 × 履约 × 取消 × 预约过期扫描）
    持续运行并断言无死锁（可捕获 PessimisticLockingFailureException/DeadlockLoserDataAccessException）；
  - 说明为什么此前 165 个测试没有发现锁拓扑不一致（提示：相关测试多为单路径 + Mock 打桩）。

【任务 5：定时任务兜底】
为两个定时任务引入分布式锁（ShedLock 或 SELECT ... FOR UPDATE SKIP LOCKED 抢占），
给无界扫描加分页/批量上限，并设置查询超时，
避免多实例重复推送与长事务持锁（当前 ReservationRepository.java:306-308
一次性加载全部过期 READY 并在整个循环期间持行锁）。
```

---

# 阶段十三（Stage 10-H）：权限模型与安全加固

## 目标

修复"读者首页必然 403"这类权限设计错误，并收敛剩余的暴露面。

## 问题清单

| 编号 | 问题 | 证据（已实测） |
| :--- | :--- | :--- |
| C11 | 读者首页的"热门借阅榜单"调用需要 `statistics:global:view` 的接口，学生**恒 403** | `home_screen.dart:60` + `StatisticsController.java:44`；实测 student 403 / librarian 200 / admin 200；学生权限清单里只有 `statistics:my:view` |
| H18 | `borrow:renew` 只授给了 STUDENT，LIBRARIAN/ADMIN 调续借一律 403，服务层为馆员写的代客续借分支成死代码 | `BorrowRecordController.java:62` vs `V5:96-110`；`BorrowCirculationServiceImpl.java:274-276` |
| H19 | 3 个分类查询端点无 `@PreAuthorize` | `CategoryController.java:32,39,46` |
| H16 | CORS `allowedOriginPatterns("*")` + `allowCredentials(true)` | `WebMvcConfig.java:14-22` |
| H17 | `/actuator/metrics` 任意已认证用户可读；`show-details: always`；生产 Swagger 匿名开放 | `application.yml:19-28`、`SecurityConfig.java:54,59-60` |
| H15 | 登录无失败频控/锁定；注册匿名开放且无验证；密码最小长度仅 6（允许 `123456`） | `AuthServiceImpl.java:86-105`、`RegisterRequest.java:31-33` |
| M11 | Refresh Token 明文写入日志，且 dev/test 下 `com.library` 为 DEBUG | `RefreshTokenService.java:88`、`logback-spring.xml:22` |
| L2 | `TraceIdFilter` 直接采用外部传入的 traceId，可日志注入 | `TraceIdFilter.java:27-38` |
| L3 | 注册接口回显"用户名/邮箱已被占用"，可枚举用户 | `AuthServiceImpl.java:50-57` |
| L1 | `/api/v1/public/**` 为空放行规则 | `SecurityConfig.java:58` |
| — | 馆员拥有 `statistics:global:view`，但接口注释标注为"管理员"专属，权限与文档不一致 | `StatisticsController.java:59-63`、`V7:92-95` |

## 验收标准

1. student_demo 登录后首页"热门借阅榜单"显示真实 Top 5（不再是"排行榜加载失败"）；librarian/admin 登录后同样正常。
2. `statistics:global:view` 语义在"权限设计文档 → 迁移脚本 → 注解 → 前端展示"四处一致，不再出现文档说管理员、代码给馆员的情况。
3. librarian_demo 调 `POST /borrow-records/{id}/renew` 返回 200（当前 403）。
4. 学生账号调 `/api/v1/categories` 正常；未登录调该接口返回 401。
5. 用错误的 Origin（如 `http://evil.example.com`）发预检请求，不再返回 `Access-Control-Allow-Origin: *`。
6. 未登录访问 `/v3/api-docs` 在 prod profile 下返回 401（dev 下仍可访问）。
7. 连续 20 次错误密码登录后，第 21 次返回"账号已临时锁定"或明确限流提示（当前无限尝试）。
8. 日志中 `grep -c "refresh_token:" backend.log` 结果为 0（token 已脱敏）。

## 修复提示词

```text
【任务】Stage 10-H：修复权限模型与安全加固

【背景：读者首页恒 403 的确定性缺陷】
home_screen.dart:60 无条件 watch popularBooksRankingProvider，
它调用 /api/v1/statistics/books/ranking，
而 StatisticsController.java:44 要求 hasAuthority('statistics:global:view')，
V7__create_ai_and_statistics_tables.sql 只把该权限授给 LIBRARIAN / ADMIN。
学生权限清单实测为：user:profile:view,user:profile:update,book:view,borrow:apply,
borrow:return,borrow:renew,borrow:query:my,reservation:*,ai:recommend:view,
ai:insight:view,ai:feedback:submit,statistics:my:view,notification:*。
实测结果：student_demo 403、librarian_demo 200、admin_demo 200。
也就是说"读者首页的热门借阅榜"这个面向学生的模块，对真正的读者永远报错。

【任务 1：修复排行榜权限（先做决策，再动手）】
请在两种方案中做出选择并说明理由，然后实施：
  方案 A（推荐）：新增一个面向读者的公开排行榜接口，例如
    GET /api/v1/statistics/public/books/ranking（或 /api/v1/books/ranking），
    只返回书名/作者/借阅次数/在架册数等非敏感字段，
    权限点设为 book:view（学生已有），home_screen 改调该接口；
    同时保留现有馆员版接口（含更全字段）。
  方案 B：给 STUDENT 授予 statistics:global:view。
    需评估：该权限同时解锁 /overview、/categories/hot、/recommendation-metrics、
    librarian-dashboard（后者的 or 条件含该权限），是否会造成越权，若会则不可选。
无论选哪个方案，都必须同步修复 home_screen.dart:48-53 的 _onRefresh：
它用 Future.wait 合并了一个可能 403 的请求，会导致 Future 以异常结束、
RefreshIndicator 行为未定义。请改为逐项 catch 或按权限条件渲染。
另请一并校正 StatisticsController 注释标注的"管理员"与 V7 实际授予 LIBRARIAN 的矛盾：
确定 statistics:global:view 的真实归属（文档、迁移、注解、前端四处保持一致）。

【任务 2：修复 borrow:renew 权限漏授】
V5__create_borrow_circulation_tables.sql:96-110 中，
STUDENT 有 borrow:renew，但 LIBRARIAN 与 ADMIN 的角色权限绑定清单里没有它，
导致 BorrowRecordController.java:62 的 @PreAuthorize("hasAuthority('borrow:renew')")
对馆员一律 403，而 BorrowCirculationServiceImpl.java:274-276 专门为
"馆员代客续借"写的分支（isAdminOrLibrarian 判断）成为永远不可达的死代码。
请二选一并实施：① 在迁移中把 borrow:renew 补授给 LIBRARIAN/ADMIN；
② 明确该接口仅读者自助，删除服务层的代客分支并修正接口文档。
不要留下"权限没给但代码为它写了分支"这种矛盾状态。

【任务 3：补齐缺失的权限注解】
CategoryController.java:32,39,46 的 3 个 GET 端点没有任何 @PreAuthorize
（任何已认证用户都能访问，写操作有 category:manage）。
请补 book:view（读者查分类字典需要）或新增 category:view 权限点，
保持"默认拒绝、显式授权"原则。

【任务 4：收敛配置暴露面】
1. application.yml:19-28：prod 下 show-details: never，metrics 不对外暴露
   （或限制到专用管理角色）；健康检查用分组端点只暴露存活探针。
   当前任意已认证学生可读 /actuator/metrics 的 JVM/线程池/HTTP 指标。
2. SecurityConfig.java:59-60：Swagger 放行改为仅 dev/test 生效
   （用 @Profile 或在配置中按 profile 控制 springdoc 开关），
   prod 下 /v3/api-docs 不应匿名可读。
3. WebMvcConfig.java:14-22：CORS 的 allowedOriginPatterns("*") +
   allowCredentials(true) 改为从配置读取白名单来源。
4. SecurityConfig.java:58 的 /api/v1/public/** 放行规则当前无任何控制器对应，
   属未使用的匿名通道，删除或明确保留理由。

【任务 5：认证加固】
1. 登录失败频控：基于 Redis 做账号 + IP 双维度计数，
   超过阈值（如 5 次/15 分钟）临时锁定并返回明确中文提示。
   注意：不要泄露"用户是否存在"——当前登录接口两路失败返回同一文案，这点是正确的，请保持。
2. 注册接口：加频率限制与邮箱验证（或邀请码）；
   注册时的"用户名/邮箱已被占用"回显构成用户枚举，改为统一模糊提示 + 邮件确认。
3. 密码策略：RegisterRequest.java:31-33 的 @Size(min=6) 改为更严格策略
   （建议最小 8 位 + 弱口令黑名单，明确禁止 123456 等）。

【任务 6：日志与输入脱敏】
1. RefreshTokenService.java:88 把 refresh token 明文写入日志，
   改为仅打印前 6 位或 hash。
2. logback-spring.xml:22 的 com.library DEBUG 级别按 profile 区分，
   prod 下不应输出 DEBUG。
3. TraceIdFilter.java:27-38 对外部传入的 traceId 做格式校验
   （仅接受 UUID 正则），否则重新生成，避免日志注入与长度攻击。
4. 审查 application-dev.yml:22 的 show-sql: true 在并发压测时的日志开销，
   建议通过 profile 或开关控制。

【任务 7：越权复核（正面结论，需保持）】
审计确认关键 IDOR 点均已防护（borrow-records 的归还/续借、
reservations 的详情/取消/履约、notifications 的已读、copies 的归属校验、
ai 反馈的 logId 归属），授权 authorities 每次请求从数据库实时加载
（claims 中的 roles 不参与鉴权）。请在修复后补一组越权回归测试，
用 student A 的 token 访问 student B 的资源，断言一律 403/404，防止后续改动破坏该性质。
```

---

# 阶段十四（Stage 10-I）：性能、可观测性与前端健壮性

## 目标

消除已确认的 N+1、无界查询与索引错配，收敛前端易泄漏资源，并让配置可环境化。

## 问题清单

| 编号 | 问题 | 证据 |
| :--- | :--- | :--- |
| M1 | 借阅/预约列表逐行懒加载（每行 3 次查询） | `BorrowRecordResponse.java:110-125`、`BorrowCirculationServiceImpl.java:334,349,394` |
| M2 | 公告广播 `userRepository.findAll()` 无界全表读 + 逐条 INSERT | `NotificationServiceImpl.java:129-144`；DB 实测 4717 用户，`saveAll` 无 batch_size |
| M3 | 模糊检索 `lower(title) LIKE '%..%'` 与 `gin(title gin_trgm_ops)` 索引错配 | `BookServiceImpl.java:162-164,206,211-219` vs `V3:51-53` |
| M4 | 未配置 `hibernate.jdbc.batch_size` / `order_inserts` | 全部 yml |
| M12 | `EnvConfig` 编译期常量，test/prod 分支永不生效 | `env_config.dart:5,10-14` |
| — | `AsyncConfig` queueCapacity=500 先于扩容填满，maxPoolSize=8 实际不可达 | `AsyncConfig.java:30-43` |
| — | 推荐接口每次 GET 都写曝光日志（写事务挂在读接口上），日志表无归档 | `AiRecommendServiceImpl.java:157-188` |
| L4 | 编目页 6 个 `TextEditingController` 从不 dispose | `catalog_manage_screen.dart:263-268,613-616` |
| L5 | 通知筛选缺 `RESERVATION_EXPIRED`；`BORROW_RECORD` 类型点击无跳转但仍显示"点击查看详情" | `notification_center_screen.dart:18-25,255-261` |
| L6 | `AppLogger` 零调用，release 仍 debugPrint | `app_logger.dart:11-20` |
| L7 | 未使用依赖与死代码约 10 处 | `cupertino_icons: ^1.0.8`、`libraryOverviewProvider` 等 |

## 验收标准

1. 打开"我的借阅历史"（20 条），后端日志中的 SQL 条数从 ~60 条降到 ≤3 条。
2. 对一个 5000 用户的库执行一次广播公告，接口响应时间 < 2s，且 `notifications` 批量插入在日志中呈现批量行为（非 5000 条 INSERT）。
3. `EXPLAIN ANALYZE` 验证图书模糊检索命中 `gin_trgm_ops` 索引（修复后不再 Seq Scan）。
4. 用一个不存在的账号密码登录，检查 profile 页面的"阅读画像"仍能正常加载（不因未使用 Provider 报错）。
5. `flutter analyze` 0 warning；`flutter test` 42 用例全绿。

## 修复提示词

```text
【任务】Stage 10-I：性能、可观测性与前端健壮性收口

【任务 1：消除 N+1】
BorrowRecordResponse.java:110-125 每行触发 3 次懒加载（book/bookCopy/user），
调用点 BorrowCirculationServiceImpl.java:334,349,394；
ReservationResponse / ReservationDetailResponse 同类问题
（调用点 ReservationServiceImpl.java:286,303,336）。
请改造成 @EntityGraph 或 JOIN FETCH 查询，或直接返回投影 DTO。
验收：打开"我的借阅历史"（20 条）时，日志中该请求的 SQL 条数从约 60 条降到 ≤3 条。
另外 AiRecommendServiceImpl.java:66-76 逐本 findById（注释声称已避免 N+1 实际没有），
改为 findAllById 批量取。

【任务 2：修复公告广播】
NotificationServiceImpl.java:129 的 userRepository.findAll() 会把全部用户载入内存
（实测 4717 行）再过滤 ACTIVE，随后 saveAll 因未配置 batch_size 逐条 INSERT。
请改为：按状态 + id 游标分页（每批 500）流式处理；
在 yml 补 hibernate.jdbc.batch_size=50~100 与 order_inserts/order_updates；
考虑把广播异步化（复用已有的 notificationExecutor，但先修任务 4 的线程池配置）。

【任务 3：修复索引错配】
BookServiceImpl.java:162-164,206,211-219 使用 cb.lower(root.get("title")) + %keyword%，
而 V3:51-53 建的是原始列的 gin_trgm_ops 索引，导致模糊检索走顺序扫描。
请新增迁移建 lower(title) 的 gin_trgm_ops 表达式索引
（或把查询改为 ILIKE 以利用现有索引，二选一并说明）。
对 author/isbn 的检索条件做同样核查。
验收：EXPLAIN ANALYZE 确认命中索引。

【任务 4：线程池与调度配置】
AsyncConfig.java:30-43 中 queueCapacity=500 会先于扩容填满，
使 maxPoolSize=8 永远不可达（有效并发只有 core=2），
且 CallerRunsPolicy 会把通知入库任务放回请求线程执行并占用数据库连接。
请调整队列容量与拒绝策略（例如 queue=50 + AbortPolicy 并降级记录日志），
并补 spring.task.scheduling.pool.size（当前默认单线程）。

【任务 5：推荐日志治理】
AiRecommendServiceImpl.java:157-188 让每次 GET 推荐都在读接口上写一条曝光日志，
ai_recommendation_logs 无归档/TTL，StatisticsServiceImpl.java:461-466 还对其做全表 count。
请：改为批量/异步写入；为日志表加保留期清理任务（新增定时任务或 Flyway 分区方案）；
优化统计口径（避免每次全表 count，可维护汇总表）。

【任务 6：前端环境可配置化】
env_config.dart:5 的 static const currentEnvironment 使 test/prod 分支成为死配置，
任何 release 构建都会指向 http://localhost:8080/api/v1，
换机器或局域网访问（如用 192.168.x.x 打开前端）时全部请求失败。
改为 String.fromEnvironment('API_BASE_URL', defaultValue: 'http://localhost:8080/api/v1')，
并在启动脚本/构建命令中通过 --dart-define 注入；
同时处理 Flutter Web 下 flutter_secure_storage 仅在 secure context（https 或 localhost）
可用的限制：非 https 访问时 crypto.subtle 不可用会导致读写抛异常，
请在 token_storage 中捕获并给出可读提示（当前异常会在 api_client.dart:31 的
onRequest 中直接抛出，使每个请求失败且无法归类为 DioException）。

【任务 7：前端资源与体验收尾】
1. catalog_manage_screen.dart:263-268 与 :613-616 的 6 个 TextEditingController
   补 dispose（对话框关闭时释放）。
2. notification_center_screen.dart:18-25 的筛选 Tab 补 RESERVATION_EXPIRED
   （后端 NotificationType 有 5 个值，前端只覆盖 4 个）；
   :255-261 的 BORROW_RECORD 类型当前无跳转但仍显示"点击查看详情 >"，
   要么实现跳转，要么按类型隐藏该提示。
3. app_logger.dart 全项目零调用：要么在关键错误路径接入（替代裸 debugPrint），
   要么删除该工具类；release 下不得输出调试日志。
4. 清理未使用依赖（cupertino_icons）与死代码
   （libraryOverviewProvider、categoryCirculationProvider、
   recommendationMetricsProvider、unreadNotificationCountProvider、
   refreshBookInsight、auth_repository.register、auth_repository.refreshToken、
   reservation_repository.getReservationDetail 等无调用点的实现）。
   注意：删除前先确认确实无引用，并保留 getReservationDetail 这类
   未来明确要用的能力（可加注释说明）或一并删除，不要留下半死状态。
```

---

# 阶段十五（Stage 10-J）：文档与答辩材料去伪存真

## 目标

让 README 与 docs/ 的每一处声明都能被代码或实测输出支撑。这一阶段不做完，前面所有修复的成果都会在答辩/评审时被文档失真抵消。

## 问题清单

| 编号 | 问题 | 证据（已核对） |
| :--- | :--- | :--- |
| H24 | 测试数量失真：README 称 161/161 与 38/38，实测后端 **165** 个 `@Test`（36 个测试文件）、前端 **42** 个用例（14 个文件） | `README.md:14,15,186,190,199-200` |
| H25 | 6 张"系统截图"全部是 `via.placeholder.com` 占位图，且该域名已停止服务（渲染为破图） | `README.md:48,49,56,57,64,65` |
| M21 | Flyway 写 V1~V9（实际存在 V10，且 V10 正是并发安全关键修复）；JDK 徽章写 21（`pom.xml:22` 为 17）；「镜像体积 215MB / 缩减 75%」无任何实测依据；目录结构树漏 `scripts/` | `README.md:100,202,221,9,90,210,139,208-240`、`backend/pom.xml:22` |
| M19 | 无 CI，构建错误与测试漂移长期无人发现 | `.github/` 不存在 |
| — | `docs/Stage7-A-部署指南.md:211-224` 手写恢复命令而未引用 `docker/scripts/restore.sh`（后者质量更高、含校验） | 双轨维护 |
| — | `docs/REPAIR-PROMPTS.md`、`docs/FINAL-Deep-Audit.md` 记录了 AI 辅助修复痕迹与自曝问题，与 `Stage8-Final-Risk-Report.md:15-16` 的"原创架构、网络上绝无雷同模板"存在冲突 | 需评估公开范围 |

## 验收标准

1. README 中每一处数字都能给出产生它的命令与输出。
2. 6 张截图替换为真实运行截图（`flutter run -d chrome` 截取首页/AI推荐/检索/预约时间线/AI导读/馆员大盘/批量导入）。
3. `docker compose -f docker-compose.prod.yml build` 在 CI 中作为门禁执行；后端 `mvn verify` 与前端 `flutter analyze && flutter test` 亦然。
4. README 的 Flyway、JDK、目录结构、测试数量四处与实际完全一致。
5. LICENSE 存在（阶段六已完成）或相关声明已删除。

## 修复提示词

```text
【任务】Stage 10-J：文档与答辩材料去伪存真

【任务 1：用实测数据替换所有失真的数字】
已核对的事实：
- 后端 @Test 实际 165 个（grep -rho "@Test[A-Za-z]*" backend/src/test | sort | uniq -c
  → 165 @Test + 1 @TestMethodOrder 噪声），分布 36 个测试文件；
  参数化测试 @ParameterizedTest 为 0 个。README.md:14,186,199 写的是 161/161。
- 前端用例实际 42 个（grep -rhoE "testWidgets\(|\btest\(" frontend/test → 27 + 15），
  14 个测试文件。README.md:15,190,200 写的是 38/38。
- README.md:16,201 的「flutter analyze 0 Issues」无任何存档证据。
请：
  1. 实际执行 mvn clean test 与 flutter test，把真实输出（用例数、通过数、耗时）
     写入 README，并同步修正徽章数字；
  2. 徽章建议改为接入 shields.io 动态 endpoint 或 CI 产物，
     避免后续再次手写漂移；
  3. 无法给出证据支撑的数字（如 flutter analyze 0 Issues）要么执行后据实填写，
     要么删除该徽章。

【任务 2：替换占位截图】
README.md:48,49,56,57,64,65 的 6 张图全部是 via.placeholder.com 的占位图，
且该域名已停止服务，实际渲染为破图。
请用真实运行截图替换（启动后端 + flutter run -d chrome 后用演示账号登录截取）：
首页与 AI 推荐、多维检索与图书详情、预约流转时间线、
AI 智能导读、馆员运营监控看板、Excel 流式批量编目。
图片存放到 docs/screenshots/ 并在 README 中引用相对路径。
如果某功能当前仍不可用（例如阶段九/十一未完成时 AI 导读会 500），
先修好再截图，不要用假图或跳过。

【任务 3：校准技术声明】
1. README.md:100,202,221 的 Flyway「V1~V9」改为 V1~V10（实际存在
   V10__add_active_borrow_unique_constraint.sql，且它正是并发安全的关键修复，
   文档漏掉反而显得实现落后）。
2. README.md:9,90,210 的 JDK 21 与 backend/pom.xml:22 的
   <java.version>17</java.version> 矛盾（编译产物是 Java 17 字节码，
   Dockerfile 用 JDK 21 只是为了 -XX:+ZGenerational）。
   请统一口径：要么把 pom 升到 21，要么在 README 说明"运行时 JDK 21 / 编译目标 17"。
3. README.md:139 的「镜像体积缩减 75% 至 215MB」在构建修复（阶段七）后
   用 docker images 的真实输出替换，或删除该数字。
4. README.md:208-240 的目录结构树补上 scripts/（9 个文件）与根目录 4 个启动脚本，
   并核对每个列出的路径确实存在。
5. 顺带核对 README 第 116-139 行"核心技术亮点"的每条声明是否仍成立。
   审计确认以下声明属实，可保留：确定性锁顺序（Book→BookCopy）、
   多路加权推荐公式（0.4/0.4/0.2/+15.0 与 AiRecommendServiceImpl.java:132,134 一致）、
   FIFO 预约状态机、REQUIRES_NEW 事务解耦、SPA history 回退、gzip 含 wasm、
   多阶段构建 + 非 root 用户。但"多路加权推荐"需注意冷启动分支还有 +20.0 加成
   （AiRecommendServiceImpl.java:95）文档未提，请补一句以免被追问时答不上。

【任务 4：补 CI 门禁】
新增 .github/workflows/ci.yml（当前 .github 目录不存在），至少包含：
  - backend 任务（Linux runner）：JDK 21 + mvn -B verify
    （注意：pom.xml:163-169 的 surefire 硬编码 C:\Temp 在 Linux 上必须已按
    阶段七修复，否则该任务必然失败）
  - frontend 任务：flutter analyze && flutter test
  - docker 任务：docker compose -f docker-compose.prod.yml build
    （这是防止阶段七的两个构建错误复发的唯一手段）
把测试结果接入 README 徽章。

【任务 5：文档与脚本收敛为单一事实来源】
docs/Stage7-A-部署指南.md:211-224 手写了一份恢复命令，
未引用仓库内质量更高的 docker/scripts/restore.sh（后者含参数校验与行数核对）。
请统一指向脚本，删除手写命令，避免双轨维护。

【任务 6：评估过程文档的公开范围（需用户决策）】
docs/ 下的 REPAIR-PROMPTS.md、FINAL-Deep-Audit.md 等文档记录了
AI 辅助修复的痕迹与项目自曝缺陷，与 Stage8-Final-Risk-Report.md:15-16 的
「原创架构、网络上绝无雷同模板」表述存在张力。
请不要自行删除任何文档，而是：
  1. 列出哪些文档属于"过程材料"、哪些属于"交付材料"；
  2. 给出"公开发布 / 仅本地保留 / 移入 .gitignore"三档建议清单；
  3. 交由用户决定。
```

---

## 附录 A：本轮审计已验证为**真实可用**的能力（避免过度否定）

以下宣称经实测或代码核对**成立**，修复时不要误伤：

| 能力 | 证据 |
| :--- | :--- |
| 登录 / 日志刷新 / 用户信息接口 | 实测 `student_demo` 登录返回 `{"code":"SUCCESS",...}` 与完整角色权限清单 |
| 图书列表、多维检索、分类树 | 实测均 200 且返回真实种子数据（666 本书、52 本演示书） |
| 借阅在借/历史、我的预约、未读数 | 实测 student_demo 全部 200 |
| 确定性锁顺序 Book → BookCopy | `BorrowCirculationServiceImpl.java:425,433`，且有 `LockOrderingTest` |
| 多路加权推荐公式 | 与 `AiRecommendServiceImpl.java:132,134` 逐系数一致 |
| FIFO 预约状态机（WAITING→READY→COMPLETED/EXPIRED/CANCELLED） | 枚举与 V6 的 CHECK 完全一致 |
| `REQUIRES_NEW` 事务解耦外部 I/O | `AiInsightTransactionHelper` 存在且经独立 Bean 注入（代理生效） |
| 关键越权点防护 | 借还/续借/预约/通知/AI 反馈/副本归属均有 service 层校验；authorities 每次请求实时查库，claims 不可提权 |
| 后端多阶段构建 + 非 root 用户 + 固定时区 | `docker/backend/Dockerfile:9,26,40-48` |
| prod compose 不对外暴露 DB/Redis 端口 | 仅 Nginx 发布 80/443，dev compose 才暴露 15437/16381 |
| 健康检查与依赖编排 | `depends_on.condition: service_healthy` 配置正确 |
| 枚举一致性（除 notifications 外的 16 个列） | 逐列 DB DISTINCT 核对通过 |

## 附录 B：问题统计

| 严重度 | 数量 | 分布阶段（阶段：项数） |
| :--- | :--- | :--- |
| 🔴 严重 | 13 | 六：3、七：3、八：2、九：2、十：1、十一：1、十三：1 |
| 🟡 高 | 21 | 六：1、七：3、八：4、九：3、十：2、十一：2、十二：1、十三：3、十五：2 |
| 🟡 中 | 25 | 七：4、八：1、九：3、十：1、十一：2、十二：4、十三：4、十四：4、十五：2 |
| 🟡 低 | 7 | 十三：3、十四：4 |
| **合计** | **66** | 六：4、七：10、八：7、九：8、十：4、十一：5、十二：5、十三：10、十四：9、十五：4 |

**建议执行顺序**：六 → 七 → 八 → 九 → 十 → 十一 → 十二 → 十三 → 十四 → 十五。

其中 **阶段六、七、八、九 属于"不做就不能对外演示"** 的阻塞项：
- 阶段六不修 → 系统可被任何人伪造管理员身份；
- 阶段七不修 → README 首推的一键部署不可用，Excel 导入功能形同虚设；
- 阶段八不修 → token 一过期整个应用不可用；
- 阶段九不修 → 首页 AI 推荐与阅读画像永远是错误态/空态。

---

## 附录 C：部署前复审追加的三个阶段（Stage 10-K / 10-L / 10-M）

阶段六~十五全部完成后，在"部署到服务器"前的最后一次全项目复审中又发现 6 个问题。
它们不属于功能缺陷（本地开发全部正常），而是**只在真实部署路径上才会暴露**的交付链缺口，
因此单独立为三个阶段并已修复完成。

## 阶段十六（Stage 10-K）：前端产物接口地址注入

| 编号 | 问题 | 证据 |
| :--- | :--- | :--- |
| K1 | 前端镜像构建**完全没有注入接口地址**，产物退化为源码占位域名 | `docker/frontend/Dockerfile` 只有 `ARG FLUTTER_VERSION`；`env_config.dart` prod 分支为 `https://library.campus.edu.cn/api/v1` |
| K2 | `EnvConfig.currentEnvironment` 是写死的 `dev` 常量，**prod 分支永不生效** | 生产构建即使注入了地址，环境分支也走不到（与阶段十-I 的 H12 同源） |

**修复**：Dockerfile 增加 `ARG APP_ENV=prod` 与 `ARG API_BASE_URL=`（默认留空），
两者都透传为 `--dart-define`；`EnvConfig` 支持构建期注入环境标识，
并在 prod 下**从页面 origin 运行时推导**同源绝对地址 `https://<域名>/api/v1`
（换域名无需重新构建，且得到绝对地址以避开"Dio 在非 Web 平台拒绝相对 baseUrl"的平台差异）。

**实测**：以 `--dart-define=APP_ENV=prod` 构建产物并用纯静态服务托管（模拟网关同源），
浏览器登录请求命中 `POST /api/v1/auth/login`，目标为页面自身 origin —— 证明推导生效。

## 阶段十七（Stage 10-L）：首次部署可用性

| 编号 | 问题 | 证据 |
| :--- | :--- | :--- |
| L1 | 全新生产库**可用账号数为 0**：V11 正确禁用了演示账号并作废其口令哈希，但没有任何后续引导手段 | 把 V1~V17 应用到全新库后实测 `可用登录账号数 = 0` |
| L2 | 部署指南 8 个章节中没有"创建首个管理员"一节；注册接口只发 STUDENT 角色，也无启动期初始化器 | 指南目录；`AuthServiceImpl.register` 只授予 STUDENT |

**修复**：新增 `BootstrapAdminInitializer`（`ApplicationRunner`），由 `BOOTSTRAP_ADMIN_*` 环境变量驱动：
未配置时彻底 no-op、口令强度不足（<8 位或纯数字/纯字母）时拒绝且不留下半成品账号、
同名账号已存在时只跳过不覆盖不静默提权、日志绝不输出口令。部署指南补 5.1 节，
`.env.example` 补对应开关。5 个自动化用例守护上述边界。

## 阶段十八（Stage 10-M）：发布产物与配置收尾

| 编号 | 问题 | 证据 |
| :--- | :--- | :--- |
| M1 | 工具缓存目录 `.mimosa/` 位于 `src/main/resources/db/migration/` 下，被 Maven 原样复制进 `target/classes` 并打进生产 jar | 实测 `target/classes/db/migration/.mimosa` 存在 |
| M2 | `application-prod.yml` 保留数据库口令弱默认值 `${DB_PASSWORD:library_password}` | 与阶段十-A 对 JWT 的处置不一致：单独跑 jar 时会静默使用源码内已知口令 |
| M3 | `APP_CORS_ALLOWED_ORIGINS`、`DEMO_DATA_ENABLED` 等未接入 compose 与 `.env.example` | 跨域部署时会静默被 CORS 拦下；答辩演示环境不知道有演示数据开关 |

**修复**：pom 增加 `resources` 排除规则；prod 数据源口令去掉默认值（缺失即启动失败）；
compose 接线 CORS 白名单与引导变量；`.env.example` 补全 4 组开关并逐项注释。

**实测**：`mvn clean package` 后 `target/classes` 与 jar 内均无 `.mimosa`；
prod profile 不提供 `DB_PASSWORD` 时启动报 `password authentication failed`（不会静默用弱口令），
显式提供正确口令后 Flyway 校验通过、应用正常启动。

**回归**：后端 `235 / 235 PASS`（+6 用例），前端 `83 / 83 PASS`（+8 用例），`flutter analyze` 0 问题。

---

## 附录 D：演示库被测试污染（Stage 10-S / 10-T）

阶段十六~十八之后，演示环境出现"界面显示可借、点借阅却报无可借副本"以及馆员归还撞
`books_check` 约束的现象。追查根因不在业务代码，而在**测试与演示共用同一个数据库**。
两个阶段已修复完成。

## 阶段十九（Stage 10-S）：测试与演示库隔离

| 编号 | 问题 | 证据 |
| :--- | :--- | :--- |
| S1 | 集成测试直连开发/演示库 `library_system`，夹具数据逐年沉积 | 实测演示库书目 1989 本，其中夹具书目 1937 本（占 97%），演示界面能搜到"测试分类"下的假书 |
| S2 | 夹具只写 `books` 的冗余计数，不写 `book_copies` 实况，两者长期不一致 | 演示库出现 `available_copies` 与实际副本行数不符；归还路径 `available_copies + 1` 越界触发 `books_check`（表现为"数据状态冲突"，见 10-R 的收敛修复） |
| S3 | 每次 `mvn clean test` 都会让污染继续增长，且没有任何机制阻止 | 一次全量测试后演示库书目 +100 以上，测试库与演示库无从区分 |

**修复**：`application-test.yml` 的 URL 改为 `${TEST_DB_NAME:library_system_test}`，
测试库名可覆盖；`scripts/run-backend-test.ps1` 在运行前检测 `campus-library-postgres`
容器并在库不存在时自动 `CREATE DATABASE`（PostgreSQL 的 JDBC 驱动连不上不存在的库，
必须由外部先建好），容器不可用时打印手工命令而不是静默继续；
CI 的 postgres service 容器改为以 `library_system_test` 为库名，与本地约定一致。

**实测（隔离有效性）**：全量跑一次测试，前后对比演示库 ——
书目 1989 → 1989、用户 16130 → 16130、借阅流水 196 → 196、计数漂移 0，**完全未被触碰**；
夹具数据全部落进测试库（书目 150 / 用户 836，Flyway 自动迁移 V1~V17）。
测试库可随时 `DROP` 重建，下次运行自动重迁移。

## 阶段二十（Stage 10-T）：被静默跳过的注册开关关闭态用例

| 编号 | 问题 | 证据 |
| :--- | :--- | :--- |
| T1 | 注册开关"关闭态 403"用例写成 `static class` 内部类，被 surefire 静默跳过 | `@Test` 注解总数 248，surefire 报告总数 247，差的正好是这一条；JUnit 5 只把 `@Nested`（非静态内部类）纳入发现，`static` 内部类不在其中 |
| T2 | 套件缺少"注解数 = 执行数"的校验，漏跑无法被发现 | 该缺陷在多次全量回归中均未被察觉，直到逐个类比对注解数与报告数才暴露 |

**修复**：拆为顶层类 `PublicRegistrationDisabledTest`（同样以属性覆盖开启独立上下文）。

**实测**：`@Test` 注解数与 surefire `Tests run` 总数现已精确相等 ——
248 处注解 / 61 个测试类 / 248 条实际执行，逐类比对零偏差；
`mvn clean test` → `Tests run: 248, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`。
README 补入该校准方法（注解数与执行数不等即说明有用例没跑）。

**回归**：后端 `248 / 248 PASS`（+1 用例 +1 条补跑），前端 `96 / 96 PASS`，`flutter analyze` 0 问题。

