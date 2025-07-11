package com.kepg.BaseBallLOCK.modules.game.highlight.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kepg.BaseBallLOCK.modules.game.highlight.doamin.GameHighlight;

public interface GameHighlightRepository extends JpaRepository<GameHighlight, Integer> {
	
	// 특정 경기(scheduleId)에서 지정된 랭킹 순번(ranking)의 하이라이트 한 개 조회
    GameHighlight findByScheduleIdAndRanking(Integer scheduleId, Integer ranking);
    
    // 특정 경기(scheduleId)의 하이라이트 전체를 랭킹 오름차순으로 정렬해 조회
    List<GameHighlight> findByScheduleIdOrderByRankingAsc(Integer scheduleId);

}