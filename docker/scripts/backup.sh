#!/bin/bash
# ==============================================================================
# PostgreSQL Automated Backup Script - Campus Library Borrowing System
# Usage: ./backup.sh
# Scheduled via crontab: 0 3 * * * /path/to/docker/scripts/backup.sh
# ==============================================================================

set -euo pipefail

# 基础配置 (支持环境变量覆盖)
CONTAINER_NAME="${POSTGRES_CONTAINER_NAME:-campus-library-postgres}"
DB_USER="${POSTGRES_USER:-library}"
DB_NAME="${POSTGRES_DB:-library_system}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/campus_library}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/${DB_NAME}_backup_${TIMESTAMP}.sql.gz"

echo "[$(date +'%Y-%m-%d %H:%M:%S')] [INFO] 开始执行数据库备份: ${DB_NAME} ..."

mkdir -p "${BACKUP_DIR}"

# 通过 docker exec 执行 pg_dump 并使用 gzip 压缩 (使用 -i 避免 -t 伪终端 \\r\\n 污染管道)
if docker exec -i "${CONTAINER_NAME}" pg_dump -U "${DB_USER}" -d "${DB_NAME}" --clean --if-exists | gzip > "${BACKUP_FILE}"; then
    BACKUP_SIZE=$(du -h "${BACKUP_FILE}" | cut -f1)
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] [SUCCESS] 备份成功完成: ${BACKUP_FILE} (大小: ${BACKUP_SIZE})"
else
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] [ERROR] 数据库备份失败!" >&2
    exit 1
fi

# 清理保留周期之外的历史备份文件
echo "[$(date +'%Y-%m-%d %H:%M:%S')] [INFO] 清理 ${RETENTION_DAYS} 天前的过期备份..."
find "${BACKUP_DIR}" -name "${DB_NAME}_backup_*.sql.gz" -type f -mtime +"${RETENTION_DAYS}" -exec rm -f {} \;

echo "[$(date +'%Y-%m-%d %H:%M:%S')] [INFO] 备份流程全部结束。"
