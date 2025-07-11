package com.kepg.BaseBallLOCK.modules.game.record.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.kepg.BaseBallLOCK.modules.game.record.domain.PitcherRecord;

@Repository
public interface PitcherRecordRepository extends JpaRepository<PitcherRecord, Integer> {
	
	// scheduleId와 teamId의 투수 기록 전체 조회
	List<PitcherRecord> findByScheduleIdAndTeamId(int scheduleId, int teamId);
	
	// 특정 경기/팀/선수의 투수 기록 존재 여부 확인
	boolean existsByScheduleIdAndTeamIdAndPlayerId(int scheduleId, int teamId, int playerId);
	
	// 특정 경기의 모든 투수 기록 조회
	List<PitcherRecord> findByScheduleId(int scheduleId);
	
	// 특정 경기/팀의 투수 이름 목록 조회
	@Query(value = """
	    SELECT DISTINCT p.name
	    FROM pitcherRecord pr
	    JOIN player p ON pr.playerId = p.id
	    WHERE pr.scheduleId = :scheduleId AND pr.teamId = :teamId
	""", nativeQuery = true)
	List<String> findPitcherNamesByScheduleIdAndTeamId(@Param("scheduleId") int scheduleId,
	                                                   @Param("teamId") int teamId);
}
