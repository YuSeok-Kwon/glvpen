package com.kepg.BaseBallLOCK.modules.team.teamStats.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kepg.BaseBallLOCK.modules.team.teamStats.domain.TeamStats;
import com.kepg.BaseBallLOCK.modules.team.teamStats.dto.TeamStatRankingInterface;
import com.kepg.BaseBallLOCK.modules.team.teamStats.dto.TopStatTeamInterface;

public interface TeamStatsRepository extends JpaRepository<TeamStats, Integer> {
    
	// 특정 팀의 시즌별 카테고리 통계 조회
	Optional<TeamStats> findByTeamIdAndSeasonAndCategory(Integer teamId, Integer season, String category);
    
	// 특정 시즌과 카테고리에 대해 가장 높은 값을 기록한 팀 정보 조회 (내림차순)
    @Query(value = """
	    SELECT 
	        ts.category AS category,
	        ts.value AS value,
	        t.id AS teamId,
	        t.name AS teamName,
	        t.logoName AS teamLogo
	    FROM teamStats ts
	    JOIN team t ON ts.teamId = t.id
	    WHERE ts.season = :season AND ts.category = :category
	    ORDER BY ts.value DESC
	    LIMIT 1
	""", nativeQuery = true)
	TopStatTeamInterface findTopByCategoryAndSeasonMax(@Param("season") int season, @Param("category") String category);

    // 특정 시즌과 카테고리에 대해 가장 낮은 값을 기록한 팀 정보 조회 (오름차순)
	@Query(value = """
	    SELECT 
	        ts.category AS category,
	        ts.value AS value,
	        t.id AS teamId,
	        t.name AS teamName,
	        t.logoName AS teamLogo
	    FROM teamStats ts
	    JOIN team t ON ts.teamId = t.id
	    WHERE ts.season = :season AND ts.category = :category
	    ORDER BY ts.value ASC
	    LIMIT 1
	""", nativeQuery = true)
	TopStatTeamInterface findTopByCategoryAndSeasonMin(@Param("season") int season, @Param("category") String category);
    	
	// 모든 팀의 시즌별 통계 데이터를 카테고리별로 하나의 레코드로 변환하여 조회 (TeamStatRankingDTO용)
	@Query(value = """
	    SELECT 
	      t.id AS teamId,
	      t.name AS teamName,
	      t.logoName AS logoName,
	      MAX(CASE WHEN ts.category = 'OPS' THEN ts.value END) AS ops,
	      MAX(CASE WHEN ts.category = 'AVG' THEN ts.value END) AS avg,
	      MAX(CASE WHEN ts.category = 'HR' THEN ts.value END) AS hr,
	      MAX(CASE WHEN ts.category = 'SB' THEN ts.value END) AS sb,
	      MAX(CASE WHEN ts.category = 'BetterWAR' THEN ts.value END) AS betterWar,
	      MAX(CASE WHEN ts.category = 'PitcherWAR' THEN ts.value END) AS pitcherWar,
	      MAX(CASE WHEN ts.category = 'SO' THEN ts.value END) AS so,
	      MAX(CASE WHEN ts.category = 'W' THEN ts.value END) AS w,
	      MAX(CASE WHEN ts.category = 'H' THEN ts.value END) AS h,
	      MAX(CASE WHEN ts.category = 'SV' THEN ts.value END) AS sv,
	      MAX(CASE WHEN ts.category = 'ERA' THEN ts.value END) AS era,
	      MAX(CASE WHEN ts.category = 'WHIP' THEN ts.value END) AS whip,
	      MAX(CASE WHEN ts.category = 'BB' THEN ts.value END) AS bb,
	      MAX(CASE WHEN ts.category = '타격' THEN ts.value END) AS battingWaa,
	      MAX(CASE WHEN ts.category = '주루' THEN ts.value END) AS baserunningWaa,
	      MAX(CASE WHEN ts.category = '수비' THEN ts.value END) AS defenseWaa,
	      MAX(CASE WHEN ts.category = '선발' THEN ts.value END) AS startingWaa,
	      MAX(CASE WHEN ts.category = '불펜' THEN ts.value END) AS bullpenWaa
	    FROM teamStats ts
	    JOIN team t ON ts.teamId = t.id
	    WHERE ts.season = :season
	    GROUP BY t.id, t.name, t.logoName
	""", nativeQuery = true)
	List<TeamStatRankingInterface> findAllTeamStats(@Param("season") int season);
}
