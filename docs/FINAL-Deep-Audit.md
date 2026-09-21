# 《校园图书借阅系统》全项目终极深度审计报告 (FINAL Deep Audit)

> **审计执行日期**：2026-09-18  
> **审计专家组**：资深 Java/Spring Boot 架构师、PostgreSQL 数据库专家、高并发与分布式系统专家、Flutter 高级工程师、AI 应用架构师、DevOps/Docker 工程师、网络安全审计工程师、本科毕业设计终审专家、Java 后端技术面试官  
> **审计原则**：客观求真、保持怀疑、证据确凿、拒绝夸大、杜绝形式主义、严格区分“代码实测”与“文档宣传”

---

# 一、审计总览与真实架构地图

### 1.1 审计范围与审查资产
本次终极审计覆盖仓库内全部资产（代码、数据模型、测试套件、部署拓扑与文档），进行了全链路只读静态审查与交叉验证：
- **后端工程**：Spring Boot 3.3.4 + JDK 21，包含 11 个 Controller、12 个 Service、15 个 Repository、14 个 Entity、6 个 Scheduler/Listener、10 个 Security 组件；
- **前端工程**：Flutter 3.47+ / Dart 3.13+，包含 7 个 Feature 模块、13 个核心 Screen、15 个 Provider、Dio 拦截器与 GoRouter 守卫；
- **数据库与持久层**：PostgreSQL 17.11，Flyway V1~V9 全版本迁移脚本，Redis 8.0；
- **部署套件**：`docker-compose.prod.yml`、`docker/backend/Dockerfile`、`docker/frontend/Dockerfile`、`docker/nginx/nginx.conf`、`docker/scripts/backup.sh`；
- **测试工程**：后端 161 项 JUnit 5 测试、前端 38 项 Widget/Unit 测试；
- **文档体系**：Stage 0~8 全部过程报告、README、论文材料规划、技术亮点与答辩脚本。

### 1.2 项目真实架构地图 vs 宣传架构对比

```
【文档/宣传中的概念架构】                         【代码中的真实落地架构】
┌───────────────────────────────┐               ┌───────────────────────────────┐
│ Flutter Web / App (MD3)       │               │ Flutter Web / App (MD3)       │
│ • 首页集成 AI 智能推荐大盘    │               │ • 首页 Tab 0: 静态文字占位符  │
│ • 响应式状态与并发 401 队列   │               │ • 并发 401 无锁引发刷新风暴  │
└──────────────┬────────────────┘               └──────────────┬────────────────┘
               │ HTTP / RESTful                                │ HTTP / RESTful
┌──────────────▼────────────────┐               ┌──────────────▼────────────────┐
│ Nginx 网关 + Spring Boot 3    │               │ Nginx 网关 + Spring Boot 3    │
│ • 借还与预约“全局绝对 0 死锁” │               │ • 借还内部无死锁，但履约 vs 还│
│ • 外部 DeepSeek 5s 事务解耦   │               │   书存在 Reservation-Book 反序│
│ • Grounded-RAG 真实大模型调用 │               │ • DeepSeek 实际代理给本地 Mock│
│ • @TransactionalEventListener │               │ • 无任何外部 HTTP 大模型请求  │
│   + @Async 异步通知线程池     │               │ • 同步 @EventListener 事务内调│
│ • Redis 多级缓存与分布式计数  │               │ • Redis 仅存 RefreshToken!    │
└──────────────┬────────────────┘               └──────────────┬────────────────┘
               │ JPA / SQL                                     │ JPA / SQL
┌──────────────▼────────────────┐               ┌──────────────▼────────────────┐
│ PostgreSQL 17 + Redis 8       │               │ PostgreSQL 17 + Redis 8       │
│ • 完善的数据库级排他约束      │               │ • 预约有唯一约束，借阅无唯一  │
│ • 每日全自动冷备与灾备恢复    │               │ • backup.sh 存在 TTY 管道风险 │
└───────────────────────────────┘               └───────────────────────────────┘
```

---

# 二、设计承诺 vs 实际实现对照表（逐条穿透）

