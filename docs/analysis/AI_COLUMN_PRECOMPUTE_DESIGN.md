# 분석 데이터 사전 계산 서비스 + AI 멘트 전용 프롬프트 설계

---

## 1. 배경 및 목적

### 기존 문제

| 문제                    | 설명                                                    |
| ----------------------- | ------------------------------------------------------- |
| **수치 날조**     | Gemini가 DB에 없는 수치를 임의로 생성                   |
| **데이터 불일치** | 프롬프트에 최소한의 데이터만 전달 → AI가 나머지를 추측 |
| **재현 불가**     | 동일 주제라도 매번 다른 수치가 나옴                     |

### 해결 방향

```
[기존] DB 조회 → 간략 데이터 → Gemini(분석+작성 모두 위임)
[변경] DB 조회 → Java에서 완전 계산 → Gemini(멘트만 작성)
```

**핵심 원칙**: Java에서 15개 주제별 분석 데이터를 **모두 사전 계산** → Gemini는 **계산된 수치에 맞춰 멘트만 작성**

---

## 2. 아키텍처

```
┌─────────────────────────────────────────────────────────┐
│                  AiColumnGeneratorService               │
│                                                         │
│  @Scheduled(목요일)                                       │
│  generateRotatingTopicColumn()                          │
│       │                                                 │
│       ├─ topicIndex 결정 (weekOfYear % 15)               │
│       │                                                 │
│       ├─ ColumnDataCalculator.calcXxx(season)           │
│       │       │                                         │
│       │       ├─ BatterStatsRepository                  │
│       │       ├─ PitcherStatsRepository                 │
│       │       ├─ TeamHeadToHeadRepository               │
│       │       ├─ CrowdStatsRepository                   │
│       │       ├─ ScheduleRepository                     │
│       │       ├─ ScoreBoardRepository                   │
│       │       ├─ FuturesBatter/PitcherStatsRepository   │
│       │       └─ PlayerRepository, TeamRepository       │
│       │                                                 │
│       ├─ preComputedData (String 데이터 블록)              │
│       │                                                 │
│       └─ GeminiClient.callGemini(prompt)                │
│               │                                         │
│               └─ "사전 분석 데이터 + 작성 규칙"        	  │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 3. 수정/생성 파일 목록

| 파일                                   | 작업                | 위치                                    |
| -------------------------------------- | ------------------- | --------------------------------------- |
| `ColumnDataCalculator.java`          | **신규 생성** | `modules/analysis/service/`           |
| `AiColumnGeneratorService.java`      | 수정                | `modules/analysis/service/`           |
| `ScoreBoardRepository.java`          | 쿼리 추가           | `modules/game/scoreBoard/repository/` |
| `ScheduleRepository.java`            | 쿼리 추가           | `modules/game/schedule/repository/`   |
| `FuturesBatterStatsRepository.java`  | 쿼리 추가           | `modules/futures/stats/repository/`   |
| `FuturesPitcherStatsRepository.java` | 쿼리 추가           | `modules/futures/stats/repository/`   |

---

## 4. Repository 추가 쿼리

### ScoreBoardRepository

```java
@Query(value = """
    SELECT sb.* FROM scoreBoard sb
    JOIN schedule s ON sb.scheduleId = s.id
    WHERE YEAR(s.matchDate) = :season AND s.status = '종료'
    """, nativeQuery = true)
List<ScoreBoard> findAllBySeasonFinished(@Param("season") int season);
```

- **용도**: 이닝별 득점 패턴 분석 (주제 5)
- **조인**: schedule 테이블과 JOIN하여 시즌+상태 필터링

### ScheduleRepository

```java
@Query(value = """
    SELECT * FROM schedule
    WHERE YEAR(matchDate) = :season AND status = '종료'
    AND homeTeamScore IS NOT NULL AND awayTeamScore IS NOT NULL
    ORDER BY matchDate ASC
    """, nativeQuery = true)
