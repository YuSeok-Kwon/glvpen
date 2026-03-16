package com.kepg.glvpen.modules.game.record.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kepg.glvpen.modules.game.record.domain.BatterRecord;

public interface BatterRecordRepository extends JpaRepository<BatterRecord, Integer> {

	// scheduleId와 teamId의 타자 기록 전체 조회
	List<BatterRecord> findByScheduleIdAndTeamId(int scheduleId, int teamId);

	// scheduleId의 타자 기록 전체 조회
	List<BatterRecord> findByScheduleId(int scheduleId);

	// 특정 경기/팀/선수의 기록 존재 여부 확인
	boolean existsByScheduleIdAndTeamIdAndPlayerId(int scheduleId, int teamId, int playerId);

	// 특정 경기/팀/선수의 기록 조회 (saveOrUpdate용)
	Optional<BatterRecord> findByScheduleIdAndTeamIdAndPlayerId(int scheduleId, int teamId, int playerId);
}

