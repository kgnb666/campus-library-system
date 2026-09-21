# 《校园图书借阅系统》生产部署指南 (Stage 7-A)

本文档面向系统运维工程师、DevOps 工程师及答辩评审专家，提供《校园图书借阅系统》在生产 Linux 服务器环境下的标准化容器化部署、运维监控、数据备份与容灾恢复完整指南。

---

## 目录

1. [服务器环境与硬件要求](#1-服务器环境与硬件要求)
2. [Docker 与 Docker Compose 环境安装](#2-docker-与-docker-compose-环境安装)
3. [环境变量规范化配置](#3-环境变量规范化配置)
4. [一键全栈容器化启动与管理](#4-一键全栈容器化启动与管理)
5. [数据库自动初始化与版本演进验证](#5-数据库自动初始化与版本演进验证)
6. [系统日志查看与健康监控](#6-系统日志查看与健康监控)
7. [数据库自动化备份与灾难恢复](#7-数据库自动化备份与灾难恢复)
8. [生产 HTTPS 与 SSL 证书部署](#8-生产-https-与-ssl-证书部署)

---

## 1. 服务器环境与硬件要求

### 1.1 推荐硬件规格

| 部署形态 | CPU | 内存 (RAM) | 磁盘空间 | 推荐网络带宽 | 适用场景 |
|:---|:---:|:---:|:---:|:---:|:---|
| **最低演示配置** | 2 核 | 4 GB | 30 GB SSD | 3 Mbps | 毕业答辩现场演示、单机测试体验 |
| **标准生产配置** | 4 核 | 8 GB | 100 GB SSD | 10 Mbps | 高校图书馆实际业务试运行 (日借还 2,000+ 册) |
| **企业高可用配置** | 8 核 | 16 GB | 200 GB NVMe | 20 Mbps+ | 万人规模高校并发流通高峰期 |

### 1.2 操作系统与网络端口
- **操作系统**：Ubuntu 22.04 LTS / 24.04 LTS（推荐）或 CentOS Stream 9 / Debian 12。
- **防火墙/安全组开放端口**：
  - `80/TCP`：HTTP 入口网关；
  - `443/TCP`：HTTPS 安全网关；
  - `22/TCP`：SSH 远程运维；
  - *(内部容器端口 `5432`、`6379`、`8080` 默认仅在 Docker 内网暴露，严禁直接向公网放行)*。

---

## 2. Docker 与 Docker Compose 环境安装

以 Ubuntu 22.04 / 24.04 LTS 为例：

```bash
# 1. 卸载历史旧版本
sudo apt-get remove docker docker-engine docker.io containerd runc -y

# 2. 安装必要工具与 GPG 密钥
sudo apt-get update
sudo apt-get install ca-certificates curl gnupg lsb-release -y
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# 3. 添加官方 Docker 源
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 4. 安装 Docker Engine 与 Docker Compose 插件
sudo apt-get update
sudo apt-get install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin -y

# 5. 验证安装版本 (要求 Docker >= 24.x, Docker Compose >= 2.20)
docker --version
docker compose version
```

---

## 3. 环境变量规范化配置

生产环境下所有敏感口令、密钥与连接串通过根目录 `.env` 文件进行环境隔离与集中管控，严禁在代码中硬编码。

```bash
# 1. 进入项目根目录
cd "Campus Library Borrowing System"

# 2. 从标准模板生成生产配置文件
cp .env.example .env

# 3. 严格配置安全密码 (使用 openssl 或 pwgen 生成强随机串)
vim .env
```

### `.env` 核心配置说明：

```ini
# 宿主机端口
HOST_HTTP_PORT=80
HOST_HTTPS_PORT=443

# PostgreSQL 数据库
POSTGRES_DB=library_system
POSTGRES_USER=library
# 生成方式: openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 32
POSTGRES_PASSWORD=<填入随机生成的强口令>

# Redis 缓存密码
# 生成方式: openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 32
REDIS_PASSWORD=<填入随机生成的强口令>

# JWT 安全密钥 (必填，>= 256 位；缺失/过短/命中泄露指纹后端将拒绝启动)
# 生成方式: openssl rand -base64 48
JWT_SECRET=<填入 openssl rand -base64 48 生成的随机值>

# AI 大模型接口
AI_BASE_URL=https://api.deepseek.com/v1
AI_API_KEY=sk-your-deepseek-api-key-here
AI_MODEL=deepseek-chat

# 前端产物 API 地址 (构建期生效；留空 = 运行时按页面 origin 推导，同源部署无需填)
PUBLIC_API_BASE_URL=

# 跨域白名单 (同源部署无需改动)
APP_CORS_ALLOWED_ORIGINS=http://localhost:*,http://127.0.0.1:*

# 演示数据开关 (生产必须为 false；仅隔离的答辩演示环境可设 true)
DEMO_DATA_ENABLED=false

# 自助注册开关 (true=读者可自助建号，仅获 STUDENT 角色；公网正式部署建议 false)
ALLOW_PUBLIC_REGISTRATION=true

# 首次部署管理员引导 (建号后请清空；见 5.1 节)
BOOTSTRAP_ADMIN_USERNAME=
BOOTSTRAP_ADMIN_PASSWORD=
```

### 3.1 前端接口地址在构建期与环境标识一起注入

Flutter Web 的接口地址与环境标识都是**编译期常量**（`--dart-define`），运行期无法覆盖。
生产镜像固定注入 `APP_ENV=prod`，而接口地址默认**留空**，由前端在运行时按页面 origin 推导：

| 部署形态 | 需要做什么 | 产物里的接口地址 |
| :--- | :--- | :--- |
| **同源（推荐）**：网关同时托管前端与 `/api/` | 什么都不用改 | 运行时推导为 `https://<你的域名>/api/v1` |
| **跨域**：前端与后端不同域名 | 在 `.env` 设 `PUBLIC_API_BASE_URL=https://api.example.com/api/v1` 并**重新构建** | 固定为该绝对地址 |

```bash
# 仅跨域部署时需要重新构建前端镜像（改了 .env 只重启容器不会生效）
docker compose -f docker-compose.prod.yml --env-file .env build frontend
docker compose -f docker-compose.prod.yml --env-file .env up -d frontend
```

> **历史坑位（两个，都会表现为"后端健康、前端所有接口都打不出去"）**
> 1. 镜像构建原先完全没有注入接口地址，产物退化为源码里 `prod` 分支的占位域名；
> 2. `EnvConfig` 里的环境标识曾是写死的 `dev` 常量，导致 `prod` 分支永不生效 ——
>    即使注入了地址，环境分支也走不到。
>
> 排查时可先在镜像里核对产物实际使用的地址：
> ```bash
> docker run --rm --entrypoint sh <frontend镜像> \
>   -c "grep -o 'https\?://[^\"]*/api/v1' /usr/share/nginx/html/main.dart.js | sort -u | head"
> ```
> 更直接的办法：部署后打开浏览器开发者工具的 Network 面板，
> 看登录请求（`POST .../api/v1/auth/login`）究竟发往哪个 origin。

---

## 4. 一键全栈容器化启动与管理

本项目提供完整的生产编排文件 `docker-compose.prod.yml`，一键启动包含 PostgreSQL 17、Redis 8、Spring Boot 3、Flutter Web 与 Nginx Gateway 的完整栈：

```bash
# 1. 后台构建并启动所有容器栈
docker compose -f docker-compose.prod.yml --env-file .env up -d --build

# 2. 检查所有容器健康状态 (要求全部状态为 healthy 或 running)
docker compose -f docker-compose.prod.yml ps

# 3. 停止所有服务
docker compose -f docker-compose.prod.yml down

# 4. 重启某个指定服务 (例如重启后端)
docker compose -f docker-compose.prod.yml restart backend
```

---

## 5. 数据库自动初始化与版本演进验证

系统内置 **Flyway 数据库自动化演进引擎**。容器首次启动时，后端服务将自动执行从 `V1` 到 `V17` 的全量增量迁移与种子数据填充：

```bash
# 查看后端启动日志中 Flyway 执行结果
docker compose -f docker-compose.prod.yml logs backend | grep -i flyway

# 预期输出示例：
# [INFO] o.f.c.i.database.base.BaseDatabaseType - Database: PostgreSQL 17.x
# [INFO] o.f.core.internal.command.DbMigrate - Current version of schema "public": << Empty Schema >>
# [INFO] o.f.core.internal.command.DbMigrate - Migrating schema "public" to version "1 - init schema"
# ...
# [INFO] o.f.core.internal.command.DbMigrate - Migrating schema "public" to version "17 - switch recommendation log id to sequence"
# [INFO] o.f.core.internal.command.DbMigrate - Successfully applied 17 migrations to schema "public"
```

> 该迁移链已实测：把 `V1`~`V17` 按版本号顺序应用到全新库可完整通过（含 `pg_trgm` 扩展、
> 种子数据、以及 V13/V15/V16/V17 的约束与索引变更）。

### 5.1 首次部署必须初始化管理员账号 (重要)

生产库以 `DEMO_DATA_ENABLED=false`（默认值）初始化时，迁移 `V11` 会把三个演示账号
置为 `DISABLED` 并把口令哈希替换为不可解析的占位串 —— 这是刻意的安全处置，
但带来的直接后果是：**全新部署完成后可用账号数为 0，没人能登录**。

此时有两条建号途径：

1. **自助注册**（默认开放，`ALLOW_PUBLIC_REGISTRATION=true`）：读者在登录页点「立即注册」，
   自助创建账号并**自动登录**，注册后仅获 `STUDENT` 角色（17 项读者权限，无任何管理权限）。
   公网可访问的正式部署建议设为 `false`，否则任何人都能建账号；
2. **管理员发放**（注册关闭时的唯一途径）：按下面 5.1 节做一次管理员引导，
   之后由管理员在「系统管理 → 用户管理」中为读者建号/停用/重置口令。

> 无论走哪条，**管理员账号都只能由 5.1 节的引导流程产生** ——
> 自助注册只发 `STUDENT` 角色，这也是刻意的：避免"注册即成管理员"。

因此首次部署需要执行一次"管理员引导"，步骤如下：

```bash
# 1. 在 .env 中临时加入引导变量（口令需 ≥8 位且同时含字母与数字，否则会被拒绝）
cat >> .env <<'EOF'
BOOTSTRAP_ADMIN_USERNAME=libadmin
BOOTSTRAP_ADMIN_PASSWORD=<在此填写一个强口令，不要用 123456>
BOOTSTRAP_ADMIN_EMAIL=libadmin@your-domain.edu.cn
BOOTSTRAP_ADMIN_NICKNAME=图书馆管理员
EOF

# 2. 重启后端触发引导（幂等：同名账号已存在时只记录并跳过，不覆盖既有口令）
docker compose -f docker-compose.prod.yml up -d backend
docker compose -f docker-compose.prod.yml logs backend | grep -i "管理员引导"

# 预期输出：首次部署管理员引导完成: username=libadmin, email=..., role=ADMIN

# 3. 用该账号登录确认（浏览器访问 http://<服务器IP>/），然后【务必】移除引导变量
#    删除 .env 中的 BOOTSTRAP_ADMIN_* 四行，并重启后端
docker compose -f docker-compose.prod.yml restart backend
```

引导机制的边界（均由自动化用例守护）：

| 行为 | 说明 |
| :--- | :--- |
| 未配置时不执行 | `BOOTSTRAP_ADMIN_*` 全空 = 彻底 no-op，代码中不存在任何内置默认口令 |
| 弱口令被拒绝 | 口令 <8 位或不含字母/数字组合时拒绝创建，且不留下半成品账号 |
| 幂等 | 账号已存在时只记录并跳过，不覆盖既有口令、不静默提权、不重复授权 |
| 不泄露口令 | 日志只输出用户名与角色，绝不输出口令 |

> 若这次部署**只用于答辩演示**且机器处于隔离网络，也可以在 `.env` 里设
> `DEMO_DATA_ENABLED=true` 直接启用演示账号（`student_demo` 等，口令 `123456`）。
> 这等同于对外暴露一个已知口令的管理员账号，**仅限隔离的演示环境**，切勿用于真实业务。

---

## 6. 系统日志查看与健康监控

### 6.1 实时日志查看

```bash
# 实时跟踪全栈日志
docker compose -f docker-compose.prod.yml logs -f

# 单独跟踪后端 Spring Boot 日志 (过滤 ERROR 与 WARN)
docker compose -f docker-compose.prod.yml logs -f backend | grep -E "ERROR|WARN"

# 单独跟踪 Nginx 网关访问日志
docker compose -f docker-compose.prod.yml logs -f nginx
```

### 6.2 Spring Boot Actuator 健康检查探针

网关默认将 `/actuator/` 端点路由至后端，可通过 HTTP 访问验证全栈健康状态：

```bash
curl -i http://localhost/actuator/health
```

返回 JSON 状态：
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "database": "PostgreSQL" } },
    "redis": { "status": "UP", "details": { "version": "8.x" } },
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

### 6.3 前端中文显示为方块（tofu）的排查

**症状**：能登录、页面结构正常，但所有汉字渲染成 □□□。

**根因**：Flutter Web（CanvasKit）的中日韩字形默认在运行时从 `fonts.gstatic.com` 拉取。
校园网/国内网络访问不到该域名时就会出现此现象——**这是网络问题，不是界面代码问题**。

**本项目已内置修复**：前端自带了 Noto Sans SC 子集字体（1.8MB）并设为全局字体族，
正常情况下不应再出现方块。若仍出现，按顺序检查：

```bash
# 1. 确认字体确实进了产物（关键：FontManifest.json 里应能看到 CampusLibraryCJK）
docker run --rm --entrypoint sh <frontend镜像> \
  -c "ls -lh /usr/share/nginx/html/assets/assets/fonts/ && cat /usr/share/nginx/html/assets/FontManifest.json"

# 2. 确认浏览器实际请求了本地字体（开发者工具 Network 面板）
#    应看到 GET /assets/assets/fonts/CampusLibraryCJK-Subset-Regular.ttf 200
#    若看到对 fonts.gstatic.com 的请求，说明产物是未内置字体的旧版本

# 3. 重新构建前端镜像（字体随产物打包，改了字体必须重建）
docker compose -f docker-compose.prod.yml build frontend
docker compose -f docker-compose.prod.yml up -d frontend
```

> 若只是个别生僻字显示为方块，说明该字不在子集内 —— 属于字体覆盖范围问题，
> 按 README「前端自带中文字体」一节的说明重跑子集脚本扩充即可。

---

## 7. 数据库自动化备份与灾难恢复

### 7.1 自动冷备脚本

项目内置标准备份脚本 `docker/scripts/backup.sh`：

```bash
# 赋予执行权限
chmod +x docker/scripts/backup.sh

# 手动执行一次全量备份
./docker/scripts/backup.sh

# 备份文件存储路径: /var/backups/campus_library/library_system_backup_YYYYMMDD_HHMMSS.sql.gz
```

### 7.2 配置 Linux Crontab 定时备份

```bash
# 编辑定时任务
sudo crontab -e

# 添加如下任务：每日凌晨 03:00 自动执行全量压缩备份，保留 30 天
0 3 * * * /bin/bash /path/to/Campus\ Library\ Borrowing\ System/docker/scripts/backup.sh >> /var/log/library_backup.log 2>&1
```

### 7.3 灾难恢复实操 (Restore)

**统一使用仓库内的恢复脚本**，不要再手写 `gunzip | psql` 管道：

```bash
# 方式一：指定备份文件恢复（脚本完成存在性校验、gzip 完整性校验与行数核对）
./docker/scripts/restore.sh /var/backups/campus_library/library_system_backup_20260918_030000.sql.gz

# 方式二：不带参数，交互式选择最近的备份
./docker/scripts/restore.sh
```

脚本会做三件手写命令容易漏掉的事：备份文件存在性与 `gzip -t` 完整性校验、
恢复前后的行数核对、恢复失败时明确的退出码与提示；
若备份启用了可选的 gpg 加密，脚本会一并处理解密。

> **为什么不再保留手写命令**：同一件事在两处各维护一份实现，必然出现
> "文档里的命令没有校验、脚本里的有校验"的双轨漂移。恢复是灾难场景下的操作，
> 任何一步疏漏的代价都最高，必须走经过校验的脚本。

---

## 8. 生产 HTTPS 与 SSL 证书部署

生产环境强烈建议启用 HTTPS，确保学生密码、借阅敏感数据及 JWT 令牌传输加密。

### 8.1 基于 Certbot 免费申请 Let's Encrypt 证书

```bash
# 1. 安装 Certbot
sudo apt-get install certbot python3-certbot-nginx -y

# 2. 申请域名 SSL 证书 (例如: library.campus.edu.cn)
sudo certbot certonly --standalone -d library.campus.edu.cn
```

### 8.2 挂载 SSL 证书至 Nginx

证书生成于 `/etc/letsencrypt/live/library.campus.edu.cn/`。修改 `docker-compose.prod.yml` 的 `nginx` 卷挂载：

```yaml
    volumes:
      - ./docker/nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - /etc/letsencrypt:/etc/letsencrypt:ro
      - campus_nginx_logs:/var/log/nginx
```

并在 `docker/nginx/nginx.conf` 中配置 SSL 监听与 HTTP 自动重定向：

```nginx
server {
    listen 80;
    server_name library.campus.edu.cn;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name library.campus.edu.cn;

    ssl_certificate /etc/letsencrypt/live/library.campus.edu.cn/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/library.campus.edu.cn/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    # 包含原代理配置 ...
}
```
