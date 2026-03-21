# glvpen 3.0

**글러브와 펜** — KBO 데이터 분석 & 시뮬레이션 플랫폼

## 프로젝트 개요

glvpen은 KBO 공식 사이트에서 경기 데이터를 크롤링하여 **데이터 분석 인사이트를 도출**하고, 카드 기반 시뮬레이션 게임을 즐길 수 있는 플랫폼입니다. 다시즌(2020~2025) 데이터와 세이버메트릭스 지표를 활용한 분석 대시보드, AI 자동 분석 컬럼, 고급 통계 분석을 제공합니다.

### 프로젝트 진화

| 버전 | 단계 | 주요 내용 |
|------|------|---------|
| v1.0 | 개발 연습 | 경기 일정 크롤링, 간단한 시뮬레이션 게임, Spring Boot 학습 |
| v2.0 | 게임 확장 | 카드/라인업/봇 대전, 크롤러 고도화, 엔티티 25개로 성장 |
| v3.0 | 데이터 분석 + 배포 | 분석 대시보드, 고급 분석 5종, AI 자동 분석 컬럼, AWS 배포 |

### 핵심 기능

- **데이터 분석 대시보드**: 포지션별 WAR 분포, 팀 타투 밸런스, 선수 성장 추이 차트
- **고급 분석 5종**: 클러치 지수, 선수 유사도, Marcel 성적 예측, 승리 예측, 투수 피로도
- **분석 컬럼 (매거진)**: 선수/팀/경기/트렌드 분석 기사 (수동 작성 + AI 자동 생성)
- **다시즌 크롤링**: 2020~2025 KBO 데이터 일괄 수집 (일정, 팀, 선수, 관중, 퓨처스, 게임센터)
- **세이버메트릭스**: BABIP, ISO, K%, BB%, K/9, BB/9, FIP, xFIP 등 파생 지표
- **시뮬레이션 게임**: 실제 선수 스탯 기반 카드 수집 및 대전
- **AI 분석**: Google Gemini API를 활용한 15개 주제 순환 분석 기사 자동 생성
- **Python 분석 파이프라인**: 88개 스크립트로 선수/팀/경기 심층 분석 자동화

## 배포 아키텍처

```
[사용자] → HTTPS → [Nginx (리버스 프록시)] → localhost:8080 → [Spring Boot]
                                                                    ↓
                                                              [RDS MySQL 8.0]
                                                                    ↑
                                                       [Python 분석 파이프라인]

AWS EC2 (t2.small) + RDS (db.t3.micro)
DuckDNS (무료 도메인) + Let's Encrypt (무료 SSL, 자동 갱신)
systemd 서비스 등록 (자동 시작/재시작)
```

### 자동 스케줄

| 주기 | 작업 |
|------|------|
| 매일 00:00 | KBO 데이터 크롤링 (경기 일정, 선수 통계) |
| 매주 월/목 01:00 | Python 분석 파이프라인 실행 + AI 기사 생성 |
| 매주 목 06:00 | 15개 주제 순환 AI 컬럼 자동 생성 |

## 기술 스택

| 분류 | 기술 |
|------|------|
| Backend | Java 17, Spring Boot 3.3.10, Spring Data JPA, MyBatis |
| Frontend | Thymeleaf (Layout Dialect), Bootstrap 5, Chart.js 4.4.1 |
| Database | MySQL 8.0 (RDS), Flyway 마이그레이션 |
| Crawling | Playwright 1.49.0, Selenium 4.20.0, Jsoup 1.15.3 |
| Auth | BCrypt (spring-security-crypto 6.3.0), Session 기반 |
| AI | Google Gemini API (gemini-1.5-flash) |
| Infra | AWS EC2, RDS, Nginx, Let's Encrypt, systemd |
| Analysis | Python 3 (pandas, numpy, scipy) |
| Dev Tools | Lombok, P6Spy, Spring DevTools |

## 프로젝트 구조

