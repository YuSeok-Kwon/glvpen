# glvpen AWS 배포 가이드

## 아키텍처

```
[사용자] → HTTPS → [Nginx (EC2)] → localhost:8080 → [Spring Boot]
                                                        ↓
                                                  [RDS MySQL 8.0]
                                                        ↑
                                              [Python 분석 파이프라인]

DuckDNS (무료 도메인) + Let's Encrypt (무료 SSL)
```

| 구성 요소 | 사양 | 비용 |
|-----------|------|------|
| EC2 | t2.small (1vCPU, 2GB) Ubuntu 22.04 | ~$15/월 |
| RDS | db.t3.micro MySQL 8.0, 20GB | ~$13/월 |
| DuckDNS | 무료 서브도메인 | 무료 |
| Let's Encrypt | 무료 SSL | 무료 |
| **합계** | | **~$28/월** |

---

## 사전 준비

- AWS 계정
- SSH 키 페어 (`.pem` 파일)
- [DuckDNS](https://www.duckdns.org) 가입 (GitHub 로그인)

---

## 1단계: RDS MySQL 생성

### AWS 콘솔 설정

1. **AWS 콘솔 → RDS → 데이터베이스 생성**
2. 설정:
   - 엔진: MySQL 8.0
   - 템플릿: 프리 티어 (db.t3.micro)
   - DB 인스턴스 식별자: `glvpen-db`
   - 마스터 사용자: `root`
   - 마스터 암호: 안전한 비밀번호 설정
   - 스토리지: 20GB gp2
   - 퍼블릭 액세스: **예** (초기 데이터 이관 후 비활성화)
   - DB 이름: `glvpen`
3. 보안 그룹: EC2 보안 그룹에서 3306 포트 인바운드 허용

### RDS 엔드포인트 확인

```
glvpen-db.xxxxxxxxxx.ap-northeast-2.rds.amazonaws.com
```

---

## 2단계: 로컬 데이터 이관

```bash
bash deploy/db-dump.sh <RDS_ENDPOINT> <RDS_PASSWORD>
```

이관 후 RDS 퍼블릭 액세스를 **비활성화**로 변경합니다.

---

## 3단계: EC2 인스턴스 생성

### AWS 콘솔 설정

1. **AWS 콘솔 → EC2 → 인스턴스 시작**
2. 설정:
   - AMI: Ubuntu Server 22.04 LTS
   - 인스턴스 유형: t2.small
   - 키 페어: 기존 키 사용 또는 새로 생성
   - 보안 그룹 인바운드 규칙:
     - SSH (22): 내 IP만
     - HTTP (80): 0.0.0.0/0
     - HTTPS (443): 0.0.0.0/0

### EC2 초기 환경 설정

```bash
# EC2 접속
ssh -i ~/.ssh/glvpen-key.pem ubuntu@<EC2_IP>

# 초기 설정 스크립트 실행
sudo bash /opt/glvpen/deploy/setup-ec2.sh
```

또는 로컬에서 직접 전송 후 실행:

```bash
scp -i ~/.ssh/glvpen-key.pem deploy/setup-ec2.sh ubuntu@<EC2_IP>:/tmp/
ssh -i ~/.ssh/glvpen-key.pem ubuntu@<EC2_IP> "sudo bash /tmp/setup-ec2.sh"
```

---

## 4단계: 환경변수 설정

EC2에서 `/opt/glvpen/.env` 파일 생성:

```bash
ssh -i ~/.ssh/glvpen-key.pem ubuntu@<EC2_IP>

cat > /opt/glvpen/.env << 'EOF'
DB_HOST=glvpen-db.xxxxxxxxxx.ap-northeast-2.rds.amazonaws.com
DB_PORT=3306
DB_USERNAME=root
DB_PASSWORD=<실제_비밀번호>
GEMINI_API_KEY=<실제_API키>
CRAWLER_USERNAME=<이메일>
CRAWLER_PASSWORD=<비밀번호>
EOF

# Python 분석용 .env도 생성
cat > /opt/glvpen/python-analysis/.env << 'EOF'
DB_HOST=glvpen-db.xxxxxxxxxx.ap-northeast-2.rds.amazonaws.com
DB_PORT=3306
DB_USER=root
DB_PASSWORD=<실제_비밀번호>
DB_NAME=glvpen
CURRENT_SEASON=2025
EOF

chmod 600 /opt/glvpen/.env /opt/glvpen/python-analysis/.env
```

---

## 5단계: 첫 배포

```bash
# 로컬에서 실행
bash deploy/deploy.sh <EC2_IP> ~/.ssh/glvpen-key.pem
```

이 스크립트가 수행하는 작업:
1. `./gradlew bootJar` 빌드
2. JAR 파일 EC2 전송
3. Python 분석 파이프라인 전송
4. 서비스 재시작

---

## 6단계: DuckDNS + HTTPS 설정

### DuckDNS 도메인 등록

1. https://www.duckdns.org 접속 → GitHub 로그인
2. 서브도메인 등록 (예: `glvpen`) → `glvpen.duckdns.org`
3. EC2 퍼블릭 IP를 입력

### SSL 설정 (EC2에서 실행)

```bash
ssh -i ~/.ssh/glvpen-key.pem ubuntu@<EC2_IP>

# deploy 파일 전송 (최초 1회)
# 로컬에서:
scp -i ~/.ssh/glvpen-key.pem -r deploy/ ubuntu@<EC2_IP>:/opt/glvpen/deploy/

# EC2에서:
sudo bash /opt/glvpen/deploy/setup-ssl.sh glvpen <DUCKDNS_TOKEN> kyus0919@gmail.com
```

---

## 7단계: systemd 서비스 등록

```bash
# EC2에서 실행
sudo cp /opt/glvpen/deploy/glvpen.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable glvpen
sudo systemctl start glvpen
```

---

## 검증

```bash
# 서비스 상태
sudo systemctl status glvpen

# HTTPS 접속 테스트
curl -I https://glvpen.duckdns.org

# 실시간 로그
sudo journalctl -u glvpen -f

# 애플리케이션 로그
tail -f /opt/glvpen/logs/glvpen.log

# DB 연결 확인
mysql -h <RDS_ENDPOINT> -u root -p -e "SELECT COUNT(*) FROM glvpen.kbo_schedule;"
```

---

## 운영

### 재배포

```bash
bash deploy/deploy.sh <EC2_IP> ~/.ssh/glvpen-key.pem
```

### 롤백

```bash
bash deploy/rollback.sh <EC2_IP> ~/.ssh/glvpen-key.pem
```

### 자동 스케줄

| 시간 | 서비스 | 주기 |
|------|--------|------|
| 매일 00:00 | ScheduledCrawlerService (크롤링) | 매일 |
| 매주 월/목 01:00 | ScheduledAnalysisService (분석) | 주 2회 |

### 주요 명령어

```bash
# 서비스 제어
sudo systemctl start glvpen
sudo systemctl stop glvpen
sudo systemctl restart glvpen

# 로그 확인
sudo journalctl -u glvpen -f            # 실시간 로그
sudo journalctl -u glvpen --since today  # 오늘 로그

# Nginx
sudo nginx -t                            # 설정 검증
sudo systemctl reload nginx              # 설정 반영

# SSL 인증서 수동 갱신
sudo certbot renew
```

---

## 파일 구조

```
deploy/
├── .env.template      # 환경변수 템플릿
├── deploy.sh          # 빌드 + 배포 스크립트
├── rollback.sh        # 롤백 스크립트
├── db-dump.sh         # 로컬 DB → RDS 이관
├── setup-ec2.sh       # EC2 초기 환경 설정
├── setup-ssl.sh       # DuckDNS + Let's Encrypt 설정
├── glvpen.service     # systemd 서비스 파일
└── nginx-glvpen.conf  # Nginx 리버스 프록시 설정
```

---

## 보안 점검표

- [ ] RDS 퍼블릭 액세스 비활성화 (데이터 이관 후)
- [ ] EC2 SSH는 내 IP에서만 허용
- [ ] `.env` 파일 권한 600
- [ ] `security-variable.yml`은 EC2에 배포하지 않음
- [ ] DuckDNS 토큰 노출 주의
