# glvpen 3.0

**글러브와 펜** — KBO 데이터 분석 & 시뮬레이션 플랫폼

## 프로젝트 개요

glvpen은 KBO 공식 사이트에서 경기 데이터를 크롤링하여 **데이터 분석 인사이트를 도출**하고, 카드 기반 시뮬레이션 게임을 즐길 수 있는 플랫폼입니다. 다시즌(2020~2025) 데이터와 세이버메트릭스 지표를 활용한 분석 대시보드, AI 자동 분석 컬럼, 관중/퓨처스 리그 통계를 제공합니다.

### 핵심 기능

- **데이터 분석 대시보드**: 포지션별 WAR 분포, 팀 타투 밸런스, 선수 성장 추이 차트
- **분석 컬럼 (매거진)**: 선수/팀/경기/트렌드 분석 기사 (수동 작성 + AI 자동 생성)
- **다시즌 크롤링**: 2020~2025 KBO 데이터 일괄 수집 (일정, 팀, 선수, 관중, 퓨처스, 게임센터)
- **세이버메트릭스**: BABIP, ISO, K%, BB%, K/9, BB/9, FIP, xFIP 등 파생 지표
- **시뮬레이션 게임**: 실제 선수 스탯 기반 카드 수집 및 대전
- **AI 분석**: Google Gemini API를 활용한 분석 기사 자동 생성
- **관중 통계**: 시즌별/팀별 관중 현황 분석
- **퓨처스 리그**: 2군 경기 일정, 타자/투수/팀 통계

## 기술 스택

| 분류 | 기술 |
|------|------|
| Backend | Java 17, Spring Boot 3.3.10, Spring Data JPA, MyBatis |
| Frontend | Thymeleaf (Layout Dialect), Bootstrap 5, Chart.js 4.4.1 |
| Database | MySQL 8.0 (mysql-connector-j) |
| Crawling | Playwright 1.49.0, Selenium 4.20.0, Jsoup 1.15.3 |
| Auth | BCrypt (spring-security-crypto 6.3.0), Session 기반 |
| AI | Google Gemini API (gemini-1.5-flash) |
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
│   │   └── util/                           #   WebDriverFactory, CrawlerUtils, TeamMappingConstants
│   │
│   ├── common/                             # 공통 모듈
│   │   ├── ai/GeminiClient                 #   Gemini API 클라이언트
│   │   ├── stats/SabermetricsCalculator    #   세이버메트릭스 계산기
│   │   ├── game/                           #   MvpCalculator, GameResultSaver, GameLogFormatter
│   │   ├── enums/                          #   BatterSortType, PitcherSortType, DefenseSortType, RunnerSortType
│   │   ├── exception/                      #   GlobalExceptionHandler
│   │   └── validator/                      #   SeasonValidator
│   │
│   └── modules/                            # Feature-based 모듈
│       ├── user/                           #   사용자 관리 (로그인/가입/세션)
│       ├── game/                           #   경기 데이터
│       │   ├── schedule/                   #     경기 일정 (controller/service/repository)
│       │   ├── scoreBoard/                 #     스코어보드
│       │   ├── highlight/                  #     경기 하이라이트
│       │   ├── lineUp/                     #     타자 라인업
│       │   ├── record/                     #     타자/투수 기록
│       │   ├── keyPlayer/                  #     경기 주요 선수
│       │   └── summaryRecord/              #     경기 요약 기록
│       ├── player/                         #   선수 정보
│       │   ├── stats/                      #     시즌 통계 + 세이버메트릭스
│       │   │   ├── service/                #       BatterStats, PitcherStats, DefenseStats, RunnerStats
│       │   │   ├── repository/             #       각 스탯별 Repository
│       │   │   ├── dto/                    #       통계 DTO
│       │   │   ├── statsDto/               #       랭킹 DTO (Batter/Pitcher/Defense/Runner)
│       │   │   └── controller/             #       뷰 + REST 컨트롤러
│       │   └── crawler/                    #     선수 크롤링 추가 로직
│       ├── team/                           #   팀 정보
│       │   ├── teamRanking/                #     팀 순위
│       │   ├── teamStats/                  #     팀 통계
│       │   ├── teamHeadToHead/             #     팀 간 상대 성적
│       │   └── crawler/                    #     팀 크롤링 추가 로직
│       ├── analysis/                       #   데이터 분석 (v3)
│       │   ├── controller/                 #     대시보드/컬럼/매거진 뷰 + REST API
│       │   ├── domain/                     #     AnalysisColumn Entity
│       │   ├── dto/                        #     Dashboard, ChartData, PlayerAnalysis, TeamAnalysis
│       │   ├── repository/                 #     AnalysisColumnRepository
│       │   └── service/                    #     AnalysisService, ColumnService, AiColumnGeneratorService
│       ├── crowd/                          #   관중 데이터
│       ├── futures/                        #   퓨처스 리그
│       │   ├── schedule/                   #     퓨처스 경기 일정
│       │   └── stats/                      #     퓨처스 타자/투수/팀 통계
│       └── gameMode/                       #   게임 모드
│           └── simulationMode/             #     시뮬레이션 (카드/라인업/대전)
│
├── src/main/resources/
│   ├── application.yml                     # 앱 설정
│   ├── security-variable.yml               # 보안 변수 (gitignored)
│   ├── db/migration/                       # Flyway 마이그레이션
│   │   ├── V3__Drop_Custom_and_RealMatch_Tables.sql
│   │   ├── V4__Drop_Review_Tables.sql
│   │   └── V5__Add_AnalysisColumn_Magazine_Fields.sql
│   ├── static/                             # 정적 리소스
│   │   ├── css/style.css                   #   메인 스타일시트
│   │   └── emblems/                        #   팀 로고 이미지
│   └── templates/                          # Thymeleaf 템플릿
│       ├── layouts/                        #   레이아웃 (default, userDefault, noheader)
│       ├── fragments/                      #   공통 조각 (header, footer, userHeader)
│       ├── analysis/                       #   분석 대시보드/매거진/컬럼 (7개)
│       ├── game/                           #   시뮬레이션 게임 (11개)
│       ├── ranking/                        #   팀/선수 순위 (7개)
│       ├── schedule/                       #   경기 일정/결과 (2개)
│       └── user/                           #   로그인/가입/홈 (6개)
│
├── docs/                                   # 프로젝트 문서
├── build.gradle                            # Gradle 빌드 설정
├── CLAUDE.md                               # AI 어시스턴트 가이드
└── run_crawler.sh                          # 크롤러 실행 스크립트
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

