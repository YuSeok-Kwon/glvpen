# glvpen SQL 패턴 & 데이터 접근 전략

## 목차

1. [개요](#1-개요)
2. [데이터 접근 방식 비교](#2-데이터-접근-방식-비교)
3. [Spring Data JPA Method Query](#3-spring-data-jpa-method-query)
4. [Native SQL (@Query)](#4-native-sql-query)
5. [JPQL (@Query)](#5-jpql-query)
6. [JdbcTemplate 배치 Upsert](#6-jdbctemplate-배치-upsert)
7. [Interface Projection](#7-interface-projection)
8. [Entity 매핑 설계](#8-entity-매핑-설계)
9. [Flyway 마이그레이션](#9-flyway-마이그레이션)
10. [인덱스 & 제약 조건 전략](#10-인덱스--제약-조건-전략)
11. [JPA 설정 (application.yml)](#11-jpa-설정-applicationyml)

---

## 1. 개요

### 사용 기술 현황

| 방식 | 사용 | 용도 | 파일 수 |
|------|:----:|------|:-------:|
| Spring Data JPA Method Query | O | 단순 CRUD, 조건 조회 | 전체 Repository |
| @Query (Native SQL) | O | 복잡한 집계/피봇/윈도우 함수 | 12+ Repository |
| @Query (JPQL) | O | 엔티티 기반 간단 조회 | 5 Repository |
| JdbcTemplate BatchUpdate | O | 크롤링 데이터 대량 Upsert | 5 Service |
| Flyway Migration | O | 스키마 버전 관리 | 12 파일 (V1~V12) |
| MyBatis | X | 미사용 | - |
| EntityManager 직접 | X | 미사용 | - |

### 방식 선택 기준

```
단순 CRUD/조건 조회  →  Method Query (자동 생성)
엔티티 기반 간단 조회  →  JPQL
복잡한 집계/피봇/윈도우  →  Native SQL
대량 데이터 배치 저장  →  JdbcTemplate BatchUpdate
스키마 변경 이력 관리  →  Flyway Migration
```

---

## 2. 데이터 접근 방식 비교

### 성능 & 유지보수 트레이드오프

| 기준 | Method Query | JPQL | Native SQL | JdbcTemplate |
|------|:---:|:---:|:---:|:---:|
| 코드 간결성 | 최고 | 좋음 | 보통 | 낮음 |
| SQL 제어력 | 없음 | 보통 | 최고 | 최고 |
| DB 독립성 | 최고 | 좋음 | 없음 | 없음 |
| 배치 성능 | 낮음 | 낮음 | 보통 | 최고 |
| 타입 안전성 | 최고 | 좋음 | 낮음 (Object[]) | 없음 |

### 프로젝트에서의 실제 사용 분포

- **Native SQL**: 70% — 통계 테이블의 EAV→피봇 변환이 핵심이므로
- **Method Query**: 20% — 단순 엔티티 조회
- **JdbcTemplate**: 8% — 크롤링 배치 저장 전용
- **JPQL**: 2% — 분석 컬럼, 퓨처스 등 소수

---

## 3. Spring Data JPA Method Query

메서드 이름을 파싱하여 SQL을 자동 생성하는 방식.

### 3.1 사용처

**UserRepository.java**
```java
Optional<User> findByLoginId(String loginId);
int countByLoginId(String loginId);
int countByNickname(String nickname);
int countByNicknameAndIdNot(String nickname, Integer id);  // 본인 제외 중복 체크
Optional<User> findByNameAndEmail(String name, String email);
User findByLoginIdAndNameAndEmail(String loginId, String name, String email);
```

**ScheduleRepository.java**
```java
Schedule findFirstByMatchDateBetween(Timestamp start, Timestamp end);
List<Schedule> findByMatchDateBetweenOrderByMatchDate(Timestamp start, Timestamp end);
Optional<Schedule> findById(int id);
```

**PlayerRepository.java**
```java
Optional<Player> findByNameAndTeamId(String name, int teamId);
Optional<Player> findByKboPlayerId(String kboPlayerId);
List<Player> findByKboPlayerIdIsNotNullAndProfileUpdatedAtIsNull();
```

**AnalysisResultRepository.java** — Method Query만 사용
```java
Optional<AnalysisResult> findByTopicAndSeason(Integer topic, Integer season);
Optional<AnalysisResult> findByTopicAndSubTopicAndSeasonAndComparisonType(
    Integer topic, String subTopic, Integer season, String comparisonType);
Optional<AnalysisResult> findByTopicAndSubTopicAndSeasonAndComparisonTypeAndAnchorValue(
    Integer topic, String subTopic, Integer season, String comparisonType, String anchorValue);
List<AnalysisResult> findBySeasonOrderByTopicAsc(Integer season);
List<AnalysisResult> findByTopicAndSeasonOrderBySubTopicAsc(Integer topic, Integer season);
```

**AnalysisColumnRepository.java**
```java
Page<AnalysisColumn> findByCategoryOrderByPublishDateDesc(String category, Pageable pageable);
Page<AnalysisColumn> findAllByOrderByPublishDateDesc(Pageable pageable);
List<AnalysisColumn> findTop5ByOrderByPublishDateDesc();
Optional<AnalysisColumn> findFirstByFeaturedTrueOrderByPublishDateDesc();
Optional<AnalysisColumn> findFirstByIdLessThanOrderByIdDesc(Long id);    // 이전 글
Optional<AnalysisColumn> findFirstByIdGreaterThanOrderByIdAsc(Long id);  // 다음 글
```

**기타 Method Query 전용 Repository**
- `BatterRecordRepository` — `findByScheduleId()`
- `ScoreBoardRepository` — `findByScheduleId()`
- `GameHighlightRepository` — `findByScheduleId()`
- `BatterLineupRepository` — `findByScheduleIdAndTeamId()`
- `GameSummaryRecordRepository` — `findByScheduleId()`
- `TeamHeadToHeadRepository` — `findBySeasonAndTeamIdAndOpponentTeamId()`

### 3.2 네이밍 규칙

| 키워드 | 생성 SQL | 예시 |
|--------|----------|------|
| `findBy` | `SELECT ... WHERE` | `findByLoginId` |
| `countBy` | `SELECT COUNT(*) WHERE` | `countByNickname` |
| `And` | `AND` | `findByNameAndEmail` |
| `Not` | `!=` | `countByNicknameAndIdNot` |
| `Between` | `BETWEEN` | `findByMatchDateBetween` |
| `OrderBy...Desc` | `ORDER BY ... DESC` | `findAllByOrderByPublishDateDesc` |
| `Top5` | `LIMIT 5` | `findTop5ByOrderByPublishDateDesc` |
| `IsNotNull` | `IS NOT NULL` | `findByKboPlayerIdIsNotNull` |

---

## 4. Native SQL (@Query)

MySQL 전용 문법(윈도우 함수, RAND(), FIELD() 등)이 필요한 복잡한 쿼리에 사용.

### 4.1 MAX CASE 피봇 패턴

EAV(Entity-Attribute-Value) 구조의 통계 테이블을 일반 컬럼처럼 조회하는 **프로젝트 핵심 패턴**.

**개념**: `player_batter_stats` 테이블은 (playerId, category, value) 형태로 저장되어 있어, 한 선수의 모든 지표를 한 행으로 보려면 PIVOT 필요.

```
-- DB 저장 형태 (EAV)
playerId | category | value
1        | AVG      | 0.301
1        | HR       | 25
1        | OPS      | 0.856

-- PIVOT 후 결과
playerId | avg   | hr | ops
1        | 0.301 | 25 | 0.856
```

**BatterStatsRepository — 시즌 전체 타자 (36개 컬럼)**
```java
@Query(value = """
    SELECT
        b.position, p.name AS playerName, t.name AS teamName, t.logoName,
        MAX(CASE WHEN b.category = 'WAR' THEN b.value END) AS war,
        MAX(CASE WHEN b.category = 'AVG' THEN b.value END) AS avg,
        MAX(CASE WHEN b.category = 'OPS' THEN b.value END) AS ops,
        MAX(CASE WHEN b.category = 'HR' THEN b.value END) AS hr,
        MAX(CASE WHEN b.category = 'SB' THEN b.value END) AS sb,
        MAX(CASE WHEN b.category = 'wRC+' THEN b.value END) AS wrcPlus,
        MAX(CASE WHEN b.category = 'G' THEN b.value END) AS g,
        MAX(CASE WHEN b.category = 'PA' THEN b.value END) AS pa,
        MAX(CASE WHEN b.category = 'H' THEN b.value END) AS h,
        MAX(CASE WHEN b.category = 'RBI' THEN b.value END) AS rbi,
        MAX(CASE WHEN b.category = 'BB' THEN b.value END) AS bb,
        MAX(CASE WHEN b.category = 'SO' THEN b.value END) AS so,
        -- ... 총 36개 컬럼
    FROM player_batter_stats b
    JOIN player p ON b.playerId = p.id
    JOIN team t ON p.teamId = t.id
    WHERE b.season = :season
      AND b.series = '0' AND b.situationType = '' AND b.situationValue = ''
    GROUP BY p.id, b.position, t.id
    """, nativeQuery = true)
List<Object[]> findAllBatters(@Param("season") int season);
```

**PitcherStatsRepository — 시즌 전체 투수 (39개 컬럼)**
```java
@Query(value = """
    SELECT
        p.name AS playerName, t.name AS teamName, t.logoName,
        MAX(CASE WHEN b.category = 'ERA' THEN b.value END) AS era,
        MAX(CASE WHEN b.category = 'WHIP' THEN b.value END) AS whip,
        MAX(CASE WHEN b.category = 'WAR' THEN b.value END) AS war,
        MAX(CASE WHEN b.category = 'FIP' THEN b.value END) AS fip,
        -- ... 총 39개 컬럼
    FROM player p
    JOIN team t ON p.teamId = t.id
    JOIN player_pitcher_stats b ON p.id = b.playerId
    WHERE b.season = :season
      AND b.series = '0' AND b.situationType = '' AND b.situationValue = ''
    GROUP BY p.name, t.name, t.logoName, t.id
    """, nativeQuery = true)
List<Object[]> findAllPitchers(@Param("season") int season);
```

**TeamStatsRepository — 팀 스탯 (18개 컬럼)**
```java
@Query(value = """
    SELECT
        t.id AS teamId, t.name AS teamName, t.logoName,
        MAX(CASE WHEN ts.category = 'OPS' THEN ts.value END) AS ops,
        MAX(CASE WHEN ts.category = 'AVG' THEN ts.value END) AS avg,
        MAX(CASE WHEN ts.category = 'HR' THEN ts.value END) AS hr,
        MAX(CASE WHEN ts.category = 'ERA' THEN ts.value END) AS era,
        MAX(CASE WHEN ts.category = 'WHIP' THEN ts.value END) AS whip,
        -- ... 총 18개 컬럼
    FROM team_stats ts
    JOIN team t ON ts.teamId = t.id
    WHERE ts.season = :season
    GROUP BY t.id, t.name, t.logoName
    """, nativeQuery = true)
List<TeamStatRankingInterface> findAllTeamStats(@Param("season") int season);
```

### 4.2 ROW_NUMBER 윈도우 함수 (순위 매기기)

**포지션별 WAR 1위 타자**
```java
@Query(value = """
    SELECT position, playerName, teamName, logoName, war
    FROM (
        SELECT
            bs.position, p.name AS playerName, t.name AS teamName, t.logoName,
            COALESCE(bs.value, 0) AS war,
            ROW_NUMBER() OVER (
                PARTITION BY bs.position ORDER BY COALESCE(bs.value, 0) DESC
            ) AS row_num
        FROM player_batter_stats bs
        JOIN player p ON bs.playerId = p.id
        JOIN team t ON p.teamId = t.id
        WHERE bs.season = :season AND bs.category = 'WAR'
          AND bs.series = '0' AND bs.situationType = '' AND bs.situationValue = ''
    ) AS ranked
    WHERE ranked.row_num = 1
    ORDER BY ranked.position
    """, nativeQuery = true)
List<Object[]> findTopBattersByPosition(@Param("season") int season);
```

**카테고리별 1위 투수 (ERA/WHIP는 낮을수록 좋으므로 ORDER BY 분기)**
```java
@Query(value = """
    SELECT ranked.position, ranked.playerName, ranked.teamName,
           ranked.logoName, ranked.record_value, ranked.category
    FROM (
        SELECT
            bs.position, p.name AS playerName, t.name AS teamName, t.logoName,
            COALESCE(bs.value, 0) AS record_value, bs.category,
            ROW_NUMBER() OVER (
                PARTITION BY bs.position, bs.category
                ORDER BY CASE
                    WHEN bs.category IN ('ERA', 'WHIP') THEN bs.value
                    ELSE -bs.value
                END
            ) AS row_num
        FROM player_pitcher_stats bs
        JOIN player p ON bs.playerId = p.id
        JOIN team t ON p.teamId = t.id
        WHERE bs.season = :season
          AND bs.category IN ('ERA','WHIP','G','IP','W','HLD','SV','SO','WAR')
          AND (bs.category != 'ERA' OR bs.value > 0)
          AND bs.series = '0' AND bs.situationType = '' AND bs.situationValue = ''
    ) AS ranked
    WHERE ranked.row_num = 1
    ORDER BY ranked.position, ranked.category
    """, nativeQuery = true)
List<Object[]> findTopPitchersAsTuple(@Param("season") int season);
```

### 4.3 LEFT JOIN + 서브쿼리 (크로스 테이블 조인)

SB(도루)는 `player_runner_stats` 테이블에만 존재하므로, 타자 분석 시 LEFT JOIN 필요.

```java
@Query(value = """
    SELECT
        p.id AS playerId, p.name AS playerName, t.name AS teamName,
        MAX(CASE WHEN b.category = 'AVG' THEN b.value END) AS avg,
        MAX(CASE WHEN b.category = 'OBP' THEN b.value END) AS obp,
        MAX(CASE WHEN b.category = 'OPS' THEN b.value END) AS ops,
        -- ... 기타 타자 지표
        r_sub.sb AS sb
    FROM player_batter_stats b
    JOIN player p ON b.playerId = p.id
    JOIN team t ON p.teamId = t.id
    LEFT JOIN (
        SELECT playerId,
            MAX(CASE WHEN category = 'SB' THEN value END) AS sb
        FROM player_runner_stats
        WHERE season = :season AND series = '0'
        GROUP BY playerId
    ) r_sub ON r_sub.playerId = p.id
    WHERE b.season = :season
      AND b.series = '0' AND b.situationType = '' AND b.situationValue = ''
    GROUP BY p.id, p.name, t.name, t.logoName, t.id, r_sub.sb
    HAVING MAX(CASE WHEN b.category = 'G' THEN b.value END) >= 30
    """, nativeQuery = true)
List<Object[]> findAllBattersForAnalysis(@Param("season") int season);
```

### 4.4 UNION ALL (복수 테이블 합산)

**팀별 경기 수 (홈+원정 합산)**
```java
@Query(value = """
    SELECT teamId, COUNT(*) AS gamesPlayed
    FROM (
        SELECT s.homeTeamId AS teamId
        FROM kbo_schedule s
        WHERE YEAR(s.matchDate) = :season
          AND s.matchDate < CURRENT_DATE()
          AND s.homeTeamScore IS NOT NULL AND s.awayTeamScore IS NOT NULL
        UNION ALL
        SELECT s.awayTeamId AS teamId
        FROM kbo_schedule s
        WHERE YEAR(s.matchDate) = :season
          AND s.matchDate < CURRENT_DATE()
          AND s.homeTeamScore IS NOT NULL AND s.awayTeamScore IS NOT NULL
    ) AS all_teams
    GROUP BY teamId
    """, nativeQuery = true)
List<Object[]> countGamesByTeam(@Param("season") int season);
```

**출신교 분류별 선수 수 (타자+투수 UNION ALL)**
```java
@Query(value = """
    SELECT sub.season, sub.schoolType, SUM(sub.cnt) AS playerCount
    FROM (
        SELECT bs.season,
            CASE
                WHEN p.school LIKE '%고등학교%' OR p.school LIKE '%고%' THEN '고졸'
                WHEN p.school LIKE '%대학교%' OR p.school LIKE '%대%' THEN '대졸'
                ELSE '기타'
            END AS schoolType,
            COUNT(DISTINCT p.id) AS cnt
        FROM player p
        JOIN player_batter_stats bs ON bs.playerId = p.id
            AND bs.category = 'WAR' AND bs.series = '0'
        WHERE bs.season BETWEEN :startYear AND :endYear
        GROUP BY bs.season, schoolType
        UNION ALL
        SELECT ps.season,
            CASE ... END AS schoolType,
            COUNT(DISTINCT p.id) AS cnt
        FROM player p
        JOIN player_pitcher_stats ps ON ps.playerId = p.id ...
        GROUP BY ps.season, schoolType
    ) sub
    GROUP BY sub.season, sub.schoolType
    ORDER BY sub.season, sub.schoolType
    """, nativeQuery = true)
List<Object[]> findSchoolTypeDistributionAll(...);
```

### 4.5 시리즈별 비교 (FIELD 정렬)

```java
@Query(value = """
    SELECT
        bs.series AS series,
        AVG(CASE WHEN bs.category = 'AVG' THEN bs.value END) AS avgVal,
        AVG(CASE WHEN bs.category = 'OPS' THEN bs.value END) AS opsVal,
        AVG(CASE WHEN bs.category = 'HR' THEN bs.value END) AS hrVal
    FROM player_batter_stats bs
    WHERE bs.season = :season
      AND bs.series IN ('0', 'KS', 'PS', 'WC')
      AND bs.situationType = '' AND bs.situationValue = ''
    GROUP BY bs.series
    ORDER BY FIELD(bs.series, '0', 'WC', 'PS', 'KS')
    """, nativeQuery = true)
List<Object[]> findSeriesComparison(@Param("season") int season);
```

### 4.6 RAND() 랜덤 추출 (시뮬레이션 카드)

```java
@Query(value = """
    SELECT pco.*
    FROM sim_player_card_overall pco
    JOIN player p ON pco.playerId = p.id
    JOIN player_batter_stats bs ON pco.playerId = bs.playerId
    WHERE bs.position = :position
      AND pco.overall BETWEEN :minOverall AND :maxOverall
    ORDER BY RAND()
    LIMIT 1
    """, nativeQuery = true)
PlayerCardOverall findRandomByPositionAndOverallRange(
    @Param("position") String position,
    @Param("minOverall") double minOverall,
    @Param("maxOverall") double maxOverall);
```

### 4.7 리그 평균 계산 (Marcel 예측용)

```java
// 타자 리그 평균
@Query(value = """
    SELECT b.category, AVG(b.value) AS avgValue
    FROM player_batter_stats b
    WHERE b.season = :season
      AND b.series = '0' AND b.situationType = '' AND b.situationValue = ''
      AND b.category IN ('AVG','OPS','HR','WAR','SB','RBI','OBP','SLG',
                         'BB%','K%','ISO','wRC+','BABIP','PA')
    GROUP BY b.category
    """, nativeQuery = true)
List<Object[]> findLeagueAvgBySeason(@Param("season") int season);

// 투수 리그 평균
@Query(value = """
    SELECT p.category, AVG(p.value) AS avgValue
    FROM player_pitcher_stats p
    WHERE p.season = :season
      AND p.series = '0' AND p.situationType = '' AND p.situationValue = ''
      AND p.category IN ('ERA','WHIP','WAR','FIP','xFIP','K/9','BB/9',
                         'W','SV','HLD','IP','SO')
    GROUP BY p.category
    """, nativeQuery = true)
List<Object[]> findLeagueAvgBySeason(@Param("season") int season);
```

### 4.8 피로도 분석용 등판 기록

```java
@Query(value = """
    SELECT pr.playerId, p.name AS playerName, t.name AS teamName, t.logoName,
           pr.innings, pr.earnedRuns, pr.pitchCount, pr.entryType,
           s.matchDate, pr.strikeouts, pr.hits, pr.bb, pr.hr, t.id AS teamId
    FROM kbo_pitcher_record pr
    JOIN kbo_schedule s ON pr.scheduleId = s.id
    JOIN player p ON pr.playerId = p.id
    JOIN team t ON pr.teamId = t.id
    WHERE YEAR(s.matchDate) = :season AND s.status = '종료'
    ORDER BY pr.playerId, s.matchDate
    """, nativeQuery = true)
List<Object[]> findAllPitcherAppearancesBySeason(@Param("season") int season);
```

### 4.9 다년도 추이 (리그 평균 ERA/OPS)

```java
@Query(value = """
    SELECT
        ts_era.season AS season,
        AVG(ts_era.value) AS avgEra,
        AVG(ts_ops.value) AS avgOps
    FROM team_stats ts_era
    JOIN team_stats ts_ops
        ON ts_era.teamId = ts_ops.teamId AND ts_era.season = ts_ops.season
    WHERE ts_era.category = 'ERA' AND ts_ops.category = 'OPS'
      AND ts_era.season BETWEEN :startYear AND :endYear
    GROUP BY ts_era.season
    ORDER BY ts_era.season
    """, nativeQuery = true)
List<Object[]> findLeagueAvgEraOps(
    @Param("startYear") int startYear, @Param("endYear") int endYear);
```

---

## 5. JPQL (@Query)

엔티티 객체를 직접 다루는 간단한 쿼리에 사용. DB 독립적.

```java
// UserRepository — 모든 유저 ID 조회
@Query("SELECT u.id FROM User u")
List<Integer> findAllUserIds();

// AnalysisColumnRepository — 관련 컬럼 조회
@Query("SELECT c FROM AnalysisColumn c " +
       "WHERE c.category = :category AND c.id <> :excludeId " +
       "ORDER BY c.publishDate DESC")
List<AnalysisColumn> findRelatedColumns(
    @Param("category") String category,
    @Param("excludeId") Long excludeId,
    Pageable pageable);

// PlayerCardOverallRepository — JPQL DELETE
@Modifying
@Query("DELETE FROM PlayerCardOverall p WHERE p.playerId = :playerId AND p.season = :season")
void deleteByPlayerIdAndSeason(
    @Param("playerId") Integer playerId, @Param("season") Integer season);

// FuturesBatterStatsRepository — 복합키 조회
@Query("SELECT f FROM FuturesBatterStats f " +
       "WHERE f.playerId = :playerId AND f.season = :season " +
       "AND f.category = :category AND f.league = :league")
Optional<FuturesBatterStats> findByFullKey(
    @Param("playerId") int playerId, @Param("season") int season,
    @Param("category") String category, @Param("league") String league);
```

---

## 6. JdbcTemplate 배치 Upsert

크롤링 데이터 수천 건을 한 번에 저장할 때 사용. JPA `save()` 대비 10배 이상 빠름.

### 핵심 패턴: INSERT ... ON DUPLICATE KEY UPDATE

```java
// BatterStatsService.java
@Transactional
public void saveBatch(List<BatterStatsDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) return;

    String sql = """
        INSERT INTO player_batter_stats
            (playerId, season, category, value, ranking, series, situationType, situationValue)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE value = VALUES(value), ranking = VALUES(ranking)
        """;

    jdbcTemplate.batchUpdate(sql, dtos, 500, (ps, dto) -> {
        ps.setInt(1, dto.getPlayerId());
        ps.setInt(2, dto.getSeason());
        ps.setString(3, dto.getCategory());
        ps.setDouble(4, dto.getValue());
        if (dto.getRanking() != null) ps.setInt(5, dto.getRanking());
        else ps.setNull(5, java.sql.Types.INTEGER);
        ps.setString(6, dto.getSeries() != null ? dto.getSeries() : "0");
        ps.setString(7, dto.getSituationType() != null ? dto.getSituationType() : "");
        ps.setString(8, dto.getSituationValue() != null ? dto.getSituationValue() : "");
    });
    log.info("[배치저장] 타자 스탯 {}건 저장 완료", dtos.size());
}
```

### 사용처 일람

| Service | 테이블 | Unique Key | 배치 크기 |
|---------|--------|------------|:---------:|
| BatterStatsService | player_batter_stats | (playerId, season, category, series, situationType, situationValue) | 500 |
| PitcherStatsService | player_pitcher_stats | (playerId, season, category, series, situationType, situationValue) | 500 |
| DefenseStatsService | player_defense_stats | (playerId, season, series, position, category) | 500 |
| RunnerStatsService | player_runner_stats | (playerId, season, series, category) | 500 |
| TeamStatsService | team_stats | (teamId, season, category) | 500 |

### TeamStatsService (5개 컬럼 Upsert)

```java
@Transactional
public void saveBatch(List<TeamStatsDTO> dtos) {
    String sql = """
        INSERT INTO team_stats (teamId, season, category, value, ranking)
        VALUES (?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE value = VALUES(value), ranking = VALUES(ranking)
        """;
    jdbcTemplate.batchUpdate(sql, dtos, 500, (ps, dto) -> {
        ps.setInt(1, dto.getTeamId());
        ps.setInt(2, dto.getSeason());
        ps.setString(3, dto.getCategory());
        ps.setDouble(4, dto.getValue());
        String rankStr = dto.getRank();
        if (rankStr != null && !rankStr.isEmpty()) {
            ps.setInt(5, Integer.parseInt(rankStr));
        } else {
            ps.setNull(5, java.sql.Types.INTEGER);
        }
    });
}
```

### Upsert가 동작하는 원리

1. `INSERT` 시도
2. Unique Key 충돌 발생하면 `UPDATE` 실행
3. 새 데이터면 INSERT, 기존 데이터면 UPDATE → **Upsert**

```
전제조건: Unique Key가 반드시 존재해야 함
→ Flyway V9/V12에서 모든 통계 테이블에 Unique Key 추가됨
```

---

## 7. Interface Projection

Native SQL 결과를 `Object[]` 대신 타입 안전한 인터페이스로 매핑.

### TeamStatsRepository 예시

```java
// 인터페이스 정의
public interface TopStatTeamInterface {
    Integer getTeamId();
    String getTeamName();
    String getTeamLogo();
    String getCategory();
    String getValue();
}

public interface TeamStatRankingInterface {
    Integer getTeamId();
    String getTeamName();
    String getLogoName();
    String getOps();
    String getAvg();
    String getHr();
    String getEra();
    String getWhip();
    // ... 총 18개 getter
}

// Repository에서 사용
@Query(value = """
    SELECT t.id AS teamId, t.name AS teamName, t.logoName AS teamLogo,
           ts.category AS category, ts.value AS value
    FROM team_stats ts
    JOIN team t ON ts.teamId = t.id
    WHERE ts.season = :season AND ts.category = :category
    ORDER BY ts.value DESC LIMIT 1
    """, nativeQuery = true)
TopStatTeamInterface findTopByCategoryAndSeasonMax(
    @Param("season") int season, @Param("category") String category);
```

### Object[] vs Interface Projection 비교

```java
// Object[] 방식 — 인덱스 기반, 타입 캐스팅 필요
Object[] row = result.get(0);
String teamName = (String) row[1];  // 인덱스 실수 위험

// Interface Projection — getter 기반, 타입 안전
TopStatTeamInterface top = repository.findTopByCategoryAndSeasonMax(2025, "ERA");
String teamName = top.getTeamName();  // 컴파일 타임 검증
```

---

## 8. Entity 매핑 설계

### 8.1 전체 Entity 목록 (34개)

| 모듈 | Entity | 테이블명 | Unique Key |
|------|--------|----------|------------|
| **선수 통계** | BatterStats | player_batter_stats | (playerId, season, category, series, situationType, situationValue) |
| | PitcherStats | player_pitcher_stats | (playerId, season, category, series, situationType, situationValue) |
| | DefenseStats | player_defense_stats | (playerId, season, series, position, category) |
| | RunnerStats | player_runner_stats | (playerId, season, series, category) |
| **선수** | Player | player | (kboPlayerId) |
| **팀** | Team | team | - |
| | TeamRanking | team_ranking | (season, teamId) |
| | TeamStats | team_stats | (teamId, season, category) |
| | TeamHeadToHead | team_head_to_head | (season, teamId, opponentTeamId) |
| **경기** | Schedule | kbo_schedule | - |
| | ScoreBoard | kbo_score_board | (scheduleId) |
| | BatterRecord | kbo_batter_record | (scheduleId, playerId) |
| | PitcherRecord | kbo_pitcher_record | (scheduleId, playerId) |
| | BatterLineup | kbo_batter_lineup | - |
| | GameHighlight | kbo_game_highlight | - |
| | GameKeyPlayer | kbo_game_key_player | (scheduleId, playerType, metric, ranking, playerName) |
| | GameSummaryRecord | kbo_game_summary_record | (scheduleId, category) |
| | CrowdStats | kbo_crowd_stats | (gameDate, homeTeamId, awayTeamId) |
| **시뮬레이션** | PlayerCardOverall | sim_player_card_overall | - |
| | UserCard | sim_user_card | - |
| | UserLineup | sim_user_lineup | - |
| | GameResult | sim_game_result | - |
| | SimulationGameSchedule | sim_game_schedule | - |
| | GameEventQuestion | sim_event_question | - |
| | GameEventAnswer | sim_event_answer | - |
| **분석** | AnalysisColumn | analysis_column | - |
| | AnalysisResult | analysis_result | (topic, subTopic, season, comparisonType, anchorValue) |
| **사용자** | User | user | - |
| **퓨처스** | FuturesSchedule | futures_schedule | (matchDate, homeTeamId, awayTeamId) |
| | FuturesBatterStats | futures_batter_stats | (playerId, season, category, league) |
| | FuturesPitcherStats | futures_pitcher_stats | (playerId, season, category, league) |
| | FuturesTeamStats | futures_team_stats | (teamId, season, category, league, statType) |

### 8.2 Entity 선언 예시 (Unique + Index)

```java
@Entity
@Table(name = "player_batter_stats",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_batter_stats_full",
        columnNames = {"playerId","season","category","series","situationType","situationValue"}
    ),
    indexes = {
        @Index(name = "idx_batter_player_season", columnList = "playerId, season"),
        @Index(name = "idx_batter_season_category", columnList = "season, category"),
        @Index(name = "idx_batter_season_series_sit",
               columnList = "season, series, situationType, situationValue")
    })
public class BatterStats {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Integer playerId;
    private Integer season;
    private String category;
    private Double value;
    private Integer ranking;
    private String position;
    private String series;           // "0"(정규), "KS", "PS", "WC"
    private String situationType;    // ""(기본), "COUNT", "RUNNER" 등
    private String situationValue;   // ""(기본), "0-0", "RISP" 등
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

### 8.3 컬럼명 규칙

```
camelCase 유지: playerId, teamId, situationType, matchDate
(snake_case 아님!)

설정:
  physical-strategy: PhysicalNamingStrategyStandardImpl  → 이름 그대로 사용
  implicit-strategy: ImplicitNamingStrategyLegacyJpaImpl → JPA 기본 규칙
```

### 8.4 연관 관계 매핑

```java
// User → Team (다대일)
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "favoriteTeamId")
private Team favoriteTeam;

// BatterRecord → Player (다대일)
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "playerId", insertable = false, updatable = false)
private Player player;
```

---

## 9. Flyway 마이그레이션

### 마이그레이션 이력

| 버전 | 파일 | 설명 | 핵심 SQL |
|:----:|------|------|----------|
| V1 | Add_Training_Points | 훈련 포인트 컬럼 | `ALTER TABLE ... ADD COLUMN` |
| V2 | Create_Custom_Teams | 커스텀 팀 테이블 | `CREATE TABLE` |
| V3 | Drop_Custom_and_RealMatch | 미사용 모듈 삭제 | `DROP TABLE IF EXISTS` |
| V4 | Drop_Review_Tables | 리뷰 모듈 삭제 | `DROP TABLE IF EXISTS` |
| V5 | Add_AnalysisColumn_Magazine | 매거진 필드 추가 | `ALTER TABLE ... ADD COLUMN` |
| V6 | Create_Analysis_Result | 분석 결과 테이블 | `CREATE TABLE` |
| V7 | Extend_Analysis_Result | 서브토픽/비교 확장 | `ALTER TABLE ... ADD COLUMN` + `DROP INDEX` + `ADD UNIQUE KEY` |
| V8 | Cleanup_Orphan_Tables | 잔존 테이블 정리 | `DROP TABLE IF EXISTS` |
| **V9** | **Add_Unique_Constraints** | **9개 테이블 UK 추가** | `DELETE 중복` + `ADD UNIQUE KEY` |
| **V10** | **Add_Performance_Indexes** | **17개 인덱스 추가** | `CREATE INDEX` |
| V11 | Cleanup_Orphan_Records | 고아 레코드 정리 | `DELETE ... NOT IN (SELECT ...)` |
| V12 | Add_TeamStats_UK | 팀 스탯 UK 추가 | `DELETE 중복` + `ADD UNIQUE KEY` |

### V9 핵심: Unique Constraint 추가 (중복 제거 후)

```sql
-- 1단계: 기존 중복 데이터 제거 (작은 ID 삭제)
DELETE b1 FROM player_batter_stats b1
INNER JOIN player_batter_stats b2
ON b1.playerId = b2.playerId
   AND b1.season = b2.season
   AND b1.category = b2.category
   AND b1.series = b2.series
   AND b1.situationType = b2.situationType
   AND b1.situationValue = b2.situationValue
   AND b1.id < b2.id;

-- 2단계: Unique Key 추가
ALTER TABLE player_batter_stats
    ADD UNIQUE KEY uk_batter_stats_full
    (playerId, season, category, series, situationType, situationValue);
```

### V10 핵심: 성능 인덱스

```sql
-- 통계 테이블 (조회 빈도 높음)
CREATE INDEX idx_batter_player_season ON player_batter_stats (playerId, season);
CREATE INDEX idx_batter_season_category ON player_batter_stats (season, category);
CREATE INDEX idx_batter_season_series_sit ON player_batter_stats
    (season, series, situationType, situationValue);

-- 경기 일정 (날짜별 조회)
CREATE INDEX idx_schedule_match_date ON kbo_schedule (matchDate);
CREATE INDEX idx_schedule_home_team ON kbo_schedule (homeTeamId);
CREATE INDEX idx_schedule_away_team ON kbo_schedule (awayTeamId);

-- 시뮬레이션 카드 (등급별 조회)
CREATE INDEX idx_card_player_season ON sim_player_card_overall (playerId, season);
CREATE INDEX idx_card_overall_grade ON sim_player_card_overall (overall, grade);

-- 선수 (팀별/이름별 조회)
CREATE INDEX idx_player_team ON player (teamId);
CREATE INDEX idx_player_name_team ON player (name, teamId);
```

### V11: 고아 레코드 정리

```sql
-- scheduleId FK가 없는 고아 레코드 삭제
DELETE FROM kbo_batter_lineup  WHERE scheduleId NOT IN (SELECT id FROM kbo_schedule);
DELETE FROM kbo_batter_record  WHERE scheduleId NOT IN (SELECT id FROM kbo_schedule);
DELETE FROM kbo_pitcher_record WHERE scheduleId NOT IN (SELECT id FROM kbo_schedule);
DELETE FROM kbo_score_board    WHERE scheduleId NOT IN (SELECT id FROM kbo_schedule);

-- playerId FK가 없는 고아 레코드 삭제
DELETE FROM kbo_batter_record      WHERE playerId NOT IN (SELECT id FROM player);
DELETE FROM kbo_pitcher_record     WHERE playerId NOT IN (SELECT id FROM player);
DELETE FROM sim_player_card_overall WHERE playerId NOT IN (SELECT id FROM player);
```

---

## 10. 인덱스 & 제약 조건 전략

### Unique Key 설계 원칙

| 테이블 유형 | Unique Key 구성 | 이유 |
|------------|----------------|------|
| 선수 통계 | (playerId, season, category, series, 상황) | 한 선수의 동일 지표는 1건만 |
| 경기 기록 | (scheduleId, playerId) | 한 경기에 선수당 1레코드 |
| 팀 순위 | (season, teamId) | 시즌당 팀 순위 1건 |
| 분석 결과 | (topic, subTopic, season, comparisonType, anchorValue) | 동일 분석은 1건만 |
| 1:1 관계 | (scheduleId) — ScoreBoard | 경기당 스코어보드 1개 |

### 인덱스 설계 원칙

```
1. 조회 패턴 기반: 실제 @Query에서 WHERE 조건에 사용되는 컬럼 조합
2. 복합 인덱스: 선택도 높은 컬럼을 앞에 배치 (season > category > series)
3. 커버링 인덱스: (season, series, situationType, situationValue) → WHERE 절 전체 커버
4. FK 인덱스: JOIN 대상 컬럼 (teamId, scheduleId, playerId)
```

---

## 11. JPA 설정 (application.yml)

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/glvpen
    username: root
    password: ${mysql.password}      # security-variable.yml에서 주입

  jpa:
    open-in-view: false              # OSIV 비활성화 (LazyInitializationException 방지)
    hibernate:
      ddl-auto: validate             # Flyway가 스키마 관리 → validate만 수행
      naming:
        implicit-strategy: org.hibernate.boot.model.naming.ImplicitNamingStrategyLegacyJpaImpl
        physical-strategy: org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl

  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 7              # V7까지는 이미 적용된 상태에서 시작
    locations: classpath:db/migration
    validate-on-migrate: false
```

### 설정 의미

| 설정 | 값 | 설명 |
|------|-----|------|
| `ddl-auto` | validate | Entity ↔ DB 스키마 불일치 시 앱 기동 실패 |
| `open-in-view` | false | 컨트롤러에서 Lazy 로딩 불가 (서비스 계층에서 처리) |
| `PhysicalNamingStrategyStandardImpl` | camelCase 유지 | `playerId` → DB에도 `playerId` |
| `baseline-version` | 7 | V1~V7은 수동 적용 완료, V8부터 Flyway 관리 |

---

## 주의사항 & 트러블슈팅

### Native Query Object[] 반환 시 빈 배열 체크

```java
// 잘못된 코드
Optional<Object[]> result = repository.findSomething();
String value = (String) result.get()[0];  // 빈 배열이면 ArrayIndexOutOfBoundsException

// 올바른 코드
Optional<Object[]> result = repository.findSomething();
if (result.isPresent() && result.get().length >= 1) {
    String value = (String) result.get()[0];
}
```

### OSIV false 환경에서 Lazy 로딩

```java
// 잘못된 코드 — 컨트롤러에서 Lazy 로딩 시도
User user = (User) session.getAttribute("loginUser");
user.getFavoriteTeam().getName();  // LazyInitializationException!

// 올바른 코드 — 서비스에서 다시 조회
User freshUser = userService.findById(user.getId());
model.addAttribute("user", freshUser);
```
