# 《校园图书借阅系统》Stage 5：AI 智能推荐与数据统计分析系统 Design Review 审核报告

> **阶段**：Stage 5-A Design Review（只读设计审核阶段）  
> **审查日期**：2026-09-17  
> **阶段纪律**：只读分析，严禁在确认前编写代码、修改数据库或改动 Flutter 页面。  

---

## 一、当前项目架构评估与 Stage 5 接入点分析

在进入 Stage 5 之前，项目已完整通过 Stage 0 ~ Stage 4 全部阶段，系统后端运行在 Spring Boot 3.3 + PostgreSQL 17 + Redis 8 架构上，前端运行在 Flutter 3.47 + Riverpod + Material 3 架构上，已构建了 121 个后端测试与 27 个前端测试的坚实防线。

### 1.1 既有领域模型与数据画像可行性

| 领域实体 | 当前已有字段与能力 | Stage 5 接入与利用方式 |
|---|---|---|
| **User (用户中心)** | id, username, 
ickname, orrow_rule_id, status, RBAC 角色与权限 | 读者的身份主体。用于隔离个人阅读数据、记录个性化推荐日志，并提供细粒度 RBAC 鉴权。 |
| **Book (图书书目)** | id, isbn, 	itle, uthor, category_id, description, cover_url, 	otal_copies, vailable_copies, status | 推荐与导读的核心客体。提供分类关联、作者维度、简介文本挖掘与在架库存感知（Stock-Aware）。 |
| **BorrowRecord (借阅流水)** | id, ecord_no, user_id, ook_id, orrowed_at, due_at, eturned_at, status, enew_count, ine_amount | 读者真实阅读行为的唯一事实源。为行为推荐、协同过滤、借阅排行榜、读者阅读偏好画像提供数据支撑。 |
| **Reservation (图书预约)** | id, user_id, ook_id, status, queue_position, eserved_at | 读者即时强烈借阅意向的反映，可作为近期行为推荐的强化特征因子。 |
| **Category (图书分类)** | id, code, 
ame, parent_id (多级分类树) | 读者偏好分类统计、分类热度排行、内容匹配得分的核心分类维度。 |

### 1.2 基础设施与技术栈兼容性

1. **PostgreSQL 17**：
   - 作为系统**唯一单一真理源（Single Source of Truth）**，承担所有推荐日志、导读缓存与业务流水的强一致性存储；
   - 具备高效的索引类型（B-tree、Partial Index、JSONB）支持聚合分析与精准埋点追溯。
2. **Redis 8**：
   - 当前用于 JWT Refresh Token 黑白名单管理；
   - 在 Stage 5 中，可用于高频全馆统计指标（如 TOP 10 热门图书榜单、全馆借阅概览指标）的短时读缓存（TTL 10~30 分钟），避免大屏和高频分析对 OLTP 业务数据库造成锁争用与慢查询。
3. **Spring Security 6 RBAC 体系**：
   - 原生支持基于注解的方法级细粒度鉴权（@PreAuthorize）；
   - 保证读者只能访问自己的阅读分析与推荐反馈，图书管理员与系统管理员才能访问全馆大盘。
4. **Flutter 3.47 + Riverpod 架构**：
   - 统一由 Dio 拦截器自动携带认证 Token；
   - 具备 AsyncNotifier / StateNotifier 规范的状态机管理体系，适合实现推荐瀑布流、AI 导读折叠卡片与阅读数据统计图表。

---

## 二、AI 推荐系统设计方案 (Hybrid Recommendation & Closed-Loop Evaluation)

### 2.1 拒绝虚假 AI 准确率的设计哲学

在常见学术或毕设项目中，经常出现“模型推荐准确率 98.7%”等脱离实际的编造数据。本系统坚决摒弃虚假指标，确立三大工程铁律：
1. **馆藏保真（Grounded-RAG）**：推荐的每一本书必是本馆当前 PostgreSQL 数据库中真实收录、有明确 ook_id、ISBN 和索书号的实体书，绝不让大模型凭空臆造虚构图书；
2. **真实数据闭环（Closed-Loop Audit）**：不谈虚假离线准确率，通过真实在线行为埋点，以 **曝光量、点击率 CTR、借阅转化率、用户满意度** 等客观商业指标来度量推荐系统的实效；
3. **可解释性（Explainability）**：每一条推荐结果必须向读者公开解释推荐原因（例如：“因您曾借阅《深入理解计算机系统》，为您推荐同类经典”、“本月全馆借阅热门 TOP 3”）。

### 2.2 混合推荐算法模型 (Hybrid Recommendation Model)

