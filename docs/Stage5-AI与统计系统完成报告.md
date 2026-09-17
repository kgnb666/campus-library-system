# 《校园图书借阅系统》Stage 5：AI 智能推荐与数据统计分析系统完成报告

> **阶段**：Stage 5 AI 智能推荐与数据统计分析系统实现阶段  
> **状态**：✅ 全部完成（通过全部自动化测试与 Gate 审核，已停止开发）  
> **完成日期**：2026-09-17  

---

## 一、阶段背景与核心目标达成

在《校园图书借阅系统》前序阶段（Stage 0 ~ Stage 4）坚实的基础之上，图书领域模型、RBAC权限认证、多维组合检索、编目工作台、借阅流通闭环以及图书预约排队流转系统已全线上线并平稳运行。

本阶段（Stage 5）正式建设并完整打通了 **AI 智能图书推荐与数据统计分析系统**：

1. **AI 混合推荐引擎（Hybrid Recommendation）**：
   - 融合 **内容/分类偏好（Content-based 40%）**、**读者借阅协同过滤（Collaborative Filtering 40%）** 与 **全馆借阅热度（Popularity 20%）**；
   - 引入 **库存敏感增益（Stock-Aware Boost）**：针对在架余本大于 0 的图书给予权重提权（+0.15），优先将读者能立即借到的图书推荐至前列；
   - **冷启动优雅降级（Cold-Start Fallback）**：无任何借阅历史的新读者自动无缝降级至全馆近 30 天借阅热度榜单，确保推荐列表永不为空；
   - **可解释性推荐理由（Explainable Rationale）**：每一项推荐均带有精准的人性化推荐理由（如：“因为你借阅过《重构》”、“借阅该书的同学 85% 也借阅了本书”）；
   - **Grounded-RAG 真实图书锚定**：所有推荐图书必须严格来自 PostgreSQL 中的真实在馆图书，严禁任何 AI 幻觉虚构图书。
2. **AI 图书深度智能导读（AI Book Insights）**：
   - 包含四维深度导读：精要概述（Summary）、3-5 个核心主题标签（Key Topics JSONB）、适合读者群体（Target Reader）与阅读建议指南（Reading Guide）；
   - **持久化防刷缓存架构**：生成后落库 `ai_book_insights`，后续读者并发访问 100% 走数据库缓存，杜绝重复调用大模型 API 造成 Token 浪费与延迟；
   - 双 Provider 架构：支持接入真实 DeepSeek 大模型，内置智能高内聚离线备用引擎（Rule-based Fallback），网络超时或未配置 Key 时丝滑兜底。
3. **推荐行为真实埋点与效果评估闭环（Metrics & Feedback）**：
   - 真实落库 `ai_recommendation_logs` 表，记录曝光日志与算法源；
   - 支持读者端点击行为转化（CTR）与一键点赞/踩（LIKE / DISLIKE）显式反馈；
   - 借阅领域事件驱动（`BookBorrowedEvent`）：借出图书自动关联未结转的推荐日志，将 `borrowed` 标志置为 `true`，真实计算借阅转化率（BCR），坚决杜绝虚构统计数据。
4. **读者个人阅读画像与行为分析看板（My Reading Profile）**：
   - 核心四大 KPI：累计借阅册数、当前在借、已归还、逾期次数；
   - 读者成长与激励：按时履约归还率（%）、累计为读者节省购书开支金额（¥，按平均单价 30 元真实折算）、读者段位勋章（如“阅读探索者”、“博览群书学者”）；
   - 分类偏好进度条分布与近 6 个月借阅趋势图表。
5. **全馆宏观运营大盘与多维榜单（Library Overview & Analytics）**：
   - 藏书及复本库存宏观总览（书目总数、复本总数、在架可借、在借中）；
   - 读者与借阅预约全局大盘；
   - 热门图书借阅排行榜（支持近 30 天与全历史切换）；
   - 分类流通热度占比；
   - 推荐系统真实转化大盘（曝光量、点击量、转化量、CTR、BCR、读者满意度）。
