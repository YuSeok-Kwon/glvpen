package com.kepg.glvpen.modules.crowd.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kepg.glvpen.modules.crowd.domain.CrowdStats;

public interface CrowdStatsRepository extends JpaRepository<CrowdStats, Integer> {

    Optional<CrowdStats> findByGameDateAndHomeTeamIdAndAwayTeamId(LocalDate gameDate, int homeTeamId, int awayTeamId);

    List<CrowdStats> findBySeasonOrderByGameDateAsc(int season);

    List<CrowdStats> findBySeasonAndHomeTeamId(int season, int homeTeamId);
}
