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
POSTGRES_PASSWORD=Prod_Postgres_Passwd_2026_Secure!

# Redis 缓存密码
REDIS_PASSWORD=Prod_Redis_Passwd_2026_Secure!

# JWT 安全密钥 (必须 >= 256 位，Base64 编码)
JWT_SECRET=c2VjdXJlLWNhbXB1cy1saWJyYXJ5LWJvcnJvd2luZy1zeXN0ZW0tc2VjcmV0LWtleS0yMDI2LTA5LTE2LWZvci1qcGEtc3ByaW5nLXNlY3VyaXR5

# AI 大模型接口
AI_BASE_URL=https://api.deepseek.com/v1
AI_API_KEY=sk-your-deepseek-api-key-here
AI_MODEL=deepseek-chat
```

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

系统内置 **Flyway 数据库自动化演进引擎**。容器首次启动时，后端服务将自动执行从 `V1` 到 `V9` 的全量增量迁移与种子数据填充：

```bash
# 查看后端启动日志中 Flyway 执行结果
docker compose -f docker-compose.prod.yml logs backend | grep -i flyway

# 预期输出示例：
# [INFO] o.f.c.i.database.base.BaseDatabaseType - Database: PostgreSQL 17.x
# [INFO] o.f.core.internal.command.DbMigrate - Current version of schema "public": << Empty Schema >>
# [INFO] o.f.core.internal.command.DbMigrate - Migrating schema "public" to version "1 - init schema"
# ...
# [INFO] o.f.core.internal.command.DbMigrate - Migrating schema "public" to version "9 - seed demo data"
# [INFO] o.f.core.internal.command.DbMigrate - Successfully applied 9 migrations to schema "public"
```

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

```bash
# 1. 查找目标备份文件
TARGET_BACKUP="/var/backups/campus_library/library_system_backup_20260918_030000.sql.gz"

# 2. 解压并通过 psql 导入恢复
gunzip < "${TARGET_BACKUP}" | docker exec -i campus-library-postgres psql -U library -d library_system

# 3. 验证恢复结果
docker exec -it campus-library-postgres psql -U library -d library_system -c "SELECT count(*) FROM books;"
```

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
