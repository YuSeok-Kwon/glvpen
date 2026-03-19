package com.kepg.glvpen.modules.player.stats.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kepg.glvpen.modules.player.stats.domain.BatterStats;

public interface BatterStatsRepository extends JpaRepository<BatterStats, Integer> {

    // 6-key 조회 (시리즈+상황 포함)
    @Query(value = """
            SELECT * FROM player_batter_stats
            WHERE playerId = :playerId AND season = :season AND category = :category
              AND series = :series AND situationType = :situationType AND situationValue = :situationValue
            LIMIT 1
            """, nativeQuery = true)
    Optional<BatterStats> findByFullKey(
            @Param("playerId") Integer playerId, @Param("season") Integer season,
            @Param("category") String category, @Param("series") String series,
            @Param("situationType") String situationType, @Param("situationValue") String situationValue);

    // 기존 3-key 호환 (정규시즌 전체만 조회)
    @Query(value = """
            SELECT * FROM player_batter_stats
            WHERE playerId = :playerId AND season = :season AND category = :category
              AND series = '0' AND situationType = '' AND situationValue = ''
            LIMIT 1
            """, nativeQuery = true)
    Optional<BatterStats> findByPlayerIdAndSeasonAndCategory(
            @Param("playerId") Integer playerId, @Param("season") Integer season, @Param("category") String category);

    @Query(value = """
            SELECT s.value FROM player_batter_stats s
            WHERE s.playerId = :playerId AND s.category = :category AND s.season = :season
              AND s.series = '0' AND s.situationType = '' AND s.situationValue = ''
            """, nativeQuery = true)
    Optional<String> findStatValueByPlayerIdCategoryAndSeason(
            @Param("playerId") int playerId, @Param("category") String category, @Param("season") int season);

    // 포지션별 wOBA 1위 타자 조회 (수비 포지션 기준)
    @Query(value = """
            SELECT
                position, playerName, teamName, logoName, woba
            FROM (
                SELECT
                    COALESCE(d_pos.mainPosition, bs.position, 'DH') AS position,
                    p.name AS playerName,
                    t.name AS teamName,
                    t.logoName AS logoName,
                    COALESCE(bs.value, 0) AS woba,
                    ROW_NUMBER() OVER (
                        PARTITION BY COALESCE(d_pos.mainPosition, bs.position, 'DH')
                        ORDER BY COALESCE(bs.value, 0) DESC
                    ) AS row_num
                FROM player_batter_stats bs
                JOIN player p ON bs.playerId = p.id
                JOIN team t ON p.teamId = t.id
                LEFT JOIN (
                    SELECT playerId, mainPosition FROM (
                        SELECT playerId,
                            CASE position
                                WHEN '포수' THEN 'C'
                                WHEN '1루수' THEN '1B'
                                WHEN '2루수' THEN '2B'
                                WHEN '3루수' THEN '3B'
                                WHEN '유격수' THEN 'SS'
                                WHEN '좌익수' THEN 'LF'
                                WHEN '우익수' THEN 'RF'
                                WHEN '중견수' THEN 'CF'
                                ELSE NULL
                            END AS mainPosition,
                            ROW_NUMBER() OVER (PARTITION BY playerId ORDER BY value DESC) AS rn
                        FROM player_defense_stats
                        WHERE season = :season AND series = '0' AND category = 'G' AND position != '투수'
                    ) ranked_d WHERE rn = 1
                ) d_pos ON d_pos.playerId = p.id
                WHERE bs.season = :season
                  AND bs.category = 'wOBA'
                  AND bs.series = '0' AND bs.situationType = '' AND bs.situationValue = ''
            ) AS ranked
            WHERE ranked.row_num = 1
            ORDER BY ranked.position
            """, nativeQuery = true)
    List<Object[]> findTopBattersByPosition(@Param("season") int season);

