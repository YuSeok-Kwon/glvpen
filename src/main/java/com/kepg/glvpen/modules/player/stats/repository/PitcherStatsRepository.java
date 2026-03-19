package com.kepg.glvpen.modules.player.stats.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kepg.glvpen.modules.player.stats.domain.PitcherStats;

public interface PitcherStatsRepository extends JpaRepository<PitcherStats, Integer> {

    // 6-key 조회 (시리즈+상황 포함)
    @Query(value = """
            SELECT * FROM player_pitcher_stats
            WHERE playerId = :playerId AND season = :season AND category = :category
              AND series = :series AND situationType = :situationType AND situationValue = :situationValue
            LIMIT 1
            """, nativeQuery = true)
    Optional<PitcherStats> findByFullKey(
            @Param("playerId") Integer playerId, @Param("season") Integer season,
            @Param("category") String category, @Param("series") String series,
            @Param("situationType") String situationType, @Param("situationValue") String situationValue);

    // 기존 3-key 호환 (정규시즌 전체만 조회)
    @Query(value = """
            SELECT * FROM player_pitcher_stats
            WHERE playerId = :playerId AND season = :season AND category = :category
              AND series = '0' AND situationType = '' AND situationValue = ''
            LIMIT 1
            """, nativeQuery = true)
    Optional<PitcherStats> findByPlayerIdAndSeasonAndCategory(
            @Param("playerId") Integer playerId, @Param("season") Integer season, @Param("category") String category);

    // 특정 투수의 스탯 값을 category 기준으로 조회
    @Query(value = """
            SELECT s.value
            FROM player_pitcher_stats s
            WHERE s.playerId = :playerId
              AND s.category = :category
              AND s.season = :season
              AND s.series = '0' AND s.situationType = '' AND s.situationValue = ''
            """, nativeQuery = true)
    Optional<String> findStatValueByPlayerIdCategoryAndSeason(
            @Param("playerId") int playerId, @Param("category") String category, @Param("season") int season);

    // 시즌별 각 카테고리별 1위 투수 조회 (ROW_NUMBER 윈도우 함수 사용, 규정이닝 50% 이상 필터)
    @Query(value = """
            SELECT ranked.position, ranked.playerName, ranked.teamName,
                   ranked.logoName, ranked.record_value, ranked.category
            FROM (
                SELECT
                    bs.position AS position,
                    p.name AS playerName,
                    t.name AS teamName,
                    t.logoName AS logoName,
                    COALESCE(bs.value, 0) AS record_value,
                    bs.category,
                    ROW_NUMBER() OVER (
                        PARTITION BY bs.category
                        ORDER BY CASE
                            WHEN bs.category IN ('ERA', 'WHIP', 'FIP') THEN bs.value
                            ELSE -bs.value
                        END
                    ) AS row_num
                FROM player_pitcher_stats bs
                JOIN player p ON bs.playerId = p.id
                JOIN team t ON p.teamId = t.id
                WHERE bs.season = :season
                  AND bs.category IN ('ERA', 'WHIP', 'G', 'IP', 'W', 'HLD', 'SV', 'SO', 'FIP')
                  AND (bs.category != 'ERA' OR bs.value > 0)
                  AND (bs.category != 'FIP' OR bs.value > 0)
                  AND bs.series = '0' AND bs.situationType = '' AND bs.situationValue = ''
                  AND EXISTS (
                      SELECT 1 FROM player_pitcher_stats ip_stat
                      WHERE ip_stat.playerId = bs.playerId
                        AND ip_stat.season = bs.season
                        AND ip_stat.category = 'IP'
                        AND ip_stat.series = '0' AND ip_stat.situationType = '' AND ip_stat.situationValue = ''
                        AND ip_stat.value >= :minIp
                  )
            ) AS ranked
            WHERE ranked.row_num = 1
            ORDER BY ranked.category
            """, nativeQuery = true)
    List<Object[]> findTopPitchersAsTuple(@Param("season") int season, @Param("minIp") double minIp);

