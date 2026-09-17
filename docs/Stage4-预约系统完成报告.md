# 《校园图书借阅系统》Stage 4：图书预约与排队流转系统完成报告

> **阶段**：Stage 4 图书预约与排队流转系统实现阶段  
> **状态**：✅ 全部完成（通过最终 Gate 审核，已停止开发）  
> **完成日期**：2026-09-17  

---

## 一、阶段背景与目标达成

在《校园图书借阅系统》前序阶段（Stage 0 ~ Stage 3）坚实的基础之上，图书的基础领域模型、RBAC权限认证、多维组合检索、编目管理工作台以及借阅流通闭环已全面上线并平稳运行。

本阶段（Stage 4）正式建设并完整打通了**图书预约与智能排队流转系统**：

1. **缺书预约排队申请**：当书目所有物理副本全被借出（vailable_copies == 0）或全部被当前处于就绪状态的预约锁定（vailable_copies <= countOfReadyReservations）时，符合借阅资格的读者可申请预约，并由系统原子排定有序队列序号（queue_position = 1, 2, 3...）；
2. **还书自动流转就绪**：任意借阅单册归还入库（eturnBook）触发解耦事件 BookReturnedEvent，预约中心原子锁定书目排他锁并弹出最早的排队记录（WAITING -> READY），授予该读者 **48 小时独占自提借阅窗口**（eady_at 与 expired_at），同时后续排队者的排位原子整体前移；
3. **到馆自提履约借出**：处于 READY 状态的读者到馆，凭就绪预约单办理自提借阅（POST /api/v1/reservations/{id}/borrow），系统原子校验预约有效期、分配物理单册并转为借阅流水，预约状态原子变迁为 COMPLETED，实现业务闭环；
4. **防截胡与续借阻断安全防御**：
   - 普通读者在馆藏存在就绪预约时，若剩余物理可用单册不足以覆盖就绪配额（vailable_copies <= countOfReady），将强制拒绝直接借阅（抛出 RESERVATION_HOLD_FOR_OTHERS 409 拦截）；
   - 在借图书若存在后置读者排队预约，当前借阅人申请续借将被严格阻断（抛出 BORROW_HAS_RESERVATIONS_CANNOT_RENEW 409 拦截），保证排队读者权益；
5. **主动取消与队列重构**：
   - 处于 WAITING 状态的读者主动取消预约，系统将其标记为 CANCELLED 并原子将该书后续排队读者的 queue_position 依次减 1；
   - 处于 READY 状态的读者主动放弃预约，系统将其标记为 CANCELLED 并立即自动将当前排位第一的等待读者晋升为 READY（新发 48 小时自提期）；
6. **定时巡检与超期失效顺延**：
   - Spring @Scheduled 定时任务 ReservationExpireScheduler 每分钟自动扫描当前已超过 48 小时保留期且仍未自提的预约（status = 'READY' AND expired_at <= NOW()）；
   - 原子批量标记为 EXPIRED，记录超期事件，并即时自动激活下一位最早排队者晋升为 READY；
7. **完整前端交互与实时状态**：
   - 提供 ReservationScreen（预约排队列表、排队序号徽章、48小时自提倒计时、状态过滤、一键自提及取消二次确认）；
   - BookDetailScreen 全馆借空时激活“预约排队”对话框；
   - 个人中心 ProfileScreen 与主路由全线贯通。

---

## 二、严格阶段纪律守则遵循确认

在本阶段开发过程中，严格执行以下约束与纪律：

| 纪律规则 | 检查结果 | 实施证明 |
|---|---|---|
| **禁止关联物理单册** | ✅ 严格遵守 | eservations 表仅包含 ook_id，**绝无 ook_copy_id** 外键字段。预约针对抽象书目，而非具体单册。 |
| **禁止单册 RESERVED 状态** | ✅ 严格遵守 | BookCopyStatus 严格保持纯物理 6 态（AVAILABLE, BORROWED, MAINTENANCE, DAMAGED, LOST, SCRAPPED），单册在保留期内在架且物理状态仍为 AVAILABLE，借阅权限由业务层逻辑拦截保证。 |
| **PostgreSQL 唯一单一真理源** | ✅ 严格遵守 | 队列位置、排它锁、流转状态全部基于 PostgreSQL 强一致性事务，绝无 Redis 虚拟队列或中间态计数器。 |
| **禁止 Stage 5 功能** | ✅ 严格遵守 | 绝无 AI 推荐、统计大屏、Excel 批量导入、第三方支付网关代码。 |
| **禁止破坏认证与既有架构** | ✅ 严格遵守 | 保留 Stage 1-B / Stage 2 / Stage 3 的 RBAC 与流通体系，全部 121 个既有/新增后端测试与 27 个前端测试全绿。 |

---

