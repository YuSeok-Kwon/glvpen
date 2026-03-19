package com.kepg.glvpen.modules.futures.stats.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kepg.glvpen.modules.futures.stats.domain.FuturesPitcherStats;

public interface FuturesPitcherStatsRepository extends JpaRepository<FuturesPitcherStats, Integer> {

    @Query("SELECT f FROM FuturesPitcherStats f WHERE f.playerId = :playerId AND f.season = :season AND f.category = :category AND f.league = :league")
    Optional<FuturesPitcherStats> findByFullKey(
            @Param("playerId") int playerId,
            @Param("season") int season,
            @Param("category") String category,
            @Param("league") String league);

    // 특정 시즌의 퓨처스 투수 스탯 전체 조회
    @Query(value = "SELECT * FROM futures_pitcher_stats WHERE season = :season", nativeQuery = true)
    List<FuturesPitcherStats> findBySeason(@Param("season") int season);
}
