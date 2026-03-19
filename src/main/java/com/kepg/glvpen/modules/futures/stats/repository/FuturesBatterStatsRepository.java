package com.kepg.glvpen.modules.futures.stats.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kepg.glvpen.modules.futures.stats.domain.FuturesBatterStats;

public interface FuturesBatterStatsRepository extends JpaRepository<FuturesBatterStats, Integer> {

    @Query("SELECT f FROM FuturesBatterStats f WHERE f.playerId = :playerId AND f.season = :season AND f.category = :category AND f.league = :league")
    Optional<FuturesBatterStats> findByFullKey(
            @Param("playerId") int playerId,
            @Param("season") int season,
            @Param("category") String category,
            @Param("league") String league);

    // 특정 시즌의 퓨처스 타자 스탯 전체 조회
    @Query(value = "SELECT * FROM futures_batter_stats WHERE season = :season", nativeQuery = true)
    List<FuturesBatterStats> findBySeason(@Param("season") int season);
}