鉴于高校毕设系统的数据规模与工程可维护性，不采用不可解释的复杂黑盒深度网络，采用透明、高效且可量化的**多路加权启发式混合推荐算法**：

\text{RecommendScore}(u, b) = 0.4 \times \text{ContentScore}(u, b) + 0.4 \times \text{BehaviorScore}(u, b) + 0.2 \times \text{PopularityScore}(b) + \text{StockBoost}(b)

#### (1) 内容相关度得分：$\text{ContentScore}(u, b) \in [0, 100]$
- **分类契合度 (40分)**：分析读者历史借阅/预约书目的分类分布。命中读者第一高频偏好分类得 40 分，命中第二偏好分类得 25 分，其他命中分类得 10 分。
- **作者契合度 (40分)**：若候选图书的作者在读者历史阅读记录中出现过，加 40 分；同出版社加 10 分。
- **文本相似度 (20分)**：书名关键词或简介文本与读者借阅过的同类书目重合度。

#### (2) 行为协同度得分：$\text{BehaviorScore}(u, b) \in [0, 100]$
- **已读去重原则 (Hard Filter)**：读者当前在借或历史归还过的相同图书，得分直接归零（排除重复推荐）；
- **近期行为时效加权 (Time Decay)**：近 30 天内有借阅/预约的分类，权重乘数 .0$；30~90 天乘数 .8$；90~180 天乘数 .5$；
- **预约协同加权**：若读者近期因缺书预约过某类书，对该类别未借书目额外赋予 20 分意向加分。

#### (3) 全馆热门度得分：$\text{PopularityScore}(b) \in [0, 100]$
- 统计全馆最近 90 天内该书的借阅次数 $：
  \text{PopularityScore}(b) = \min\left(100, \frac{N_b}{\max(1, N_{\text{hot\_top}})} \times 100\right)
- 保障冷启动（新用户没有任何借阅记录时）能平滑降级为全馆优质热门书目推荐。

#### (4) 在架优先奖励加权：$\text{StockBoost}(b)$
- 若 .available_copies > 0，额外赋予 $+15$ 分奖励加权；
- 确保学生在推荐列表中一眼看到的图书大多数“到馆即借”，极大提升线下流通转化效率。

---

## 三、AI 智能导读设计方案 (i_book_insights)

### 3.1 业务价值与防重复调用机制

- **读者痛点**：图书详情页原有的出版社官方简介通常晦涩冗长（动辄上千字），读者难以在 3 秒内判断该书是否适合自己当前的学习阶段。
- **AI 智能导读能力**：提炼生成**核心内容简介（150字内）、3~5个核心主题标签、适合阅读人群、阶段性阅读指南与核心推荐理由**。
- **持久化防刷防重机制**：
  - 大模型 API 调用存在延迟（1~3秒）与 Token 成本；
  - 设计 **DB-First 持久化存储策略**：首次打开图书详情时若 i_book_insights 表中不存在记录，触发异步/同步生成并落库；后续读者访问该书直接从 PostgreSQL 读取，实现毫秒级瞬间呈现；
  - 仅图书管理员拥有强制刷新重新生成导读的权限。

### 3.2 实体与字段规范

`sql
CREATE TABLE ai_book_insights (
    id BIGSERIAL PRIMARY KEY,
    book_id BIGINT NOT NULL UNIQUE REFERENCES books(id) ON DELETE CASCADE,
    summary TEXT NOT NULL,
    key_topics JSONB NOT NULL DEFAULT '[]',
    target_reader VARCHAR(255) NOT NULL,
    reading_guide TEXT NOT NULL,
    model_name VARCHAR(64) NOT NULL DEFAULT 'deepseek-chat',
    generated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
`

---

## 四、AI 服务抽象层与大模型集成方案 (AiProvider)

### 4.1 核心架构解耦设计

严格禁止 Controller 层或具体业务 Service 直接耦合外部 HTTP 大模型 API。建立三层解耦体系：

`
[ Controller 层 (AiRecommendController / BookController) ]
                           │
                           ▼
[ 业务编排服务层 (AiRecommendService / AiInsightService) ]
                           │ (调用统一接口)
                           ▼
               [ <<interface>> AiProvider ]
                 ▲                      ▲
                 │                      │
   [ DeepSeekAiProvider ]     [ RuleBasedMockAiProvider ]
   (真实大模型在线推理)          (本地启发式规则与离线降级兜底)
`

### 4.2 接口与能力契约

`java
public interface AiProvider {
    /**
     * 生成图书智能导读结构化报告
     */
    AiBookInsightResult generateBookInsight(Book book);

    /**
     * 基于读者画像与候选图书集，生成个性化推荐理由
     */
    String generateRecommendationReason(Book book, User user, String dominantFactor);
}
`