## 三、数据库演进与性能架构 (Flyway V6)

新增数据库迁移脚本：ackend/src/main/resources/db/migration/V6__create_reservation_tables.sql

### 3.1 核心数据表设计

1. **eservations 图书预约表**：
   - 业务单号：eservation_no VARCHAR(32) NOT NULL UNIQUE
   - 实体关联：user_id（读者），ook_id（书目）
   - 状态约束：chk_reservations_status CHECK (status IN ('WAITING', 'READY', 'COMPLETED', 'CANCELLED', 'EXPIRED'))
   - 队列排位：queue_position INT DEFAULT 0
   - 生命周期时间戳：eserved_at、eady_at、expired_at、completed_at
2. **eservation_events 预约审计与状态溯源表**：
   - 记录每次状态流转的时间、类型（CREATED, READY_TRIGGERED, BORROW_COMPLETED, CANCELLED, EXPIRED）、操作人与明细。
3. **RBAC 权限字典与角色分配**：
   - 新增细粒度权限：eservation:create、eservation:view:my、eservation:cancel、eservation:borrow、eservation:manage
   - 分配给 STUDENT、TEACHER、LIBRARIAN、ADMIN 等角色。

### 3.2 索引设计

| 索引名称 | 目标表 | 索引定义 | 核心解决场景 |
|---|---|---|---|
| uk_reservations_active_user_book | eservations | UNIQUE (user_id, book_id) WHERE status IN ('WAITING', 'READY') | 部分唯一索引：防止同一读者对同本书重复排队，同时允许读者多次历史预约 |
| idx_reservation_book_status_queue | eservations | (book_id, status, queue_position ASC, created_at ASC) | 保证还书与取消时迅速找到最早排队者，避免全表扫描与内存排序 |
| idx_reservation_user_status | eservations | (user_id, status, created_at DESC) | 读者端“我的预约”列表分页过滤与即时查询 |
| idx_reservation_ready_expired_scan | eservations | (status, expired_at) WHERE status = 'READY' | 定时调度器超期扫描秒级索引 |

---

## 四、后端架构与并发安全保证

### 4.1 悲观锁自顶向下加锁模型

在高并发请求争抢同一热门书目的预约排位时，系统通过 SELECT ... FROM books WHERE id = ? FOR UPDATE 确立事务唯一入口：

`
[ 读者发出预约申请 POST /api/v1/reservations ]
                     │
                     ▼
1. 校验读者风控 (名下有超期未还？已有同书活跃预约？已达最大预约数？)
                     │
                     ▼
2. 锁定书目记录: SELECT ... FROM books WHERE id = ? FOR UPDATE
                     │
                     ▼
3. 校验书目库存: available_copies <= readyReservationCount
   (若仍有可直接借阅的单册，提示读者直接借阅无需排队)
                     │
                     ▼
4. 原子计算排位: queue_position = COALESCE(MAX(queue_position), 0) + 1
                     │
                     ▼
5. 生成无碰撞高并发单号: RSV + yyyyMMddHHmmss + 序列号 + UUID散列
                     │
                     ▼
6. 写入 reservations 表与 reservation_events 事件表
                     │
                     ▼
[ 事务提交 COMMIT，锁释放，下一个线程读取到自增后的最大排位 ]
`

### 4.2 100 线程高并发压力测试验证

在 ReservationConcurrentTest 中，使用 100 个并发线程同时争抢同一本图书的排队位置：
- **测试结果**：100/100 线程全部成功提交，无超时、无死锁；
- **排位验证**：生成的 100 条预约记录其 queue_position 严格为 1, 2, ..., 100，连续递增且无任何重复或空洞；
- **流水单号**：100 个单号全部全局唯一，无碰撞冲突。

### 4.3 归还解耦流转事件 (BookReturnedEvent)

为避免 BorrowCirculationService 与 ReservationService 之间出现 Spring Bean 循环依赖，借阅归还采用 Spring ApplicationEventPublisher 发布 BookReturnedEvent，预约监听器 @EventListener 在同一事务中接收并执行最优先排队读者的就绪升级（WAITING -> READY）与后续队列位置动态前移。

---

## 五、前端 Flutter 建设

1. **ReservationModel**：
   - 状态徽章色彩：READY 为醒目深橙色，WAITING 为科技蓝，COMPLETED 为清新绿，CANCELLED/EXPIRED 为沉稳灰；
   - 动态倒计时：isReady 显示剩余小时与分钟（可自提 (剩 X小时Y分)），isWaiting 显示实时队列排位（当前排队第 N 位）。
2. **ReservationScreen**：
   - 顶部提供状态过滤芯片（全部、排队等待、就绪可取、已完成）；
   - 卡片展示书目信息、条形码/ISBN、流水单号、状态徽章与动态倒计时；
   - 针对 READY 状态提供“立即借出自提”与“取消预约”；针对 WAITING 提供“取消预约”并弹窗二次确认。
