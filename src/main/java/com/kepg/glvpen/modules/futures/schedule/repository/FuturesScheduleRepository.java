package com.kepg.glvpen.modules.futures.schedule.repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kepg.glvpen.modules.futures.schedule.domain.FuturesSchedule;

public interface FuturesScheduleRepository extends JpaRepository<FuturesSchedule, Integer> {

    Optional<FuturesSchedule> findByMatchDateAndHomeTeamIdAndAwayTeamId(
            Timestamp matchDate, Integer homeTeamId, Integer awayTeamId);

    List<FuturesSchedule> findByMatchDateBetweenOrderByMatchDateAsc(
            Timestamp start, Timestamp end);
}