    // 시즌별 전체 타자 스탯 요약 (수비 포지션 기준)
    @Query(value = """
            SELECT
                COALESCE(d_pos.mainPosition, ANY_VALUE(b.position), 'DH') AS position,
                p.name AS playerName,
                t.name AS teamName,
                t.logoName AS logoName,
                MAX(CASE WHEN b.category = 'wOBA' THEN b.value ELSE NULL END) AS woba,
                MAX(CASE WHEN b.category = 'AVG' THEN b.value ELSE NULL END) AS avg,
                MAX(CASE WHEN b.category = 'OPS' THEN b.value ELSE NULL END) AS ops,
                MAX(CASE WHEN b.category = 'HR' THEN b.value ELSE NULL END) AS hr,
                MAX(CASE WHEN b.category = 'SB' THEN b.value ELSE NULL END) AS sb,
                MAX(CASE WHEN b.category = 'wRC+' THEN b.value ELSE NULL END) AS wrcPlus,
                MAX(CASE WHEN b.category = 'G' THEN b.value ELSE NULL END) AS g,
                MAX(CASE WHEN b.category = 'PA' THEN b.value ELSE NULL END) AS pa,
                MAX(CASE WHEN b.category = 'H' THEN b.value ELSE NULL END) AS h,
                MAX(CASE WHEN b.category = 'RBI' THEN b.value ELSE NULL END) AS rbi,
                MAX(CASE WHEN b.category = 'BB' THEN b.value ELSE NULL END) AS bb,
                MAX(CASE WHEN b.category = 'SO' THEN b.value ELSE NULL END) AS so,
                MAX(CASE WHEN b.category = '2B' THEN b.value ELSE NULL END) AS twoB,
                MAX(CASE WHEN b.category = '3B' THEN b.value ELSE NULL END) AS threeB,
                MAX(CASE WHEN b.category = 'OBP' THEN b.value ELSE NULL END) AS obp,
                MAX(CASE WHEN b.category = 'SLG' THEN b.value ELSE NULL END) AS slg,
                t.id AS teamId,
                MAX(CASE WHEN b.category = 'BABIP' THEN b.value ELSE NULL END) AS babip,
                MAX(CASE WHEN b.category = 'ISO' THEN b.value ELSE NULL END) AS iso,
                MAX(CASE WHEN b.category = 'K%' THEN b.value ELSE NULL END) AS kRate,
                MAX(CASE WHEN b.category = 'BB%' THEN b.value ELSE NULL END) AS bbRate,
                MAX(CASE WHEN b.category = 'AB' THEN b.value ELSE NULL END) AS ab,
                MAX(CASE WHEN b.category = 'R' THEN b.value ELSE NULL END) AS r,
                MAX(CASE WHEN b.category = 'TB' THEN b.value ELSE NULL END) AS tb,
                MAX(CASE WHEN b.category = 'SAC' THEN b.value ELSE NULL END) AS sac,
                MAX(CASE WHEN b.category = 'SF' THEN b.value ELSE NULL END) AS sf,
                MAX(CASE WHEN b.category = 'IBB' THEN b.value ELSE NULL END) AS ibb,
                MAX(CASE WHEN b.category = 'HBP' THEN b.value ELSE NULL END) AS hbp,
                MAX(CASE WHEN b.category = 'GDP' THEN b.value ELSE NULL END) AS gdp,
                MAX(CASE WHEN b.category = 'MH' THEN b.value ELSE NULL END) AS mh,
                MAX(CASE WHEN b.category = 'RISP' THEN b.value ELSE NULL END) AS risp,
                MAX(CASE WHEN b.category = 'PH-BA' THEN b.value ELSE NULL END) AS phBa
            FROM player_batter_stats b
            JOIN player p ON b.playerId = p.id
            JOIN team t ON p.teamId = t.id
            LEFT JOIN (
                SELECT playerId, mainPosition FROM (
                    SELECT playerId,
                        CASE position
                            WHEN '포수' THEN 'C'
                            WHEN '1루수' THEN '1B'
                            WHEN '2루수' THEN '2B'
                            WHEN '3루수' THEN '3B'
                            WHEN '유격수' THEN 'SS'
                            WHEN '좌익수' THEN 'LF'
                            WHEN '우익수' THEN 'RF'
                            WHEN '중견수' THEN 'CF'
                            ELSE NULL
                        END AS mainPosition,
                        ROW_NUMBER() OVER (PARTITION BY playerId ORDER BY value DESC) AS rn
                    FROM player_defense_stats
                    WHERE season = :season AND series = '0' AND category = 'G' AND position != '투수'
                ) ranked_d WHERE rn = 1
            ) d_pos ON d_pos.playerId = p.id
            WHERE b.season = :season
              AND b.series = '0' AND b.situationType = '' AND b.situationValue = ''
            GROUP BY p.id, t.id, d_pos.mainPosition
            """, nativeQuery = true)
    List<Object[]> findAllBatters(@Param("season") int season);

    // 선수 ID, 시즌 기준 스탯 전체 조회 (정규시즌 전체)
    @Query(value = """
            SELECT category, value
            FROM player_batter_stats
            WHERE playerId = :playerId AND season = :season
              AND series = '0' AND situationType = '' AND situationValue = ''
            """, nativeQuery = true)
    List<Object[]> findStatsRawByPlayerIdAndSeason(@Param("playerId") Integer playerId, @Param("season") int season);

    // 선수 포지션 조회 - AVG 기준 포지션 1개
    @Query(value = """
            SELECT position
            FROM player_batter_stats
            WHERE playerId = :playerId AND season = :season AND category = 'AVG'
              AND series = '0' AND situationType = '' AND situationValue = ''
            LIMIT 1
            """, nativeQuery = true)
    String findPositionByPlayerIdAndSeason(@Param("playerId") Integer playerId, @Param("season") int season);

    // 특정 선수의 시즌별 스탯 목록 조회 (정규시즌 전체)
    @Query(value = """
            SELECT * FROM player_batter_stats
            WHERE playerId = :playerId AND season = :season
              AND series = '0' AND situationType = '' AND situationValue = ''
            """, nativeQuery = true)
    List<BatterStats> findByPlayerIdAndSeason(@Param("playerId") int playerId, @Param("season") int season);

