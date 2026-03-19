package com.kepg.glvpen.modules.futures.stats.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kepg.glvpen.modules.futures.stats.domain.FuturesTeamStats;

public interface FuturesTeamStatsRepository extends JpaRepository<FuturesTeamStats, Integer> {

    @Query("SELECT f FROM FuturesTeamStats f WHERE f.teamId = :teamId AND f.season = :season AND f.category = :category AND f.league = :league AND f.statType = :statType")
    Optional<FuturesTeamStats> findByFullKey(
            @Param("teamId") int teamId,
            @Param("season") int season,
            @Param("category") String category,
            @Param("league") String league,
            @Param("statType") String statType);
}
