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
| EC2 | t3.small (2vCPU, 2GB) Amazon Linux 2023 | ~$19/월 |
| RDS | db.t3.micro MySQL 8.0, 20GB | ~$13/월 |
| DuckDNS | 무료 서브도메인 | 무료 |
| Let's Encrypt | 무료 SSL | 무료 |
| **합계** | | **~$32/월** |

> **참고**: Playwright 기반 크롤러(선수/팀 통계)를 EC2에서 실행하려면 최소 2GB RAM이 필요합니다. t2.micro(1GB)에서는 Chromium 실행이 불가합니다.

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
   - AMI: Amazon Linux 2023
   - 인스턴스 유형: t3.small (Playwright 크롤링을 위해 최소 2GB RAM 필요)
   - 키 페어: 기존 키 사용 또는 새로 생성
   - 보안 그룹 인바운드 규칙:
     - SSH (22): 내 IP만
     - HTTP (80): 0.0.0.0/0
     - HTTPS (443): 0.0.0.0/0

### EC2 초기 환경 설정

```bash
# EC2 접속
ssh -i <SSH_KEY_PATH> ec2-user@<EC2_IP>

# 초기 설정 스크립트 실행
sudo bash /opt/glvpen/deploy/setup-ec2.sh
```

또는 로컬에서 직접 전송 후 실행:

```bash
scp -i <SSH_KEY_PATH> deploy/setup-ec2.sh ec2-user@<EC2_IP>:/tmp/
ssh -i <SSH_KEY_PATH> ec2-user@<EC2_IP> "sudo bash /tmp/setup-ec2.sh"
```

---

## 4단계: 환경변수 설정

EC2에서 `/opt/glvpen/env.conf` 파일 생성:

```bash
ssh -i <SSH_KEY_PATH> ec2-user@<EC2_IP>

cat > /opt/glvpen/env.conf << 'EOF'
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
CURRENT_SEASON=2026
EOF

chmod 600 /opt/glvpen/env.conf /opt/glvpen/python-analysis/.env
```

---

## 5단계: 첫 배포

```bash
# 로컬에서 실행
bash deploy/deploy.sh <EC2_IP> <SSH_KEY_PATH>
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
ssh -i <SSH_KEY_PATH> ec2-user@<EC2_IP>

# deploy 파일 전송 (최초 1회)
# 로컬에서:
scp -i <SSH_KEY_PATH> -r deploy/ ec2-user@<EC2_IP>:/opt/glvpen/deploy/

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
bash deploy/deploy.sh <EC2_IP> <SSH_KEY_PATH>
```

### 롤백

```bash
bash deploy/rollback.sh <EC2_IP> <SSH_KEY_PATH>
```

### 자동 스케줄

| 시간 | 서비스 | 주기 | 비고 |
|------|--------|------|------|
| 매일 00:00 | ScheduledCrawlerService | 매일 | 일정/선수/팀/게임센터/세이버메트릭스 |
| 매주 월 06:00 | AiColumnGeneratorService | 주 1회 | 팀별 주간 분석 (시즌 fallback 지원) |
| 매주 월/목 01:00 | ScheduledAnalysisService | 주 2회 | Python 분석 파이프라인 |
| 매주 목 06:00 | AiColumnGeneratorService | 주 1회 | 15개 주제 순환 특집 (시즌 fallback 지원) |

### 주요 명령어

```bash
# 서비스 제어
sudo systemctl start glvpen
sudo systemctl stop glvpen
sudo systemctl restart glvpen

# 로그 확인
sudo journalctl -u glvpen -f            # 실시간 로그
sudo journalctl -u glvpen --since today  # 오늘 로그
tail -f /opt/glvpen/logs/glvpen.log     # 애플리케이션 로그

# 크롤링 로그 확인
grep "자동 크롤링" /opt/glvpen/logs/glvpen.log | tail -20
grep -E "월요일|목요일" /opt/glvpen/logs/glvpen.log | tail -20

# Nginx
sudo nginx -t                            # 설정 검증
sudo systemctl reload nginx              # 설정 반영

# SSL 인증서 수동 갱신
sudo certbot renew
```