List<Schedule> findAllFinishedBySeason(@Param("season") int season);
```

- **용도**: 홈/원정 분석, 월별 패턴, 이닝 분석 등 다수 주제에서 공통 사용
- **기존 `findFinishedGamesBySeason`과 차이**: kboGameId 조건 없이 전체 종료 경기 반환

### FuturesBatterStatsRepository / FuturesPitcherStatsRepository

```java
@Query(value = "SELECT * FROM futures_batter_stats WHERE season = :season", nativeQuery = true)
List<FuturesBatterStats> findBySeason(@Param("season") int season);
```

- **용도**: 퓨처스 유망주 스카우팅 (주제 8)

---

## 5. ColumnDataCalculator - 15개 계산 메서드

### 의존성

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ColumnDataCalculator {
    private final BatterStatsRepository batterStatsRepository;
    private final PitcherStatsRepository pitcherStatsRepository;
    private final TeamRankingRepository teamRankingRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final TeamHeadToHeadRepository headToHeadRepository;
    private final CrowdStatsRepository crowdStatsRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScoreBoardRepository scoreBoardRepository;
    private final DefenseStatsRepository defenseStatsRepository;
    private final RunnerStatsRepository runnerStatsRepository;
    private final FuturesBatterStatsRepository futuresBatterStatsRepository;
    private final FuturesPitcherStatsRepository futuresPitcherStatsRepository;
}
```

### 메서드 매핑표

| #  | 메서드명                           | 주제                  | 주요 데이터 소스                                  |
| -- | ---------------------------------- | --------------------- | ------------------------------------------------- |
| 0  | `calcSabermetricsTrend(season)`  | 세이버메트릭스 트렌드 | findAllBatters, findAllPitchers                   |
| 1  | `calcWarSpotlight(season)`       | WAR 스포트라이트      | findAllBatters, findAllPitchers + 전년 비교       |
| 2  | `calcBreakoutCandidates(season)` | 브레이크아웃 후보     | findAllBatters(season) + findAllBatters(season-1) |
| 3  | `calcLuckAdjusted(season)`       | 행운 보정 분석        | BABIP 편차, FIP-ERA 갭                            |
| 4  | `calcHeadToHead(season)`         | 상대전적 패턴         | TeamHeadToHeadRepository                          |
| 5  | `calcInningScoring(season)`      | 이닝별 득점 패턴      | ScoreBoardRepository (신규 쿼리)                  |
| 6  | `calcHomeAwayAdvantage(season)`  | 홈/원정 & 파크팩터    | ScheduleRepository (신규 쿼리)                    |
| 7  | `calcCrowdTrend(season)`         | 관중 동원 트렌드      | CrowdStatsRepository                              |
| 8  | `calcFuturesProspects(season)`   | 퓨처스 유망주         | FuturesBatter/PitcherStatsRepository (신규 쿼리)  |
| 9  | `calcAbsImpact(season)`          | ABS 도입 영향         | K%, BB%, K/9, BB/9 전년 비교                      |
| 10 | `calcClutchChoker(season)`       | 클러치/초커 분석      | AVG vs RISP 갭                                    |
| 11 | `calcAgeCurve(season)`           | 나이-성과 커브        | Player.birthDate + WAR                            |
| 12 | `calcLineupEfficiency(season)`   | 라인업 효율성         | 포지션 그룹별 wRC+, OPS                           |
| 13 | `calcSeasonalPatterns(season)`   | 월별/계절별 패턴      | 월별 승률, 상하반기 변화                          |
| 14 | `calcPositionValue(season)`      | 포지션별 가치         | 포지션별 WAR, wRC+, 대체 수준                     |

### 각 메서드 상세

#### 주제 0: `calcSabermetricsTrend`

**계산 항목**:

- 리그 평균 타자 지표: wRC+, BABIP, ISO, K%, BB%, OPS
- 리그 평균 투수 지표: FIP, xFIP, K/9, BB/9, ERA
- WAR 상위 5명 타자/투수

**출력 예시**:

```
[타자 리그 평균]
wRC+: 100.23, BABIP: .298, ISO: .145, K%: 22.10%, BB%: 8.50%, OPS: .720

[투수 리그 평균]
FIP: 4.21, xFIP: 4.35, K/9: 7.80, BB/9: 3.20, ERA: 4.50

[타자 WAR 상위 5명]
- 홍길동 (삼성) WAR: 5.2, wRC+: 145, OPS: .920
```

#### 주제 1: `calcWarSpotlight`

**계산 항목**:

- 타자/투수 WAR 상위 10명 순위 테이블
- 전년도 WAR과 비교하여 변동폭 표시

**출력 형식**: 순위 테이블 (순위, 선수명, 팀, 포지션, WAR, OPS, wRC+, 전년WAR)

#### 주제 2: `calcBreakoutCandidates`

**계산 항목**:

- 전년 대비 WAR 상승폭 상위 5명
- WAR, wRC+, ISO 각각의 전년→올해 변화량

**조건**: 전년도 데이터가 있는 선수만 대상

#### 주제 3: `calcLuckAdjusted`

**계산 항목**:

- 타자: BABIP 리그 평균 대비 편차 상위/하위 5명 (PA 100 이상)
- 투수: FIP-ERA 갭 기준 과대평가/과소평가 상위 5명 (IP 30 이상)

#### 주제 4: `calcHeadToHead`

**계산 항목**:

- 승률 격차가 큰 상대전적 매칭 5개 (최소 5경기 이상)
- 팀명 매핑 포함

#### 주제 5: `calcInningScoring`

**계산 항목**:

- 팀별 이닝별(1~9회) 평균 득점
- 초반(1-3회), 중반(4-6회), 후반(7-9회) 구간 평균
- 팀 유형 분류: 선행형 / 역전형 / 균형형

**데이터 파싱**: `homeInningScores` / `awayInningScores` 문자열 ("1,0,2,...") → int 배열 변환

#### 주제 6: `calcHomeAwayAdvantage`

**계산 항목**:

- 팀별 홈 승률 vs 원정 승률
- 구장별 평균 총 득점
- 파크팩터 근사: `구장 평균 득점 / 리그 평균 득점`
- 분류: 타자 친화(>1.03) / 투수 친화(<0.97) / 중립

#### 주제 7: `calcCrowdTrend`

**계산 항목**:

- 팀별 홈 경기 평균 관중 (경기 수 포함)
- 요일별 평균 관중
- 순위 vs 평균 관중 상관관계

#### 주제 8: `calcFuturesProspects`

**계산 항목**:

- 퓨처스 타자: playerId별 스탯 그룹핑 → AVG 기준 상위 5명 (PA 50 이상)
- 퓨처스 투수: playerId별 스탯 그룹핑 → ERA 기준 상위 5명 (IP 20 이상)
- Player 테이블에서 birthDate 조회 → 나이 계산

#### 주제 9: `calcAbsImpact`

**계산 항목**:

- 리그 평균 K%, BB%, K/9, BB/9의 전년 대비 변화
- K% 변화가 가장 큰 타자 5명
- K/9 변화가 가장 큰 투수 5명

#### 주제 10: `calcClutchChoker`

**계산 항목**:

- 타율(AVG, idx5) vs 득점권 타율(RISP, idx34) 갭
- 클러치: RISP > AVG 갭 큰 순 상위 5명
- 초커: RISP < AVG 갭 큰 순 상위 5명
- 조건: PA 100 이상, RISP 데이터 존재

#### 주제 11: `calcAgeCurve`

**계산 항목**:

- 나이 구간: 20-23세, 24-26세, 27-29세, 30-32세, 33세+
- 타자/투수 각각 구간별 평균 WAR
- 피크 구간 식별

**매핑 방식**: 선수명 + teamId로 Player 엔티티 매칭 → birthDate에서 나이 계산

#### 주제 12: `calcLineupEfficiency`

**계산 항목**:

- 포지션 그룹 분류: 포수(C), 내야(1B/2B/3B/SS), 외야(LF/CF/RF), DH
- 리그 전체 포지션 그룹별 평균 wRC+, OPS, WAR
- 팀별 포지션 그룹 wRC+ 요약 테이블

