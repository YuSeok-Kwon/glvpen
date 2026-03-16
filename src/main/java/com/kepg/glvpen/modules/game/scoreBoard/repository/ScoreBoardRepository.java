package com.kepg.glvpen.modules.game.scoreBoard.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.kepg.glvpen.modules.game.scoreBoard.domain.ScoreBoard;

@Repository
public interface ScoreBoardRepository extends JpaRepository<ScoreBoard, Integer> {

	// scheduleId에 해당하는 스코어보드 정보를 조회
    ScoreBoard findByScheduleId(Integer scheduleId);

    // scheduleId의 스코어보드 데이터가 존재하는지 여부 확인
    boolean existsByScheduleId(Integer scheduleId);

    // 특정 시즌의 종료된 경기 스코어보드 전체 조회
    @Query(value = """
        SELECT sb.* FROM kbo_score_board sb
        JOIN kbo_schedule s ON sb.scheduleId = s.id
        WHERE YEAR(s.matchDate) = :season AND s.status = '종료'
        """, nativeQuery = true)
    List<ScoreBoard> findAllBySeasonFinished(@Param("season") int season);
}