```
glvpen/
├── src/main/java/com/kepg/glvpen/
│   ├── GlvpenApplication.java             # Spring Boot 메인 클래스
│   │
│   ├── config/                             # 설정
│   │   ├── SecurityConfig                  #   BCrypt 인코더
│   │   ├── WebMvcConfig                    #   MVC 인터셉터 등록
│   │   └── LoginCheckInterceptor           #   세션 기반 인증 체크
│   │
│   ├── crawler/                            # 데이터 크롤링 (KBO 공식)
│   │   ├── kbo/                            #   KBO 크롤러
│   │   │   ├── KboPlayerStatsCrawler       #     선수 통계 (타자/투수/수비/주루)
│   │   │   ├── KboTeamStatsCrawler         #     팀 순위/통계
│   │   │   ├── KboScheduleCrawler          #     경기 일정
│   │   │   ├── KboGameCenterCrawler        #     경기 상세 (스코어보드, 기록)
│   │   │   ├── KboPlayerProfileCrawler     #     선수 프로필
│   │   │   ├── KboCrowdCrawler             #     관중 현황
│   │   │   ├── KboFuturesCrawler           #     퓨처스 리그
│   │   │   └── util/                       #     KboConstants, PlaywrightFactory
│   │   ├── CrawlersManualRunner            #   수동 실행 (다시즌 배치 포함)
│   │   └── util/                           #   WebDriverFactory, CrawlerUtils
│   │
│   ├── common/                             # 공통 모듈
│   │   ├── ai/GeminiClient                 #   Gemini API 클라이언트
│   │   ├── stats/SabermetricsCalculator    #   세이버메트릭스 계산기
│   │   ├── game/                           #   MvpCalculator, GameResultSaver
│   │   ├── enums/                          #   BatterSortType, PitcherSortType 등
│   │   ├── exception/                      #   GlobalExceptionHandler
│   │   └── validator/                      #   SeasonValidator
│   │
│   └── modules/                            # Feature-based 모듈
│       ├── user/                           #   사용자 관리 (로그인/가입/세션)
│       ├── game/                           #   경기 데이터
│       │   ├── schedule/                   #     경기 일정
│       │   ├── scoreBoard/                 #     스코어보드
│       │   ├── highlight/                  #     경기 하이라이트
│       │   ├── lineUp/                     #     타자 라인업
│       │   ├── record/                     #     타자/투수 기록
│       │   ├── keyPlayer/                  #     경기 주요 선수
│       │   └── summaryRecord/              #     경기 요약 기록
│       ├── player/                         #   선수 정보 + 세이버메트릭스
│       ├── team/                           #   팀 정보 + 순위 + 상대 성적
│       ├── analysis/                       #   데이터 분석 (v3)
│       │   ├── controller/                 #     대시보드/컬럼/매거진 뷰 + REST API
│       │   ├── service/                    #     고급 분석 5종 + AI 기사 생성
│       │   ├── domain/                     #     AnalysisColumn Entity
│       │   ├── dto/                        #     Dashboard, ChartData, PlayerAnalysis
│       │   └── repository/                 #     AnalysisColumnRepository
│       ├── crowd/                          #   관중 데이터
│       ├── futures/                        #   퓨처스 리그
│       └── gameMode/                       #   게임 모드
│           └── simulationMode/             #     시뮬레이션 (카드/라인업/대전)
│
├── python-analysis/                        # Python 분석 파이프라인
│   ├── main.py                             #   메인 진입점
│   ├── run_scheduled.py                    #   스케줄 실행
│   ├── player/                             #   선수 분석 9종
│   ├── team/                               #   팀 분석 6종
│   ├── game/                               #   경기 분석 7종
│   ├── topics/                             #   주제별 분석 15종
│   └── common/                             #   공용 모듈 (DB, Gemini, 차트)
│
├── deploy/                                 # 배포 스크립트
│   ├── deploy.sh                           #   배포 실행
│   ├── rollback.sh                         #   롤백 (긴급 복구)
│   ├── setup-ec2.sh                        #   EC2 초기 설정
│   ├── setup-ssl.sh                        #   SSL 인증서 설정
│   ├── db-dump.sh                          #   로컬 DB → RDS 이관
│   ├── glvpen.service                      #   systemd 서비스 파일
│   └── nginx-glvpen.conf                   #   Nginx 리버스 프록시 설정
│
├── src/main/resources/
│   ├── application.yml                     # 개발 설정
│   ├── application-prod.yml                # 프로덕션 설정
│   ├── security-variable.yml               # 보안 변수 (gitignored)
│   ├── db/migration/                       # Flyway 마이그레이션 (15개)
│   ├── static/                             # 정적 리소스
│   └── templates/                          # Thymeleaf 템플릿 (46개)
│
├── docs/                                   # 프로젝트 문서
├── build.gradle                            # Gradle 빌드 설정
└── CLAUDE.md                               # AI 어시스턴트 가이드
```