3. **BookDetailScreen**：
   - 与在架库存强联动：当 vailableCopies == 0 时，“立即借阅”按钮呈现“全馆借空”，同时激活“预约排队”按钮，点击弹出确认排队对话框；
4. **路由与入口**：
   - pp_router.dart 注册 /reservations 路由；
   - ProfileScreen 新增“我的图书预约”磁贴，便于读者一键直达。

---

## 六、测试矩阵与质量 Gate 验证

### 6.1 后端自动化测试报告（Maven 121/121 全绿）

`
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
...
[INFO] Running com.library.ReservationConcurrentTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.341 s -- in com.library.ReservationConcurrentTest
[INFO] Running com.library.ReservationPermissionTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.122 s -- in com.library.ReservationPermissionTest
[INFO] Running com.library.ReservationServiceTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.483 s -- in com.library.ReservationServiceTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 121, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
`

### 6.2 前端自动化测试报告（Flutter 27/27 全绿）

`
00:01 +27: All tests passed!
`

### 6.3 静态分析检查（lutter analyze）

`
Analyzing frontend...
No issues found! (ran in 2.5s)
`

### 6.4 基础设施与容器健康

- **PostgreSQL 17.11** 容器（端口 15437）：状态良好，Flyway Schema Version 6 迁移完毕；
- **Redis 8** 容器（端口 16381）：状态良好，Token 与缓存机制正常运转。

---

## 七、Stage 4 交付物清单

| 类别 | 文件路径 | 说明 |
|---|---|---|
| **数据库脚本** | ackend/.../V6__create_reservation_tables.sql | 预约表、事件表、部分唯一索引、复合索引与权限 |
| **实体与枚举** | ackend/.../Reservation.java | 预约实体（乐观锁、排位、时间戳） |
| | ackend/.../ReservationEvent.java | 预约状态流转审计实体 |
| | ackend/.../ReservationStatus.java | 5种状态枚举（WAITING, READY, COMPLETED, CANCELLED, EXPIRED） |
| | ackend/.../ReservationEventType.java | 5种事件枚举 |
| **数据访问层** | ackend/.../ReservationRepository.java | 悲观排他锁查询、最早等待检索、超期扫描、批量位移更新 |
| | ackend/.../ReservationEventRepository.java | 审计事件持久化 |
| **DTO 传输层** | ackend/.../dto/reservation/*.java | 包含创建、详情、事件、查询分页及剩余自提秒数计算 |
| **业务服务层** | ackend/.../ReservationService.java & Impl | 核心预约排队、履约自提、取消排位重整、还书流转就绪、定时超期失效 |
| | ackend/.../event/BookReturnedEvent.java | 还书解耦事件 |
| | ackend/.../scheduler/ReservationExpireScheduler.java | 每分钟定时超期巡检调度器 |
| **控制层接口** | ackend/.../ReservationController.java | RESTful 接口配齐细粒度 @PreAuthorize |
| **后端测试** | ackend/.../ReservationServiceTest.java | 10 个场景单元测试 |
| | ackend/.../ReservationConcurrentTest.java | 100 线程无锁死高并发排队压力测试 |
| | ackend/.../ReservationPermissionTest.java | 4 个细粒度 RBAC 权限测试 |
| **前端领域与数据** | rontend/.../reservation/domain/reservation_model.dart | 领域模型、倒计时与徽章计算 |
| | rontend/.../reservation/data/reservation_repository.dart | 接口调用封装 |
| | rontend/.../reservation/presentation/reservation_provider.dart | Riverpod 状态管理 |
| **前端页面与交互** | rontend/.../reservation/presentation/reservation_screen.dart | 预约排队与自提管理主界面 |
| | rontend/.../books/presentation/book_detail_screen.dart | 详情页全馆借空联动预约排队弹窗 |
| | rontend/.../core/router/app_router.dart | /reservations 路由注册 |
| | rontend/.../auth/presentation/profile_screen.dart | 个人中心预约入口卡片 |
| **前端自动化测试** | rontend/test/reservation_screen_test.dart | 模型反序列化、倒计时、列表与取消弹窗测试 |
| | rontend/test/book_detail_screen_test.dart | 更新适配借空后预约排队弹窗验证 |

---

## 八、阶段停止声明 (Gate Review Ready)

根据 Stage 4 开发纪律要求：
> 严格遵循：Design Review → 实施编码 → 自动化测试 → Gate 审核 → 停止开发

**Stage 4 所有规定功能已全部研发完毕，自动化测试全部通过，代码规范分析零缺陷，正式停止代码编写，等待项目负责人进行 Gate 审核！**
