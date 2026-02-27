package com.kepg.BaseBallLOCK.modules.player.stats.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kepg.BaseBallLOCK.modules.player.stats.domain.DefenseStats;

public interface DefenseStatsRepository extends JpaRepository<DefenseStats, Integer> {

    @Query(value = """
            SELECT * FROM defenseStats
            WHERE playerId = :playerId AND season = :season AND category = :category
              AND series = :series AND position = :position
            LIMIT 1
            """, nativeQuery = true)
    Optional<DefenseStats> findByFullKey(
            @Param("playerId") Integer playerId, @Param("season") Integer season,
            @Param("category") String category, @Param("series") String series,
            @Param("position") String position);
}