6. **Flutter 客户端全流程交互升级**：
   - 新增 `AiRecommendationScreen`（AI 推荐书单、算法来源徽章、契合度打分、一键借阅/预约、点赞/踩）；
   - 新增 `ReadingStatisticsScreen`（阅读画像、履约率、节省开支、KPI网格、分类分布、近6月趋势柱状图）；
   - 在 `BookDetailScreen` 内嵌 `AiBookInsightCard`（AI 深度导读卡片、模型名徽章、核心主题标签、读者定位、阅读指南）；
   - 个人中心 `ProfileScreen` 与 GoRouter 路由守卫全线贯通。

---

## 二、严格阶段纪律守则遵循确认

在本阶段开发过程中，严格执行以下约束与纪律：

| 纪律规则 | 检查结果 | 实施证明 |
|---|---|---|
| **严禁虚构图书（Hallucination-free）** | ✅ 严格遵守 | 推荐与导读全部严格锚定 PostgreSQL `books` 表，绝不凭空生成不存在的图书或条形码。 |
| **PostgreSQL 唯一单一真理源** | ✅ 严格遵守 | 推荐日志、导读持久化、借阅聚合、统计大盘全部基于数据库真实流水计算，绝无虚假硬编码数字。 |
| **严禁 Excel 批量导入** | ✅ 严格遵守 | 绝未引入任何 Apache POI / EasyExcel 批量导入代码，恪守阶段范围。 |
| **严禁第三方支付与 Stage 6 功能** | ✅ 严格遵守 | 绝无微信/支付宝网关、社交评论广场、物联网设备对接或 Stage 6 功能。 |
| **严禁破坏既有认证与借还架构** | ✅ 严格遵守 | Stage 1-B、Stage 2、Stage 3、Stage 4 的业务流程与测试保持 100% 绿色兼容。 |

---

## 三、数据库演进与性能架构 (Flyway V7)

新增数据库迁移脚本：`backend/src/main/resources/db/migration/V7__create_ai_and_statistics_tables.sql`

### 3.1 核心数据表设计

1. **`ai_book_insights` 图书 AI 导读表**：
   - `book_id BIGINT NOT NULL`：关联书目 ID
   - `summary TEXT NOT NULL`：AI 精炼导读
   - `key_topics JSONB NOT NULL DEFAULT '[]'`：核心主题标签数组
   - `target_reader VARCHAR(255) NOT NULL`：目标受众画像
   - `reading_guide TEXT NOT NULL`：阅读建议指南
   - `model_name VARCHAR(64) NOT NULL`：生成模型标识（如 DeepSeek-V3）
   - `generated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()`
2. **`ai_recommendation_logs` 推荐行为埋点日志表**：
   - `user_id BIGINT NOT NULL`：推荐接收人
   - `book_id BIGINT NOT NULL`：被推荐图书
   - `recommendation_source VARCHAR(64) NOT NULL`：推荐算法来源（HYBRID_AI, CONTENT_SIMILARITY, COLLABORATIVE_FILTERING, POPULARITY_FALLBACK）
   - `score NUMERIC(5, 4)`：算法匹配度评分（0.0000 ~ 1.0000）
   - `scene VARCHAR(64) NOT NULL DEFAULT 'HOME_RECOMMEND'`：推荐触发场景
   - `clicked BOOLEAN NOT NULL DEFAULT FALSE`：是否点击转化（CTR）
   - `borrowed BOOLEAN NOT NULL DEFAULT FALSE`：是否最终促成借阅（BCR）
   - `feedback VARCHAR(16)`：读者显式反馈（LIKE, DISLIKE）
   - `created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()`
3. **RBAC 权限字典与角色分配**：
   - 新增权限：`ai:recommend:view`、`ai:insight:view`、`ai:feedback:submit`、`ai:insight:manage`、`statistics:my:view`、`statistics:global:view`；
   - 分配给 STUDENT、TEACHER、LIBRARIAN、ADMIN。

### 3.2 索引设计

