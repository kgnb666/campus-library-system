# 《校园图书借阅系统》最终深度审计问题：分阶段修复任务提示词库

> **说明**：本提示词库基于 `docs/FINAL-Deep-Audit.md` 审计报告提炼，按**软件工程高内聚、低耦合、按风险等级推进**的原则拆分为 5 个独立执行阶段。  
> 每个阶段的提示词均包含：**阶段目标、严格限制、涉及文件、详细实现指导、测试验证命令及验收门禁（Gate）**，可直接复制作为下一轮对话的指令输入。

---

## 阶段规划概览

- **Stage 9-A：核心并发锁拓扑统一与数据库物理防穿透加固（P1 / P2 核心安全）**
- **Stage 9-B：前端主页聚合体验重塑与 401 并发刷新风暴治理（P1 展示与稳定性）**
- **Stage 9-C：生产运维 DevOps 修复与系统安全基线收敛（P2 生产交付）**
- **Stage 9-D：领域事件异步解耦闭环与 AI 提供者标准化（P1 架构一致性）**
- **Stage 9-E：文档学术真实性对齐与答辩/简历去伪存真（P1 文档与答辩准备）**

---

# 阶段一提示词：Stage 9-A 核心并发锁拓扑统一与数据库物理防穿透加固

```markdown
# 任务目标：Stage 9-A 核心并发锁拓扑统一与数据库物理防穿透加固

你现在作为【高并发系统专家】与【PostgreSQL DBA】。
根据 Final Deep Audit 审计结果，解决流通模块的核心并发与数据一致性隐患。

==================================================
一、严格限制
==================================================
1. 不修改前端代码，不调整 Controller 接口契约；
2. 不引入外部分布式锁或新的依赖库；
3. 修改后必须保证现有 161 项后端测试继续 100% PASS，并补充新增死锁防御测试。

==================================================
二、具体实施任务
==================================================

### 任务 1：统一预约履约加锁拓扑，彻底消除 `fulfillReservation` 与 `returnBook` 循环等待死锁
- **涉及文件**：`backend/src/main/java/com/library/service/impl/ReservationServiceImpl.java`
- **问题分析**：
  - `returnBook` 的加锁顺序为：`Book` -> `BookCopy` -> 同步事件触发 `Reservation`（`findEarliestWaitingForUpdate`）；
  - 而原 `fulfillReservation` 先执行 `reservationRepository.findByIdForUpdate(reservationId)`，随后调用 `borrowBook` 试图锁定 `Book`；
  - 二者并发时构成 `Reservation <-> Book` 循环等待死锁（PostgreSQL 40P01）。
- **重构要求**：
  - 在 `fulfillReservation(reservationId, currentUser)` 中：
    1. 先通过无排他锁只读查询获取该预约单对应的 `bookId` 并进行基础校验；
    2. 严格先行调用 `bookRepository.findByIdForUpdate(bookId)` 锁定父级书目 `Book`；
    3. 在持有 `Book` 锁的前提下，再锁定 `Reservation`（`findByIdForUpdate`）；
    4. 执行后续的 `borrowBook` 与状态跃迁；
  - 确保全系统所有涉及 `Book` 与 `Reservation` 的路径，加锁顺序绝对统一为：`Book -> Reservation`。

### 任务 2：编写履约与归还交叉并发压力测试
- **新增测试文件**：`backend/src/test/java/com/library/ReservationFulfillReturnDeadlockTest.java`
- **测试要求**：
  - 启动真实 SpringBootTest（Profile: test，连接真实 PostgreSQL 17）；
  - 构造初始数据：创建 1 本图书、2 本物理副本、2 个已就绪的预约单（READY）；
  - 启动 30 个线程并发执行 `fulfillReservation`，同时启动 30 个线程并发执行 `returnBook` 归还同书的物理单册；
  - 断言：死锁计数器必须为 0 (`isDeadlockException == false`)，全部事务正常完成或抛出受控业务异常，最终库存守恒。

### 任务 3：Flyway V10 增设 `borrow_records` 单册在借物理排他约束
- **新增迁移脚本**：`backend/src/main/resources/db/migration/V10__add_active_borrow_unique_constraint.sql`
- **SQL 要求**：
  ```sql
  -- 增设部分唯一索引，在数据库底层彻底锁死“同一物理副本被多次在借”的物理风险
  CREATE UNIQUE INDEX IF NOT EXISTS uk_borrow_records_active_copy 
      ON borrow_records (copy_id) 
      WHERE status IN ('BORROWING', 'OVERDUE');
  ```

### 任务 4：修复 Excel 导入单册条形码毫秒取模碰撞风险
- **涉及文件**：`backend/src/main/java/com/library/service/excel/BookImportListener.java`
- **重构要求**：
  - 将 `System.currentTimeMillis() % 1000000` 生成条形码逻辑重构；
  - 采用 `String.format("BAR-%d-%s-%02d", book.getId(), UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(), i + 1)`，彻底杜绝快速批量导入时的条码主键冲突。

==================================================
三、验证门禁
==================================================
1. 执行数据库迁移；
2. 运行全量后端测试：`mvn clean test`，要求 162+ 项测试全部通过（0 failure, 0 error）；
3. 检查 git 变更并提交：`fix(concurrency): unify reservation-circulation lock ordering and add db unique constraint`。
```

