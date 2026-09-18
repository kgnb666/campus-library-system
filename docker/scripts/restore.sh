#!/bin/bash
# ==============================================================================
# PostgreSQL Automated Database Restore Script - Campus Library Borrowing System
# Usage: ./restore.sh <backup_file.sql.gz>
# Example: ./restore.sh /var/backups/campus_library/library_system_backup_20260918_120000.sql.gz
# ==============================================================================

set -euo pipefail

# 基础配置 (支持环境变量覆盖)
CONTAINER_NAME="${POSTGRES_CONTAINER_NAME:-campus-library-postgres}"
DB_USER="${POSTGRES_USER:-library}"
DB_NAME="${POSTGRES_DB:-library_system}"

# 参数校验
if [ $# -lt 1 ]; then
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] [ERROR] 缺少备份文件参数!" >&2
    echo "用法: $0 <backup_file.sql.gz>" >&2
    exit 1
fi

RESTORE_FILE="$1"

if [ ! -f "${RESTORE_FILE}" ]; then
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] [ERROR] 目标备份文件不存在: ${RESTORE_FILE}" >&2
    exit 1
fi

# 容器运行状态检查
if ! docker ps --format '{{.Names}}' | grep -Eq "^${CONTAINER_NAME}\$"; then
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] [ERROR] 数据库容器未在运行: ${CONTAINER_NAME}" >&2
    exit 1
fi

echo "[$(date +'%Y-%m-%d %H:%M:%S')] [INFO] 开始数据库恢复流程..."
echo "[$(date +'%Y-%m-%d %H:%M:%S')] [INFO] 目标容器: ${CONTAINER_NAME}"
echo "[$(date +'%Y-%m-%d %H:%M:%S')] [INFO] 目标数据库: ${DB_NAME} (用户: ${DB_USER})"
echo "[$(date +'%Y-%m-%d %H:%M:%S')] [INFO] 恢复源文件: ${RESTORE_FILE}"

# 通过管道解压并执行 psql 恢复 (使用 -i 确保纯净输入流，杜绝 TTY 污染)
if gunzip -c "${RESTORE_FILE}" | docker exec -i "${CONTAINER_NAME}" psql -U "${DB_USER}" -d "${DB_NAME}" --quiet; then
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] [SUCCESS] 数据库 SQL 转储文件导入成功!"
else
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] [ERROR] 数据库恢复失败!" >&2
    exit 1
fi

# 校验恢复后核心业务表数据行数
echo "[$(date +'%Y-%m-%d %H:%M:%S')] [INFO] 校验数据库核心表数据状态..."
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

docker exec -i "${CONTAINER_NAME}" psql -U "${DB_USER}" -d "${DB_NAME}" -c "${VERIFY_SQL}"

echo "[$(date +'%Y-%m-%d %H:%M:%S')] [SUCCESS] 数据库数据恢复与校验全部顺利完成。"
