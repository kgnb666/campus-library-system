#!/bin/bash
# ==============================================================================
# PostgreSQL Backup Script - Campus Library Borrowing System
#
# 用法: ./backup.sh
#
# 支持两种运行模式（自动探测，无需手工切换）:
#   [容器内模式] prod 编排中的 backup 服务: 直接调用本地 pg_dump 连接 PGHOST
#   [宿主机模式] 开发机手工执行:             通过 docker exec 进入数据库容器执行 pg_dump
#
# 探测依据: 能执行 docker 命令且目标容器正在运行 → 宿主机模式；否则容器内模式。
# 这样同一个脚本既能被 prod 容器调度，也能在开发机上直接跑，避免两套实现漂移。
#
# 可选加密: 设置 BACKUP_ENCRYPTION_PASSPHRASE 后启用 gpg AES-256 对称加密。
#           若设置了该变量但环境缺少 gpg，脚本会立即失败而不是静默产出明文备份。
# ==============================================================================

set -euo pipefail

# 基础配置 (支持环境变量覆盖)
CONTAINER_NAME="${POSTGRES_CONTAINER_NAME:-campus-library-postgres}"
DB_HOST="${PGHOST:-${DB_HOST:-localhost}}"
DB_PORT="${PGPORT:-${DB_PORT:-5432}}"
DB_USER="${PGUSER:-${POSTGRES_USER:-library}}"
DB_NAME="${PGDATABASE:-${POSTGRES_DB:-library_system}}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/campus_library}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
ENCRYPTION_PASSPHRASE="${BACKUP_ENCRYPTION_PASSPHRASE:-}"

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/${DB_NAME}_backup_${TIMESTAMP}.sql.gz"

log() { echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*"; }
log_error() { echo "[$(date +'%Y-%m-%d %H:%M:%S')] [ERROR] $*" >&2; }

log "[INFO] 开始执行数据库备份: ${DB_NAME} ..."
mkdir -p "${BACKUP_DIR}"

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
# 加密前置校验: 要求加密却没有 gpg 时必须失败，绝不静默降级为明文备份
# ------------------------------------------------------------------------------
if [ -n "${ENCRYPTION_PASSPHRASE}" ] && ! command -v gpg >/dev/null 2>&1; then
    log_error "已设置 BACKUP_ENCRYPTION_PASSPHRASE，但当前环境没有 gpg，拒绝生成明文备份。"
    log_error "请先安装 gnupg (Alpine: apk add --no-cache gnupg)，或清空该变量以明确接受明文备份。"
    exit 1
fi

# ------------------------------------------------------------------------------
# 导出
# ------------------------------------------------------------------------------
pg_dump_stream() {
    if [ "${MODE}" = "host" ]; then
        # -i 确保纯净输入流，避免 TTY 污染管道
        docker exec -i "${CONTAINER_NAME}" pg_dump -U "${DB_USER}" -d "${DB_NAME}" --clean --if-exists
    else
        pg_dump -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" --clean --if-exists
    fi
}

if [ -n "${ENCRYPTION_PASSPHRASE}" ]; then
    BACKUP_FILE="${BACKUP_FILE}.gpg"
    log "[INFO] 已启用 gpg AES-256 加密"
    if ! pg_dump_stream | gzip | gpg --batch --yes --quiet --symmetric --cipher-algo AES256 \
            --passphrase "${ENCRYPTION_PASSPHRASE}" -o "${BACKUP_FILE}"; then
        log_error "数据库备份失败!"
        rm -f "${BACKUP_FILE}"
        exit 1
    fi
else
    if ! pg_dump_stream | gzip > "${BACKUP_FILE}"; then
        log_error "数据库备份失败!"
        rm -f "${BACKUP_FILE}"
        exit 1
    fi
fi

# ------------------------------------------------------------------------------
# 完整性校验
# 仅打印文件大小无法发现截断/损坏，必须真正解压验证 gzip 流完整
# ------------------------------------------------------------------------------
verify_backup() {
    if [ -n "${ENCRYPTION_PASSPHRASE}" ]; then
        gpg --batch --yes --quiet --decrypt --passphrase "${ENCRYPTION_PASSPHRASE}" \
            "${BACKUP_FILE}" 2>/dev/null | gzip -t
    else
        gzip -t "${BACKUP_FILE}"
    fi
}

if ! verify_backup; then
    log_error "备份文件完整性校验失败（gzip 流损坏或被截断），已删除该文件!"
    rm -f "${BACKUP_FILE}"
    exit 1
fi

BACKUP_SIZE=$(du -h "${BACKUP_FILE}" | cut -f1)
log "[SUCCESS] 备份成功并通过完整性校验: ${BACKUP_FILE} (大小: ${BACKUP_SIZE})"

# ------------------------------------------------------------------------------
# 清理保留周期之外的历史备份
# ------------------------------------------------------------------------------
log "[INFO] 清理 ${RETENTION_DAYS} 天前的过期备份..."
find "${BACKUP_DIR}" -type f \
    \( -name "${DB_NAME}_backup_*.sql.gz" -o -name "${DB_NAME}_backup_*.sql.gz.gpg" \) \
    -mtime +"${RETENTION_DAYS}" -exec rm -f {} \;

log "[INFO] 备份流程全部结束。"
