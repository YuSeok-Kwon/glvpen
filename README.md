# BaseBall LOCK 2.0

KBO 야구 데이터 기반 시뮬레이션 게임

## 프로젝트 개요

BaseBall LOCK은 실제 KBO 경기 데이터를 활용한 야구 시뮬레이션 게임입니다. 사용자는 실제 경기 일정과 라인업으로 게임을 즐기거나, 커스텀 타자를 육성하거나, 시뮬레이션 모드로 빠른 대전을 즐길 수 있습니다.

### 핵심 기능

- **실제 데이터 기반**: KBO 실제 경기 데이터를 크롤링하여 사용
- **다양한 게임 모드**: Real Match, Custom Player, Simulation 3가지 모드 제공
- **성장 시스템**: 커스텀 타자 육성 및 스탯 관리
- **실시간 시뮬레이션**: WebSocket 기반 턴제 게임 진행
- **통계 및 랭킹**: 상세한 경기 통계와 팀 순위 시스템

## 기술 스택

### Backend
- **Language**: Java 17+
- **Framework**: Spring Boot
- **ORM**: JPA (Hibernate)
- **Database**: MySQL / MariaDB
- **Authentication**: BCrypt, JWT (Access + Refresh Token)
- **Real-time**: WebSocket

### Frontend
- **Framework**: React
- **Language**: TypeScript
- **Animation**: GSAP, SVG

### Crawler
- **Library**: Selenium, Jsoup
- **Target**: Statiz.com (KBO 데이터)

## 프로젝트 구조

```
BaseBallLOCK/
├── src/
│   ├── main/
│   │   ├── java/com/kepg/BaseBallLOCK/
│   │   │   ├── modules/              # Feature-based 모듈 구조
│   │   │   │   ├── user/            # 사용자 관리
│   │   │   │   │   ├── controller/
│   │   │   │   │   ├── service/
│   │   │   │   │   ├── repository/
│   │   │   │   │   ├── domain/
│   │   │   │   │   └── dto/
│   │   │   │   ├── gameMode/        # 게임 모드
│   │   │   │   │   ├── realMatch/   # Real Match 모드
│   │   │   │   │   ├── customPlayerMode/ # Custom Player 모드
│   │   │   │   │   └── simulationMode/   # Simulation 모드
│   │   │   │   ├── game/            # 경기 관련
│   │   │   │   │   ├── schedule/    # 경기 일정
│   │   │   │   │   ├── lineUp/      # 라인업
│   │   │   │   │   ├── record/      # 경기 기록
│   │   │   │   │   ├── scoreBoard/  # 스코어보드
│   │   │   │   │   └── highlight/   # 하이라이트
│   │   │   │   ├── player/          # 선수 정보
│   │   │   │   │   ├── domain/
│   │   │   │   │   ├── service/
│   │   │   │   │   ├── repository/
│   │   │   │   │   └── stats/       # 선수 통계
│   │   │   │   ├── team/            # 팀 정보
│   │   │   │   │   ├── domain/
│   │   │   │   │   ├── service/
│   │   │   │   │   ├── teamRanking/ # 팀 순위
│   │   │   │   │   └── teamStats/   # 팀 통계
│   │   │   │   └── review/          # 리뷰 시스템
│   │   │   │       ├── controller/
│   │   │   │       ├── service/
│   │   │   │       ├── repository/
│   │   │   │       └── summary/     # 리뷰 요약
│   │   │   ├── crawler/             # 데이터 크롤링
│   │   │   │   ├── game/            # 경기 크롤러
│   │   │   │   ├── player/          # 선수 크롤러
│   │   │   │   ├── schedule/        # 일정 크롤러
│   │   │   │   ├── team/            # 팀 크롤러
│   │   │   │   └── util/            # 크롤링 유틸리티
│   │   │   ├── common/              # 공통 모듈
│   │   │   │   ├── ai/              # AI 관련
│   │   │   │   ├── game/            # 게임 공통 로직
│   │   │   │   ├── enums/           # Enum 정의
│   │   │   │   ├── exception/       # 예외 처리
│   │   │   │   └── validator/       # 검증 로직
│   │   │   └── config/              # 설정
│   │   └── resources/
│   │       ├── static/              # 정적 리소스
│   │       │   ├── css/
│   │       │   ├── game/
│   │       │   ├── emblems/         # 팀 엠블럼
│   │       │   ├── soundEffect/
│   │       │   └── textures/
│   │       ├── templates/           # Thymeleaf 템플릿
│   │       │   ├── user/
│   │       │   ├── game/
│   │       │   ├── schedule/
│   │       │   ├── review/
│   │       │   ├── ranking/
│   │       │   ├── realmatch/
│   │       │   ├── customplayer/
│   │       │   ├── fragments/
│   │       │   └── layouts/
│   │       └── db/
│   │           └── migration/       # Flyway 마이그레이션
│   └── test/
├── docs/                            # 프로젝트 문서
│   ├── planning/                    # 기획서 및 게임 기획
│   ├── architecture/                # 아키텍처 설계 문서
│   ├── database/                    # DB 스키마 및 ERD
│   ├── reports/                     # 개발 리포트
│   ├── migration/                   # 마이그레이션 가이드
│   └── references/                  # 참고 자료
├── build.gradle                     # Gradle 빌드 설정
├── settings.gradle                  # Gradle 설정
└── README.md                        # 프로젝트 소개
```

