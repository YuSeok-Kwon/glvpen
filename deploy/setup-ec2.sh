#!/bin/bash
# ============================================
# glvpen EC2 초기 환경 설정 스크립트
# Ubuntu 22.04 LTS (t2.small) 기준
# 사용법: sudo bash setup-ec2.sh
# ============================================

set -e

echo "=========================================="
echo " glvpen EC2 초기 환경 설정 시작"
echo "=========================================="

# --- 1. 시스템 패키지 업데이트 ---
echo "[1/7] 시스템 패키지 업데이트..."
apt update && apt upgrade -y

# --- 2. Java 17 설치 ---
echo "[2/7] OpenJDK 17 설치..."
apt install -y openjdk-17-jdk
java -version

# --- 3. Python 3 + pip 설치 ---
echo "[3/7] Python 3 및 pip 설치..."
apt install -y python3 python3-pip python3-venv
python3 --version

# --- 4. Nginx 설치 ---
echo "[4/7] Nginx 설치..."
apt install -y nginx
systemctl enable nginx

# --- 5. Certbot 설치 (Let's Encrypt) ---
echo "[5/7] Certbot 설치..."
apt install -y certbot python3-certbot-nginx

# --- 6. 프로젝트 디렉토리 생성 ---
echo "[6/7] 프로젝트 디렉토리 생성..."
mkdir -p /opt/glvpen/logs
mkdir -p /opt/glvpen/python-analysis/output
chown -R ubuntu:ubuntu /opt/glvpen

# --- 7. Python 의존성 설치 ---
echo "[7/7] Python 의존성 설치..."
if [ -f /opt/glvpen/python-analysis/requirements.txt ]; then
    pip3 install -r /opt/glvpen/python-analysis/requirements.txt
else
    pip3 install pandas numpy scipy mysql-connector-python python-dotenv
fi

echo ""
echo "=========================================="
echo " EC2 초기 환경 설정 완료!"
echo "=========================================="
echo ""
echo "다음 단계:"
echo "  1. /opt/glvpen/.env 파일 생성 (deploy/.env.template 참고)"
echo "  2. app.jar 배포: scp build/libs/glvpen.jar ubuntu@<IP>:/opt/glvpen/app.jar"
echo "  3. python-analysis/ 배포: scp -r python-analysis/ ubuntu@<IP>:/opt/glvpen/"
echo "  4. Nginx 설정: sudo cp deploy/nginx-glvpen.conf /etc/nginx/sites-available/glvpen"
echo "  5. SSL 설정: sudo bash deploy/setup-ssl.sh"
echo "  6. 서비스 등록: sudo cp deploy/glvpen.service /etc/systemd/system/"
echo "  7. 서비스 시작: sudo systemctl daemon-reload && sudo systemctl enable --now glvpen"
echo ""
