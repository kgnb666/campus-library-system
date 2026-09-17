# 《校园图书借阅系统》Stage 6：架构审查与设计评估报告

> **评审角色**：资深架构评审工程师（Senior Architecture Review Engineer）  
> **审查阶段**：Stage 6 Architecture Design Review  
> **审查基准版本**：Stage 5 完成态（Git 139 个后端测试全绿，34 个前端测试全绿，Flyway V7 已生效）  
> **评审日期**：2026-09-17  
> **当前状态**：Design Review 只读分析（严格禁止修改代码、严禁创建迁移、严禁实现接口）  

---

## 一、当前系统架构现状全面审查

### 1. 后端工程分层与组件边界审查

| 分层维度 | 当前实现状态 | 架构评估结论 |
|---|---|---|
| **Controller 表现层** | 统一返回 `Result<T>` 泛型信封，统一使用 Spring Security `@PreAuthorize` 保护端点，入参全面采用 `@Valid` JSR-303 校验。 | **优良**。表现层职责纯粹，无业务泄漏。 |
| **DTO / Entity 隔离性** | `BorrowRecordResponse`、`ReservationResponse`、`BookInsightResponse`、`RecommendedBookResponse` 及统计 DTO 均定义在独立 `dto` 包下，通过工厂方法与 Entity 显式转换。 | **优良**。Entity 实体持久态未直接逃逸出 Controller 层，杜绝了 JSON 序列化时的死循环与懒加载脏读。 |
| **Service 业务层** | 采用接口与实现类分离架构（`service` vs `service.impl`），核心业务采用领域事件（`BookReturnedEvent`、`BookBorrowedEvent`）实现借还流通与预约/推荐系统的依赖解耦。 | **良好，但存在事务与锁细节缺陷**（详见问题清单）。 |
| **Repository 数据访问层** | 基于 Spring Data JPA，对核心并发实体定制了悲观排他锁（`findByIdForUpdate`、`findByBarcodeForUpdate`、`findEarliestWaitingForUpdate`）。 | **优良**。对高并发竞态场景具备清晰的防超卖与防并发重构意识。 |
| **异常与错误码体系** | 采用统一 `BusinessException` + `ResultCode` 枚举体系，由 `GlobalExceptionHandler` 集中捕获兜底。 | **优良**。异常码颗粒度细致，错误响应结构统一。 |

---

### 2. 核心并发控制与事务模型深度审计

重点审查 Stage 3（借阅）、Stage 4（预约）与 Stage 5（AI）：

