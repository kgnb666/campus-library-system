# 《校园图书借阅系统》Stage 3：图书借阅流通系统完成报告

> **阶段**：Stage 3 图书借阅流通系统实现阶段  
> **状态**：✅ 全部完成（通过最终 Gate 审核，已立即停止）  
> **完成日期**：2026-09-17  

---

## 一、本阶段目标与纪律坚守

Stage 3 基于已完成的 Stage 1-B（用户认证与 RBAC）、Stage 2-A（图书领域模型与基础数据建设）以及 Stage 2-B（图书多维检索与编目管理工作台），正式建设图书馆核心流通业务闭环：

1. **读者图书借阅出库**：支持读者自主借阅图书（可自动分派或扫描指定单册条形码），扣减书目可用库存，物理单册变迁为 `BORROWED`，生成规范流水单号（`REC...`）；
2. **图书归还结清与逾期核算**：支持读者自助还书与图书管理员前台代办结清，物理单册原子回滚为 `AVAILABLE`，书目在架可用库存原子回加，根据逾期天数精准核算每日逾期罚金（`0.10 元/天`）；
3. **图书顺延续借**：在借读者在未逾期且未达续借上限前提下申请续借，应还时间顺延 30 天，更新已续借次数；
4. **读者借阅看板与流通监管**：提供读者端“当前在借”与“历史归还”流水看板（配备动态倒计时与状态彩色徽章）；提供图书管理员/系统管理员全馆借阅流水分页检索与状态/时间跨度审计；
5. **极高并发安全性（Zero-Oversell & Zero-Deadlock）**：坚守 **PostgreSQL 为全系统唯一单一真理源（Single Source of Truth）**，全面实行**自顶向下排他锁（`SELECT ... FOR UPDATE`）**，彻底杜绝超卖、负库存与并发死锁；
6. **严格阶段纪律坚守**：
   - ❌ 绝不提前引入预约排队队列（Reservation，属于 Stage 4）；
   - ❌ `BookCopy` 严格保持 6 种纯物理状态（`AVAILABLE`, `BORROWED`, `MAINTENANCE`, `DAMAGED`, `LOST`, `SCRAPPED`），严禁引入 `RESERVED` 状态；
   - ❌ 绝不在 Redis 中设置任何虚拟库存计数器或预扣减机制；
   - ❌ 绝不提前引入 AI 推荐、Excel 批量导入、统计报表看板或第三方支付网关。

---

## 二、数据库演进 (Flyway V5)