---

# 阶段二提示词：Stage 9-B 前端主页体验重塑与 401 并发刷新风暴治理

```markdown
# 任务目标：Stage 9-B 前端主页体验重塑与 401 并发刷新风暴治理

你现在作为【高级 Flutter 架构师】。
根据 Final Deep Audit 审计结果，解决主界面展示严重缺陷与网络拦截并发隐患。

==================================================
一、严格限制
==================================================
1. 不修改后端接口；
2. 保持 Material Design 3 视觉设计规范与 Riverpod 状态流；
3. `flutter analyze` 必须维持 0 warning 0 error，前端测试全部通过。

==================================================
二、具体实施任务
==================================================

### 任务 1：重塑主导航 Tab 0，消除“首页概览 (Stage 1 就绪)”空白占位符
- **涉及文件**：
  - `frontend/lib/core/router/app_router.dart`
  - 新建 `frontend/lib/features/home/presentation/home_screen.dart`
- **实现要求**：
  - 编写现代化的 `HomeScreen` 替换原 `Center(child: Text('首页概览 (Stage 1 就绪)'))`；
  - 主页内容聚合四大核心区块：
    1. **顶部欢迎与快速检索栏**：包含馆藏搜索快捷输入框，支持一键跳转到图书列表并带入关键词；
    2. **流通快捷操作金刚区（Quick Action Grid）**：提供“AI 智能推荐”、“我的借阅”、“我的预约”、“扫码/条码查书”快捷入口；
    3. **AI 智能推荐精选轮播/横向卡片（Featured AI Picks）**：复用 `aiRecommendProvider`，为当前读者展示 Top 3 推荐好书及实时在架状态；
    4. **全馆热门借阅榜单预览（Popular Books Top 5）**：展示馆内借阅热度最高的图书封面、题名与作者；
  - 完善加载骨架与下拉刷新（`RefreshIndicator`）。

### 任务 2：治理 `api_client.dart` 401 令牌并发刷新风暴（Thundering Herd）
- **涉及文件**：`frontend/lib/core/network/api_client.dart`
- **问题分析**：
  - 页面并发请求时，若 Access Token 失效，多个请求同时触发 401 并发进入 `onError`，各自创建独立的 `tokenDio` 重复刷新，极易造成刷新竞争与凭证被冲刷清除。
- **重构要求**：
  - 在 `api_client.dart` 内部维护一个单例刷新锁与挂起等待队列：
    ```dart
    bool _isRefreshing = false;
    Completer<String?>? _refreshCompleter;
    ```
  - 当第 1 个 401 到达时，`_isRefreshing = true`，创建 `_refreshCompleter`，发起刷新；
  - 当后续的第 2~N 个并发 401 到达时，不再重复调用 `/auth/refresh`，而是直接 `await _refreshCompleter!.future` 等待首个刷新任务返回新 Token；
  - 首个刷新成功后，向等待队列广播新 Token，并唤醒所有被挂起的请求使用新 Token 重试执行；
  - 若刷新彻底失败，清空本地凭据并重定向登录页。

### 任务 3：补充前端 Home 页面与 401 并发刷新单元测试
- **新增/更新测试**：`frontend/test/home_screen_test.dart`
- **断言要求**：验证 Tab 0 正确渲染欢迎词、推荐书单与快捷操作，点击跳转路由正确。

==================================================
三、验证门禁
==================================================
1. 执行静态语法扫描：`flutter analyze`，确保 0 issues；
2. 执行前端自动化测试：`flutter test`，全部测试用例 PASS；
3. 检查 git 变更并提交：`feat(frontend): revamp home screen and eliminate 401 refresh token storm`。
```

---

# 阶段三提示词：Stage 9-C 生产运维 DevOps 修复与系统安全基线收敛