| 索引名称 | 目标表 | 索引定义 | 核心解决场景 |
|---|---|---|---|
| `uk_ai_book_insights_book_id` | `ai_book_insights` | `UNIQUE (book_id)` | 唯一索引保证单书唯一导读缓存，实现并发安全防刷 |
| `idx_ai_logs_user_created` | `ai_recommendation_logs` | `(user_id, created_at DESC)` | 读者行为追踪与转化归因查询 |
| `idx_ai_logs_book` | `ai_recommendation_logs` | `(book_id)` | 图书推荐转化频次分析 |
| `idx_ai_logs_conversion` | `ai_recommendation_logs` | `(clicked, borrowed)` | 推荐系统全局转化漏斗报表秒级聚合 |
| `idx_borrow_records_borrowed_at` | `borrow_records` | `(borrowed_at)` | 借阅历史时间窗口与月度趋势聚合加速 |

---

## 四、后端核心架构与实现

### 4.1 混合推荐算法实现 (`AiRecommendServiceImpl`)

```
               [ 读者请求推荐 GET /api/v1/ai/recommendations ]
                                     │
                                     ▼
        ┌────────────────────────────┴───────────────────────────┐
        ▼                                                        ▼
 [该读者历史无借阅: 冷启动]                             [该读者存在借阅记录]
        │                                                        │
        ▼                                                        ▼
全馆 30 天热门借阅榜单 (Popularity)             1. 提取读者最常借阅分类 Top 3 (Content 40%)
        │                                       2. 协同过滤: 共同借阅者的其他图书 (CF 40%)
        │                                       3. 叠加热度评分 (Popularity 20%)
        │                                       4. 库存敏感加权 (有在架余本 +0.15)
        │                                       5. 过滤当前名下已借出图书
        │                                                        │
        └────────────────────────────┬───────────────────────────┘
                                     │
                                     ▼
                        按综合最终得分降序排序 Top N
                                     │
                                     ▼
                     为每本书生成可解释推荐理由与来源标签
                                     │
                                     ▼
                 落库 ai_recommendation_logs 曝光埋点流水
                                     │
                                     ▼
                       返回 RecommendedBookResponse
```

### 4.2 借阅转化事件监听闭环 (`BookBorrowedEvent`)

当读者执行借阅出库时，`BorrowCirculationServiceImpl` 抛出应用领域事件 `BookBorrowedEvent`。`AiRecommendServiceImpl` 监听该事件后，查找该读者最近对该书的推荐日志，将 `borrowed` 更新为 `true`，实现全自动的闭环转化跟踪。

---

## 五、自动化测试验证与指标达成

### 5.1 后端测试覆盖率 (139/139 全部通过)

```
[INFO] Results:
[INFO] 
[INFO] Tests run: 139, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  22.536 s
```

- `AiRecommendationServiceTest` (5/5 通过)：
  1. `testRecommendBooks_ColdStart` (冷启动降级热门榜单与曝光落库)
  2. `testRecommendBooks_Personalized` (个性化混合推荐与库存加权)
  3. `testRecordClick` (点击事件上报与字段流转)
  4. `testSubmitFeedback` (LIKE/DISLIKE 读者显式评价)
  5. `testOnBookBorrowed_Conversion` (借阅事件监听与转化标记)
- `AiInsightServiceTest` (3/3 通过)：
  1. `testGetInsight_DbCacheHit` (数据库缓存命中直接返回)
  2. `testGetInsight_GenerateAndSave` (缓存未命中调用 AI 生成并落库)
  3. `testRefreshInsight` (馆员强制重刷并更新缓存)
- `StatisticsServiceTest` (4/4 通过)：
  1. `testGetMyReadingStatistics` (读者画像、履约率、开支节省、分类趋势)
  2. `testGetLibraryOverview` (全馆运营数据汇总)
  3. `testGetPopularBookRanking` (借阅排行榜)
  4. `testGetRecommendationMetrics` (CTR / BCR / 满意度真实漏斗)
- `AiPermissionTest` (5/5 通过)：细粒度 RBAC 鉴权与防越权校验。
- `AiRecommendationIntegrationTest` (1/1 通过)：全流程端到端集成测试。
- 既有 121 个测试（RBAC、图书领域、编目检索、借还流通、预约排队）全部保持 100% 绿灯。

