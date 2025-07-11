package com.kepg.BaseBallLOCK.modules.game.scoreBoard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.kepg.BaseBallLOCK.modules.game.scoreBoard.domain.ScoreBoard;

@Repository
public interface ScoreBoardRepository extends JpaRepository<ScoreBoard, Integer> {

	// scheduleId에 해당하는 스코어보드 정보를 조회
    ScoreBoard findByScheduleId(Integer scheduleId);

    // scheduleId의 스코어보드 데이터가 존재하는지 여부 확인
    boolean existsByScheduleId(Integer scheduleId);
}
