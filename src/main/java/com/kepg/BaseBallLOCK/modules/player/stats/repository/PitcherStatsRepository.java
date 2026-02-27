package com.kepg.BaseBallLOCK.modules.player.stats.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kepg.BaseBallLOCK.modules.player.stats.domain.PitcherStats;

public interface PitcherStatsRepository extends JpaRepository<PitcherStats, Integer> {

    // 6-key 조회 (시리즈+상황 포함)
    @Query(value = """
            SELECT * FROM pitcherStats
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
            SELECT * FROM pitcherStats
            WHERE playerId = :playerId AND season = :season AND category = :category
              AND series = '0' AND situationType = '' AND situationValue = ''
            LIMIT 1
            """, nativeQuery = true)
    Optional<PitcherStats> findByPlayerIdAndSeasonAndCategory(
            @Param("playerId") Integer playerId, @Param("season") Integer season, @Param("category") String category);

    // 특정 투수의 스탯 값을 category 기준으로 조회
    @Query(value = """
            SELECT s.value
            FROM pitcherStats s
            WHERE s.playerId = :playerId
              AND s.category = :category
              AND s.season = :season
              AND s.series = '0' AND s.situationType = '' AND s.situationValue = ''
            """, nativeQuery = true)
    Optional<String> findStatValueByPlayerIdCategoryAndSeason(
            @Param("playerId") int playerId, @Param("category") String category, @Param("season") int season);

    // 시즌별 각 카테고리별 1위 투수 조회
    @Query(value = """
            SELECT
                ranked.position,
                ranked.playerName,
                ranked.teamName,
                ranked.logoName,
                ranked.value AS record_value,
                ranked.category
            FROM (
                SELECT
                    bs.position AS position,
                    p.name AS playerName,
                    t.name AS teamName,
                    t.logoName AS logoName,
                    COALESCE(bs.value, 0) AS value,
                    bs.category,
                    @rank := IF(@prev_pos_cat = CONCAT(bs.position, bs.category), @rank + 1, 1) AS row_num,
                    @prev_pos_cat := CONCAT(bs.position, bs.category)
                FROM pitcherStats bs
                JOIN player p ON bs.playerId = p.id
                JOIN team t ON p.teamId = t.id
                CROSS JOIN (SELECT @rank := 0, @prev_pos_cat := '') vars
                WHERE bs.season = :season
                  AND bs.category IN ('ERA', 'WHIP', 'G', 'IP', 'W', 'HLD', 'SV', 'SO', 'WAR')
                  AND (bs.category != 'ERA' OR bs.value > 0)
                  AND bs.series = '0' AND bs.situationType = '' AND bs.situationValue = ''
                ORDER BY
                    bs.position,
                    bs.category,
                    CASE
                        WHEN bs.category IN ('ERA', 'WHIP') THEN bs.value
                        WHEN bs.category IN ('G', 'IP', 'W', 'HLD', 'SV', 'SO', 'WAR') THEN -bs.value
                        ELSE bs.value
                    END
            ) AS ranked
            WHERE ranked.row_num = 1
            ORDER BY ranked.position, ranked.category
            """, nativeQuery = true)
    List<Object[]> findTopPitchersAsTuple(@Param("season") int season);

    // 시즌 전체 투수 스탯 요약
    @Query("""
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
                MAX(CASE WHEN b.category = 'WAR' THEN b.value ELSE NULL END) AS war,
                t.id AS teamId,
                MAX(CASE WHEN b.category = 'FIP' THEN b.value ELSE NULL END) AS fip,
                MAX(CASE WHEN b.category = 'xFIP' THEN b.value ELSE NULL END) AS xfip,
                MAX(CASE WHEN b.category = 'K/9' THEN b.value ELSE NULL END) AS k9,
                MAX(CASE WHEN b.category = 'BB/9' THEN b.value ELSE NULL END) AS bb9
            FROM Player p
            JOIN Team t ON p.teamId = t.id
            JOIN PitcherStats b ON p.id = b.playerId
            WHERE b.season = :season
              AND b.series = '0' AND b.situationType = '' AND b.situationValue = ''
            GROUP BY p.name, t.name, t.logoName, t.id
            """)
    List<Object[]> findAllPitchers(@Param("season") int season);

    // 투수의 raw 스탯 리스트 (category, value 쌍)
    @Query(value = """
            SELECT s.category, s.value
            FROM pitcherStats s
            WHERE s.playerId = :playerId
              AND s.season = :season
              AND s.series = '0' AND s.situationType = '' AND s.situationValue = ''
            """, nativeQuery = true)
    List<Object[]> findStatsRawByPlayerIdAndSeason(@Param("playerId") Integer playerId, @Param("season") int season);

    // 시즌별 특정 투수 스탯 목록 조회 (정규시즌 전체)
    @Query(value = """
            SELECT * FROM pitcherStats
            WHERE playerId = :playerId AND season = :season
              AND series = '0' AND situationType = '' AND situationValue = ''
            """, nativeQuery = true)
    List<PitcherStats> findByPlayerIdAndSeason(@Param("playerId") int playerId, @Param("season") int season);

    // 시즌별 투수 존재 여부 확인 (정규시즌 전체)
    @Query(value = """
            SELECT COUNT(*) > 0
            FROM pitcherStats
            WHERE playerId = :playerId AND season = :season
              AND series = '0' AND situationType = '' AND situationValue = ''
            """, nativeQuery = true)
    boolean existsByPlayerIdAndSeason(@Param("playerId") Integer playerId, @Param("season") Integer season);

    // 시즌별 등록된 투수 ID 리스트 조회 (정규시즌 전체)
    @Query(value = """
            SELECT DISTINCT s.playerId
            FROM pitcherStats s
            WHERE s.season = :season
              AND s.series = '0' AND s.situationType = '' AND s.situationValue = ''
            """, nativeQuery = true)
    List<Integer> findDistinctPlayerIdsBySeason(@Param("season") int season);

    // 시즌별 전체 투수 스탯 조회 (정규시즌 전체)
    @Query(value = """
            SELECT *
            FROM pitcherStats
            WHERE season = :season
              AND series = '0' AND situationType = '' AND situationValue = ''
            """, nativeQuery = true)
    List<PitcherStats> findBySeason(@Param("season") int season);
}