### 4.3 弹性降级与配置驱动（Resilience & Fallback）

1. **配置驱动**：在 pplication.yml 中支持配置：
   `yaml
   ai:
     provider:  # 可选 deepseek 或 local
     deepseek:
       api-key: 
       base-url: https://api.deepseek.com/v1
       model: deepseek-chat
       timeout-ms: 5000
   `
2. **自动容灾降级机制**：
   - 当配置为 deepseek 时，系统建立带 5 秒超时保护的 HTTP 客户端；
   - 一旦遇到 API Key 未配、网络超时、余额不足或 HTTP 5xx 异常，系统**自动捕获异常并无缝回退至 RuleBasedMockAiProvider**；
   - 保证单元测试、本地断网开发以及答辩演示环境下 100% 稳定运行，绝不出现任何 500 崩溃。

---

## 五、推荐行为埋点与真实数据闭环 (i_recommendation_logs)

### 5.1 埋点日志数据表结构

`sql
CREATE TABLE ai_recommendation_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id BIGINT NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    recommendation_source VARCHAR(32) NOT NULL 
        CHECK (recommendation_source IN ('CONTENT_BASED', 'BEHAVIOR_COLLABORATIVE', 'POPULARITY', 'HYBRID_AI')),
    score NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    scene VARCHAR(32) NOT NULL DEFAULT 'HOME_RECOMMEND',
    clicked BOOLEAN NOT NULL DEFAULT FALSE,
    borrowed BOOLEAN NOT NULL DEFAULT FALSE,
    feedback VARCHAR(20) CHECK (feedback IN ('LIKE', 'DISLIKE', 'NEUTRAL')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
`

### 5.2 效果评估数学指标与 SQL 计算公式

1. **推荐总曝光量**：
   \text{ImpressionCount} = \text{COUNT}(id)
2. **点击率 (CTR, Click-Through Rate)**：
   \text{CTR} = \frac{\text{COUNT}(id) \text{ WHERE clicked = true}}{\text{COUNT}(id)} \times 100\%
3. **借阅转化率 (BCR, Borrow Conversion Rate)**：
   \text{BCR} = \frac{\text{COUNT}(id) \text{ WHERE borrowed = true}}{\text{COUNT}(id)} \times 100\%
4. **读者好评满意度**：
   \text{Satisfaction} = \frac{\text{COUNT}(id) \text{ WHERE feedback = 'LIKE'}}{\text{COUNT}(id) \text{ WHERE feedback IN ('LIKE', 'DISLIKE')}} \times 100\%

---

## 六、数据统计与决策分析模块设计 (StatisticsService)

### 6.1 读者端：个人阅读画像分析 (/api/v1/statistics/my-reading)

读者进入“个人中心 -> 我的阅读分析”，系统实时基于该读者名下真实数据计算返回：
1. **基础概览**：
   - 累计借阅总册数 (	otalBorrowedCount)；
   - 当前在借中册数 (ctiveBorrowingCount)；
   - 按期履约还书率 (onTimeReturnRate，例如 96.5%)；
   - 累计节约购书成本（按每本书平均定价估算）。
2. **阅读偏好分布**：
   - 按《中图法》一级分类统计个人借阅册数与百分比占比列表；
3. **借阅月度趋势**：
   - 近 6 个月的借阅柱状/折线趋势数据（年月、借阅量）。

### 6.2 管理端：全馆流通大盘分析 (/api/v1/statistics/**)

1. **全馆概览大盘 (/api/v1/statistics/overview)**：
   - 馆藏总书目数、馆藏物理总册数、当前在架册数、当前借出册数、预约排队中总量、有效读者总量；
2. **热门借阅榜单 TOP 10 (/api/v1/statistics/books/ranking)**：
   - 统计最近 90 天内借出频次最高的前 10 本书目（包含书名、封面、作者、借出次数、当前在架可借状态）；
3. **分类流通热度榜 (/api/v1/statistics/categories/hot)**：
   - 统计各大分类在借阅流水中的借出频次与流通度占比；
4. **AI 推荐效果大盘 (/api/v1/statistics/recommendation-metrics)**：
   - 汇总呈现总曝光、整体 CTR、借阅转化率与好评满意度。

---

## 七、数据库迁移方案 (Flyway V7)

新增迁移脚本文件：ackend/src/main/resources/db/migration/V7__create_ai_and_statistics_tables.sql

### 7.1 新增数据表与字段
1. i_book_insights 表及外键约束；
2. i_recommendation_logs 表及外键约束。

