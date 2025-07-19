# BaseBall LOCK RESTful API 설계 가이드

## RESTful 원칙 적용

### 1. HTTP 메서드 사용

| HTTP 메서드 | 용도             | 예시                                               |
| ----------- | ---------------- | -------------------------------------------------- |
| GET         | 리소스 조회      | `GET /api/games`, `GET /api/users/123`             |
| POST        | 리소스 생성      | `POST /api/games`, `POST /api/reviews`             |
| PUT         | 리소스 전체 수정 | `PUT /api/users/123`, `PUT /api/lineups/456`       |
| PATCH       | 리소스 부분 수정 | `PATCH /api/players/123`, `PATCH /api/teams/456`   |
| DELETE      | 리소스 삭제      | `DELETE /api/games/123`, `DELETE /api/reviews/456` |

### 2. URL 설계 규칙

#### ✅ RESTful URL 구조

```
/api/{resource-collection}           # 컬렉션 조회/생성
/api/{resource-collection}/{id}      # 특정 리소스 조회/수정/삭제
/api/{resource-collection}/{id}/{sub-resource}  # 하위 리소스
```

#### ❌ 기존 Non-RESTful URLs

```
/user/mypage                         # 동작 중심
/ranking/teamranking-view           # 혼재된 구조
/game/ready                         # 동작 중심
/review/calendar                    # 동작 중심
```

#### ✅ 새로운 RESTful URLs

```
GET  /api/users/{userId}/profile     # 사용자 프로필 조회
GET  /api/teams/rankings            # 팀 랭킹 조회
POST /api/games                     # 게임 생성
GET  /api/reviews?date={date}       # 리뷰 목록 조회 (날짜별)
```

## 3. 전체 프로젝트 RESTful API 설계

### User Management

```http
GET    /api/users/{userId}                 # 사용자 정보 조회
PUT    /api/users/{userId}                 # 사용자 정보 수정
GET    /api/users/{userId}/profile         # 사용자 프로필 조회
GET    /api/users/{userId}/statistics      # 사용자 통계 조회
```

### Team Management

```http
GET    /api/teams                          # 팀 목록 조회
GET    /api/teams/{teamId}                 # 특정 팀 조회
GET    /api/teams/rankings                 # 팀 랭킹 조회
GET    /api/teams/{teamId}/stats           # 팀 통계 조회
```

### Player Management

```http
GET    /api/players                        # 선수 목록 조회
GET    /api/players/{playerId}             # 특정 선수 조회
GET    /api/players/{playerId}/stats       # 선수 통계 조회
GET    /api/players/top                    # 상위 선수 조회
```

### Game Management

```http
GET    /api/games                          # 게임 목록 조회
POST   /api/games                          # 게임 생성
GET    /api/games/{gameId}                 # 특정 게임 조회
PUT    /api/games/{gameId}                 # 게임 수정
DELETE /api/games/{gameId}                 # 게임 삭제
GET    /api/games/schedules                # 경기 일정 조회
GET    /api/games/{gameId}/highlights      # 게임 하이라이트 조회
```

### Review Management

```http
GET    /api/reviews                        # 리뷰 목록 조회
POST   /api/reviews                        # 리뷰 생성
GET    /api/reviews/{reviewId}             # 특정 리뷰 조회
PUT    /api/reviews/{reviewId}             # 리뷰 수정
DELETE /api/reviews/{reviewId}             # 리뷰 삭제
GET    /api/reviews/calendar               # 캘린더용 리뷰 조회
GET    /api/reviews/summaries              # 리뷰 요약 조회
```

### Game Modes

#### Quick Game Mode

```http
POST   /api/quick-games                    # 빠른 게임 생성
GET    /api/quick-games/{gameId}           # 특정 게임 조회
GET    /api/quick-games?userId={userId}    # 사용자별 게임 목록
DELETE /api/quick-games/{gameId}           # 게임 삭제
```

#### Match Prediction Mode

```http
POST   /api/match-predictions              # 예측 게임 생성
GET    /api/match-predictions/{gameId}     # 특정 예측 조회
GET    /api/match-predictions?userId={userId} # 사용자별 예측 목록
PUT    /api/match-predictions/{gameId}     # 예측 수정
GET    /api/match-predictions/{gameId}/results # 예측 결과 조회
```

#### Custom Player Mode

```http
GET    /api/custom-players                 # 커스텀 선수 목록 조회
POST   /api/custom-players                 # 커스텀 선수 생성
GET    /api/custom-players/{playerId}      # 특정 커스텀 선수 조회
PUT    /api/custom-players/{playerId}      # 커스텀 선수 수정
DELETE /api/custom-players/{playerId}      # 커스텀 선수 삭제
```

#### Simulation Mode

```http
POST   /api/simulations                    # 시뮬레이션 게임 생성
GET    /api/simulations/{gameId}           # 특정 시뮬레이션 조회
GET    /api/simulations/{gameId}/results   # 시뮬레이션 결과 조회
PUT    /api/simulations/{gameId}/lineup    # 라인업 수정
GET    /api/user-cards                     # 사용자 카드 조회
POST   /api/user-cards                     # 사용자 카드 생성
```

# 사용자 통계 조회

GET /api/quick-games/users/{userId}/statistics

# 게임 삭제

DELETE /api/quick-games/{gameId}

````