### Feature-based 구조의 장점

- **도메인별 관심사 집중**: 각 모듈이 독립적인 기능을 담당
- **높은 응집도**: 관련된 코드가 한 곳에 모여 있음
- **확장 가능성**: 새 기능 추가 시 폴더 하나로 확장
- **테스트 용이성**: 모듈별 단위 테스트 작성 가능
- **마이크로서비스 전환 용이**: 필요 시 독립적인 서비스로 분리 가능

## 게임 모드

### 1. Real Match Mode

실제 KBO 경기 일정과 라인업을 기반으로 한 시뮬레이션 모드

- **데이터 소스**: Statiz.com 크롤링
- **크롤링 스케줄**:
  - 전일 라인업: 새벽 자동 크롤링
  - 당일 라인업: 경기 1시간 전 크롤링
- **주요 테이블**: `schedule`, `lineup`, `record`, `score_board`
- **특징**: 실제 선수들의 최신 폼을 반영한 시뮬레이션

### 2. Custom Player Mode

유저가 만든 타자를 키우는 RPG형 성장 시스템

- **진행 방식**: WebSocket 기반 실시간 턴제
- **성장 시스템**: 경험치 획득 및 레벨업
- **스탯 종류**:
  - **파워**: 홈런 확률 증가
  - **정확**: 안타 확률 증가
  - **속도**: 2루타 확률 증가
  - **수비**: MVP 선정 평가 반영
- **특별 보상**: MVP 달성 시 경험치 2배

### 3. Simulation Mode

빠른 대전을 위한 자동 시뮬레이션 모드

- **구성**: 유저 라인업 vs 봇 라인업
- **난이도**: EASY / NORMAL / HARD
- **연출**: 하이라이트 중심의 빠른 진행
- **특징**: 시간 압축형 게임 플레이

## 시작하기

### 필수 요구사항

- Java 17 이상
- MySQL 8.0 이상
- Gradle 7.0 이상
- Chrome WebDriver (크롤링용)

### 설치 및 실행

1. 저장소 클론
```bash
git clone https://github.com/your-repo/BaseBallLOCK.git
cd BaseBallLOCK
```

2. 데이터베이스 설정
```sql
CREATE DATABASE baseball_lock;
```

3. application.yml 설정
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/baseball_lock
    username: your_username
    password: your_password
```

4. 빌드 및 실행
```bash
./gradlew build
./gradlew bootRun
```

5. 브라우저에서 접속
```
http://localhost:8080
```

### 크롤링 실행

```bash
# 일정 크롤링
./run_crawler.sh schedule

# 선수 데이터 크롤링
./run_crawler.sh player

# 경기 기록 크롤링
./run_crawler.sh game
```

## API 문서

### RESTful API 설계 원칙

- **네이밍**: kebab-case 사용 (`/api/custom-players`)
- **복수형**: 리소스는 복수형으로 표현 (`/api/users`)
- **HTTP 메서드**: GET (조회), POST (생성), PUT (전체 수정), PATCH (부분 수정), DELETE (삭제)

### 주요 엔드포인트

#### 사용자
```
GET    /api/users/{userId}           # 사용자 정보 조회
PUT    /api/users/{userId}           # 사용자 정보 수정
```

#### 게임
```
GET    /api/games                    # 게임 목록 조회
POST   /api/games                    # 게임 생성
GET    /api/games/{gameId}           # 게임 상세 조회
GET    /api/games/{gameId}/highlights # 하이라이트 조회
```

#### 커스텀 선수
```
GET    /api/custom-players           # 커스텀 선수 목록
POST   /api/custom-players           # 커스텀 선수 생성
POST   /api/custom-players/{id}/match-result  # 경기 결과 반영
POST   /api/custom-players/{id}/allocate      # 스탯 포인트 할당
```

#### 리뷰
```
GET    /api/reviews                  # 리뷰 목록
POST   /api/reviews                  # 리뷰 작성
GET    /api/reviews/calendar         # 캘린더 형식 조회
```

상세한 API 문서는 `docs/architecture/RESTFUL_API_GUIDE.md`를 참조하세요.

## 개발 가이드

### 코딩 컨벤션

#### 네이밍 규칙

| 대상 | 규칙 | 예시 |
|------|------|------|
| 클래스 | PascalCase | `GameService` |
| 메서드/변수 | camelCase | `calculateMvp()` |
| 상수 | UPPER_SNAKE | `MAX_LEVEL` |
| 테이블 | snake_case | `custom_player` |
| URL | kebab-case | `/api/custom-players` |

#### 파일 구조

각 모듈은 다음과 같은 구조를 따릅니다:

```
domain/
├── controller/
│   └── {Domain}Controller.java
├── service/
│   └── {Domain}Service.java
├── repository/
│   └── {Domain}Repository.java
├── domain/
│   └── {Domain}.java
└── dto/
    ├── {Domain}RequestDTO.java
    └── {Domain}ResponseDTO.java
