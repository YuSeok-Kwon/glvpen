package com.kepg.glvpen.modules.game.record.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.kepg.glvpen.modules.game.record.domain.PitcherRecord;

@Repository
public interface PitcherRecordRepository extends JpaRepository<PitcherRecord, Integer> {

	// scheduleId와 teamId의 투수 기록 전체 조회
	List<PitcherRecord> findByScheduleIdAndTeamId(int scheduleId, int teamId);

	// 특정 경기/팀/선수의 투수 기록 존재 여부 확인
	boolean existsByScheduleIdAndTeamIdAndPlayerId(int scheduleId, int teamId, int playerId);

	// 특정 경기/팀/선수의 투수 기록 조회 (saveOrUpdate용)
	Optional<PitcherRecord> findByScheduleIdAndTeamIdAndPlayerId(int scheduleId, int teamId, int playerId);
	
	// 특정 경기의 모든 투수 기록 조회
	List<PitcherRecord> findByScheduleId(int scheduleId);
	
	// 특정 경기/팀의 투수 이름 목록 조회
	@Query(value = """
	    SELECT DISTINCT p.name
	    FROM kbo_pitcher_record pr
	    JOIN player p ON pr.playerId = p.id
	    WHERE pr.scheduleId = :scheduleId AND pr.teamId = :teamId
	""", nativeQuery = true)
	List<String> findPitcherNamesByScheduleIdAndTeamId(@Param("scheduleId") int scheduleId,
	                                                   @Param("teamId") int teamId);

	// 피로도 분석용: 시즌 전체 투수 등판 기록 (날짜 포함)
	@Query(value = """
	    SELECT pr.playerId, p.name AS playerName, t.name AS teamName, t.logoName,
	           pr.innings, pr.earnedRuns, pr.pitchCount, pr.entryType,
	           s.matchDate, pr.strikeouts, pr.hits, pr.bb, pr.hr,
	           t.id AS teamId
	    FROM kbo_pitcher_record pr
	    JOIN kbo_schedule s ON pr.scheduleId = s.id
	    JOIN player p ON pr.playerId = p.id
	    JOIN team t ON pr.teamId = t.id
	    WHERE YEAR(s.matchDate) = :season AND s.status = '종료'
	    ORDER BY pr.playerId, s.matchDate
	""", nativeQuery = true)
	List<Object[]> findAllPitcherAppearancesBySeason(@Param("season") int season);
}