### 분석 컬럼 (매거진)

데이터 기반 분석 기사를 작성하고 관리합니다.

- **카테고리**: 선수(player), 팀(team), 경기(game), 트렌드(trend)
- **AI 자동 생성**: Gemini API를 통해 매주 월요일 주간 분석 자동 생성
- **인라인 차트**: 기사 내 Chart.js 차트 삽입 지원

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

`CrawlersManualRunner`에서 크롤링 옵션을 설정하여 실행합니다.

```bash
# 기본 실행 (CrawlersManualRunner 내부 설정 사용)
./run_crawler.sh

# 시스템 프로퍼티로 옵션 오버라이드
java -Dcrawl.seasonStart=2022 -Dcrawl.seasonEnd=2024 \
     -Dcrawl.pitcherOnly=true \
     -Dcrawl.sabermetrics=true \
     -jar build/libs/glvpen-0.0.1-SNAPSHOT.war
```

**크롤링 옵션**:
- `crawlSchedule` — 경기 일정
- `crawlTeam` — 팀 데이터
- `crawlPlayer` — 선수 데이터 (타자/투수/수비/주루)
- `crawlPitcherOnly` / `crawlDefenseOnly` / `crawlRunnerOnly` — 포지션별 재크롤링
- `crawlCrowd` — 관중 현황
- `crawlPlayerProfile` — 선수 프로필
- `crawlFutures` — 퓨처스 리그
- `crawlGameCenter` — 게임센터 (경기 상세)
- `calculateSabermetrics` — 세이버메트릭스 일괄 계산

## 시작하기

### 필수 요구사항

- Java 17 이상
- MySQL 8.0 이상
- Playwright (크롤링용, 자동 설치)

### 설치 및 실행

```bash
git clone https://github.com/your-repo/glvpen.git
cd glvpen

# DB 생성
mysql -u root -p -e "CREATE DATABASE BaseBallLOCK;"

# 보안 변수 설정 (security-variable.yml)
# mysql.password 및 gemini.api-key 설정

# 빌드 및 실행
./gradlew build
./gradlew bootRun
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
| Java 파일 | 217개 |
| 엔티티 클래스 | 41개 |
| HTML 템플릿 | 40개 |
| KBO 크롤러 | 7개 |
| Flyway 마이그레이션 | 5개 |

## 라이선스

이 프로젝트는 교육 목적으로 제작되었습니다.

## 문의

프로젝트 관련 문의: kyus0919@gmail.com
