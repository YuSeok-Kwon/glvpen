package com.kepg.glvpen.modules.player.stats.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kepg.glvpen.modules.player.stats.domain.RunnerStats;

public interface RunnerStatsRepository extends JpaRepository<RunnerStats, Integer> {

    @Query(value = """
            SELECT * FROM player_runner_stats
            WHERE playerId = :playerId AND season = :season AND category = :category
              AND series = :series
            LIMIT 1
            """, nativeQuery = true)
    Optional<RunnerStats> findByFullKey(
            @Param("playerId") Integer playerId, @Param("season") Integer season,
            @Param("category") String category, @Param("series") String series);

    @Query(value = """
            SELECT
                p.name AS playerName,
                t.name AS teamName,
                t.logoName AS logoName,
                MAX(CASE WHEN r.category = 'G' THEN r.value ELSE NULL END) AS g,
                MAX(CASE WHEN r.category = 'SBA' THEN r.value ELSE NULL END) AS sba,
                MAX(CASE WHEN r.category = 'SB' THEN r.value ELSE NULL END) AS sb,
                MAX(CASE WHEN r.category = 'CS' THEN r.value ELSE NULL END) AS cs,
                MAX(CASE WHEN r.category = 'SB%' THEN r.value ELSE NULL END) AS sbPct,
                MAX(CASE WHEN r.category = 'OOB' THEN r.value ELSE NULL END) AS oob,
                MAX(CASE WHEN r.category = 'PKO' THEN r.value ELSE NULL END) AS pko
            FROM player_runner_stats r
            JOIN player p ON r.playerId = p.id
            JOIN team t ON p.teamId = t.id
            WHERE r.season = :season
              AND r.series = '0'
            GROUP BY p.id, t.id
            """, nativeQuery = true)
    List<Object[]> findAllRunners(@Param("season") int season);
}