    // 시즌 전체 투수 스탯 요약
    @Query(value = """
            SELECT
                p.name AS playerName,
                t.name AS teamName,
                t.logoName AS logoName,
                MAX(CASE WHEN b.category = 'ERA' THEN b.value ELSE NULL END) AS era,
                MAX(CASE WHEN b.category = 'WHIP' THEN b.value ELSE NULL END) AS whip,
                MAX(CASE WHEN b.category = 'W' THEN b.value ELSE NULL END) AS wins,
                MAX(CASE WHEN b.category = 'L' THEN b.value ELSE NULL END) AS losses,
                MAX(CASE WHEN b.category = 'SV' THEN b.value ELSE NULL END) AS saves,
                MAX(CASE WHEN b.category = 'HLD' THEN b.value ELSE NULL END) AS holds,
                MAX(CASE WHEN b.category = 'SO' THEN b.value ELSE NULL END) AS strikeouts,
                MAX(CASE WHEN b.category = 'BB' THEN b.value ELSE NULL END) AS walks,
                MAX(CASE WHEN b.category = 'H' THEN b.value ELSE NULL END) AS hitsAllowed,
                MAX(CASE WHEN b.category = 'HR' THEN b.value ELSE NULL END) AS homeRunsAllowed,
                MAX(CASE WHEN b.category = 'IP' THEN b.value ELSE NULL END) AS inningsPitched,
                t.id AS teamId,
                MAX(CASE WHEN b.category = 'FIP' THEN b.value ELSE NULL END) AS fip,
                MAX(CASE WHEN b.category = 'xFIP' THEN b.value ELSE NULL END) AS xfip,
                MAX(CASE WHEN b.category = 'K/9' THEN b.value ELSE NULL END) AS k9,
                MAX(CASE WHEN b.category = 'BB/9' THEN b.value ELSE NULL END) AS bb9,
                MAX(CASE WHEN b.category = 'G' THEN b.value ELSE NULL END) AS g,
                MAX(CASE WHEN b.category = 'WPCT' THEN b.value ELSE NULL END) AS wpct,
                MAX(CASE WHEN b.category = 'HBP' THEN b.value ELSE NULL END) AS hbp,
                MAX(CASE WHEN b.category = 'R' THEN b.value ELSE NULL END) AS r,
                MAX(CASE WHEN b.category = 'ER' THEN b.value ELSE NULL END) AS er,
                MAX(CASE WHEN b.category = 'CG' THEN b.value ELSE NULL END) AS cg,
                MAX(CASE WHEN b.category = 'SHO' THEN b.value ELSE NULL END) AS sho,
                MAX(CASE WHEN b.category = 'QS' THEN b.value ELSE NULL END) AS qs,
                MAX(CASE WHEN b.category = 'BSV' THEN b.value ELSE NULL END) AS bsv,
                MAX(CASE WHEN b.category = 'TBF' THEN b.value ELSE NULL END) AS tbf,
                MAX(CASE WHEN b.category = 'NP' THEN b.value ELSE NULL END) AS np,
                MAX(CASE WHEN b.category = 'AVG' THEN b.value ELSE NULL END) AS avg,
                MAX(CASE WHEN b.category = '2B' THEN b.value ELSE NULL END) AS twoB,
                MAX(CASE WHEN b.category = '3B' THEN b.value ELSE NULL END) AS threeB,
                MAX(CASE WHEN b.category = 'SAC' THEN b.value ELSE NULL END) AS sac,
                MAX(CASE WHEN b.category = 'SF' THEN b.value ELSE NULL END) AS sf,
                MAX(CASE WHEN b.category = 'IBB' THEN b.value ELSE NULL END) AS ibb,
                MAX(CASE WHEN b.category = 'WP' THEN b.value ELSE NULL END) AS wp,
                MAX(CASE WHEN b.category = 'BK' THEN b.value ELSE NULL END) AS bk
            FROM player p
            JOIN team t ON p.teamId = t.id
            JOIN player_pitcher_stats b ON p.id = b.playerId
            WHERE b.season = :season
              AND b.series = '0' AND b.situationType = '' AND b.situationValue = ''
            GROUP BY p.name, t.name, t.logoName, t.id
            """, nativeQuery = true)
    List<Object[]> findAllPitchers(@Param("season") int season);

    // 투수의 raw 스탯 리스트 (category, value 쌍)
    @Query(value = """
            SELECT s.category, s.value
            FROM player_pitcher_stats s
            WHERE s.playerId = :playerId
              AND s.season = :season
              AND s.series = '0' AND s.situationType = '' AND s.situationValue = ''
            """, nativeQuery = true)
    List<Object[]> findStatsRawByPlayerIdAndSeason(@Param("playerId") Integer playerId, @Param("season") int season);

    // 시즌별 특정 투수 스탯 목록 조회 (정규시즌 전체)
    @Query(value = """
            SELECT * FROM player_pitcher_stats
            WHERE playerId = :playerId AND season = :season
              AND series = '0' AND situationType = '' AND situationValue = ''
            """, nativeQuery = true)
    List<PitcherStats> findByPlayerIdAndSeason(@Param("playerId") int playerId, @Param("season") int season);

    // 시즌별 투수 존재 여부 확인 (정규시즌 전체)
    @Query(value = """
            SELECT COUNT(*) > 0
            FROM player_pitcher_stats
            WHERE playerId = :playerId AND season = :season
              AND series = '0' AND situationType = '' AND situationValue = ''
            """, nativeQuery = true)
    boolean existsByPlayerIdAndSeason(@Param("playerId") Integer playerId, @Param("season") Integer season);

    // 시즌별 등록된 투수 ID 리스트 조회 (정규시즌 전체)
    @Query(value = """
            SELECT DISTINCT s.playerId
            FROM player_pitcher_stats s
            WHERE s.season = :season
              AND s.series = '0' AND s.situationType = '' AND s.situationValue = ''
            """, nativeQuery = true)
    List<Integer> findDistinctPlayerIdsBySeason(@Param("season") int season);

    // 시즌별 전체 투수 스탯 조회 (정규시즌 전체)
    @Query(value = """
            SELECT *
            FROM player_pitcher_stats
            WHERE season = :season
              AND series = '0' AND situationType = '' AND situationValue = ''
            """, nativeQuery = true)
    List<PitcherStats> findBySeason(@Param("season") int season);

    // 리그 평균 (카테고리별, Marcel 예측용)
    @Query(value = """
            SELECT p.category, AVG(p.value) AS avgValue
            FROM player_pitcher_stats p
            WHERE p.season = :season
              AND p.series = '0' AND p.situationType = '' AND p.situationValue = ''
              AND p.category IN ('ERA', 'WHIP', 'FIP', 'xFIP', 'K/9', 'BB/9', 'W', 'SV', 'HLD', 'IP', 'SO')
            GROUP BY p.category
            """, nativeQuery = true)
    List<Object[]> findLeagueAvgBySeason(@Param("season") int season);
}