    // 해당 시즌에 playerId가 있는지 확인 (정규시즌 전체)
    @Query(value = """
            SELECT COUNT(*) > 0
            FROM player_batter_stats
            WHERE playerId = :playerId AND season = :season
              AND series = '0' AND situationType = '' AND situationValue = ''
            """, nativeQuery = true)
    boolean existsByPlayerIdAndSeason(@Param("playerId") Integer playerId, @Param("season") Integer season);

    // 시즌에 등록된 선수 ID 목록 조회 (정규시즌 전체)
    @Query(value = """
            SELECT DISTINCT playerId FROM player_batter_stats
            WHERE season = :season
              AND series = '0' AND situationType = '' AND situationValue = ''
            """, nativeQuery = true)
    List<Integer> findDistinctPlayerIdsBySeason(@Param("season") int season);

    // 시즌 전체 스탯 조회 (정규시즌 전체)
    @Query(value = """
            SELECT * FROM player_batter_stats
            WHERE season = :season
              AND series = '0' AND situationType = '' AND situationValue = ''
            """, nativeQuery = true)
    List<BatterStats> findBySeason(@Param("season") int season);

    // 팀이름, 로고이름, 포지션만 조회
    @Query(value = """
            SELECT
                bs.position,
                t.name AS teamName,
                t.logoName AS logoName
            FROM player_batter_stats bs
            JOIN player p ON bs.playerId = p.id
            JOIN team t ON p.teamId = t.id
            WHERE bs.playerId = :playerId AND bs.season = :season AND bs.category = 'AVG'
              AND bs.series = '0' AND bs.situationType = '' AND bs.situationValue = ''
            LIMIT 1
            """, nativeQuery = true)
    Optional<Object[]> findTeamAndPosition(@Param("playerId") int playerId, @Param("season") int season);

    // 분석용 타자 전체 조회 (G >= 30 필터, playerId 포함)
    // SB는 player_runner_stats에만 존재하므로 LEFT JOIN
    // 컬럼 인덱스: 0:playerId, 1:playerName, 2:teamName, 3:logoName, 4:teamId,
    //   5:woba, 6:avg, 7:ops, 8:hr, 9:sb(runner_stats), 10:kRate, 11:bbRate, 12:iso,
    //   13:g, 14:pa, 15:risp, 16:phBa, 17:rbi, 18:wrcPlus, 19:obp, 20:slg
    @Query(value = """
            SELECT
                p.id AS playerId,
                p.name AS playerName,
                t.name AS teamName,
                t.logoName AS logoName,
                t.id AS teamId,
                MAX(CASE WHEN b.category = 'wOBA' THEN b.value END) AS woba,
                MAX(CASE WHEN b.category = 'AVG' THEN b.value END) AS avg,
                MAX(CASE WHEN b.category = 'OPS' THEN b.value END) AS ops,
                MAX(CASE WHEN b.category = 'HR' THEN b.value END) AS hr,
                r_sub.sb AS sb,
                MAX(CASE WHEN b.category = 'K%' THEN b.value END) AS kRate,
                MAX(CASE WHEN b.category = 'BB%' THEN b.value END) AS bbRate,
                MAX(CASE WHEN b.category = 'ISO' THEN b.value END) AS iso,
                MAX(CASE WHEN b.category = 'G' THEN b.value END) AS g,
                MAX(CASE WHEN b.category = 'PA' THEN b.value END) AS pa,
                MAX(CASE WHEN b.category = 'RISP' THEN b.value END) AS risp,
                MAX(CASE WHEN b.category = 'PH-BA' THEN b.value END) AS phBa,
                MAX(CASE WHEN b.category = 'RBI' THEN b.value END) AS rbi,
                MAX(CASE WHEN b.category = 'wRC+' THEN b.value END) AS wrcPlus,
                MAX(CASE WHEN b.category = 'OBP' THEN b.value END) AS obp,
                MAX(CASE WHEN b.category = 'SLG' THEN b.value END) AS slg
            FROM player_batter_stats b
            JOIN player p ON b.playerId = p.id
            JOIN team t ON p.teamId = t.id
            LEFT JOIN (
                SELECT playerId, MAX(CASE WHEN category = 'SB' THEN value END) AS sb
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

    // 리그 평균 (카테고리별, Marcel 예측용)
    @Query(value = """
            SELECT b.category, AVG(b.value) AS avgValue
            FROM player_batter_stats b
            WHERE b.season = :season
              AND b.series = '0' AND b.situationType = '' AND b.situationValue = ''
              AND b.category IN ('AVG', 'OPS', 'HR', 'wOBA', 'SB', 'RBI', 'OBP', 'SLG', 'BB%', 'K%', 'ISO', 'wRC+', 'BABIP', 'PA')
            GROUP BY b.category
            """, nativeQuery = true)
    List<Object[]> findLeagueAvgBySeason(@Param("season") int season);

    // 시리즈별 AVG/OPS/HR 평균 비교 (정규시즌 vs 포스트시즌)
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
}
