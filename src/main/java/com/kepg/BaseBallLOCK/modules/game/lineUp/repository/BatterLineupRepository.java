package com.kepg.BaseBallLOCK.modules.game.lineUp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.kepg.BaseBallLOCK.modules.game.lineUp.domain.BatterLineup;

@Repository
public interface BatterLineupRepository extends JpaRepository<BatterLineup, Integer> {
	
	// scheduleId와 teamId의 라인업 목록 조회
	List<BatterLineup> findByScheduleIdAndTeamId(int scheduleId, int teamId);
	
	// 해당 경기, 팀, 선수 조합의 라인업 존재 여부 확인
	boolean existsByScheduleIdAndTeamIdAndPlayerId(int scheduleId, int teamId, int playerId);
	
	// 해당 경기, 팀의 라인업에 있는 선수 이름만 중복 없이 조회
	@Query(value = """
		    SELECT DISTINCT p.name 
		    FROM batterLineup bl
		    JOIN player p ON bl.playerId = p.id
		    WHERE bl.scheduleId = :scheduleId AND bl.teamId = :teamId
		""", nativeQuery = true)
	List<String> findBatterNamesByScheduleIdAndTeamId(@Param("scheduleId") int scheduleId,
		                                                  @Param("teamId") int teamId);
}