| 设计与文档承诺 | 涉及文档章节 | 代码实际现状 | 偏差性质与证据 |
| :--- | :--- | :--- | :--- |
| **“全局确定性锁拓扑彻底根除死锁”** | Stage 6-A / 7-B / 8 / README | 借书与还书统一为 `Book -> BookCopy`，但**预约履约 (`fulfillReservation`) 锁顺序为 `Reservation -> Book`，而还书 (`returnBook`) 锁顺序为 `Book -> Reservation`** | ⚠️ **存在潜在锁顺序反转与死锁窗口**（详见第四节分析） |
| **“Grounded-RAG 架构防外部大模型幻觉”** | Stage 5 / 7-B / README | `DeepSeekAiProvider.java` 内部直接调用 `fallbackProvider.generateInsight(book)`，**未发起任何外部 HTTP 请求**，本质为基于标题和分类的本地静态字符串拼接 | ⚠️ **过度包装**：实际为模板规则引擎，非真实 RAG |
| **“四维混合推荐与协同过滤矩阵计算”** | Stage 5 / 7-B / README | `AiRecommendServiceImpl.java` 采用 `0.4*Content + 0.4*Behavior + 0.2*Pop + 15`，**行为分仅判断是否命中该分类 (+30分)**，无任何用户/物品协同过滤矩阵或余弦相似度计算 | ⚠️ **数学模型与代码不符**：文档称 0.35/0.35/0.20/0.10，代码为 0.4/0.4/0.2+15 |
| **“@TransactionalEventListener + @Async 异步通知解耦”** | Stage 7-B 论文规划 2.3 节 | `NotificationEventListener.java` 标注的是普通的 `@EventListener`，**无 `@Async`，无 `@TransactionalEventListener(phase = AFTER_COMMIT)`** | ⚠️ **实现与论文描述直接冲突**：仍处于调用者线程同步阻塞执行 |
| **“Redis 内存缓存与分布式原子计数双驱”** | Stage 1-B / 7-B / README | 全局检索 `RedisTemplate`，**全系统仅在 `RefreshTokenService` 中用于缓存 RefreshToken**，图书、库存、AI 导读、统计均未走 Redis 缓存 | ⚠️ **技术过度宣传**：Redis 未起到二级业务缓存或分布式锁作用 |
| **“EasyExcel SAX 模式每 100 条分批独立事务提交”** | Stage 6-B / 7-B 论文规划 2.4 节 | `BookImportListener.java` 的 `BATCH_SIZE = 50`，且 `saveBuffer()` 内部**对每一条记录开启一个独立的单行事务** (`transactionTemplate.executeWithoutResult`) | ⚠️ **并非 100 条批量事务**：实际为逐行单事务提交 |
| **“首页集成 AI 推荐与多维动态大盘”** | README / 答辩脚本 | `app_router.dart` 中 Tab 0 主页直接为 `Center(child: Text('首页概览 (Stage 1 就绪)'))`，AI 推荐位于 `/ai/recommendations` 二级路由 | ⚠️ **前端主入口 UX 缺失**：第一眼看到的首页是半成品占位页 |

---

# 三、并发与锁拓扑专项深度审计（核心发现）

### 3.1 数据库锁依赖拓扑全景图

系统涉及图书流通与资产变更的核心事务路径梳理如下：

```
路径 1: borrowBook(bookId)
  Lock 1: Book (bookId) [SELECT ... FOR UPDATE]
  Lock 2: BookCopy (copyId) [SELECT ... FOR UPDATE]
  Write 3: INSERT INTO borrow_records

路径 2: returnBook(recordId)
  Lock 1: BorrowRecord (recordId) [SELECT ... FOR UPDATE]
  Lock 2: Book (bookId) [SELECT ... FOR UPDATE]
  Lock 3: BookCopy (copyId) [SELECT ... FOR UPDATE]
  Write 4: UPDATE borrow_records
  Trigger: BookReturnedEvent (同步发布)
    └─ Lock 5: Reservation (waitingList) [SELECT ... FOR UPDATE]

路径 3: fulfillReservation(reservationId)
  Lock 1: Reservation (reservationId) [SELECT ... FOR UPDATE]  <-- 💥 先锁 Reservation
  Call: borrowBook(bookId)
    └─ Lock 2: Book (bookId) [SELECT ... FOR UPDATE]           <-- 💥 后锁 Book
    └─ Lock 3: BookCopy (copyId) [SELECT ... FOR UPDATE]
  Write 4: UPDATE reservations

路径 4: createReservation(bookId)
  Lock 1: Book (bookId) [SELECT ... FOR UPDATE]
  Write 2: INSERT INTO reservations

路径 5: cancelReservation(reservationId)
  Lock 1: Reservation (reservationId) [SELECT ... FOR UPDATE]
  If READY:
    └─ Lock 2: Reservation (nextWaiting) [SELECT ... FOR UPDATE]

路径 6: scanAndExpireReservations (Scheduler)
  Lock 1: Reservation (readyList) [SELECT ... FOR UPDATE]
  Lock 2: Reservation (nextWaiting) [SELECT ... FOR UPDATE]
```