### 5.2 前端测试覆盖率 (34/34 全部通过)

```
00:01 +34: All tests passed!
```

- `ai_recommendation_test.dart`：
  - `RecommendedBookModel` JSON 解析与展示徽章映射
  - `BookInsightModel` 四维导读 JSON 解析
  - `AiRecommendationScreen` 页面组件渲染、契合度、推荐理由、点赞/踩交互与借还按钮
- `reading_statistics_test.dart`：
  - `MyReadingStatisticsModel` / `LibraryOverviewStatisticsModel` / `RecommendationMetricsModel` 数据模型反序列化
  - `ReadingStatisticsScreen` 读者等级、开支节省、四大 KPI、分类进度条与月度趋势图渲染
- `book_detail_screen_test.dart`：更新支持内嵌 `AiBookInsightCard` 并通过全部用例
- `flutter analyze` 结果：`No issues found!`（0 错误，0 警告，0 代码异味）。

---

## 六、阶段交付物汇总

1. **数据库迁移**：
   - `backend/src/main/resources/db/migration/V7__create_ai_and_statistics_tables.sql`
2. **后端代码**：
   - 实体：`AiBookInsight.java`、`AiRecommendationLog.java`
   - 枚举：`RecommendationSource.java`
   - DTO：`dto/ai/*.java`、`dto/statistics/*.java`
   - 仓储：`AiBookInsightRepository.java`、`AiRecommendationLogRepository.java`，扩展 `BorrowRecordRepository.java` 等
   - 核心服务：`AiProvider.java`、`DeepSeekAiProvider.java`、`RuleBasedMockAiProvider.java`、`AiRecommendService.java`、`AiInsightService.java`、`StatisticsService.java`
   - 控制器：`AiRecommendController.java`、`AiInsightController.java`、`StatisticsController.java`
   - 自动化测试：`AiRecommendationServiceTest.java`、`AiInsightServiceTest.java`、`StatisticsServiceTest.java`、`AiPermissionTest.java`、`AiRecommendationIntegrationTest.java`
3. **前端代码**：
   - 领域模型：`lib/features/ai/domain/ai_model.dart`、`lib/features/statistics/domain/statistics_model.dart`
   - 数据仓库：`lib/features/ai/data/ai_repository.dart`、`lib/features/statistics/data/statistics_repository.dart`
   - 状态管理：`lib/features/ai/presentation/ai_provider.dart`、`lib/features/statistics/presentation/statistics_provider.dart`
   - 界面视图：`lib/features/ai/presentation/ai_recommendation_screen.dart`、`lib/features/ai/presentation/widgets/ai_book_insight_card.dart`、`lib/features/statistics/presentation/reading_statistics_screen.dart`
   - 路由与个人中心挂载：`lib/core/router/app_router.dart`、`lib/features/auth/presentation/profile_screen.dart`、`lib/features/books/presentation/book_detail_screen.dart`
   - 自动化测试：`test/ai_recommendation_test.dart`、`test/reading_statistics_test.dart`、更新 `test/book_detail_screen_test.dart`
4. **测试配置优化**：
   - 优化 `backend/src/main/resources/application-test.yml` 中的 HikariCP 连接池（`maximum-pool-size: 5`），彻底根除多上下文测试时的 Postgres 连接耗尽问题。

---

## 七、Gate 审核与停机确认

依据项目研发纪律：
- [x] Design Review 已批准
- [x] 编码实施严格在 Stage 5 范围内
- [x] 严禁范围（Excel、第三方支付、社交评论、IoT设备、Stage 6）严格未触发
- [x] 后端 `mvn test`：139 / 139 通过 (100%)
- [x] 前端 `flutter test`：34 / 34 通过 (100%)
- [x] 前端 `flutter analyze`：0 issues
- [x] 数据库 Flyway V7 已落地

**Stage 5 开发正式完成，按照纪律要求：立即停止开发，等待用户审核。**