### 7.2 核心索引设计

| 索引名称 | 目标表 | 索引列及定义 | 核心场景 |
|---|---|---|---|
| uk_ai_book_insights_book_id | i_book_insights | UNIQUE (book_id) | 图书导读单书唯一与秒级命中 |
| idx_ai_logs_user_created | i_recommendation_logs | (user_id, created_at DESC) | 读者推荐历史与偏好回溯 |
| idx_ai_logs_book | i_recommendation_logs | (book_id, created_at DESC) | 单书推荐转化与埋点关联更新 |
| idx_ai_logs_conversion | i_recommendation_logs | (created_at, recommendation_source, clicked, borrowed) | 推荐系统全局转化率秒级聚合 |
| idx_borrow_records_stats_date | orrow_records | (borrowed_at DESC, status) | 热门图书排行榜与月度借阅趋势极速统计 |

### 7.3 RBAC 细粒度权限扩展

在 permissions 表中新增以下权限项：
- i:recommend:view（查看个性化推荐，分配给 STUDENT, TEACHER, LIBRARIAN, ADMIN）
- i:insight:view（查看图书 AI 导读，分配给 STUDENT, TEACHER, LIBRARIAN, ADMIN）
- i:feedback:submit（提交推荐埋点与评价，分配给 STUDENT, TEACHER）
- i:insight:manage（管理员重新生成或编辑导读，分配给 LIBRARIAN, ADMIN）
- statistics:my:view（查看个人阅读分析，分配给 STUDENT, TEACHER, LIBRARIAN, ADMIN）
- statistics:global:view（查看全馆统计大盘与推荐指标，分配给 LIBRARIAN, ADMIN）

---

## 八、API 接口详细设计方案

### 8.1 接口清单与安全鉴权

| HTTP 方法 | 接口路径 | 权限标识要求 | 功能说明 |
|---|---|---|---|
| GET | /api/v1/ai/recommendations | i:recommend:view | 获取当前登录用户的个性化推荐图书列表（带来源与理由） |
| POST | /api/v1/ai/recommendations/{logId}/click | i:feedback:submit | 推荐卡片点击事件埋点上报 |
| POST | /api/v1/ai/recommendations/{logId}/feedback | i:feedback:submit | 提交读者对推荐卡片的点赞/点踩反馈 |
| GET | /api/v1/ai/books/{bookId}/insight | i:insight:view | 获取指定图书的 AI 智能导读（持久化优先） |
| POST | /api/v1/ai/books/{bookId}/insight/refresh | i:insight:manage | 强制重新生成指定图书的 AI 智能导读 |
| GET | /api/v1/statistics/my-reading | statistics:my:view | 查询当前登录读者的个人阅读行为与偏好统计画像 |
| GET | /api/v1/statistics/overview | statistics:global:view | 查询图书馆藏与流通宏观概览统计数据 |
| GET | /api/v1/statistics/books/ranking | statistics:global:view | 查询全馆热门借阅图书排行榜 TOP 10 |
| GET | /api/v1/statistics/categories/hot | statistics:global:view | 查询全馆借阅图书分类流通分布与占比 |
| GET | /api/v1/statistics/recommendation-metrics | statistics:global:view | 查询 AI 推荐系统的效果评估指标（CTR、转化率、满意度） |

---

## 九、Flutter 前端设计方案 (Material 3 + Riverpod)

### 9.1 新增界面与交互组件

1. **AiRecommendationScreen（智能推荐发现主页）**：
   - 顶部提供个性化推荐说明横幅：“AI 智能猜你喜欢 · 混合推荐引擎”；
   - 推荐图书卡片列表：包含图书封面、标题、作者、分类标签，清晰标注**推荐原因**（如“因为您近期借阅了《深入理解计算机系统》”）、**推荐来源徽章**（内容匹配/历史借阅/全馆热门）；
   - 卡片直达交互：点击进入详情（自动静默触发点击埋点上报），提供快速“直接借阅”或“预约排队”按钮；
   - 卡片底部轻量反馈组件：点赞（👍）与点踩（👎）按钮，点击后即时反馈。
2. **BookDetailScreen AI 导读集成卡片 (AiBookInsightCard)**：
   - 位于图书元数据与单册状态之间；
   - 呈现结构化导读：
     - **3 秒速读简介**（浅色背景卡片，富文本呈现）；
     - **核心主题 Chip 标签流**（如 #面向对象设计, #设计模式, #架构重构）；
     - **目标受众**（带人员图标）；
     - **阅读建议与路径指导**（带学习路线图标）；
   - 右上角标注模型标牌（如 DeepSeek AI 导读）。