#### 주제 13: `calcSeasonalPatterns`

**계산 항목**:

- 팀별 월별(3~10월) 승률 매트릭스
- 상반기(4-6월) vs 하반기(7-9월) 승률 변화
- 변화 분류: 급등(+5%p 이상) / 급락(-5%p 이상) / 유지

#### 주제 14: `calcPositionValue`

**계산 항목**:

- 포지션별 평균 WAR, wRC+, OPS
- 포지션별 대체 수준: 하위 20% 평균 WAR
- 포지션별 WAR 상위 3명 리스트

---

## 6. 공통 헬퍼 메서드

| 메서드                                         | 설명                                        |
| ---------------------------------------------- | ------------------------------------------- |
| `dbl(Object)`                                | Object → double 안전 변환                  |
| `intVal(Object)`                             | Object → int 안전 변환                     |
| `str(Object)`                                | Object → String 변환                       |
| `avg(List<Object[]>, idx)`                   | 특정 인덱스의 평균 계산                     |
| `fmt(double)`                                | 소수점 2자리 포맷                           |
| `fmt3(double)`                               | 소수점 3자리 포맷                           |
| `signedFmt(double)`                          | 부호 포함 2자리 포맷 (+/-)                  |
| `signedFmt3(double)`                         | 부호 포함 3자리 포맷                        |
| `buildNameWarMap(rows, nameIdx, warIdx)`     | 선수명→WAR 맵 생성                         |
| `parseAndAccumInnings(...)`                  | 이닝 스코어 문자열 파싱 및 누적             |
| `avgRange(totals, counts, from, to)`         | 이닝 구간 평균 계산                         |
| `classifyTeamType(early, mid, late)`         | 팀 유형 분류                                |
| `ageBucket(age, labels)`                     | 나이 → 구간 매핑                           |
| `findPlayerByNameAndTeam(map, name, teamId)` | 선수명+팀ID로 Player 검색                   |
| `safeLoadBatters(season)`                    | 안전한 타자 데이터 로딩 (예외 시 빈 리스트) |
| `safeLoadPitchers(season)`                   | 안전한 투수 데이터 로딩                     |

---

## 7. AiColumnGeneratorService 변경 사항

### 의존성 추가

```java
private final ColumnDataCalculator calculator;
```

### `generateRotatingTopicColumn()` 변경

**기존**: `buildTopBattersData` + `buildTopPitchersData` + `getTopicEmphasis` → 모든 주제에 동일한 데이터 전달

**변경**: topicIndex별 switch 표현식으로 전용 calculator 메서드 호출

```java
String preComputedData = switch (topicIndex) {
    case 0  -> calculator.calcSabermetricsTrend(CURRENT_SEASON);
    case 1  -> calculator.calcWarSpotlight(CURRENT_SEASON);
    // ... (15개 매핑)
    default -> "";
};
```

### 프롬프트 구조 변경

**기존**:

```
당신은 KBO 야구 매거진 기사 작성 전문가입니다.
=== 실제 데이터 ===
[타자 WAR 상위 5명] ...
[투수 WAR 상위 5명] ...
[주제별 강조 지표] 핵심 지표: ...
=== 작성 규칙 ===
```

**변경**:

```
당신은 KBO 야구 매거진 기사 작성 전문가입니다.
주제: {topic}
시즌: {season}

=== 사전 분석 데이터 (아래 수치를 그대로 사용하세요) ===
{calculator가 생성한 상세 데이터 블록}

=== 작성 규칙 ===
- 위 데이터의 수치를 정확히 인용하세요. 존재하지 않는 수치를 만들지 마세요.
- ...
```

### 삭제된 메서드

| 메서드                     | 사유                                 |
| -------------------------- | ------------------------------------ |
| `buildTopBattersData()`  | calculator로 대체                    |
| `buildTopPitchersData()` | calculator로 대체                    |
| `getTopicEmphasis()`     | calculator의 상세 데이터가 역할 대체 |

### 유지된 메서드

