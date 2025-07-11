package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.result.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.result.domain.GameResult;

public interface GameResultRepository extends JpaRepository<GameResult, Integer> {

	@Query(value = "SELECT * FROM `gameResult` WHERE scheduleId = :scheduleId AND userId = :userId", nativeQuery = true)
	GameResult findByScheduleIdAndUserId(@Param("scheduleId") int scheduleId, @Param("userId") int userId);
}
