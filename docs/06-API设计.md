# 《校园图书借阅系统》RESTful API 设计规范说明书 (Stage 0.5 修订版)

---

## 1. 全局设计规范与通信协议

### 1.1 协议与前缀
* **传输协议**：全站强制 HTTPS / HTTP 1.1 + JSON 交互。
* **统一基础路径**：`/api/v1/`。
* **字符编码**：UTF-8。
* **时间格式**：ISO-8601 标准，统一带有时区标识，例如 `2026-09-16T16:00:00+08:00`。
* **安全标头传递**：`Authorization: Bearer <access_token>`，`X-Trace-Id: <uuid>`。

### 1.2 统一响应报文格式（Global Response Envelope）

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {},
  "traceId": "e3b0c442-98fc-1c14-9afb-4c8996fb9242",
  "timestamp": 1789545600000
}
```

### 1.3 统一业务错误码定义（Error Code Registry）

| HTTP 状态码 | 业务错误码 `code` | 默认错误消息 `message` | 发生场景说明 |
| :--- | :--- | :--- | :--- |
| **400** | `PARAM_VALIDATION_ERROR` | 请求参数校验失败 | 字段缺失、正则不匹配、数值超限 |
| **401** | `AUTH_UNAUTHORIZED` | 凭证已过期或未提供 | 未携带 Token 或 AccessToken 已失效 |
| **403** | `AUTH_FORBIDDEN` | 权限不足，拒绝访问 | 普通读者尝试访问管理员接口 |
| **403** | `USER_ACCOUNT_FROZEN` | 账号已被封禁或处于受限状态 | 严重违约导致账号冻结 |
| **403** | `USER_HAS_OVERDUE_BOOKS` | 存在逾期未还图书，权限冻结 | 借书/续借/预约前置拦截 |
| **400** | `USER_BORROW_LIMIT_EXCEEDED` | 已达到个人最大借阅册数上限 | 达到规则最大允许借书量 |
| **404** | `RESOURCE_NOT_FOUND` | 请求的实体资源不存在 | 查无此书、副本或用户 |
| **409** | `BOOK_NO_AVAILABLE_COPY` | 该书目当前无在架可借副本 | 可借库存为 0，引导读者预约 |
| **409** | `COPY_NOT_AVAILABLE` | 目标物理副本当前不可借出 | 副本处于已借出、维修中或损毁 |
| **409** | `RENEW_COUNT_EXCEEDED` | 已达到最大允许续借次数 | 借阅单已达到规则续借上限 |
| **409** | `BOOK_HAS_RESERVATIONS` | 该书有其他读者正在排队预约 | 存在预约等待时，禁止续借 |
| **409** | `RESERVATION_ALREADY_EXISTS` | 您已预约过该图书，请勿重复预约 | 单个读者对同一书目防重复入队 |
| **400** | `RESERVATION_NOT_ALLOWED` | 馆内仍有在架可借副本，不可预约 | 只有当在架数 == 0 时才允许预约 |
| **400** | `IMPORT_DATA_INVALID` | 批量导入数据校验不通过 | Excel 中存在重复条码或必填字段缺失 |
| **409** | `CONCURRENT_CONFLICT` | 数据并发操作冲突，请重试 | 高并发锁竞争冲突 |
| **500** | `SYSTEM_INTERNAL_ERROR` | 系统繁忙，请稍后重试 | 未捕获异常与系统级故障 |

---

## 2. API 接口全景清单与详细契约

### 2.1 认证与令牌模块（Authentication）

#### 1. 用户登录 `POST /api/v1/auth/login`
* **权限**：公开 `[PUBLIC]`
* **Request Body**：
```json
{
  "loginId": "20260101",
  "password": "Password@123"
}
```
* **Response Data**：
```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "d8e8f7a9-...",
  "expiresIn": 7200,
  "user": {
    "id": 1001,
    "studentNo": "20260101",
    "realName": "张三",
    "roles": ["ROLE_STUDENT"]
  }
}
```

#### 2. 刷新令牌 `POST /api/v1/auth/refresh`
* **权限**：公开 `[PUBLIC]`
* **Request Body**：`{ "refreshToken": "d8e8f7a9-..." }`
* **Response Data**：`{ "accessToken": "eyJhbG...", "expiresIn": 7200 }`

#### 3. 主动退出登录 `POST /api/v1/auth/logout` (Stage 0.5 简化收敛)
* **权限**：登录用户 `[AUTHENTICATED]`
* **说明**：服务端仅需在 Redis 中删除对应的 `refresh_token:{userId}`，客户端清空本地 Token 即可。
* **Response Data**：`{ "message": "已成功登出" }`

---

### 2.2 图书与物理副本模块（Books & Copies）

#### 1. 分页检索图书列表 `GET /api/v1/books`
* **权限**：公开通用 `[STUDENT, LIBRARIAN, ADMIN]`
* **Query Params**：`keyword`, `categoryId`, `onlyAvailable`, `sortBy`, `page`, `size`
* **Response Data**：包含书目元数据、`coverUrl`、`availableCopies`、`totalCopies`。

#### 2. 获取图书详情与物理副本列表 `GET /api/v1/books/{id}`
* **权限**：通用 `[AUTHENTICATED]`
* **Response Data**：包含图书详情与副本列表（物理状态：`AVAILABLE`, `BORROWED`, `MAINTENANCE` 等）。

#### 3. 上传图书封面 `POST /api/v1/files/upload/cover` (Stage 0.5 新增)
* **权限**：管理员 `[LIBRARIAN, ADMIN]`
* **Content-Type**：`multipart/form-data`
* **Request Body**：`file: (binary)`（限制 JPG/PNG，小于 2MB）
* **Response Data**：
```json
{
  "coverUrl": "/uploads/covers/20260916_csapp.jpg",
  "storageType": "LOCAL"
}
```

---

### 2.3 Excel 图书批量导入模块（Book Import - Stage 0.5 新增）

#### 1. 下载导入标准模板 `GET /api/v1/books/import/template`
* **权限**：管理员 `[LIBRARIAN, ADMIN]`
* **Response**：下载二进制 Excel 文件流（`book_import_template.xlsx`）。

#### 2. 上传 Excel 并执行校验与差异预览 `POST /api/v1/books/import/upload-preview`
* **权限**：管理员 `[LIBRARIAN, ADMIN]`
* **Content-Type**：`multipart/form-data`
* **Request Body**：`file: (binary)`
* **Response Data**：
```json
{
  "importToken": "imp_token_98fa72...",
  "totalRows": 50,
  "validRows": 48,
  "errorRows": 2,
  "previewItems": [
    {
      "rowNum": 2,
      "isbn": "9787111544937",
      "title": "深入理解计算机系统",
      "barcode": "LIB-2026-000101",
      "isNewBook": true,
      "status": "VALID",
      "message": "合格"
    },
    {
      "rowNum": 5,
      "isbn": "9787121360000",
      "title": "测试图书",
      "barcode": "LIB-2026-000101",
      "isNewBook": false,
      "status": "ERROR",
      "message": "条形码 LIB-2026-000101 与第 2 行重复"
    }
  ]
}
```

#### 3. 确认批量落库 `POST /api/v1/books/import/confirm`
* **权限**：管理员 `[LIBRARIAN, ADMIN]`
* **Request Body**：`{ "importToken": "imp_token_98fa72..." }`
* **Response Data**：
```json
{
  "successCount": 48,
  "addedBooksCount": 20,
  "addedCopiesCount": 48,
  "message": "批量导入完成"
}
```

---

### 2.4 借阅流通业务模块（Borrow Records）

#### 1. 借阅图书（统一自顶向下排他锁事务） `POST /api/v1/borrow-records`
* **权限**：读者与管理员 `[STUDENT, LIBRARIAN]`
* **Request Body**：
```json
{
  "bookId": 501,
  "copyBarcode": "LIB-2026-000101",
  "studentNo": "20260101" // 管理员代办时选填
}
```
* **Response Data** (201 Created)：
```json
{
  "recordNo": "REC-202609160001",
  "bookTitle": "深入理解计算机系统",
  "copyBarcode": "LIB-2026-000101",
  "borrowedAt": "2026-09-16T16:00:00+08:00",
  "dueAt": "2026-10-16T16:00:00+08:00",
  "status": "BORROWING",
  "remainingRenewCount": 1
}
```

#### 2. 我的借阅列表 `GET /api/v1/borrow-records/my`
* **权限**：读者 `[STUDENT]`
* **Query Params**：`status`, `page`, `size`

#### 3. 图书归还 `POST /api/v1/borrow-records/{id}/return`
* **权限**：读者与管理员 `[STUDENT, LIBRARIAN]`
* **Response Data**：
```json
{
  "recordNo": "REC-202609160001",
  "returnedAt": "2026-09-20T10:30:00+08:00",
  "status": "RETURNED",
  "isOverdue": false,
  "fineAmount": 0.00
}
```

#### 4. 图书续借 `POST /api/v1/borrow-records/{id}/renew`
* **权限**：读者 `[STUDENT]`

---

### 2.5 缺书预约模块（Reservations - Stage 0.5 独立模型）

#### 1. 提交书目预约申请 `POST /api/v1/reservations`
* **权限**：读者 `[STUDENT]`
* **Request Body**：`{ "bookId": 501 }`
* **Response Data**：
```json
{
  "id": 201,
  "bookId": 501,
  "bookTitle": "深入理解计算机系统",
  "queueNumber": 1,
  "status": "WAITING",
  "createdAt": "2026-09-16T16:10:00+08:00"
}
```

#### 2. 我的预约列表 `GET /api/v1/reservations/my`
* **权限**：读者 `[STUDENT]`
* **Response Data**：返回预约单列表（包含 `WAITING`, `READY`, `COMPLETED`, `CANCELLED`, `EXPIRED` 各状态与自提倒计时 `expiredAt`）。

#### 3. 取消预约 `DELETE /api/v1/reservations/{id}`
* **权限**：读者本人 `[STUDENT]`

---

### 2.6 AI 馆藏智能导读与效果评估模块（AI Assistant - Stage 0.5 增强）

#### 1. 智能荐书问答 `POST /api/v1/ai/recommend`
* **权限**：登录用户 `[AUTHENTICATED]`
* **Request Body**：`{ "query": "推荐一本零基础学习 Python 和算法的馆藏图书" }`
* **Response Data**：
```json
{
  "recommendationId": 801,
  "reply": "为您在馆藏中检索到 2 本非常契合的在架好书：\n1. 《Python编程从入门到实践》...",
  "recommendedBooks": [
    {
      "id": 601,
      "title": "Python编程从入门到实践",
      "availableCopies": 2,
      "callNumber": "TP312PY/G41"
    }
  ]
}
```

#### 2. 提交推荐效果用户反馈 `POST /api/v1/ai/feedback` (Stage 0.5 新增)
* **权限**：登录用户 `[AUTHENTICATED]`
* **Request Body**：
```json
{
  "recommendationId": 801,
  "feedback": "LIKE" // 可选: LIKE(点赞), DISLIKE(点踩), RATING_1 ~ RATING_5
}
```
* **Response Data**：`{ "success": true, "message": "感谢您的评价反馈" }`
