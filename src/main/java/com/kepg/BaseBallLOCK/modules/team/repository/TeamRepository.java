package com.kepg.BaseBallLOCK.modules.team.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kepg.BaseBallLOCK.modules.team.domain.Team;

public interface TeamRepository extends JpaRepository<Team, Integer>{
	
	// 팀 ID로 팀 컬러 조회
	@Query(value = "SELECT color FROM team WHERE id = :id", nativeQuery = true)
	String findColorById(@Param("id") int id);

	// 팀 ID로 팀 이름 조회
	@Query(value = "SELECT name FROM team WHERE id = :teamId", nativeQuery = true)
	String findTeamNameById(@Param("teamId") Integer teamId);

	// 팀 ID로 팀 로고 이름 조회
	@Query(value = "SELECT logoName FROM team WHERE id = :teamId", nativeQuery = true)
	String findLogoNameById(@Param("teamId") Integer teamId);
}
