package com.kepg.glvpen.modules.player.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kepg.glvpen.modules.player.domain.Player;

public interface PlayerRepository extends JpaRepository<Player, Integer> {

    // 팀별 최고 WAR 타자 조회 (포지션이 투수가 아닌 경우)
    @Query(value = """
            SELECT p.name, s.position, s.value AS war, s.ranking, p.id
            FROM player_batter_stats s
            JOIN player p ON s.playerId = p.id
            WHERE s.category = 'WAR'
              AND s.season = :season
              AND p.teamId = :teamId
              AND s.position != 'P'
              AND s.series = '0' AND s.situationType = '' AND s.situationValue = ''
            ORDER BY s.value DESC
            LIMIT 1
            """, nativeQuery = true)
    List<Object[]> findTopHitterByTeamIdAndSeason(@Param("teamId") int teamId, @Param("season") int season);

    // 팀별 최고 WAR 투수 조회 (포지션이 투수인 경우)
    @Query(value = """
            SELECT p.name, s.position, s.value AS war, s.ranking, p.id
            FROM player_pitcher_stats s
            JOIN player p ON s.playerId = p.id
            WHERE s.category = 'WAR'
              AND s.season = :season
              AND p.teamId = :teamId
              AND s.position = 'P'
              AND s.series = '0' AND s.situationType = '' AND s.situationValue = ''
            ORDER BY s.value DESC
            LIMIT 1
            """, nativeQuery = true)
    List<Object[]> findTopPitcherByTeamIdAndSeason(@Param("teamId") int teamId, @Param("season") int season);

    // 특정 선수의 시즌/카테고리별 스탯 값 조회 (ex. WAR, AVG 등)
    @Query(value = """
            SELECT value
            FROM player_batter_stats
            WHERE playerId = :playerId
              AND category = :category
              AND season = :season
              AND series = '0' AND situationType = '' AND situationValue = ''
            """, nativeQuery = true)
    Optional<String> findStatValueByPlayerIdCategoryAndSeason(
            @Param("playerId") int playerId, @Param("category") String category, @Param("season") int season);

    // 이름 + 팀 ID로 선수 정보 조회
    Optional<Player> findByNameAndTeamId(String name, int teamId);

    // KBO 사이트 고유 ID로 선수 조회
    Optional<Player> findByKboPlayerId(String kboPlayerId);

    // 프로필 미수집 선수 목록 (kboPlayerId가 있지만 프로필이 아직 없는 선수)
    List<Player> findByKboPlayerIdIsNotNullAndProfileUpdatedAtIsNull();

    // 프로필 전체 미수집 선수 (kboPlayerId가 null인 선수)
    List<Player> findByKboPlayerIdIsNull();

    // 포지션별 최고 WAR 타자 리스트 조회 (페이징 포함)
    @Query(value = """
            SELECT p.*
            FROM player p
            JOIN player_batter_stats b ON p.id = b.playerId
            WHERE b.position = :position
              AND b.season = :season
              AND b.category = 'WAR'
              AND b.series = '0' AND b.situationType = '' AND b.situationValue = ''
            ORDER BY b.value DESC
            """, nativeQuery = true)
    List<Player> findTopBattersByPositionAndSeason(
            @Param("position") String position,
            @Param("season") int season,
            Pageable pageable);

    // 포지션별 최고 WAR 투수 리스트 조회 (페이징 포함)
    @Query(value = """
            SELECT p.*
            FROM player p
            JOIN player_pitcher_stats ps ON p.id = ps.playerId
            WHERE ps.position = :position
              AND ps.season = :season
              AND ps.category = 'WAR'
              AND ps.series = '0' AND ps.situationType = '' AND ps.situationValue = ''
            ORDER BY ps.value DESC
            """, nativeQuery = true)
    List<Player> findTopPitchersByPositionAndSeason(
            @Param("position") String position,
            @Param("season") int season,
            Pageable pageable);

    // 해당 시즌에 WAR 기록이 있는 타자 수 조회
    @Query(value = """
            SELECT COUNT(DISTINCT playerId)
            FROM player_batter_stats
            WHERE season = :season
              AND category = 'WAR'
              AND series = '0' AND situationType = '' AND situationValue = ''
            """, nativeQuery = true)
    int countBattersInSeason(@Param("season") int season);

    // 해당 시즌에 WAR 기록이 있는 투수 수 조회
    @Query(value = """
            SELECT COUNT(DISTINCT playerId)
            FROM player_pitcher_stats
            WHERE season = :season
              AND category = 'WAR'
              AND series = '0' AND situationType = '' AND situationValue = ''
            """, nativeQuery = true)
    int countPitchersInSeason(@Param("season") int season);