### Custom Player Mode
```http
# 커스텀 선수 생성
POST /api/custom-players
Body: CustomPlayerRequestDTO

# 특정 선수 조회
GET /api/custom-players/{playerId}

# 사용자별 선수 목록 조회
GET /api/custom-players?userId={userId}

# 선수 정보 수정
PUT /api/custom-players/{playerId}

# 선수 삭제
DELETE /api/custom-players/{playerId}

# 게임 실행
POST /api/custom-players/games

# 사용자 통계 조회
GET /api/custom-players/users/{userId}/statistics
````

## 4. HTTP 상태 코드 사용

| 상태 코드                 | 의미                  | 사용 예시            |
| ------------------------- | --------------------- | -------------------- |
| 200 OK                    | 성공                  | 조회, 수정 성공      |
| 201 Created               | 생성 성공             | POST 요청 성공       |
| 204 No Content            | 성공 (응답 본문 없음) | DELETE 성공          |
| 400 Bad Request           | 잘못된 요청           | 유효하지 않은 데이터 |
| 404 Not Found             | 리소스 없음           | 존재하지 않는 ID     |
| 500 Internal Server Error | 서버 오류             | 예상치 못한 오류     |
| 501 Not Implemented       | 구현되지 않음         | TODO 메서드          |

## 5. 다음 단계 개선 사항

### TODO: 미구현 메서드들

```java
// QuickGameController
- getGameById(Long gameId)
- deleteGame(Long gameId)

// CustomPlayerController
- getPlayerById(Long playerId)
- updatePlayer(Long playerId, ...)
- deletePlayer(Long playerId)
```

### 추가 RESTful 적용 대상

1. **SimulationMode Controllers**

   - UserLineupRestController
   - PlayerCardRestController
   - GameEventRestController

2. **MatchPredictionMode Controllers**

   - RealMatchController

3. **공통 모듈 Controllers**
   - User, Team, Review 등

## 6. RESTful API 명명 규칙

### 리소스 이름

- 복수형 사용: `users`, `games`, `players`
- kebab-case 사용: `quick-games`, `custom-players`
- 명사 사용, 동사 금지

### 쿼리 파라미터

- camelCase 사용: `userId`, `gameType`
- 필터링: `?status=active&level=5`
- 페이징: `?page=1&size=10`
- 정렬: `?sort=createdAt,desc`

### 응답 형식

- 일관된 DTO 반환
- 에러 응답도 표준화
- JSON 형식 사용

이 가이드를 따라 점진적으로 모든 컨트롤러를 RESTful하게 리팩토링할 예정입니다.

## 4. 뷰(View) 엔드포인트 vs API 엔드포인트

### 뷰 엔드포인트 (HTML 반환)

- 사용자가 직접 접근하는 페이지
- HTML 템플릿을 반환
- 예: `/user/login-view`, `/api/reviews/calendar`

### API 엔드포인트 (JSON 반환)

- AJAX나 API 호출을 위한 엔드포인트
- JSON 데이터를 반환
- `/api/` prefix 사용
- RESTful 원칙 준수

## 5. 전체 적용 결과

### ✅ Before (Non-RESTful)

```
/user/mypage                     → /api/users/{userId}/profile
/ranking/teamranking-view        → /api/teams/rankings
/ranking/playerranking-view      → /api/players/rankings
/game/lineup-view               → /api/user-lineups
/review/calendar-view           → /api/reviews/calendar
/game/result                    → /api/game-results
/game/playercard                → /api/player-cards
/game/simulation                → /api/simulations
/game/event                     → /api/game-events
```

### ✅ After (RESTful)

- 모든 API가 `/api/` prefix 사용
- 리소스 중심의 URL 구조
- HTTP 메서드로 동작 구분
- 일관된 네이밍 컨벤션 (kebab-case)
- 계층적 리소스 구조 반영

## 6. 네이밍 컨벤션

### URL 네이밍

- **kebab-case** 사용: `/api/user-lineups`, `/api/game-events`
- **복수형** 사용: `/api/users`, `/api/teams`, `/api/reviews`
- **계층구조** 반영: `/api/teams/rankings`, `/api/players/stats`

### 리소스 식별

- 컬렉션: `/api/users` (복수형)
- 특정 리소스: `/api/users/{userId}` (단수형 ID)
- 하위 리소스: `/api/users/{userId}/profile`

## 7. HTTP 상태 코드 가이드

| 상태 코드                 | 용도                     | 예시                  |
| ------------------------- | ------------------------ | --------------------- |
| 200 OK                    | 성공적인 GET, PUT, PATCH | 데이터 조회/수정 성공 |
| 201 Created               | 성공적인 POST            | 리소스 생성 성공      |
| 204 No Content            | 성공적인 DELETE          | 리소스 삭제 성공      |
| 400 Bad Request           | 잘못된 요청              | 필수 파라미터 누락    |
| 401 Unauthorized          | 인증 실패                | 로그인 필요           |
| 403 Forbidden             | 권한 없음                | 접근 권한 없음        |
| 404 Not Found             | 리소스 없음              | 존재하지 않는 ID      |
| 409 Conflict              | 충돌                     | 중복된 데이터         |
| 500 Internal Server Error | 서버 오류                | 예상치 못한 오류      |

## 8. 마이그레이션 가이드

### 프론트엔드 업데이트 필요사항

1. AJAX 호출 URL 변경
2. 폼 액션 URL 업데이트
3. 리다이렉션 URL 수정
4. 메뉴/네비게이션 링크 업데이트

### 테스트 케이스 업데이트

1. 컨트롤러 테스트의 URL 변경
2. API 문서 업데이트
3. Postman/API 클라이언트 설정 변경

이제 BaseBall LOCK 프로젝트의 모든 API가 RESTful 원칙을 따르며, 일관된 네이밍 컨벤션을 사용합니다.