```

### Git Commit Convention

형식:
```
[이름][type](<scope>): <description>
```

#### Type 종류

| Type | 설명 |
|------|------|
| feat | 새 기능 추가 |
| fix | 버그 수정 |
| refactor | 리팩토링 |
| docs | 문서 수정 |
| style | 코드 스타일 변경 |
| test | 테스트 추가/수정 |
| chore | 빌드, 설정 변경 |
| api | API 엔드포인트 변경 |
| ui | UI/UX 변경 |
| db | DB 스키마 변경 |

#### 예시

```
[권유석][feat](custom-player): 타자 성장 시스템 구현
[권유석][fix](auth): 토큰 갱신 로직 오류 수정
[권유석][refactor](crawler): 크롤러 유틸리티 클래스 적용
```

### 개발 원칙

1. **Entity 직접 사용 금지**: Controller와 Service 간에는 DTO 사용
2. **Service 레이어 책임**: 모든 비즈니스 로직은 Service에서 처리
3. **Hard-coding 최소화**: 설정값은 application.yml 또는 상수 클래스 사용
4. **예외 처리**: 명확한 예외 메시지와 적절한 HTTP 상태 코드 반환
5. **테스트 작성**: 주요 로직에 대한 단위 테스트 필수

## 인증 시스템

### 비밀번호 암호화
- **방식**: BCrypt
- **Salt Rounds**: 10-12

### 토큰 기반 인증
- **Access Token**: 15분 (HttpOnly Cookie)
- **Refresh Token**: 7-30일 (HttpOnly Cookie + DB)

### 보안
- Secure 쿠키 (HTTPS only)
- SameSite=Strict (CSRF 방지)
- HttpOnly (XSS 방지)

상세한 인증 시스템은 `docs/architecture/baseball-lock-auth-system.md`를 참조하세요.

## 문서 구조

### docs/planning
- 게임 기획서
- 게임 모드 상세 기획
- UI/UX 설계

### docs/architecture
- 시스템 아키텍처
- 인증 시스템 설계
- 애니메이션 설계
- RESTful API 가이드

### docs/database
- DB 스키마
- ERD
- 테이블 설계서

### docs/reports
- 개발 진행 리포트
- 에러 체크 리포트
- API 구현 리포트

### docs/migration
- 데이터 마이그레이션 가이드
- 암호화 마이그레이션

### docs/references
- 참고 자료
- 외부 프로젝트 문서

## 크롤러 시스템

### 크롤링 대상
- **경기 일정**: 날짜별 경기 일정 및 결과
- **선수 통계**: 타자/투수 시즌 통계
- **경기 기록**: 경기별 타자/투수 개인 기록
- **팀 순위**: 팀 순위 및 기본 통계

### 크롤링 스케줄
- **선수 데이터**: 매일 새벽 1시 (Cron: `0 0 1 * * *`)
- **팀 데이터**: 매일 자정 직전 (Cron: `0 59 23 * * *`)
- **경기 기록**: 수동 실행 (경기 종료 후)

### 크롤러 유틸리티
- `CrawlerUtils`: 공통 크롤링 메서드 (페이지 로딩, 숫자 파싱, 패턴 추출)
- `TeamMappingConstants`: 팀/경기장 매핑 상수
- `WebDriverFactory`: WebDriver 생성 팩토리

## 라이선스

이 프로젝트는 교육 목적으로 제작되었습니다.

## 기여

기여를 원하시는 분은 다음 절차를 따라주세요:

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m '[이름][feat]: Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 문의

프로젝트 관련 문의: kyus0919@gmail.com
