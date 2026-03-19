#!/bin/bash
# ============================================
# DuckDNS + Let's Encrypt SSL 설정 스크립트
# 사용법: sudo bash setup-ssl.sh <duckdns_domain> <duckdns_token> <email>
# 예시:   sudo bash setup-ssl.sh glvpen xxxxxxxx-xxxx kyus0919@gmail.com
# ============================================

set -e

DOMAIN="${1:?사용법: setup-ssl.sh <domain> <token> <email>}"
TOKEN="${2:?DuckDNS 토큰이 필요합니다}"
EMAIL="${3:?이메일이 필요합니다}"
FQDN="${DOMAIN}.duckdns.org"

echo "=========================================="
echo " DuckDNS + SSL 설정: ${FQDN}"
echo "=========================================="

# --- 1. DuckDNS IP 업데이트 ---
echo "[1/4] DuckDNS IP 등록..."
PUBLIC_IP=$(curl -s ifconfig.me)
RESULT=$(curl -s "https://www.duckdns.org/update?domains=${DOMAIN}&token=${TOKEN}&ip=${PUBLIC_IP}")
echo "DuckDNS 응답: ${RESULT} (IP: ${PUBLIC_IP})"

if [ "${RESULT}" != "OK" ]; then
    echo "DuckDNS 업데이트 실패! 토큰과 도메인을 확인하세요."
    exit 1
fi

# --- 2. DuckDNS 자동 갱신 크론 등록 ---
echo "[2/4] DuckDNS 자동 갱신 크론 등록..."
CRON_CMD="*/5 * * * * curl -s 'https://www.duckdns.org/update?domains=${DOMAIN}&token=${TOKEN}&ip=' > /dev/null 2>&1"
(crontab -l 2>/dev/null | grep -v "duckdns.org/update"; echo "${CRON_CMD}") | crontab -

# --- 3. Nginx 설정 ---
echo "[3/4] Nginx 설정..."
cp /opt/glvpen/deploy/nginx-glvpen.conf /etc/nginx/sites-available/glvpen 2>/dev/null || true

# server_name을 실제 도메인으로 교체
sed -i "s/glvpen.duckdns.org/${FQDN}/g" /etc/nginx/sites-available/glvpen

ln -sf /etc/nginx/sites-available/glvpen /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx

# --- 4. Let's Encrypt SSL 발급 ---
echo "[4/4] Let's Encrypt SSL 인증서 발급..."
certbot --nginx -d "${FQDN}" --non-interactive --agree-tos -m "${EMAIL}"

# 자동 갱신 테스트
certbot renew --dry-run

echo ""
echo "=========================================="
echo " SSL 설정 완료!"
echo "=========================================="
echo " https://${FQDN} 으로 접속 가능"
echo " SSL 인증서는 Certbot이 자동 갱신합니다."
echo "=========================================="