#### (1) 借阅与还书加锁偏序审计（发现严重死锁隐患）
- 在借阅操作 [`BorrowCirculationServiceImpl.borrowBook`](file:///d:/wkk/Campus%20Library%20Borrowing%20System/backend/src/main/java/com/library/service/impl/BorrowCirculationServiceImpl.java#L115-L155) 中，锁获取顺序为：
  1. 锁定父级书目：`bookRepository.findByIdForUpdate(bookId)`（Lock Book）
  2. 锁定物理副本：`bookCopyRepository.findByBarcodeForUpdate(...)` 或 `findAvailableCopiesForUpdate(...)`（Lock BookCopy）
  3. 锁顺序：$\text{Book} \longrightarrow \text{BookCopy}$
- 在还书操作 [`BorrowCirculationServiceImpl.returnBook`](file:///d:/wkk/Campus%20Library%20Borrowing%20System/backend/src/main/java/com/library/service/impl/BorrowCirculationServiceImpl.java#L239-L248) 中，代码执行顺序为：
  1. 第 239 行：锁定物理副本 `bookCopyRepository.findByIdForUpdate(record.getBookCopy().getId())`（Lock BookCopy）
  2. 第 245 行：锁定父级书目 `bookRepository.findByIdForUpdate(record.getBook().getId())`（Lock Book）
  3. 锁顺序：$\text{BookCopy} \longrightarrow \text{Book}$
- **架构审计判定**：**加锁拓扑偏序反转（Lock Ordering Inversion Deadlock Risk）**。当读者 A 正在借阅图书 $X$ 的某一副本，同时读者 B 在归还同一图书 $X$ 的另一副本时，两笔事务极易发生相互等待死锁（PostgreSQL 抛出 `40P01 deadlock_detected` 异常），必须严格统一步调。

#### (2) 事务过大与数据库连接池饥饿风险（Stage 5 AI 导读）
- 在 [`AiInsightServiceImpl.getBookInsight`](file:///d:/wkk/Campus%20Library%20Borrowing%20System/backend/src/main/java/com/library/service/impl/AiInsightServiceImpl.java#L34-L49) 中：
  - 方法标注了 `@Transactional`；
  - 缓存未命中时，在事务生命周期内直接同步调用了 `aiProvider.generateInsight(book)`；
  - `DeepSeekAiProvider` 发起的是外部大模型 HTTP 请求（网络耗时通常在 2s ~ 10s）；
- **架构审计判定**：**慢外部 I/O 阻塞数据库物理连接**。如果 20 个读者并发请求未缓存导读的图书，HikariCP 仅有的 20 个数据库连接将全部被卡在等待 HTTP 响应上，导致借书、还书、登录等所有核心 OLTP 业务瞬间连接饥饿并超时雪崩。

#### (3) 领域事件监听同步阻塞与事务生命周期耦合
- `BorrowCirculationServiceImpl` 中发布的 `BookBorrowedEvent` 与 `BookReturnedEvent` 采用 Spring 默认的同步事件总线；
- 推荐转化回填 `onBookBorrowed` 在借书事务内同步执行；
- **架构审计判定**：虽然有 try-catch 保护，但在核心写事务内执行衍生统计埋点，延长了数据库行锁持有时间。应演进为 `@TransactionalEventListener(phase = AFTER_COMMIT)` 结合 `@Async`。

---

### 3. 数据库架构与超大数据量伸缩性评估

结合高校图书馆实际业务场景（**10万图书规模、100万借阅流水规模、10万在校师生规模**）进行容量与性能测算：

```
[100万借阅记录]
   ├── 活跃在借态 (BORROWING / OVERDUE): 约 3~5 万条 (高频更新、排他锁争抢、高敏索引)
   └── 终结历史态 (RETURNED / OVERDUE_RETURNED): 约 95 万条 (只读、仅用于历史回溯与统计聚合)
```

| 数据表 | 100万级规模影响 | 当前索引与约束评价 | 伸缩性改进建议 |
|---|---|---|---|
| **`borrow_records`** (100万行) | 每日借还更新约 5000 次，全表约 300MB。 | 已具备 `idx_borrow_records_active` 部分索引、`idx_borrow_records_user_status_due` 复合索引。 | **暂不需要物理分表/分区**（PostgreSQL 在单表千万级以下 B-tree 索引表现极佳，过度分区会破坏外键完整性）。但统计聚合必须避免全表扫描。 |
| **`ai_recommendation_logs`** (暴增风险) | 若日活 10,000 读者，每次打开生成 8 条曝光，**单月产生 240 万条日志，一年超 3000 万条**。 | 当前建立有 `(user_id, created_at)` 与 `(created_at, clicked, borrowed)` 索引。 | **存在严重膨胀风险**。该表属于行为流水而非金融核心账，必须设立 **90 天定期归档/冷清理策略**，或按月进行 `PARTITION BY RANGE (created_at)` 分区。 |
| **`books` & `book_copies`** (10万书/30万复本) | 10万图书单表约 50MB，复本表约 80MB。 | V4 已建立 `gin_trgm` 模糊检索索引与分类索引。 | 基础表无需分区。但必须杜绝应用层全表 `findAll()`。 |
| **`reservations`** (10万行以内) | 绝大部分预约在 48 小时或借出后终结，活跃排队数通常稳定在数千以内。 | `uk_reservations_active_user_book` 部分唯一索引、`idx_reservation_book_status_queue` 极其精准。 | 结构健壮，无需改动。 |

---

### 4. Stage 5 AI 模块真实性与闭环专项审计

| 审查维度 | 审查项 | 审计结论与证据 |
|---|---|---|
| **防幻觉真实性 (Grounded-RAG)** | 推荐图书是否来自真实数据库？ | **通过**。推荐候选集 `candidateBooks` 全部来自 `bookRepository`，推荐结果携带真实 `bookId`、`isbn`、`availableCopies`，无任何虚构实体。 |
| **数据真实性与可解释性** | 推荐来源是否真实可追溯？ | **通过**。严格依据 Content（分类重合度）、CF（协同行为重合度）、Popularity（流通频次）和库存余量计算综合分，输出精准理由（如“因您在「软件工程」领域的阅读偏好”）。 |
| **推荐闭环与转化指标** | 是否存在捏造的虚假高准确率？ | **通过**。`RecommendationMetricsResponse` 中的 CTR（点击率）、BCR（借阅转化率）完全通过 `COUNT(clicked)`、`COUNT(borrowed)` 真实聚合，初始数据呈现真实的 0%~30% 真实区间，杜绝造假。 |
| **大模型调用成本与防刷** | 是否对导读结果进行持久化拦截？ | **通过**。`ai_book_insights` 建立了 `book_id` 唯一约束，首次生成后落库，后续并发请求 100% 命中 DB 缓存，0 额外 Token 消耗。 |
| **全表扫描潜在隐患** | `AiRecommendServiceImpl` 候选筛选方式 | **不合格（需优化）**。第 67 行 `bookRepository.findAll().stream()` 将全表 10 万图书载入 JVM 堆内存中再做过滤，在生产数据量下会造成严重 GC 甚至 OOM。 |

---

## 二、发现的问题列表 (Problem Inventory)

经过细致的代码走查与架构推演，共发现 **5 项核心架构缺陷与性能隐患**：

### 问题 1：借阅与归还事务中的加锁偏序反转死锁（Lock Ordering Inversion）
- **严重程度**：🔴 **严重 (High)**
- **原因**：
  - `borrowBook` 执行顺序：先锁 `Book`（`findByIdForUpdate`），再锁 `BookCopy`（`findByIdForUpdate`）；
  - `returnBook` 执行顺序：先锁 `BookCopy`（`findByIdForUpdate`），再锁 `Book`（`findByIdForUpdate`）；
  - 当高并发下两个读者同时针对同一书目的不同复本分别执行借书与还书时，必定触发循环互斥等待，引发 PostgreSQL 死锁异常。
- **改进建议**：
  - 严格统一全系统行级锁偏序规则：**永远遵循 `Book -> BookCopy -> BorrowRecord/Reservation` 的拓扑顺序获取排他锁**；
  - 重构 `returnBook`：先通过普通查询定位目标关联的 `Book`，先加锁锁定 `Book`，随后再加锁锁定 `BookCopy`。

---

### 问题 2：AI 外部 HTTP 远程调用持有数据库事务连接
- **严重程度**：🔴 **严重 (High)**
- **原因**：
  - `AiInsightServiceImpl.getBookInsight` 在 `@Transactional` 方法内执行 `aiProvider.generateInsight(book)`；
  - 外部 DeepSeek API 网络延迟（2~10秒）直接占有 HikariCP 数据库物理连接，极易造成数据库连接池耗尽雪崩。
- **改进建议**：
  - 采用 **事务与远程 I/O 彻底解耦模式**：
    1. 事务 1（只读）：从数据库查询是否存在缓存，若存在直接返回；
    2. 无事务区域：调用 `aiProvider.generateInsight` 获取 AI 导读结果（耗时网络请求不占用任何 DB 资源）；
    3. 事务 2（短事务写）：以独立短事务持久化导读并返回。

---

### 问题 3：AI 推荐引擎候选集内存全表扫描性能瓶颈
- **严重程度**：🟡 **中等 (Medium)**
- **原因**：
  - `AiRecommendServiceImpl.java` 第 67 行使用 `bookRepository.findAll().stream().filter(...)`；
  - 在馆藏达到 10 万册时，每次推荐请求都要全量反序列化 10 万个实体对象到 JVM 堆中，极度消耗 CPU 与内存。
- **改进建议**：
  - 将候选集下推至 SQL 数据库层：基于读者偏好分类（Top 3）及全馆热门 Top 50，通过数据库原生查询提取限量候选池（如最多 100~200 本候选），再在内存中执行轻量混合打分与在架提权。

---

### 问题 4：借阅超期缺乏自动化巡检流转机制
- **严重程度**：🟡 **中等 (Medium)**
- **原因**：
  - 目前借阅记录状态只有在读者主动还书时才被动判断是否逾期并转为 `OVERDUE_RETURNED`；
  - 数据库中的 `status` 列在借出逾期后仍滞留在 `BORROWING`，缺乏一个定时调度器将其主动置为 `OVERDUE`，也未按日递增滞纳金累计流水。
- **改进建议**：
  - 新增 `BorrowOverdueScheduler`：每日凌晨定时扫描 `due_at < NOW()` 且 `status = 'BORROWING'` 的记录，原子变迁为 `OVERDUE`，并支持消息催还告警。

---

### 问题 5：消息触达通道断裂（读者处于“盲等”状态）
- **严重程度**：🟡 **中等 (Medium)**
- **原因**：
  - Stage 4 实现了非常精巧的“还书自动激活预约并保留 48 小时”，但系统没有任何消息通知体系！
  - 读者无法得知预约已就绪，只能凭运气主动刷页面，极易导致 48 小时超期失效；
  - 借阅即将到期前无提醒，增加了无心逾期的概率。
- **改进建议**：
  - 建立统一的消息通知系统（`notifications`），支持“预约到书就绪提醒”、“图书临期催还通知”、“逾期违约预警”，并在前端建立消息中心与未读红点。

---

## 三、Stage 6 功能决策分析表

从 **毕业设计价值、系统完整度、答辩展示效果、实际使用价值、开发成本** 五个维度对候选功能进行严谨量化决策：

| 候选功能模块 | 决策结果 | 决策理由（五维综合研判） | 对系统与答辩的影响 |
|---|:---:|---|---|
| **1. 并发锁偏序修复与 AI 事务解耦** | **采用 (MUST)** | **架构刚需**。消除 `borrowBook`/`returnBook` 死锁隐患，将 AI HTTP 远程调用移出事务，优化推荐全表扫描。毕业设计答辩中体现极高的系统级并发掌控力。 | 消除死锁隐患，避免数据库连接池被大模型网络 I/O 打崩，夯实系统健壮性。 |
| **2. 站内消息通知与事件驱动中心** | **采用 (MUST)** | **完整度与体验核心**。彻底解决 Stage 4 预约就绪 48h 读者“盲等”问题与 Stage 3 借阅临期催还问题。开发成本可控，与借还/预约事件驱动天然契合。 | 闭环连接借阅与预约全生命周期，答辩时演示“还书瞬间接收就绪通知”，效果拔群。 |
| **3. 馆员运营监控与统计分析大盘** | **采用 (MUST)** | **答辩展示高光点**。Stage 5 后端已完备输出全局统计与推荐转化漏斗 API，但前端此前仅实现了读者个人画像。为馆员端补齐全局运营大盘、热门趋势与算法 CTR/BCR 看板成本低、收益极大。 | 答辩视觉冲击力强，充分展现数据驱动与 AI 转化真实成果，彻底摆脱“只有学生端”的单薄感。 |
| **4. 借阅自动逾期巡检定时任务** | **采用 (MUST)** | **业务完整性**。补齐借阅生命周期的关键定时任务，与 Stage 4 的预约超时扫描形成对称的调度器体系。 | 使读者信用惩戒、逾期罚金计算从被动变为主动，体系更加严密。 |
| **5. Excel 图书批量编目与导入** | **采用 (MUST)** | **企业级工程价值**。解决图书馆新书入库人工逐本录入的现实痛点。基于流式解析进行数据校验（ISBN校验、分类映射、复本批量创建、错误行隔离）。 | 毕业设计经典加分项，展示批量数据清洗、事务批量提交与文件导入工程能力。 |
| **6. 文件资源存储抽象 (OSS/本地)** | **延期 (POSTPONE)** | 当前图书封面使用外部合法 URL 即可满足全部检索与展示需求。引入复杂 OSS 增加了部署环境依赖，投入产出比不高。 | 维持当前 URL 机制，不额外增加 MinIO/阿里云 OSS 运维负担。 |
| **7. 滞纳金第三方在线支付 (微信/支付宝)** | **删除 (DROP)** | 高校图书借阅系统的逾期滞纳金一般在图书归还时由前台抵扣或计入校园一卡通余额，引入真实第三方商业支付需要商户资质、微信/支付宝沙箱，极易因证书和网络问题造成演示失败。 | 滞纳金在归还时结清或记录在账即可，不盲目引入外部支付网关。 |
| **8. 读者社交书评与广场论坛** | **删除 (DROP)** | 严重偏离“借阅流通与检索”核心业务主干，增加违规文本审核、敏感词过滤与防刷帖复杂度，不符合毕业设计聚焦原则。 | 坚决剔除，保持系统的专业度与工程纯粹性。 |

---

## 四、Stage 6 数据库变化规划 (Flyway V8)

为支撑 Stage 6 采纳的功能，数据库规划新增 **1 张业务表** 及相关索引，保持最小必要与高内聚原则：

### 1. 新增表：`notifications`（系统站内消息通知表）

```sql
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(128) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(32) NOT NULL 
        CHECK (type IN ('RESERVATION_READY', 'RESERVATION_EXPIRED', 'BORROW_DUE_REMIND', 'BORROW_OVERDUE', 'SYSTEM_ANNOUNCEMENT')),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMPTZ,
    related_entity_type VARCHAR(32), -- 'RESERVATION', 'BORROW_RECORD', 'BOOK'
    related_entity_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE notifications IS '系统站内消息通知表';
COMMENT ON COLUMN notifications.type IS '消息类型: 预约到书就绪, 预约超期失效, 借阅临期催还, 借阅逾期违规, 系统公告';
```

### 2. 高性能索引规划

```sql
-- 读者端未读消息秒级查询与计数索引
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread 
    ON notifications (user_id, is_read, created_at DESC);

-- 关联实体追溯索引
CREATE INDEX IF NOT EXISTS idx_notifications_related 
    ON notifications (related_entity_type, related_entity_id);
```

### 3. 细粒度权限项增补（RBAC）

```sql
INSERT INTO permissions (code, name, description) VALUES
('notification:view:my', '查看个人消息', '允许查询当前登录读者本人的站内通知'),
('notification:read', '标记已读消息', '允许更新本人消息的已读状态'),
('catalog:batch:import', '图书批量导入', '允许通过 Excel 模板批量导入新书与复本')
ON CONFLICT (code) DO NOTHING;
```

---

## 五、Stage 6 API 规划（只设计，严禁编码）

### 1. 消息通知模块 (`NotificationController`)
- `GET /api/v1/notifications/my`：分页查询当前读者的通知列表（支持未读过滤）；
- `GET /api/v1/notifications/unread-count`：获取当前读者的未读消息数量（供前端红点角标）；
- `POST /api/v1/notifications/{id}/read`：单条通知标记已读；
- `POST /api/v1/notifications/read-all`：一键全标为已读。

### 2. 图书批量编目导入模块 (`BookBatchCatalogController`)
- `GET /api/v1/admin/catalog/template`：下载图书批量导入标准 Excel 模板；
- `POST /api/v1/admin/catalog/import`：上传并流式解析 Excel，返回导入成功数、复本新增数及失败行明细报告（`multipart/form-data`）。

### 3. 管理员运营工作台看板（复用并升级 Stage 5 端点）
- 前端专属屏幕：`LibrarianDashboardScreen`
- 挂载路由：`/admin/dashboard`（由 `adminRouteGuard` 拦截非管理角色）
- 调用端点：
  - `GET /api/v1/statistics/overview`（全馆图书、复本、读者、借还流转大盘）
  - `GET /api/v1/statistics/books/ranking`（热门图书榜单，支持天数切换）
  - `GET /api/v1/statistics/categories/hot`（分类借阅热度流通占比）
  - `GET /api/v1/statistics/recommendations`（AI 推荐 CTR、BCR 及读者真实满意度）

---

## 六、Stage 6 测试规划

| 测试类别 | 目标范围 | 核心验证场景 |
|---|---|---|
| **单元测试 (Unit Tests)** | 消息通知服务、批量导入解析器、逾期调度器 | 1. 消息创建、阅读标记与未读计数准确性；<br>2. Excel 格式校验（正确格式导入成功、ISBN 格式错误行精准报错并隔离）；<br>3. 借阅逾期定时扫描状态变迁逻辑。 |
| **并发与锁验证 (Lock & Concurrency)** | 借书与还书防死锁专项测试 | **核心测试**：多线程并发对同一书目交替执行 `borrowBook` 与 `returnBook`，验证在统一步调加锁规则下 **0 死锁、0 数据不一致**。 |
| **事务与性能测试 (Transaction Boundary)** | AI 导读事务隔离性测试 | 验证在外部 AI Provider 模拟 3 秒网络延迟时，数据库连接池物理连接已被即时释放，不阻塞其他业务写请求。 |
| **集成测试 (Integration Tests)** | 领域事件驱动全链路 | 读者还书 -> 触发 `BookReturnedEvent` -> 预约自动就绪 -> **自动生成 `RESERVATION_READY` 站内通知** 全链路端到端验证。 |
| **权限安全测试 (Permission Tests)** | 批量导入与管理大盘 RBAC | 普通学生（`STUDENT`）调用 Excel 导入或查看全馆大盘强制返回 403 Forbidden；馆员（`LIBRARIAN`）正常访问。 |
| **前端自动化测试 (Flutter Tests)** | 消息中心、管理运营大盘、红点角标 | 1. 消息列表渲染、一键已读交互；<br>2. 运营看板 KPI 卡片与环形/柱状图表渲染；<br>3. 保持 `flutter analyze` 0 issues。 |

---

## 七、最终 Gate 审核结论

依据《校园图书借阅系统》研发纪律与资深架构评审标准，对 Stage 6 准备就绪度进行综合裁定：

1. **项目现状把脉清晰**：精准揪出了借还锁偏序死锁、AI 事务占用连接等关键隐患，为系统上线前的架构加固提供了决定性方向；
2. **范围边界严谨收敛**：坚决剔除第三方支付、社交评论等杂项，延期外部 OSS，聚焦核心消息闭环、运营工作台与批量编目；
3. **技术方案完全可行**：Flyway V8 仅需新增 1 张轻量通知表，其余大量复用 Stage 5 现存高价值接口，开发成本与工程风险完全可控。

```
============================================================
              Stage 6 Design Review 最终结论
============================================================
              审核结果:  PASS (通过)
============================================================
```

> ⚠️ **严格纪律提醒**：当前阶段为 Design Review 阶段。**本报告输出后立即停止一切操作，不编写任何业务代码、不创建迁移脚本，等待用户最终指令进入实现阶段。**