```markdown
# 任务目标：Stage 9-C 生产运维 DevOps 修复与系统安全基线收敛

你现在作为【DevOps 工程师】与【网络安全审计专家】。
根据 Final Deep Audit 审计结果，修复生产部署与安全配置缺陷。

==================================================
一、具体实施任务
==================================================

### 任务 1：修复 `backup.sh` 伪终端 TTY 管道污染缺陷并提供恢复测试脚本
- **修改文件**：`docker/scripts/backup.sh`
- **重构要求**：
  - 将 line 25 的 `docker exec -t "${CONTAINER_NAME}" pg_dump ...` 修改为 `docker exec -i "${CONTAINER_NAME}" pg_dump ...`；
  - 彻底去除 `-t` 参数，避免向 SQL 压缩管道注入 `\r\n` 与控制字符导致备份损坏；
- **新建配套脚本**：`docker/scripts/restore.sh`
  - 支持传入指定 `.sql.gz` 备份文件路径；
  - 自动解压并通过 `docker exec -i "${CONTAINER_NAME}" psql -U "${DB_USER}" -d "${DB_NAME}"` 执行原子恢复；
  - 校验恢复前后数据表行数完整性。

### 任务 2：修复 `docker-compose.prod.yml` 环境变量名映射偏差
- **修改文件**：`docker-compose.prod.yml`
- **问题分析**：
  - 后端代码使用 `@Value("${ai.deepseek.api-key:}")`；
  - Spring Boot 放宽绑定（Relaxed Binding）要求环境变量名为 `AI_DEEPSEEK_API_KEY`，但当前 Compose 文件中写成了 `AI_API_KEY`，导致生产注入失效。
- **重构要求**：
  - 将 backend 服务的环境变量规范化对齐：
    ```yaml
    AI_DEEPSEEK_API_KEY: ${AI_DEEPSEEK_API_KEY:-${AI_API_KEY:-mock-ai-api-key}}
    AI_DEEPSEEK_BASE_URL: ${AI_DEEPSEEK_BASE_URL:-${AI_BASE_URL:-https://api.deepseek.com/v1}}
    AI_DEEPSEEK_MODEL: ${AI_DEEPSEEK_MODEL:-${AI_MODEL:-deepseek-chat}}
    ```
  - 同步更新 `.env.example` 模版说明。

### 任务 3：收敛 Spring Security `/actuator/**` 越权访问
- **修改文件**：`backend/src/main/java/com/library/security/SecurityConfig.java`
- **重构要求**：
  - 将 `.requestMatchers("/actuator/**", "/error").permitAll()`
  - 精确收敛为：`.requestMatchers("/actuator/health", "/actuator/info", "/error").permitAll()`；
  - 其余 Actuator 敏感端点（如 `/actuator/metrics`）必须要求具有 `ADMIN` 角色鉴权，杜绝生产监控未授权暴露。

### 任务 4：安全隔离 `UserTestController`
- **修改文件**：`backend/src/main/java/com/library/controller/UserTestController.java`
- **重构要求**：
  - 增加 `@org.springframework.context.annotation.Profile({"dev", "test"})` 注解，确保在生产环境（`SPRING_PROFILES_ACTIVE=prod`）下该测试端点自动禁用。

==================================================
二、验证门禁
==================================================
1. 运行 `mvn test` 验证安全策略调整未破坏现有集成测试；
2. 执行 `docker compose -f docker-compose.prod.yml config` 验证 Compose 语法正确；
3. 检查 git 变更并提交：`fix(devops): clean tty in backup, fix env bindings, and tighten security baseline`。
```

---

# 阶段四提示词：Stage 9-D 领域事件异步解耦闭环与 AI 提供者标准化

```markdown
# 任务目标：Stage 9-D 领域事件异步解耦闭环与 AI 提供者标准化

你现在作为【Spring Boot 后端架构师】。
根据 Final Deep Audit 审计结果，解决代码实现与架构设计不一致的问题，使实现真正达到论文所描述的工业水准。

==================================================
一、具体实施任务
==================================================

### 任务 1：真正落地 `@TransactionalEventListener(AFTER_COMMIT)` 与异步线程池解耦
- **涉及文件**：
  - `backend/src/main/java/com/library/config/AsyncConfig.java`（新建或配置线程池）
  - `backend/src/main/java/com/library/event/listener/NotificationEventListener.java`
- **重构要求**：
  - 新建/配置异步线程池 `notificationExecutor`（核心线程 2，最大线程 8，队列容量 500）；
  - 将 `NotificationEventListener` 中的 `@EventListener` 重构成真正的事务提交后监听：
    ```java
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookBorrowed(BookBorrowedEvent event) { ... }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationReady(ReservationReadyEvent event) { ... }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationExpired(ReservationExpiredEvent event) { ... }
    ```
  - 这样确保：**主事务完全提交后才触发异步通知入库**，主事务回滚绝不误发通知，且通知写入绝不霸占主借还事务物理连接。

### 任务 2：标准化 `DeepSeekAiProvider`（支持真实 HTTP 远程调用与离线 Mock 双模平滑切换）
- **涉及文件**：`backend/src/main/java/com/library/service/ai/DeepSeekAiProvider.java`
- **重构要求**：
  - 依托 Spring Boot 自带的 `RestClient` 或 `RestTemplate`（配置 5 秒严格超时）；
  - 当配置了真实有效密钥（`apiKey != null && !apiKey.startsWith("mock")`）时，真正向 `https://api.deepseek.com/v1/chat/completions` 发起 POST 请求；
  - 构造标准的 System Prompt（以图书书名、作者、分类、ISBN、简介为事实依据）；
  - 当外部调用超时、限流（429）或未配置密钥时，优雅捕获异常并平滑切换调用 `RuleBasedMockAiProvider`；
  - 确保有网时能演示真实大模型，无网时 100% 具备本地规则兜底，杜绝“假代码”。

==================================================
二、验证门禁
==================================================
1. 运行 `mvn clean test`，验证通知事件与 AI 模块测试全部通过；
2. 检查 git 变更并提交：`feat(ai-event): enable true after-commit async notification and standard deepseek http client`。
```

---

# 阶段五提示词：Stage 9-E 文档学术真实性对齐与答辩/简历去伪存真

```markdown
# 任务目标：Stage 9-E 文档学术真实性对齐与答辩/简历去伪存真

你现在作为【毕业设计评审专家】与【大厂技术面试官】。
根据 Final Deep Audit 审计结果，全面核对并修正所有文档材料，杜绝任何“代码没做但文档宣称做了”的学术造假风险。

==================================================
一、具体修正任务
==================================================

### 任务 1：校准推荐算法数学公式与算法描述（代码与文档 100% 对齐）
- **修改文件**：
  - `README.md`
  - `docs/Stage7-B-论文材料规划.md`
  - `docs/Stage7-B-技术亮点总结.md`
  - `docs/Stage8-Resume-Project.md`
- **修正要求**：
  - 将原虚假公式 `0.35 Content + 0.35 CF + 0.20 Pop + 0.10 Stock`，全面修正为代码真实落地的加权公式：
    $$\text{Score}(u, i) = 0.4 \cdot S_{\text{content}} + 0.4 \cdot S_{\text{behavior}} + 0.2 \cdot S_{\text{pop}} + S_{\text{stock}}$$
    （其中在架可借时 $S_{\text{stock}} = +15.0$）；
  - 删除“计算全校读者余弦相似度矩阵的协同过滤”的虚假描述；
  - 如实、专业地表述为：“基于读者历史分类/作者偏好与在架库存感知的多路加权启发式推荐算法”。

### 任务 2：客观测度与规范技术表述，去除绝对化夸大
- **修改文件**：全套 Stage 7-B、Stage 8 与 README 文档
- **修正要求**：
  - 将“全系统绝对 0 死锁”调整为更具科学说服力的严谨表述：“经过全链路加锁拓扑审计与 DAG 有向无环偏序约束，根除了借还与预约主干路径的循环等待死锁”；
  - 将“Grounded-RAG 真实大模型”表述精准化：“具备结构化元数据约束的智能导读生成引擎（支持云端 DeepSeek LLM 与本地规则引擎双模容灾切换）”；
  - 将“Redis 分布式多级缓存与原子计数”如实表述为：“Redis 用于会话刷新凭据托管与防刷频控，核心库存依托 PostgreSQL 行级锁保障强一致性”；
  - 将“生产级高可用集群”客观修正为：“生产级容器化微服务编排与自动化冷备交付体系”。

### 任务 3：优化面试话术与答辩防御指引
- **修改文件**：`docs/Stage8-Resume-Project.md` 与 `docs/Stage8-Final-Risk-Report.md`
- **修正要求**：
  - 针对面试官深挖“为什么不直接做协同过滤”、“为什么履约借阅要先锁 Book 后锁 Reservation”提供精准无懈可击的技术回答；
  - 完善答辩老师对于算法与离线可用性提问的应对策略。

==================================================
二、验证门禁
==================================================
1. 全局比对文档中的公式、常量、类名与真实代码一致；
2. 检查 git 变更并提交：`docs: align thesis documentation and resume with authentic codebase implementation`。
```