## 데이터 분석 기능

### 분석 대시보드

Chart.js 기반 인터랙티브 차트로 KBO 데이터를 시각화합니다.

| 차트 | 유형 | 데이터 |
|------|------|--------|
| 포지션별 WAR 분포 | Bar Chart | 포지션별 WAR 평균 |
| 팀별 타투 밸런스 | Stacked Bar | 팀별 타자WAR vs 투수WAR |
| 구단 전력 레이더 | Radar Chart | 타격/투구/수비/기동력/장타력 5축 |
| 선수 성장 추이 | Line Chart | 개별 선수 시즌별 지표 |

### 고급 분석 5종

| 분석 | 알고리즘 | 설명 |
|------|---------|------|
| 클러치 지수 | Z-score 가중합 | RBI(50%) + HR(30%) + OPS(20%) 기반 결정적 상황 기여도 |
| 선수 유사도 | 코사인 유사도 | 8개 세이버 지표(WAR, AVG, OPS, HR, SB, K%, BB%, ISO) 기반 |
| Marcel 예측 | 3년 가중평균 + 리그 회귀 | 5:4:3 비율 가중 + 노화 곡선 보정 (피크 27~28세) |
| 승리 예측 | 로지스틱 회귀 | ERA/OPS/WAR 기반 순수 Java 구현 (~2,700경기 학습) |
| 피로도 분석 | 등판 간격별 ERA | 휴식일별 ERA + 전후반기 비교 + 피로도 지수 산출 |

### 분석 컬럼 (매거진)

데이터 기반 분석 기사를 작성하고 관리합니다.

- **카테고리**: 선수(player), 팀(team), 경기(game), 트렌드(trend)
- **AI 자동 생성**: Gemini API를 통해 15개 주제를 매주 순환하며 자동 생성
- **사전 계산 방식**: Java/Python에서 모든 수치를 사전 계산 → AI는 멘트만 작성 (수치 날조 방지)
- **인라인 차트**: 기사 내 Chart.js 차트 삽입 지원

### AI 자동 생성 주제 (15종 순환)

세이버메트릭스 트렌드 · WAR 스포트라이트 · 브레이크아웃 후보 · 행운 보정 분석 · 상대전적 패턴 · 이닝별 득점 패턴 · 홈/원정 & 파크팩터 · 관중 동원 트렌드 · 퓨처스 유망주 · ABS 도입 영향 · 클러치/초커 분석 · 나이-성과 커브 · 라인업 효율성 · 월별/계절 패턴 · 포지션별 가치

### Python 분석 파이프라인

| 모듈 | 분석 수 | 주요 분석 |
|------|--------|---------|
| player/ | 9종 | 시즌 추이, 세이버 프로필, 타자/투수 유형, 나이별 성과, BABIP 운 |
| team/ | 6종 | 팀 전력 레이더, 상대전적, 순위 추이, 타투 밸런스, 홈/원정 |
| game/ | 7종 | 이닝별 득점, 역전 패턴, 핵심 선수, 승리 요인, 접전/대승 |
| topics/ | 15종 | 주제별 심층 분석 (세이버, WAR, ABS, 퓨처스 등) |
| special/ | 3종 | 팀 캐리 선수, 시즌 유형, 심판 영향 |

### 세이버메트릭스