### 수동 크롤링 실행

서비스를 유지하면서 별도 프로세스로 크롤링을 실행할 수 있습니다.

```bash
# 환경변수 로드
set -a && source /opt/glvpen/env.conf && set +a

# 특정 시즌 크롤링 (서비스와 별도 포트로 실행)
nohup java \
  -Dloader.main=com.kepg.glvpen.crawler.CrawlersManualRunner \
  -Dcrawl.schedule=true -Dcrawl.team=true -Dcrawl.player=true \
  -Dcrawl.sabermetrics=true \
  -Dcrawl.seasonStart=2026 -Dcrawl.seasonEnd=2026 \
  -Dcrawl.scheduled.enabled=false -Danalysis.scheduled.enabled=false \
  -Xms256m -Xmx512m \
  -cp /opt/glvpen/glvpen.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher \
  --spring.profiles.active=prod --server.port=9999 \
  > /opt/glvpen/logs/crawl-manual.log 2>&1 &
```

---

## Playwright 의존성 (EC2)

Playwright 기반 크롤러(선수/팀 통계)를 EC2에서 실행하려면 Chromium 의존성이 필요합니다.

```bash
# Amazon Linux 2023
sudo dnf install -y \
  alsa-lib atk at-spi2-atk cups-libs libdrm libXcomposite \
  libXdamage libXrandr mesa-libgbm pango nss nspr \
  libXScrnSaver gtk3
```

> **주의**: 최소 2GB RAM (t3.small 이상)이 필요합니다. t2.micro(1GB)에서는 Chromium이 실행되지 않습니다.

---

## 인스턴스 타입 변경

EC2 인스턴스 타입을 변경하려면 (예: t2.micro → t3.small):

```bash
# AWS CLI로 변경
aws ec2 stop-instances --instance-ids <INSTANCE_ID> --region ap-northeast-2
aws ec2 wait instance-stopped --instance-ids <INSTANCE_ID> --region ap-northeast-2
aws ec2 modify-instance-attribute --instance-id <INSTANCE_ID> --instance-type '{"Value":"t3.small"}' --region ap-northeast-2
aws ec2 start-instances --instance-ids <INSTANCE_ID> --region ap-northeast-2
```

> Elastic IP가 할당되어 있으면 IP는 변경되지 않습니다.

---

## 파일 구조

```
/opt/glvpen/                 # EC2 배포 디렉토리
├── glvpen.jar               # Spring Boot 실행 파일
├── glvpen.jar.backup        # 이전 버전 백업 (롤백용)
├── env.conf                 # 환경변수 (DB, API키)
├── logs/
│   └── glvpen.log           # 애플리케이션 로그
└── python-analysis/         # Python 분석 파이프라인
    └── .env                 # Python 환경변수

deploy/                      # 프로젝트 내 배포 스크립트
├── deploy.sh                # 빌드 + 배포 스크립트
├── rollback.sh              # 롤백 스크립트
├── db-dump.sh               # 로컬 DB → RDS 이관
├── setup-ec2.sh             # EC2 초기 환경 설정
├── setup-ssl.sh             # DuckDNS + Let's Encrypt 설정
├── glvpen.service           # systemd 서비스 파일
└── nginx-glvpen.conf        # Nginx 리버스 프록시 설정
```

---

## 보안 점검표

- [ ] RDS 퍼블릭 액세스 비활성화 (데이터 이관 후)
- [ ] EC2 SSH는 내 IP에서만 허용
- [ ] `env.conf` 파일 권한 600
- [ ] `security-variable.yml`은 EC2에 배포하지 않음
- [ ] DuckDNS 토큰 노출 주의
