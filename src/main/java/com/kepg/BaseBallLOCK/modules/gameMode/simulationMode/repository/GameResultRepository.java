package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.domain.GameResult;

public interface GameResultRepository extends JpaRepository<GameResult, Integer> {

	/**
	 * 스케줄 ID와 유저 ID로 게임 결과 조회
	 * Spring Data JPA 메소드 명명 규칙 사용으로 Native Query 제거
	 */
	GameResult findByScheduleIdAndUserId(Integer scheduleId, Integer userId);
}
