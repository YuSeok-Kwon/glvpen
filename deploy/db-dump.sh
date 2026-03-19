#!/bin/bash
# ============================================
# glvpen 로컬 DB → RDS 이관 스크립트
#
# 사용법: bash deploy/db-dump.sh <RDS_ENDPOINT> <RDS_PASSWORD>
# ============================================

set -e

RDS_HOST="${1:?사용법: db-dump.sh <RDS_ENDPOINT> <RDS_PASSWORD>}"
RDS_PASSWORD="${2:?RDS 비밀번호가 필요합니다}"
LOCAL_MYSQL="/usr/local/mysql/bin/mysql"
LOCAL_MYSQLDUMP="/usr/local/mysql/bin/mysqldump"
DUMP_FILE="/tmp/glvpen_dump.sql"

echo "=========================================="
echo " glvpen DB 이관"
echo "=========================================="

# --- 1. 로컬 DB 덤프 ---
echo "[1/3] 로컬 DB 덤프 중..."
${LOCAL_MYSQLDUMP} -u root -prnjs7944 \
    --single-transaction \
    --routines \
    --triggers \
    --set-gtid-purged=OFF \
    glvpen > "${DUMP_FILE}"

DUMP_SIZE=$(du -h "${DUMP_FILE}" | cut -f1)
echo "덤프 완료: ${DUMP_FILE} (${DUMP_SIZE})"

# --- 2. RDS에 DB 생성 ---
echo "[2/3] RDS에 데이터베이스 생성..."
mysql -h "${RDS_HOST}" -u root -p"${RDS_PASSWORD}" \
    -e "CREATE DATABASE IF NOT EXISTS glvpen CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# --- 3. RDS에 복원 ---
echo "[3/3] RDS에 데이터 복원 중... (시간이 걸릴 수 있습니다)"
mysql -h "${RDS_HOST}" -u root -p"${RDS_PASSWORD}" glvpen < "${DUMP_FILE}"

# 검증
echo ""
echo "=== 테이블 수 확인 ==="
mysql -h "${RDS_HOST}" -u root -p"${RDS_PASSWORD}" glvpen \
    -e "SELECT COUNT(*) AS table_count FROM information_schema.tables WHERE table_schema = 'glvpen';"

echo ""
echo "=========================================="
echo " DB 이관 완료!"
echo "=========================================="
echo "임시 파일 삭제: rm ${DUMP_FILE}"