3. **ReadingStatisticsScreen（个人阅读数据画像页）**：
   - 顶部统计卡片：累计借阅册数、当前在借、履约率；
   - 分类偏好分布：Material 3 风格水平进度条展示各分类借阅占比；
   - 月度借阅走势图：使用轻量自定义绘制 CustomPainter 实现 6 个月借阅柱状图，流畅无任何外部大重包依赖；
   - 在个人中心（ProfileScreen）增加“我的阅读分析”磁贴入口。

---

## 十、风险分析与应对预案

| 风险场景 | 潜在威胁 | 应对预案与技术保障 |
|---|---|---|
| **大模型网络抖动或超额** | 调用 DeepSeek API 超时（>5s）或报错会导致借阅系统卡死 | 采用 AiProvider 抽象层 + 5秒超时熔断机制；捕获任何大模型异常后**秒级自动无缝降级**为本地高质量启发式导读与规则推荐，绝不向用户抛出 500 错误。 |
| **冷启动用户（新注册）** | 新读者无任何历史借阅与预约，推荐算法无法计算内容相关度与行为偏好 | 算法内置冷启动平滑降级策略：当读者历史记录为 0 时，自动切换为全馆近 90 天热门借阅榜单（权重 70%）+ 最新入库好书（权重 30%），并提示“新读者精选热门书目”。 |
| **聚合统计性能瓶颈** | 全馆大盘与月度趋势聚合统计扫描行数较多，可能影响高频借还业务 | 1. 建立 (borrowed_at, status) 复合索引；<br>2. 全馆统计使用 Redis 短时缓存（TTL 10~30 分钟），借还高频交易绝不受统计慢查询阻塞。 |
| **IDOR 越权与隐私泄露** | 恶意读者试图通过修改参数查看其他读者的阅读偏好或刷点击量 | 个人阅读统计接口与埋点接口严格从 Spring Security 上下文中提取 currentUserId，Service 强绑定当前用户身份，杜绝通过 URL 篡改用户 ID。 |

---

## 十一、Stage 5 实施拆分步骤

- **Step 1：Design Review（当前步骤，只读设计审核，本报告输出）**；
- **Step 2：数据库迁移 (Flyway V7)**；
- **Step 3：后端领域模型、Repository、枚举与 DTO 开发**；
- **Step 4：AiProvider 抽象层、DeepSeekAiProvider 与本地降级 RuleBasedMockAiProvider 实现**；
- **Step 5：AiRecommendService、AiInsightService 与推荐埋点流转服务实现**；
- **Step 6：StatisticsService 个人画像与全馆统计聚合服务实现**；
- **Step 7：Controller 接口配齐细粒度 RBAC 鉴权注解**；
- **Step 8：后端自动化测试矩阵（121 -> 135+ 个测试全绿）**；
- **Step 9：Flutter 前端 Model、Repository、Provider 与 3 大界面开发**；
- **Step 10：前端自动化测试与静态分析（27 -> 32+ 个测试全绿，lutter analyze 0 issues）**；
- **Step 11：Gate 审核与交付报告生成**。

---

## 十二、Stage 5 质量 Gate 检查项 (Gate Checklist)

- [ ] **Flyway V7 迁移成功**：i_book_insights、i_recommendation_logs 表与索引生效；
- [ ] **AI 模块无幻觉保真**：所有推荐书目 100% 映射馆藏真实书籍；
- [ ] **AI 导读持久化防重复调用**：生成一次后自动落库，二次访问毫秒级返回；
- [ ] **大模型故障降级兜底**：断网或无 Key 状态下自动平滑降级至 Local Provider，系统不崩溃；
- [ ] **真实数据闭环埋点**：推荐曝光、点击 CTR、借阅转化与反馈评分闭环统计正常；
- [ ] **个人与全馆统计隔离**：学生仅能查阅自身阅读数据，管理员可查全馆大盘与榜单；
- [ ] **后端自动化测试全绿**：mvn test 全部通过（不低于 135 个测试，0 失败，0 错误）；
- [ ] **前端自动化测试全绿**：lutter test 全部通过（不低于 32 个测试）；
- [ ] **前端代码静态分析**：lutter analyze 保持 0 issues 零缺陷；
- [ ] **阶段范围绝不越界**：无 Excel 批量导入、无第三方支付、无社交评论大屏，严守纪律。

---

> 🛑 **当前状态**：Stage 5-A Design Review 已完成，全套设计方案已归档。系统未做任何破坏性变更或超前编码，已立即停止，等待项目负责人审核指示！