| 메서드                             | 사유                        |
| ---------------------------------- | --------------------------- |
| `generateColumnByTopic()`        | 수동 트리거 하위호환        |
| `generateSeasonOverviewColumn()` | 수동 호출용                 |
| `buildTeamDetailData()`          | 팀 주간 분석에서 사용       |
| `buildTeamRankingData()`         | 수동 트리거 메서드에서 사용 |

---

## 8. findAllBatters / findAllPitchers 인덱스 참조

### 타자 (`BatterStatsRepository.findAllBatters`)

| 인덱스 | 필드       | 인덱스 | 필드         |
| ------ | ---------- | ------ | ------------ |
| 0      | position   | 18     | obp          |
| 1      | playerName | 19     | slg          |
| 2      | teamName   | 20     | teamId       |
| 3      | logoName   | 21     | babip        |
| 4      | war        | 22     | iso          |
| 5      | avg        | 23     | kRate (K%)   |
| 6      | ops        | 24     | bbRate (BB%) |
| 7      | hr         | 25     | ab           |
| 8      | sb         | 26     | r            |
| 9      | wrcPlus    | 27     | tb           |
| 10     | g          | 28     | sac          |
| 11     | pa         | 29     | sf           |
| 12     | h          | 30     | ibb          |
| 13     | rbi        | 31     | hbp          |
| 14     | bb         | 32     | gdp          |
| 15     | so         | 33     | mh           |
| 16     | 2B         | 34     | risp         |
| 17     | 3B         | 35     | phBa         |

### 투수 (`PitcherStatsRepository.findAllPitchers`)

| 인덱스 | 필드            | 인덱스 | 필드 |
| ------ | --------------- | ------ | ---- |
| 0      | playerName      | 20     | g    |
| 1      | teamName        | 21     | wpct |
| 2      | logoName        | 22     | hbp  |
| 3      | era             | 23     | r    |
| 4      | whip            | 24     | er   |
| 5      | wins            | 25     | cg   |
| 6      | losses          | 26     | sho  |
| 7      | saves           | 27     | qs   |
| 8      | holds           | 28     | bsv  |
| 9      | strikeouts      | 29     | tbf  |
| 10     | walks           | 30     | np   |
| 11     | hitsAllowed     | 31     | avg  |
| 12     | homeRunsAllowed | 32     | 2B   |
| 13     | inningsPitched  | 33     | 3B   |
| 14     | war             | 34     | sac  |
| 15     | teamId          | 35     | sf   |
| 16     | fip             | 36     | ibb  |
| 17     | xfip            | 37     | wp   |
| 18     | k9              | 38     | bk   |
| 19     | bb9             |        |      |

---

## 9. 데이터 흐름 다이어그램

```
[매주 목요일 06:00]
        │
        ▼
weekOfYear % 15 → topicIndex 결정
        │
        ▼
ColumnDataCalculator.calcXxx(season)
        │
        ├── DB 조회 (Repository 호출)
        ├── Java에서 계산 (평균, 정렬, 비교, 분류)
        └── String 데이터 블록 생성
                │
                ▼
    프롬프트 = 주제 + 데이터 블록 + 작성 규칙
                │
                ▼
    GeminiClient.callGemini(prompt)
                │
                ▼
    AI 응답 파싱 → AnalysisColumn 저장
```

---

## 10. 확장 가이드

### 새 주제 추가 방법

1. `ColumnDataCalculator`에 `calcNewTopic(int season)` 메서드 추가
2. `ROTATING_TOPICS` 배열에 주제 추가 (index 15)
3. `generateRotatingTopicColumn()`의 switch에 `case 15 ->` 추가
4. 필요 시 Repository에 쿼리 추가

### 팀 주간 분석 확장

`generateTeamWeeklyColumn()`에서도 calculator 활용 가능:

```java
// 예: 팀별 상대전적 + 이닝 패턴 데이터 추가
String h2hData = calculator.calcHeadToHead(CURRENT_SEASON);
String inningData = calculator.calcInningScoring(CURRENT_SEASON);
```