新增迁移脚本：[V5__create_borrow_circulation_tables.sql](file:///d:/wkk/Campus%20Library%20Borrowing%20System/backend/src/main/resources/db/migration/V5__create_borrow_circulation_tables.sql)

### 2.1 数据表设计

1. **`borrowing_rules` 借阅规则表**：
   - 区分用户身份（`STUDENT`、`TEACHER`、`EXTERNAL`）；
   - 包含最大借阅册数（5本）、基础借期（30天）、最大续借次数（1次）、续借天数（30天）、每日罚金金额（`0.10` 元/天）及是否允许逾期续借（`false`）。
2. **`users` 表结构增强**：
   - 新增外键字段 `borrow_rule_id REFERENCES borrowing_rules(id)`，建立用户与所属借阅规则的多对一关联。
3. **`borrow_records` 借阅流通流水表**：
   - 业务单号 `record_no VARCHAR(32) NOT NULL UNIQUE`；
   - 实体关联：`user_id`、`book_id`、`copy_id`、`borrow_rule_id`、`operator_id`、`return_operator_id`；
   - 时间节点：`borrowed_at`、`due_at`、`returned_at`；
   - 流转与费用：`status`、`renew_count`（默认 0）、`fine_amount`（默认 0.00）、`remark`；
   - 完整性校验：`chk_borrow_records_status` 严格约束 6 种流转状态。

### 2.2 性能索引与安全约束

| 索引 / 约束名称 | 目标表 | 索引列及定义 | 优化场景 |
|---|---|---|---|
| `uk_borrow_records_no` | `borrow_records` | `UNIQUE (record_no)` | 业务流水单号唯一性保障 |
| `idx_borrow_records_user_status_due` | `borrow_records` | `(user_id, status, due_at)` | 读者“我的在借”高频快速查询与即将到期排序 |
| `idx_borrow_records_active` | `borrow_records` | `(user_id, book_id) WHERE status IN ('BORROWING', 'OVERDUE')` | 部分索引（Partial Index），极速拦截同书重复借阅防刷 |
| `idx_borrow_records_due_scan` | `borrow_records` | `(due_at) WHERE status = 'BORROWING'` | 逾期扫描与定时催还高效检索 |
| `fk_borrow_records_copy` | `borrow_records` | `FOREIGN KEY (copy_id) REFERENCES book_copies(id)` | 保证借阅记录物理单册真实存在 |

### 2.3 RBAC 权限初始化

在 `permissions` 表中补齐借阅流通所需细粒度权限，并赋予相应角色：
- `borrow:apply`：图书借阅出库申请（赋予 `STUDENT`, `TEACHER`, `LIBRARIAN`, `ADMIN`）
- `borrow:return`：图书归还结清（赋予 `STUDENT`, `TEACHER`, `LIBRARIAN`, `ADMIN`）
- `borrow:renew`：图书顺延续借（赋予 `STUDENT`, `TEACHER`, `LIBRARIAN`, `ADMIN`）
- `borrow:query:my`：查询个人在借与历史借阅（赋予 `STUDENT`, `TEACHER`, `LIBRARIAN`, `ADMIN`）
- `borrow:query:all`：全馆借阅流水分页审计与监管（赋予 `LIBRARIAN`, `ADMIN`）

---

## 三、后端架构与并发安全核心

### 3.1 自顶向下严格有序悲观写锁机制

在高并发争抢环境下，多个请求同时竞争同一书目的余本，若无顺序控制极易引发超卖（overselling）或死锁（deadlock）。
Stage 3 采用严格的**自顶向下排他锁（Top-Down Locking）**架构：

```
[ HTTP 借阅请求到达 (事务隔离级别: READ_COMMITTED) ]
                         │
                         ▼
1. 基础业务风控检查 (名下借阅数超限？存在逾期未还图书？同种图书已在借？)
                         │
                         ▼
2. 锁定书目记录: SELECT ... FROM books WHERE id = ? FOR UPDATE
   (确立事务互斥入口，所有并发线程在此严格排队，杜绝脏读与虚假库存)
                         │
                         ▼
3. 校验书目库存: available_copies > 0 且 status == ACTIVE
   (若已无余本，直接抛出 ResultCode.BOOK_NO_AVAILABLE_COPY 409 拦截，快速失败)
                         │
                         ▼
4. 锁定具体单册: SELECT ... FROM book_copies WHERE id = ? FOR UPDATE
   (自顶向下加锁，绝不发生反向加锁，死锁概率在数学模型上归零)
                         │
                         ▼
5. 原子变更物理状态:
   - copy.setStatus(BookCopyStatus.BORROWED)
   - book.setAvailableCopies(book.getAvailableCopies() - 1)
   - 插入 borrow_records 流水记录
                         │
                         ▼
[ 事务提交 (COMMIT)，行级排他锁释放，下一个排队线程进入检查 ]
```

### 3.2 业务防御与安全控制 (Guardrails)

- **防越权拦截（IDOR Protection）**：学生 A 尝试对学生 B 的借阅记录进行续借或还书操作时，被 `ResultCode.AUTH_FORBIDDEN` 强制拦截；图书管理员与系统管理员具备全局代办权限；
- **防刷同书拦截（Duplicate Borrow Defense）**：在借状态下同一读者禁止借阅同一书目的不同单册，规避资源恶意囤积（`DUPLICATE_BORROW_SAME_BOOK`）；
- **逾期冻结借新拦截（Overdue Freeze）**：读者名下只要存在逾期未还图书，禁止新借任何图书（`USER_HAS_OVERDUE_BOOKS`）；
- **续借规则硬约束**：达到最大续借次数（1次）禁止再续（`RENEW_COUNT_EXCEEDED`）；逾期图书在规则未开放情况下禁止顺延（`RENEW_OVERDUE_NOT_ALLOWED`）；
- **已还单防重处理**：同一借阅记录严禁重复归还结清（`BORROW_RECORD_ALREADY_RETURNED`）。

---

## 四、后端 API 规范

| 请求方法 | 路径 | 权限要求 | 请求 Body / 参数 | 响应结果 | 说明 |
|---|---|---|---|---|---|
| **POST** | `/api/v1/borrow-records/borrow` | `borrow:apply` | `BorrowCreateRequest` (`bookId`, `copyBarcode`) | `BorrowRecordResponse` | 读者借阅出库（自顶向下排他锁，支持自动分派或条码锁定） |
| **POST** | `/api/v1/borrow-records/{id}/return` | `borrow:return` | `id` (PathVariable) | `BorrowRecordResponse` | 图书归还结清（单册与库存回滚，自动计算逾期罚金） |
| **POST** | `/api/v1/borrow-records/{id}/renew` | `borrow:renew` | `id` (PathVariable) | `BorrowRecordResponse` | 图书顺延续借（还期延长30天，续借计数递增） |
| **GET** | `/api/v1/borrow-records/my-active` | `borrow:query:my` | `page`, `size` | `PageResult<BorrowRecordResponse>` | 分页查询当前登录用户的未归还在借图书（带倒计时计算） |
| **GET** | `/api/v1/borrow-records/my-history`| `borrow:query:my` | `page`, `size` | `PageResult<BorrowRecordResponse>` | 分页查询当前登录用户的历史已还图书 |
| **GET** | `/api/v1/borrow-records` | `borrow:query:all` | `BorrowQueryParam` (支持关键词/状态/逾期/时间范围过滤) | `PageResult<BorrowRecordResponse>` | 管理员全馆流通记录综合查询与审计 |

---

## 五、Flutter 前端体验

1. **[NEW] `BorrowCirculationScreen` 双 Tab 借阅管理看板**：
   - **“当前在借” Tab**：
     - 卡片化呈现书名、作者、单册排架号、借出时间与应还时间；
     - **动态倒计时徽章**：距还期 > 3 天显示绿色（`剩余 X 天`）；距还期 ≤ 3 天显示橙色告警；已逾期显示红色高亮（`已逾期 X 天`）；
     - **快捷操作按钮**：卡片右侧集成“续借”与“还书”按钮，附带操作确认弹窗，完成后自动平滑刷新状态；
     - **空状态友好引导**：在借为空时提供“去馆藏借书”一键跳转。
   - **“借阅历史” Tab**：
     - 展示历史借还流水，包含归还时间、已续借次数及逾期罚金核算（金额大于 0 时红字警示）。
2. **`BookDetailScreen` 图书详情一键借阅闭环**：
   - 将原 Stage 2-B 的占位 Snackbar 替换为真实借阅弹窗确认；
   - 校验当前书目是否有在馆可借副本；若无余本，借阅按钮置灰并提示“暂无在架副本”；
   - 借阅成功后弹出 SnackBar 提示，并即刻刷新图书详情页的可用库存数与单册借出状态。
3. **`AppRouter` 导航集成**：
   - 在底栏 NavigationBar 中将 Tab 2 激活并绑定为“借阅流通”（`BorrowCirculationScreen`）；
   - 读者随时可以便捷切换并查看自己的在借状态。
4. **状态管理分层（Riverpod）**：
   - `activeBorrowsProvider`：维护读者在借清单与状态变更响应；
   - `borrowHistoryProvider`：维护已还历史分页数据。

---

## 六、测试验证与全套回归

### 6.1 50 线程高并发零超卖压力测试 (`ConcurrentBorrowTest`)

- **测试场景**：准备一本仅有 1 册物理单册的绝版书（`totalCopies=1, availableCopies=1`），创建 50 个独立的真实学生读者账号；
- **并发触发**：50 个线程同时在 `CountDownLatch` 信号枪下向 `borrowBook` 发起冲击；
- **验证结果**：
  ```
  [INFO] Running com.library.ConcurrentBorrowTest
  2026-09-17 17:23:38.534 DEBUG org.hibernate.SQL - select ... from books for no key update
  2026-09-17 17:23:38.542 DEBUG org.hibernate.SQL - select ... from borrow_records
  [INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.291 s -- in com.library.ConcurrentBorrowTest
  ```
  - ✅ **恰好 1 笔请求成功入库** (`successCount == 1`)；
  - ✅ **其余 49 笔请求全部被安全拦截为 409 库存不足** (`outOfStockCount == 49`)；
  - ✅ **0 笔死锁或未捕获系统异常** (`otherErrorsCount == 0`)；
  - ✅ **数据库最终库存精确归零** (`available_copies == 0`)；
  - ✅ **流水总记录数恰好为 1** (`recordCount == 1`)。

### 6.2 业务状态机与安全防护测试 (`BorrowCirculationServiceTest`)

全量 11 个集成测试用例全部通过：
1. `borrowBook_Success_AutoAssignCopy`：自动分派可用单册借出，单册变 `BORROWED`，库存减 1；
2. `borrowBook_Success_WithBarcode`：按指定条形码借出，指定单册状态精准更新；
3. `borrowBook_DuplicateBook_ThrowsException`：同种图书重复借阅防刷拦截；
4. `borrowBook_HasOverdueBook_ThrowsException`：读者有名下逾期图书借新书被阻断；
5. `returnBook_Normal_Success`：按期还书恢复在架，库存原子回滚，罚金为 0；
6. `returnBook_Overdue_CalculatesFine`：逾期还书状态变更为 `OVERDUE_RETURNED`，罚金精准核算；
7. `returnBook_AlreadyReturned_ThrowsException`：已还借单不可重复归还；
8. `renewBook_Success`：合规续借成功顺延 30 天，续借次数累加；
9. `renewBook_ExceedMaxCount_ThrowsException`：达到最大续借上限再次续借被阻断；
10. `idor_Protection_StudentACannotOperateStudentBRecord`：跨读者越权操作借单严密防护；
11. `queryRecords_ActiveAndHistory_Success`：分页检索我的在借与借阅历史数据正确。

### 6.3 RBAC 权限测试 (`BorrowPermissionTest`)

全量 4 个用例全部通过：
1. 匿名用户访问借阅端点拦截为 401 Unauthorized；
2. 普通学生访问全馆借阅流水拦截为 403 Forbidden；
3. 图书管理员访问全馆借阅流水允许访问 200 OK；
4. 普通学生访问“我的在借”流水允许访问 200 OK。

### 6.4 后端全量测试回归 (`mvn test`)

```
[INFO] Results:
[INFO] 
[INFO] Tests run: 106, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  27.784 s
```
**总计 106 个测试用例，0 失败，0 错误，100% 通过！**

### 6.5 Flutter 单元与组件测试 (`flutter test`)

```
00:00 +0: loading D:/wkk/Campus Library Borrowing System/frontend/test/auth_provider_test.dart
...
00:01 +21: D:/wkk/Campus Library Borrowing System/frontend/test/book_search_screen_test.dart: 图书检索页面 - 500ms 防抖输入逻辑验证
00:01 +22: All tests passed!
```
**总计 22 个前端测试全部通过！**

### 6.6 Flutter 静态代码分析 (`flutter analyze`)

```
Analyzing frontend...                                           
No issues found! (ran in 2.3s)
```
**0 issues found! 代码质量完全符合规范。**

---

## 七、交付成果物清单

### 7.1 后端工程
- 数据库脚本：`backend/src/main/resources/db/migration/V5__create_borrow_circulation_tables.sql`
- 实体与枚举：
  - `com.library.domain.enums.BorrowRecordStatus`
  - `com.library.domain.entity.BorrowingRule`
  - `com.library.domain.entity.BorrowRecord`
  - `com.library.domain.entity.User` (关联 `BorrowingRule`)
- 数据访问层：
  - `com.library.repository.BookRepository` (增加悲观锁查询 `findByIdForUpdate`)
  - `com.library.repository.BookCopyRepository` (增加悲观锁查询 `findByIdForUpdate`, `findByBarcodeForUpdate`, `findAvailableCopiesForUpdate`)
  - `com.library.repository.BorrowingRuleRepository`
  - `com.library.repository.BorrowRecordRepository`
- 传输模型：
  - `com.library.dto.borrow.BorrowCreateRequest`
  - `com.library.dto.borrow.BorrowRecordResponse`
  - `com.library.dto.borrow.BorrowQueryParam`
  - `com.library.dto.common.PageResult` (增加泛型映射 `from(...)`)
- 业务服务与控制器：
  - `com.library.service.BorrowCirculationService`
  - `com.library.service.impl.BorrowCirculationServiceImpl`
  - `com.library.controller.BorrowRecordController`
- 自动化测试用例：
  - `com.library.BorrowCirculationServiceTest` (11 个测试)
  - `com.library.BorrowPermissionTest` (4 个测试)
  - `com.library.ConcurrentBorrowTest` (50 线程压力测试)

### 7.2 前端工程
- 数据模型：`frontend/lib/data/models/borrow_record_model.dart`
- 仓储层：`frontend/lib/data/repositories/borrow_repository.dart`
- 状态管理：`frontend/lib/providers/borrow_provider.dart`
- 交互页面：
  - `frontend/lib/screens/borrow/borrow_circulation_screen.dart`
  - `frontend/lib/screens/books/book_detail_screen.dart` (借阅触发联动)
  - `frontend/lib/router/app_router.dart` (底部 Tab 路由挂载)
- 组件测试：`frontend/test/borrow_circulation_test.dart`

---

## 八、下一步规划 (Stage 4)

Stage 3 借阅流通领域模型与并发控制已圆满通过 Gate，按项目规划后续将进入：
**Stage 4：图书预约与排队流转系统 (Reservation & Waiting Queue System)**
- 重点解决：当书目所有单册均处于 `BORROWED` 状态时的读者排队预约机制；
- 读者预约时限、最大预约配额；
- 归还入库后触发预约命中（通知与保留期倒计时）；
- 严格遵循阶段纪律，不提前进入 Stage 5（AI 推荐）。