    // 데뷔 연도 기준 신인 타자 TOP 20 (WAR 내림차순)
    @Query(value = """
            SELECT
                p.id, p.name, p.debutYear, t.name AS teamName, t.logoName,
                MAX(CASE WHEN bs.category = 'WAR' THEN bs.value END) AS war,
                MAX(CASE WHEN bs.category = 'AVG' THEN bs.value END) AS avg,
                MAX(CASE WHEN bs.category = 'HR' THEN bs.value END) AS hr,
                MAX(CASE WHEN bs.category = 'OPS' THEN bs.value END) AS ops
            FROM player p
            JOIN team t ON p.teamId = t.id
            JOIN player_batter_stats bs ON bs.playerId = p.id
                AND bs.season = p.debutYear
                AND bs.series = '0' AND bs.situationType = '' AND bs.situationValue = ''
            WHERE p.debutYear = :debutYear AND p.position != 'P'
            GROUP BY p.id, p.name, p.debutYear, t.name, t.logoName
            HAVING war IS NOT NULL
            ORDER BY war DESC
            LIMIT 20
            """, nativeQuery = true)
    List<Object[]> findRookieBattersByDebutYear(@Param("debutYear") int debutYear);

    // 데뷔 연도 기준 신인 투수 TOP 20 (WAR 내림차순)
    @Query(value = """
            SELECT
                p.id, p.name, p.debutYear, t.name AS teamName, t.logoName,
                MAX(CASE WHEN ps.category = 'WAR' THEN ps.value END) AS war,
                MAX(CASE WHEN ps.category = 'ERA' THEN ps.value END) AS era,
                MAX(CASE WHEN ps.category = 'W' THEN ps.value END) AS wins,
                MAX(CASE WHEN ps.category = 'SO' THEN ps.value END) AS so
            FROM player p
            JOIN team t ON p.teamId = t.id
            JOIN player_pitcher_stats ps ON ps.playerId = p.id
                AND ps.season = p.debutYear
                AND ps.series = '0' AND ps.situationType = '' AND ps.situationValue = ''
            WHERE p.debutYear = :debutYear AND p.position = 'P'
            GROUP BY p.id, p.name, p.debutYear, t.name, t.logoName
            HAVING war IS NOT NULL
            ORDER BY war DESC
            LIMIT 20
            """, nativeQuery = true)
    List<Object[]> findRookiePitchersByDebutYear(@Param("debutYear") int debutYear);

    // 출신교 분류별 시즌별 선수 수
    @Query(value = """
            SELECT
                bs.season AS season,
                CASE
                    WHEN p.school LIKE '%고등학교%' OR p.school LIKE '%고%' THEN '고졸'
                    WHEN p.school LIKE '%대학교%' OR p.school LIKE '%대%' THEN '대졸'
                    ELSE '기타'
                END AS schoolType,
                COUNT(DISTINCT p.id) AS playerCount
            FROM player p
            JOIN player_batter_stats bs ON bs.playerId = p.id
                AND bs.category = 'WAR'
                AND bs.series = '0' AND bs.situationType = '' AND bs.situationValue = ''
            WHERE bs.season BETWEEN :startYear AND :endYear
              AND p.school IS NOT NULL AND p.school != ''
            GROUP BY bs.season, schoolType
            ORDER BY bs.season, schoolType
            """, nativeQuery = true)
    List<Object[]> findSchoolTypeDistribution(@Param("startYear") int startYear, @Param("endYear") int endYear);

    // 전체 시즌별 선수 수 (투수 포함, 출신교 분류)
    @Query(value = """
            SELECT
                sub.season, sub.schoolType, SUM(sub.cnt) AS playerCount
            FROM (
                SELECT bs.season AS season,
                    CASE
                        WHEN p.school LIKE '%고등학교%' OR p.school LIKE '%고%' THEN '고졸'
                        WHEN p.school LIKE '%대학교%' OR p.school LIKE '%대%' THEN '대졸'
                        ELSE '기타'
                    END AS schoolType,
                    COUNT(DISTINCT p.id) AS cnt
                FROM player p
                JOIN player_batter_stats bs ON bs.playerId = p.id
                    AND bs.category = 'WAR'
                    AND bs.series = '0' AND bs.situationType = '' AND bs.situationValue = ''
                WHERE bs.season BETWEEN :startYear AND :endYear
                  AND p.school IS NOT NULL AND p.school != ''
                GROUP BY bs.season, schoolType
                UNION ALL
                SELECT ps.season AS season,
                    CASE
                        WHEN p.school LIKE '%고등학교%' OR p.school LIKE '%고%' THEN '고졸'
                        WHEN p.school LIKE '%대학교%' OR p.school LIKE '%대%' THEN '대졸'
                        ELSE '기타'
                    END AS schoolType,
                    COUNT(DISTINCT p.id) AS cnt
                FROM player p
                JOIN player_pitcher_stats ps ON ps.playerId = p.id
                    AND ps.category = 'WAR'
                    AND ps.series = '0' AND ps.situationType = '' AND ps.situationValue = ''
                WHERE ps.season BETWEEN :startYear AND :endYear
                  AND p.school IS NOT NULL AND p.school != ''
                GROUP BY ps.season, schoolType
            ) sub
            GROUP BY sub.season, sub.schoolType
            ORDER BY sub.season, sub.schoolType
            """, nativeQuery = true)
    List<Object[]> findSchoolTypeDistributionAll(@Param("startYear") int startYear, @Param("endYear") int endYear);
}
