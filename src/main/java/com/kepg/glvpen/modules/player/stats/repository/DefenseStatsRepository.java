package com.kepg.glvpen.modules.player.stats.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kepg.glvpen.modules.player.stats.domain.DefenseStats;

public interface DefenseStatsRepository extends JpaRepository<DefenseStats, Integer> {

    @Query(value = """
            SELECT * FROM player_defense_stats
            WHERE playerId = :playerId AND season = :season AND category = :category
              AND series = :series AND position = :position
            LIMIT 1
            """, nativeQuery = true)
    Optional<DefenseStats> findByFullKey(
            @Param("playerId") Integer playerId, @Param("season") Integer season,
            @Param("category") String category, @Param("series") String series,
            @Param("position") String position);

    @Query(value = """
            SELECT position FROM player_defense_stats
            WHERE playerId = :playerId AND season = :season
              AND category = 'G' AND series = '0'
            ORDER BY value DESC LIMIT 1
            """, nativeQuery = true)
    Optional<String> findPrimaryPositionByPlayerIdAndSeason(
            @Param("playerId") Integer playerId, @Param("season") int season);

    @Query(value = """
            SELECT
                p.name AS playerName,
                t.name AS teamName,
                t.logoName AS logoName,
                d.position AS position,
                MAX(CASE WHEN d.category = 'G' THEN d.value ELSE NULL END) AS g,
                MAX(CASE WHEN d.category = 'GS' THEN d.value ELSE NULL END) AS gs,
                MAX(CASE WHEN d.category = 'IP' THEN d.value ELSE NULL END) AS ip,
                MAX(CASE WHEN d.category = 'E' THEN d.value ELSE NULL END) AS e,
                MAX(CASE WHEN d.category = 'PKO' THEN d.value ELSE NULL END) AS pko,
                MAX(CASE WHEN d.category = 'PO' THEN d.value ELSE NULL END) AS po,
                MAX(CASE WHEN d.category = 'A' THEN d.value ELSE NULL END) AS a,
                MAX(CASE WHEN d.category = 'DP' THEN d.value ELSE NULL END) AS dp,
                MAX(CASE WHEN d.category = 'FPCT' THEN d.value ELSE NULL END) AS fpct,
                MAX(CASE WHEN d.category = 'PB' THEN d.value ELSE NULL END) AS pb,
                MAX(CASE WHEN d.category = 'SB' THEN d.value ELSE NULL END) AS sb,
                MAX(CASE WHEN d.category = 'CS' THEN d.value ELSE NULL END) AS cs,
                MAX(CASE WHEN d.category = 'CS%' THEN d.value ELSE NULL END) AS csPct
            FROM player_defense_stats d
            JOIN player p ON d.playerId = p.id
            JOIN team t ON p.teamId = t.id
            WHERE d.season = :season
              AND d.series = '0'
            GROUP BY p.id, t.id, d.position
            """, nativeQuery = true)
    List<Object[]> findAllDefensePlayers(@Param("season") int season);
}
