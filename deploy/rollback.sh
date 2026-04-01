#!/bin/bash
# ============================================
# glvpen 롤백 스크립트
# 이전 버전(glvpen.jar.backup)으로 복원
#
# 사용법: bash deploy/rollback.sh <EC2_IP> <SSH_KEY_PATH>
# ============================================

set -e

EC2_IP="${1:?사용법: rollback.sh <EC2_IP> <SSH_KEY_PATH>}"
SSH_KEY="${2:?사용법: rollback.sh <EC2_IP> <SSH_KEY_PATH>}"
EC2_USER="ec2-user"

echo "=========================================="
echo " glvpen 롤백 시작"
echo "=========================================="

ssh -i "${SSH_KEY}" "${EC2_USER}@${EC2_IP}" << 'REMOTE'
    if [ ! -f /opt/glvpen/glvpen.jar.backup ]; then
        echo "백업 파일이 없습니다. 롤백 불가."
        exit 1
    fi
    cp /opt/glvpen/glvpen.jar.backup /opt/glvpen/glvpen.jar
    sudo systemctl restart glvpen
    sleep 5
    sudo systemctl status glvpen --no-pager
    echo "롤백 완료!"
REMOTE

echo "=========================================="
echo " 롤백 완료"
echo "=========================================="
