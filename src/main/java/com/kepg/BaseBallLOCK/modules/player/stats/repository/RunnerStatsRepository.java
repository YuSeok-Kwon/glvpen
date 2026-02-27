package com.kepg.BaseBallLOCK.modules.player.stats.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kepg.BaseBallLOCK.modules.player.stats.domain.RunnerStats;

public interface RunnerStatsRepository extends JpaRepository<RunnerStats, Integer> {

    @Query(value = """
            SELECT * FROM runnerStats
            WHERE playerId = :playerId AND season = :season AND category = :category
              AND series = :series
            LIMIT 1
            """, nativeQuery = true)
    Optional<RunnerStats> findByFullKey(
            @Param("playerId") Integer playerId, @Param("season") Integer season,
            @Param("category") String category, @Param("series") String series);
}
