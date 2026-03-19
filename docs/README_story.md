# glvpen 3.0

> **좋아하는 것을 따라갔더니, 하고 싶은 것을 찾았다.**

9살, 엄마 손을 잡고 처음 야구장에 갔다. 규칙도 몰랐다. 치킨이 맛있었고, 사람들이 함께 소리 지르는 게 신났고, 공이 날아가는 게 멋있었다. 그게 전부였다.

그런데 그 '전부'가 시작이었다.

야구는 어느새 매일의 루틴이 되었고, Java 개발자를 꿈꾸며 시작한 이 프로젝트는 나를 데이터의 세계로 이끌었다. Controller를 만들고 Service 로직을 짜야 하는데, 정작 내가 몰두하고 있던 건 크롤링 파싱 구조, 다시즌 데이터 스키마 설계, SQL 쿼리 최적화, 그리고 BABIP이 비정상적인 타자의 다음 시즌 예측이었다.

야구는 데이터의 스포츠다. 좋아하는 것(야구)과 흥미를 느끼는 것(데이터)이 정확히 겹치는 지점에서 glvpen이 태어났다.

---

## 프로젝트 개요

KBO 데이터 분석 & 시뮬레이션 플랫폼. 실제 KBO 경기 데이터를 외부 사이트에서 크롤링하여 데이터 분석 인사이트를 도출하고, 카드 기반 시뮬레이션을 즐길 수 있는 플랫폼이다. 다시즌(2020~2025) 데이터와 세이버메트릭스 지표를 활용한 분석 대시보드 및 AI 자동 분석 컬럼을 제공한다.

### 프로젝트 진화

| 버전 | 단계 | 주요 내용 |
|------|------|---------|
| v1.0 | 개발 연습 | 경기 일정 크롤링, 간단한 시뮬레이션 게임, Spring Boot 학습 |
| v2.0 | 게임 확장 | 카드/라인업/봇 대전, 크롤러 고도화, 엔티티 25개로 성장 |
| v3.0 | 데이터 분석 | 분석 대시보드, 세이버메트릭스 파이프라인, AI 자동 분석 컬럼 |

### 핵심 기능

- **데이터 분석 대시보드**: 포지션별 WAR 분포, 팀 타투 밸런스, 선수 성장 추이 차트
- **분석 컬럼**: 선수/팀/경기/트렌드 분석 기사 (수동 작성 + AI 자동 생성)
- **다시즌 크롤링**: 2020~2025 KBO 데이터 일괄 수집
- **세이버메트릭스**: BABIP, ISO, K%, BB%, K/9, BB/9, FIP, xFIP 등 파생 지표
- **시뮬레이션 게임**: 실제 선수 스탯 기반 카드 수집 및 대전
- **AI 분석**: Google Gemini API를 활용한 분석 기사 자동 생성

## 기술 스택

| 분류 | 기술 |
|------|------|
| Backend | Java 17, Spring Boot 3.3.10, Spring Data JPA, MyBatis |
| Frontend | Thymeleaf (Layout Dialect), Bootstrap 5, Chart.js 4.4.1 |
| Database | MySQL 8.0 (mysql-connector-j) |
| Crawling | Selenium 4.20.0, Jsoup 1.15.3, ChromeDriver |
| Auth | BCrypt (spring-security-crypto 6.3.0), Session 기반 |
| AI | Google Gemini API (gemini-1.5-flash) |
| Dev Tools | Lombok, P6Spy, Spring DevTools |

## 프로젝트 구조

```
glvpen/
├── src/main/java/com/kepg/glvpen/
│   ├── config/                         # 설정
│   ├── crawler/                        # 데이터 크롤링
│   ├── common/                         # 공통 모듈
│   └── modules/                        # Feature-based 모듈
│       ├── user/                       #   사용자 관리
│       ├── game/                       #   경기 데이터
│       ├── player/                     #   선수 정보 + 세이버메트릭스
│       ├── team/                       #   팀 정보 + 순위
│       ├── analysis/                   #   데이터 분석 (v3 신규)
│       └── gameMode/                   #   게임 모드
│           └── simulationMode/         #     시뮬레이션
├── docs/                               # 프로젝트 문서
├── CLAUDE.md                           # AI 어시스턴트 가이드
└── build.gradle                        # Gradle 빌드 설정
```

## 시작하기

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

## 문의

프로젝트 관련 문의: kyus0919@gmail.com

## 라이선스

이 프로젝트는 교육 목적으로 제작되었습니다.
