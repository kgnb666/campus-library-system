#!/bin/bash
# ==============================================================================
# PostgreSQL Restore Script - Campus Library Borrowing System
#
# 用法: ./restore.sh <backup_file.sql.gz[.gpg]>
# 示例: ./restore.sh /var/backups/campus_library/library_system_backup_20260919_030000.sql.gz
#
# 与 backup.sh 一致，支持两种运行模式（自动探测）:
#   [容器内模式] backup 服务或临时容器内执行: 直连 PGHOST 调用 psql
#   [宿主机模式] 开发机手工执行:              通过 docker exec 进入数据库容器调用 psql
#
# 若备份文件为 .gpg 加密文件，需同时设置 BACKUP_ENCRYPTION_PASSPHRASE。
# ==============================================================================

set -euo pipefail

# 基础配置 (支持环境变量覆盖)
CONTAINER_NAME="${POSTGRES_CONTAINER_NAME:-campus-library-postgres}"
DB_HOST="${PGHOST:-${DB_HOST:-localhost}}"
DB_PORT="${PGPORT:-${DB_PORT:-5432}}"
DB_USER="${PGUSER:-${POSTGRES_USER:-library}}"
DB_NAME="${PGDATABASE:-${POSTGRES_DB:-library_system}}"
ENCRYPTION_PASSPHRASE="${BACKUP_ENCRYPTION_PASSPHRASE:-}"

log() { echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*"; }
log_error() { echo "[$(date +'%Y-%m-%d %H:%M:%S')] [ERROR] $*" >&2; }

# ------------------------------------------------------------------------------
# 参数校验
# ------------------------------------------------------------------------------
if [ $# -lt 1 ]; then
    log_error "缺少备份文件参数!"
    echo "用法: $0 <backup_file.sql.gz[.gpg]>" >&2
    exit 1
fi

RESTORE_FILE="$1"

if [ ! -f "${RESTORE_FILE}" ]; then
    log_error "目标备份文件不存在: ${RESTORE_FILE}"
    exit 1
fi

# ------------------------------------------------------------------------------
# 运行模式探测
# ------------------------------------------------------------------------------
MODE="container"
if command -v docker >/dev/null 2>&1 \
   && docker ps --format '{{.Names}}' 2>/dev/null | grep -Eq "^${CONTAINER_NAME}$"; then
    MODE="host"
fi

if [ "${MODE}" = "host" ]; then
    log "[INFO] 运行模式: 宿主机 (docker exec → ${CONTAINER_NAME})"
else
    log "[INFO] 运行模式: 容器内 (直连 ${DB_HOST}:${DB_PORT})"
fi

# ------------------------------------------------------------------------------
# 解密流封装 (未加密则直接输出原文件)
# ------------------------------------------------------------------------------
if [ -n "${ENCRYPTION_PASSPHRASE}" ]; then
    if ! command -v gpg >/dev/null 2>&1; then
        log_error "备份文件需要解密，但当前环境没有 gpg。请安装 gnupg 或改用未加密备份。"
        exit 1
    fi
fi

decrypt_stream() {
    if [ -n "${ENCRYPTION_PASSPHRASE}" ]; then
        gpg --batch --yes --quiet --decrypt --passphrase "${ENCRYPTION_PASSPHRASE}" "${RESTORE_FILE}"
    else
        cat "${RESTORE_FILE}"
    fi
}

# ------------------------------------------------------------------------------
# 恢复前完整性预检: 避免把损坏的转储灌进数据库后才发现
# ------------------------------------------------------------------------------
log "[INFO] 校验备份文件完整性..."
if ! decrypt_stream | gzip -t; then
    log_error "备份文件完整性校验失败（gzip 流损坏或被截断），已中止恢复。"
    exit 1
fi

log "[INFO] 开始数据库恢复流程..."
log "[INFO] 目标数据库: ${DB_NAME} (用户: ${DB_USER})"
log "[INFO] 恢复源文件: ${RESTORE_FILE}"

apply_stream() {
    if [ "${MODE}" = "host" ]; then
        # -i 确保纯净输入流，杜绝 TTY 污染
        docker exec -i "${CONTAINER_NAME}" psql -U "${DB_USER}" -d "${DB_NAME}" --quiet
    else
        psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" --quiet
    fi
}

if decrypt_stream | gunzip -c | apply_stream; then
    log "[SUCCESS] 数据库 SQL 转储文件导入成功!"
else
    log_error "数据库恢复失败!"
    exit 1
fi

# ------------------------------------------------------------------------------
# 校验恢复后核心业务表数据行数
# ------------------------------------------------------------------------------
log "[INFO] 校验数据库核心表数据状态..."
VERIFY_SQL="
SELECT 'users' AS table_name, count(*) AS row_count FROM users
UNION ALL
SELECT 'books', count(*) FROM books
UNION ALL
SELECT 'book_copies', count(*) FROM book_copies
UNION ALL
SELECT 'borrow_records', count(*) FROM borrow_records
UNION ALL
SELECT 'reservations', count(*) FROM reservations;
"

if [ "${MODE}" = "host" ]; then
    docker exec -i "${CONTAINER_NAME}" psql -U "${DB_USER}" -d "${DB_NAME}" -c "${VERIFY_SQL}"
else
    psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -c "${VERIFY_SQL}"
fi

log "[SUCCESS] 数据库数据恢复与校验全部顺利完成。"