| 지표 | 설명 | 산출 방식 |
|------|------|----------|
| BABIP | 인플레이 타율 | (H-HR) / (AB-SO-HR+SF) |
| ISO | 순장타력 | SLG - AVG |
| K% | 삼진율 | SO / PA |
| BB% | 볼넷율 | BB / PA |
| K/9 | 9이닝당 삼진 | (SO/IP) × 9 |
| BB/9 | 9이닝당 볼넷 | (BB/IP) × 9 |
| FIP | 수비 무관 투구 | ((13×HR)+(3×BB)-(2×SO))/IP + 상수 |
| xFIP | 기대 FIP | FIP에서 HR을 리그평균 HR/FB로 대체 |
| wRC+ | 조정 득점 생산력 | KBO 크롤링 |

## 게임 모드

### Simulation Mode (시뮬레이션 대전)

카드 기반 라인업을 구성하여 봇과 대전하는 모드

- **카드 시스템**: 실제 선수 스탯 기반 오버올(0~200) 카드 수집
- **라인업 구성**: 9명 타자 + 1명 투수로 라인업 편성
- **봇 난이도**: EASY / NORMAL / HARD
- **경기 시뮬레이션**: 이닝별 시뮬레이션, 이벤트 선택, MVP 선정

## 크롤러 시스템

### 크롤링 대상

| 크롤러 | 대상 데이터 | 기술 |
|--------|------------|------|
| KboPlayerStatsCrawler | 선수 통계 (타자/투수/수비/주루 + 세이버메트릭스) | Playwright |
| KboTeamStatsCrawler | 팀 순위/통계 | Playwright |
| KboScheduleCrawler | 경기 일정 | Jsoup |
| KboGameCenterCrawler | 경기 상세 (스코어보드, 라인업, 기록) | Playwright |
| KboPlayerProfileCrawler | 선수 프로필 | Playwright |
| KboCrowdCrawler | 관중 현황 | Playwright |
| KboFuturesCrawler | 퓨처스 리그 (일정/타자/투수/팀 통계) | Playwright |

### 다시즌 배치 크롤링

```bash
# 전체 크롤링
./gradlew crawl -Dcrawl.seasonStart=2020 -Dcrawl.seasonEnd=2025

# Basic2 독립 크롤러 (상황별 통계)
./gradlew crawlBasic2 -Dcrawl.seasonStart=2020 -Dcrawl.seasonEnd=2025

# 포지션별 재크롤링
./gradlew crawl -Dcrawl.pitcherOnly=true -Dcrawl.seasonStart=2024
```

## 시작하기

### 필수 요구사항

- Java 17 이상
- MySQL 8.0 이상
- Python 3 (분석 파이프라인용)
- Playwright (크롤링용, 자동 설치)

### 로컬 실행

```bash
git clone https://github.com/your-repo/glvpen.git
cd glvpen

# DB 생성
mysql -u root -p -e "CREATE DATABASE glvpen;"

# 보안 변수 설정 (security-variable.yml)
# mysql.password 및 gemini.api-key 설정

# 빌드 및 실행
./gradlew build
./gradlew bootRun
```

### 프로덕션 배포

```bash
# EC2 초기 설정 (1회)
bash deploy/setup-ec2.sh
bash deploy/setup-ssl.sh

# 로컬 DB → RDS 이관
bash deploy/db-dump.sh

# 배포
bash deploy/deploy.sh <EC2_IP> ~/.ssh/glvpen-key.pem

# 롤백 (긴급 복구)
bash deploy/rollback.sh <EC2_IP> ~/.ssh/glvpen-key.pem
```

### 네비게이션 구조

```
일정/결과 | 랭킹 | 분석 | 시뮬레이션 | 락커룸
```

## 엔티티 관계도