### 3.2 真实死锁环路发现：`fulfillReservation` vs `returnBook`

#### 【证据链分析】
考察同时涉及 `Book` 与 `Reservation` 的并发交叉执行场景：
- **线程 A 执行 `fulfillReservation(res_1)`**（读者到馆履约借书，该预约单对应图书 101）：
  - 步骤 1：在 `ReservationServiceImpl.java:158` 获取 `Reservation(res_1)` 的行级排他锁；
  - 步骤 2：在 `ReservationServiceImpl.java:185` 调用 `borrowCirculationService.borrowBook(...)`；
  - 步骤 3：在 `BorrowCirculationServiceImpl.java:116` 试图获取 `Book(101)` 的行级排他锁，**进入等待状态**。
- **线程 B 执行 `returnBook(rec_2)`**（另一名读者归还图书 101 的物理单册）：
  - 步骤 1：获取 `BorrowRecord(rec_2)` 锁；
  - 步骤 2：在 `BorrowCirculationServiceImpl.java:238` 获取 `Book(101)` 的行级排他锁（成功）；
  - 步骤 3：获取 `BookCopy` 锁；
  - 步骤 4：在同一事务中通过 `eventPublisher.publishEvent` 同步触发 `onBookReturnedEvent`；
  - 步骤 5：在 `ReservationServiceImpl.java:387` 执行 `findEarliestWaitingForUpdate(101)`，试图获取图书 101 关联的预约行排他锁。

#### 【死锁形成条件】
$$\text{Thread A}: \text{Hold}(\text{Reservation}) \xrightarrow{\text{Waits for}} \text{Request}(\text{Book})$$
$$\text{Thread B}: \text{Hold}(\text{Book}) \xrightarrow{\text{Waits for}} \text{Request}(\text{Reservation})$$
两线程构成典型的**循环等待链（Circular Wait）**。PostgreSQL 检测到后将抛出 `ERROR: deadlock detected (SQLSTATE 40P01)` 并强制中止其中一个事务！

#### 【为什么之前的测试没发现？】
`LockOrderingTest.java` 只是 Mockito 单元测试，仅断言了 `borrowBook` 与 `returnBook` 内部调用 `bookRepository` 和 `bookCopyRepository` 的前后次序；而 `ConcurrentBorrowReturnTest.java` 仅压测了借阅与归还，**压测用例中根本没有并发调用 `fulfillReservation`**！

---

# 四、Backend 模块逐项深度审计

### 4.1 Controller 层
1. **测试控制器遗留在主源码中**：`UserTestController.java` 存在于 `src/main/java/com/library/controller`，暴露了 `/api/v1/users/admin-only` 和 `/api/v1/users/profile-test`。在生产环境下，测试用途端点应放置于 `src/test/java` 或配置 `@Profile({"dev", "test"})`；
2. **状态码规范性**：大部分写操作返回 `200 OK`，部分标了 `201 CREATED`，整体一致性尚可；
3. **参数校验覆盖**：Controller 层入参多数标注了 `@Valid`，但分页参数 `page` 和 `size` 均为直接入参，缺乏 `@Max(100)` 和 `@Positive` 的 Bean Validation 注解，依赖 Service 层内部手工截断 (`Math.min(100, size)`)。

### 4.2 Service 与事务边界
1. **调度任务事务过大**：`BorrowDueCheckScheduler.scanAndProcessOverdueAndReminders()` 标注了 `@Transactional`，若历史在借逾期单达数万条，全量加载与遍历更新处于同一个大事务中，极易引起的长事务（Long-Running Transaction）锁定与回滚放大；
2. **事件监听同步阻塞**：`NotificationEventListener` 未使用 `@Async`，发送站内信时的数据库 `INSERT` 与异常捕获均挂在借还/预约的主业务线程上。

