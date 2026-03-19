#!/bin/bash
# ============================================
# glvpen 배포 스크립트
# 로컬에서 빌드 → EC2로 전송 → 서비스 재시작
#
# 사용법: bash deploy/deploy.sh <EC2_IP> [SSH_KEY_PATH]
# 예시:   bash deploy/deploy.sh 3.35.xxx.xxx ~/.ssh/glvpen-key.pem
# ============================================

set -e

EC2_IP="${1:?사용법: deploy.sh <EC2_IP> [SSH_KEY_PATH]}"
SSH_KEY="${2:-~/.ssh/glvpen-key.pem}"
EC2_USER="ubuntu"
REMOTE_DIR="/opt/glvpen"

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "=========================================="
echo " glvpen 배포 시작"
echo " 대상: ${EC2_USER}@${EC2_IP}"
echo "=========================================="

# --- 1. 빌드 ---
echo "[1/4] Gradle 빌드..."
cd "${PROJECT_DIR}"
./gradlew bootJar -x test
JAR_FILE=$(ls -t build/libs/glvpen.jar 2>/dev/null | head -1)
if [ -z "${JAR_FILE}" ]; then
    echo "빌드 실패: glvpen.jar를 찾을 수 없습니다."
    exit 1
fi
echo "빌드 완료: ${JAR_FILE}"

# --- 2. JAR 파일 전송 ---
echo "[2/4] JAR 파일 전송..."
scp -i "${SSH_KEY}" "${JAR_FILE}" "${EC2_USER}@${EC2_IP}:${REMOTE_DIR}/app.jar.new"

# --- 3. Python 분석 파이프라인 전송 ---
echo "[3/4] Python 분석 파이프라인 전송..."
rsync -avz --exclude='__pycache__' --exclude='*.pyc' --exclude='.env' --exclude='output/*.json' \
    -e "ssh -i ${SSH_KEY}" \
    "${PROJECT_DIR}/python-analysis/" "${EC2_USER}@${EC2_IP}:${REMOTE_DIR}/python-analysis/"

# --- 4. 원자적 교체 + 서비스 재시작 ---
echo "[4/4] 서비스 재시작..."
ssh -i "${SSH_KEY}" "${EC2_USER}@${EC2_IP}" << 'REMOTE'
    # 백업
    if [ -f /opt/glvpen/app.jar ]; then
        cp /opt/glvpen/app.jar /opt/glvpen/app.jar.backup
    fi
    # 원자적 교체
    mv /opt/glvpen/app.jar.new /opt/glvpen/app.jar
    # 서비스 재시작
    sudo systemctl restart glvpen
    # 상태 확인 (5초 대기 후)
    sleep 5
    sudo systemctl status glvpen --no-pager
REMOTE

echo ""
echo "=========================================="
echo " 배포 완료!"
echo " https://glvpen.duckdns.org"
echo "=========================================="