```
User (사용자)
├── favoriteTeam → Team (N:1)
├── UserCard (1:N)              보유 카드
├── UserLineup (1:N)            저장된 라인업
└── GameResult (1:N)            시뮬레이션 결과

Schedule (경기 일정)
├── ScoreBoard (1:1)            이닝별 점수
├── GameHighlight (1:N)         하이라이트
├── BatterLineup (1:N)          타자 라인업
├── BatterRecord (1:N)          타자 경기 기록
├── PitcherRecord (1:N)         투수 경기 기록
├── GameKeyPlayer (1:N)         주요 선수
└── GameSummaryRecord (1:N)     경기 요약 기록

Player (선수)
├── BatterStats (1:N)           시즌별 타자 통계 + 세이버메트릭스
├── PitcherStats (1:N)          시즌별 투수 통계 + 세이버메트릭스
├── DefenseStats (1:N)          수비 통계
├── RunnerStats (1:N)           기동력 통계
└── PlayerCardOverall (1:N)     게임용 카드

Team (팀)
├── TeamRanking (1:N)           시즌별 순위
├── TeamStats (1:N)             팀 통계
└── TeamHeadToHead (1:N)        상대 성적

AnalysisColumn (분석 컬럼)      데이터 분석 기사

CrowdStats (관중 데이터)        시즌별/팀별 관중 현황

Futures (퓨처스 리그)
├── FuturesSchedule (1:N)       퓨처스 경기 일정
├── FuturesBatterStats (1:N)    퓨처스 타자 통계
├── FuturesPitcherStats (1:N)   퓨처스 투수 통계
└── FuturesTeamStats (1:N)      퓨처스 팀 통계
```

## REST API

### 분석 API

```http
GET  /api/analysis/dashboard?season=2025
GET  /api/analysis/player/{id}/trend?category=WAR&startYear=2020&endYear=2025
GET  /api/analysis/team/{id}/balance?season=2025
GET  /api/analysis/war-distribution?season=2025
GET  /api/analysis/team-comparison?season=2025
POST /api/analysis/columns/generate
```

### 고급 분석 API

```http
GET  /api/analysis/advanced/clutch?season=2025&limit=20
GET  /api/analysis/advanced/similarity/{playerId}?season=2025&topN=10
GET  /api/analysis/advanced/projection/batter/{playerId}?targetSeason=2026
GET  /api/analysis/advanced/projection/batters?targetSeason=2026&limit=20
GET  /api/analysis/advanced/win-prediction?homeTeamId=1&awayTeamId=5&season=2025
GET  /api/analysis/advanced/fatigue/{playerId}?season=2025
GET  /api/analysis/advanced/fatigue/ranking?season=2025&limit=20
```

### 시뮬레이션 API

```http
POST /api/simulation/start
POST /api/simulation/select-event
GET  /api/cards
GET  /api/lineups
GET  /api/game-results
```

### 통계 API

```http
GET  /api/player-stats
GET  /api/team-ranking
```

## 프로젝트 통계

| 항목 | 수량 |
|------|------|
| Java 파일 | 240개 |
| Python 스크립트 | 88개 |
| 엔티티 클래스 | 33개 |
| HTML 템플릿 | 46개 |
| KBO 크롤러 | 7개 |
| Flyway 마이그레이션 | 15개 |
| 고급 분석 기능 | 5종 |
| AI 자동 생성 주제 | 15종 |
| Python 분석 모듈 | 40종 |

## 관련 문서

| 문서 | 경로 | 설명 |
|------|------|------|
| API 가이드 | `docs/architecture/RESTFUL_API_GUIDE.md` | API 설계 원칙 |
| DB 구조 | `docs/database/DATABASE_STRUCTURE.md` | 테이블 설계 |
| 파이프라인 | `docs/PIPELINE.md` | 데이터 흐름 + 분석 파이프라인 |
| AWS 배포 가이드 | `docs/deployment/AWS_DEPLOYMENT_GUIDE.md` | 배포 아키텍처 및 단계별 설정 |
| 클러치 지수 | `docs/analysis/CLUTCH_INDEX.md` | Z-score 기반 클러치 분석 |
| 선수 유사도 | `docs/analysis/PLAYER_SIMILARITY.md` | 코사인 유사도 알고리즘 |
| Marcel 예측 | `docs/analysis/MARCEL_PROJECTION.md` | 성적 예측 시스템 |
| 승리 예측 | `docs/analysis/WIN_PREDICTION.md` | 로지스틱 회귀 모델 |
| 피로도 분석 | `docs/analysis/FATIGUE_ANALYSIS.md` | 투수 피로도 지수 |
| AI 컬럼 설계 | `docs/analysis/AI_COLUMN_PRECOMPUTE_DESIGN.md` | Gemini 자동 생성 아키텍처 |

## 라이선스

이 프로젝트는 교육 목적으로 제작되었습니다.

## 문의

프로젝트 관련 문의: kyus0919@gmail.com