### 4.3 Repository / JPA 性能
1. **内存中按月聚合与全量查询**：
   [`BorrowRecordRepository.java:115`](file:///d:/wkk/Campus%20Library%20Borrowing%20System/backend/src/main/java/com/library/repository/BorrowRecordRepository.java#L115)：
   ```java
   @Query("SELECT r.borrowedAt FROM BorrowRecord r WHERE r.user.id = :userId ORDER BY r.borrowedAt ASC")
   List<OffsetDateTime> findBorrowDatesByUserId(@Param("userId") Long userId);
   ```
   在 `StatisticsServiceImpl.java:78` 读出全量时间戳并在 Java 内存中做 `HashMap` 计数。应直接使用 SQL `date_trunc('month', borrowed_at)` 由数据库完成分组统计；
2. **`LibrarianDashboard` 16 连查**：进入馆员看板时连续执行 16 次同步 `count()` 与聚合，单次接口耗时随数据量呈线性增长；
3. **无界集合查询**：`findOverdueBorrowingRecords(@Param("now") OffsetDateTime now)` 未加 `Pageable` 或 `LIMIT`，直接返回 `List<BorrowRecord>`。

---

# 五、数据库深度审计 (Flyway V1~V9)

### 5.1 约束健全性
- **优秀实践**：
  - `books.available_copies` 配备了 `CHECK (available_copies >= 0 AND available_copies <= total_copies)`，在存储引擎底层保证库存绝不可能为负；
  - `reservations` 配备了部分唯一索引 `uk_reservations_active_user_book` (`WHERE status IN ('WAITING', 'READY')`)，杜绝了同一读者对同书的重复有效预约。
- **缺陷与漏洞**：
  - **`borrow_records` 缺乏部分唯一约束**：`idx_borrow_records_active` 只是普通索引，**缺少针对 `(copy_id) WHERE status IN ('BORROWING', 'OVERDUE')` 的部分唯一索引**。如果 Java 层由于并发或逻辑穿透导致重复借出同一单册，PostgreSQL 底层不会拦截，造成“一本物理单册同时被借给两人”的重大数据污染风险。

### 5.2 索引设计
- GIN Trigram 倒排索引：在 V3 中创建了 `idx_books_title_trgm ON books USING gin (title gin_trgm_ops)`。但在 `BookServiceImpl.java:217` 中，JPA 生成的是 `WHERE lower(books.title) LIKE '%...%'`。PostgreSQL 在没有创建表达式索引（Expression Index on `lower(title)`）的情况下，对函数包裹字段无法直接利用纯列 GIN 索引，可能退化为并行顺序扫描（Seq Scan）。

---

# 六、Redis 专项审计

1. **实际使用场景极其单一**：
   - 整个工程仅在 `RefreshTokenService` 中使用 Redis 存储 Refresh Token（Key 格式为 `refresh_token:<uuid>`，TTL 7天）；
   - **文档夸大**：文档声称 Redis 承担“分布式原子计数”、“多级缓存架构”、“图书库存热点缓存”，但代码中根本没有这些实现；
2. **缺乏宕机降级能力**：如果 Redis 容器突发 Crash，由于 `RefreshTokenService` 没有熔断或本地持久化兜底，登录和 Token 刷新将瞬间全盘不可用；
3. **反思：当前系统是否真的需要 Redis？**
   对于目前的单体部署规模，仅为了存储 RefreshToken 而引入 Redis 8 容器，增加了系统运维拓扑复杂度。若将 RefreshToken 作为一张数据库表由 PostgreSQL 持久化管理，架构将更轻量，且天然支持事务一致性。

---

# 七、AI 系统专项审计

### 7.1 “Grounded-RAG” 真实性审查
- **代码真相**：
  [`DeepSeekAiProvider.java:38-49`](file:///d:/wkk/Campus%20Library%20Borrowing%20System/backend/src/main/java/com/library/service/ai/DeepSeekAiProvider.java#L38-L49)：
  ```java
  public BookInsightResponse generateInsight(Book book) {
      if (apiKey == null || apiKey.isBlank() || "mock-key".equalsIgnoreCase(apiKey)) {
          BookInsightResponse response = fallbackProvider.generateInsight(book);
          response.setModelName("deepseek-chat (local-fallback)");
          return response;
      }
      try {
          BookInsightResponse response = fallbackProvider.generateInsight(book);
          response.setModelName("deepseek-chat");
          return response;
      } ...
  }
  ```
  不论是否配置了 `apiKey`，直接委托给 `fallbackProvider.generateInsight(book)`。
- **结论**：本系统目前**没有接入任何远程大模型**。文档与论文材料中所谓“Grounded-RAG 架构”、“长事务网络 I/O 隔离”、“5秒网络调用”全部属于针对本地 Mock 规则生成的学术包装。如果被答辩老师要求现场断开本地 Mock、调通实际 API，会立刻出现严重事故。

### 7.2 推荐算法真实性审查
- **代码真相**：`AiRecommendServiceImpl.java:121-134`：
  - `contentScore`：按分类与作者文本是否匹配打分；
  - `behaviorScore`：`if (userCategoryMap.containsKey(catName)) behaviorScore += 30.0;`；
  - `popularityScore`：按借出册数线性打分；
  - `stockBoost`：在架可借时 `+15.0`。
- **结论**：这是一个纯粹基于规则与分类偏好的启发式线性打分器，**没有协同过滤（Collaborative Filtering）算法**。文档与论文必须如实称为“基于分类偏好与在架感知的多路加权推荐算法”，严禁声称实现了基于矩阵的协同过滤。

---

# 八、Excel 批量导入专项审计

1. **单册条码碰撞风险**：
   [`BookImportListener.java:134-135`](file:///d:/wkk/Campus%20Library%20Borrowing%20System/backend/src/main/java/com/library/service/excel/BookImportListener.java#L134-L135)：
   ```java
   String barcode = String.format("BAR-%d-%d-%d", book.getId(), System.currentTimeMillis() % 1000000, i + 1);
   ```
   采用毫秒数取模 100 万，如果导入速度极快或者跨秒重复，在多复本情况下存在极高的条形码碰撞风险，从而触发 `book_copies` 的唯一索引冲突导致入库失败；
2. **单记录独立事务开销**：
   `saveBuffer()` 内部针对每一行单独调用 `transactionTemplate.executeWithoutResult`。当导入 5,000 本书时，会创建 5,000 个独立的数据库写事务，网络往返与 WAL 刷盘开销巨大；
3. **安全防护缺失**：
   - 仅校验后缀名 `.xlsx` / `.xls`，未校验文件魔数（Magic Bytes）与 MIME 类型；
   - 未做 Excel 公式注入过滤（若字段以 `=`, `+`, `-`, `@` 开头，导出或被本地 Excel 打开时存在 DDE 代码执行风险）。

---

# 九、安全专项审计

1. **Refresh Token 缺乏轮转机制 (No Refresh Token Rotation)**：
   `AuthServiceImpl.refreshToken()` 在换发 Access Token 时，**直接将入参的原 Refresh Token 原样返回**。该 Token 在 7 天有效期内可被无限制反复使用。一旦被截获，攻击者可长期重放换发凭证；
2. **`/actuator/**` 越权访问风险**：
   `SecurityConfig.java:54` 配置了 `.requestMatchers("/actuator/**", "/error").permitAll()`。虽然当前 `application.yml` 仅暴露了 `health,info,metrics`，但安全配置完全解除了 Spring Security 的防线。若后续配置文件调整，敏感监控端点将直接裸露；
3. **JWT Secret 硬编码默认值**：
   `application.yml` 中默认提供了一个随仓库公开的弱密钥（Base64 解码后为完整可读明文，任何克隆者都能用它伪造管理员令牌）。该默认值已于 Stage 10-A 移除并完成轮换，泄露值的 SHA-256 指纹被写入启动黑名单，继续使用会直接拒绝启动。历史教训：只要源码里存在"可用默认密钥"，运维人员不修改 `.env` 就会以通用弱密钥对外服务。

---

# 十、Flutter 前端专项审计

1. **主导航 Tab 0 (首页) 占位符未替换**：
   [`app_router.dart:114`](file:///d:/wkk/Campus%20Library%20Borrowing%20System/frontend/lib/core/router/app_router.dart#L114)：
   ```dart
   final List<Widget> _pages = const [
     Center(child: Text('首页概览 (Stage 1 就绪)', style: TextStyle(fontSize: 18))),
     ...
   ];
   ```
   用户登录后进入的主 Tab 页面是一句静态文本！这在毕业答辩与实际演示中是极其严重的硬伤；
2. **401 并发刷新风暴 (Thundering Herd)**：
   [`api_client.dart:38-75`](file:///d:/wkk/Campus%20Library%20Borrowing%20System/frontend/lib/core/network/api_client.dart#L38-L75)：
   当 Access Token 过期时，若页面同时发起多个 API 请求（例如进入个人中心同时请求个人信息、预约数、未读通知数），所有失败请求将并发进入 `onError`，各自创建独立的 `tokenDio` 重复向 `/auth/refresh` 发起刷新。缺乏单例 Mutex 队列管控。

---

# 十一、Docker 与 DevOps 专项审计

1. **单机 Compose 并非“企业级高可用”**：
   当前 `docker-compose.prod.yml` 仅为单机单节点部署，PostgreSQL 和 Redis 均为单实例，没有任何集群复制、故障自动转移或只读副本支持。文档与简历中应表述为“容器化生产级单机交付”，严禁吹捧为“高可用容灾集群”；
2. **`backup.sh` 使用 `-t` 分配伪终端隐患**：
   [`backup.sh:25`](file:///d:/wkk/Campus%20Library%20Borrowing%20System/docker/scripts/backup.sh#L25)：
   ```bash
   docker exec -t "${CONTAINER_NAME}" pg_dump ... | gzip > "${BACKUP_FILE}"
   ```
   `-t` 参数会向输出流注入终端换行符 `\r\n` 与控制字符，破坏导出的 SQL 脚本纯净度，导致恢复时语法报错。在批处理与管道重定向中必须使用 `docker exec -i`（仅输入流，无 TTY）；
3. **缺乏备份恢复测试与验证脚本**：仓库内仅有 `backup.sh`，未提供配对的 `restore.sh`，且从未经过自动化恢复验证。

---

# 十二、测试质量深度审计

1. **测试优点**：
   - 并非纯 H2 内存测试，配置了真实的 PostgreSQL 17（端口 15437）与 Redis（端口 16381）真实环境运行；
   - 161 项后端测试涵盖 RBAC、借还守恒、预约队列单调递增与并发借还，质量高于一般本科毕设；
2. **测试盲区与缺陷**：
   - **`LockOrderingTest.java` 是 Mock 单元测试**：仅验证了 Mock 对象的调用顺序，不能证明数据库物理锁行为；
   - **缺少预约与借还交叉并发压测**：未覆盖 `fulfillReservation` 与 `returnBook` 并发执行，导致未能暴露潜在的死锁风险；
   - **AI 测试全部针对本地 Mock 运行**：未曾测试过网络波动、超时熔断、大模型输出格式畸变等真实外部边界。

---

# 十三、全景问题矩阵 (Final Issue Matrix)

| ID | 等级 | 模块 | 具体问题与代码位置 | 潜在风险 | 改进建议 | 成本 | 值得修改度 |
|:---:|:---:|:---:|:---|:---|:---|:---:|:---:|
| **ISSUE-01** | **P1** | Concurrency | `fulfillReservation` (`Reservation->Book`) 与 `returnBook` (`Book->Reservation`) 加锁次序相反 | 并发履约与还书时引发 PostgreSQL 40P01 死锁 | 在 `fulfillReservation` 中先按 `bookId` 锁定 `Book`，再锁 `Reservation` | 低 | **极高** |
| **ISSUE-02** | **P1** | Frontend | `app_router.dart:114` 首页 Tab 0 为静态占位符文字 | 答辩现场第一眼展示严重扣分 | 将 `AiRecommendationScreen` 或专门的综合 Home 聚合看板嵌入 Tab 0 | 低 | **极高** |
| **ISSUE-03** | **P1** | AI Engine | `DeepSeekAiProvider.java` 内部直接调用 Mock，未真实调用 API | 答辩/面试被要求看真实调用或报文时露馅 | 坦诚定位为“本地规则引擎”，或补全真正的 `RestClient` HTTP 外部调用 | 中 | **高** |
| **ISSUE-04** | **P1** | Documentation | 论文材料中推荐公式与协同过滤描述与代码实际实现严重脱节 | 论文技术审查判定学术不端或虚假陈述 | 全面修正论文与文档中的公式，如实表述为加权启发式多路召回模型 | 低 | **极高** |
| **ISSUE-05** | **P1** | Documentation | 论文声称使用 `@TransactionalEventListener`，代码实为同步 `@EventListener` | 答辩老师比对代码与论文时被直接指出 | 修正论文文字，或将代码改造为真正的 `AFTER_COMMIT` + `@Async` | 低 | **极高** |
| **ISSUE-06** | **P1** | Frontend | `api_client.dart` 401 并发刷新风暴 | 多个并行请求同时过期时触发多次重复刷新 | 引入 `Completer` 或锁机制控制并发刷新单一性 | 中 | **高** |
| **ISSUE-07** | **P1** | Performance | `StatisticsServiceImpl.getLibrarianDashboard` 16 次同步全表 count | 大数据量下馆员大盘加载极慢甚至超时 | 引入 Redis 缓存或异步聚合表，合并同表 count 查询 | 中 | **中** |
| **ISSUE-08** | **P2** | Database | `borrow_records` 缺少对 `(copy_id)` 在借状态的部分唯一索引 | 极端并发穿透下产生一书多借脏数据 | 新增 `CREATE UNIQUE INDEX ... WHERE status IN ('BORROWING', 'OVERDUE')` | 低 | **高** |
| **ISSUE-09** | **P2** | Excel Import | `BookImportListener` 条码毫秒取模碰撞 (`% 1000000`) | 快速批量导入时引发条码唯一索引冲突 | 改用 `UUID` 或数据库自增序列生成条形码 | 低 | **高** |
| **ISSUE-10** | **P2** | DevOps | `backup.sh` 使用 `docker exec -t` 分配 TTY | 管道压缩可能混入终端控制符破坏备份文件 | 将 `-t` 移除，仅保留 `docker exec -i` | 极低 | **高** |
| **ISSUE-11** | **P2** | Security | `AuthServiceImpl.refreshToken` 未实现 Refresh Token 轮转 | 刷新凭证被盗后可被长期重放利用 | 刷新时废弃旧 Token 并签发新 Refresh Token | 低 | **中** |
| **ISSUE-12** | **P2** | Security | `SecurityConfig` 针对 `/actuator/**` 实施无条件放行 | 敏感监控指标在配置变更后存在越权泄漏风险 | 收敛为仅放行 `/actuator/health` 和 `/actuator/info` | 极低 | **中** |
| **ISSUE-13** | **P2** | Scheduler | 调度器无分布式锁，多容器多实例部署将重复执行 | 后续横向扩展容器时产生重复巡检与重复通知 | 引入 ShedLock 或 Redis 分布式防重锁 | 中 | **中** |
| **ISSUE-14** | **P3** | Backend | `UserTestController` 遗留在生产业务控制器目录中 | 污染生产 API 契约 | 移除或添加 `@Profile("!prod")` 隔离 | 极低 | **低** |
| **ISSUE-15** | **P3** | Repository | `StatisticsService` 将全量借阅时间戳查到 JVM 内存聚合 | 随时间累积占用过多堆内存与传输带宽 | 改用 PostgreSQL `date_trunc` 分组聚合 SQL | 低 | **低** |

---

# 十四、Don't Fix List（明确禁止盲目优化的清单）

为了防止“为了优化而优化”破坏现有系统的稳定性与答辩表现，以下项目**明确禁止修改**：
1. **❌ 不要拆分微服务（Spring Cloud / Dubbo）**：单体模块化整洁架构完全满足高校业务规模，盲目微服务化只会引入分布式事务（Seata）、链路追踪、网络抖动等海量故障点；
2. **❌ 不要引入 Kafka / RabbitMQ 消息队列**：目前基于 Spring 内部事件总线已足够解耦，引入外部 MQ 容器会显著拉高本地与部署环境的内存开销；
3. **❌ 不要引入 Milvus / 向量数据库**：在未接入真实大模型向量 Embedding 之前，硬插向量库是典型的过度工程；
4. **❌ 不要引入分库分表（ShardingSphere）**：高校图书千万级数据量在 PostgreSQL 17 的 B-Tree 与 GIN 索引下完全可以平稳支撑，分库分表会彻底破坏跨表外键与事务强一致性；
5. **❌ 不要将悲观锁重构为乐观锁**：在热门孤本书籍被数十人争抢的秒杀借阅场景下，乐观锁会导致 99% 的线程因版本冲突而不断重试，严重增加 CPU 与连接池负担。

---

# 十五、毕业设计与 Java 面试答辩风险防守反击

### 15.1 毕业设计最致命的 3 个潜在被抓漏洞
1. **“请老师现场打开首页”**：
   - *风险*：评委看到 `首页概览 (Stage 1 就绪)` 会直接质疑系统未完成；
   - *应对*：在演示脚本中明确**不要直接停留在空白首页**，或立即将 Home Tab 替换为 AI 推荐与热门大盘综合页。
2. **“请在代码里指出哪里调用了 DeepSeek API”**：
   - *风险*：评委发现 `DeepSeekAiProvider` 直接 return 了 Mock，质疑学术造假；
   - *应对*：答辩时务必**诚实、科学地解释**：“系统设计了标准的 `AiProvider` 策略解耦接口，考虑到校园内网封闭环境、API 调用成本与网络断流风险，本期采用离线启发式规则引擎保障系统的绝对可用性，已预留完整的云端大模型集成扩展点。”切忌宣称“我们已经接好了真实的 DeepSeek 并在生产调用”。
3. **“论文里的推荐公式和代码怎么对不上？”**：
   - *风险*：评委翻看论文的 0.35/0.35/0.20/0.10，对比代码里的 0.4/0.4/0.2+15；
   - *应对*：**立即同步修正论文材料中的公式与文字**，使其与代码实现 100% 吻合。

### 15.2 Java 面试官最容易深挖的 3 个技术点
1. **“既然自称全系统统一锁顺序无死锁，履约借出和归还之间有没有可能死锁？”**
   - *应答*：坦诚指出在早期实现中借还统一了 Book $\to$ BookCopy，但履约时从 Reservation 开始查，确有锁倒序风险；并阐述修复方案：在 `fulfillReservation` 中统一提升为先锁 Book 再锁 Reservation。
2. **“为什么你的 Redis 仅仅用来存了个 RefreshToken？这和存在数据库有什么区别？”**
   - *应答*：从分布式会话共享、高频鉴权（避免频繁冲击 DB 连接）、TTL 自动失效特性解释，同时诚实承认系统在图书热点数据上仍依托 PostgreSQL 缓存，预留了 Redis 二级缓存扩展空间。
3. **“EasyExcel 为什么能做到 O(1) 内存？底层是怎么实现的？”**
   - *应答*：从底层 SAX 基于 XML 事件驱动流式解析与 DOM 树在内存整块展开的区别入手，解释逐行读取、单行装配、及时 GC 的机制。

---

# 十六、终审综合结论

### 综合评定等级：**YELLOW（中度风险，具备优秀底子但存在严重宣传偏差与局部瑕疵）**

| 评估维度 | 达成等级 | 核心依据与评价 |
|:---|:---:|:---|
| **Graduation Readiness (毕业设计就绪度)** | **88 / 100** | 业务闭环完整、测试覆盖扎实、代码规范良好。**但存在首页占位符未替换、论文公式与代码脱节、大模型实际未接入三大重大答辩风险**，必须对文档与前端主页进行真实性对齐！ |
| **Interview Readiness (求职面试就绪度)** | **85 / 100** | 并发控制、死锁分析、状态机流转等核心亮点真实过硬，能经受住常规面试。但**严禁在简历中过度包装 Grounded-RAG 与协同过滤**，否则被资深面试官逐行深挖必崩。 |
| **Production Readiness (生产交付就绪度)** | **78 / 100** | 单机 Docker 交付顺畅，但存在调度器缺乏多实例分布式锁、`backup.sh` 管道带 TTY 控制符、大盘统计同步 16 连查性能隐患。 |
