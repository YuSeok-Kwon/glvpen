package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.event.answer.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.event.answer.domain.GameEventAnswer;

public interface GameEventAnswerRepository extends JpaRepository<GameEventAnswer, Integer> {
	
	@Query(value = "SELECT * FROM gameEventAnswer WHERE scheduleId = :scheduleId", nativeQuery = true)
	List<GameEventAnswer> findByScheduleId(@Param("scheduleId") int scheduleId);
	
	@Query(value = "SELECT * FROM gameEventAnswer WHERE scheduleId = :scheduleId AND userId = :userId", nativeQuery = true)
	List<GameEventAnswer> findByScheduleIdAndUserId(@Param("scheduleId") int scheduleId, @Param("userId") int userId);
}
