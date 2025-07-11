package com.kepg.BaseBallLOCK.modules.player.stats.repository;

import java.util.List;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kepg.BaseBallLOCK.modules.player.stats.domain.BatterStats;

public interface BatterStatsRepository extends JpaRepository<BatterStats, Integer> {

    Optional<BatterStats> findByPlayerIdAndSeasonAndCategory(Integer playerId, Integer season, String category);
    
    @Query("SELECT s.value FROM BatterStats s WHERE s.playerId = :playerId AND s.category = :category AND s.season = :season")
    Optional<String> findStatValueByPlayerIdCategoryAndSeason(@Param("playerId") int playerId, @Param("category") String category, @Param("season") int season);

	// 포지션별 WAR 1위 타자 조회
    @Query(value = """
    	    SELECT
    	        ranked.position,
    	        ranked.playerName,
    	        ranked.teamName,
    	        ranked.logoName,
    	        ranked.war
    	    FROM (
    	        SELECT
    	            bs.position AS position,
    	            p.name AS playerName,
    	            t.name AS teamName,
    	            t.logoName AS logoName,
    	            COALESCE(bs.value, 0) AS war,
    	            @rank := IF(@prev_pos = bs.position, @rank + 1, 1) AS row_num,
    	            @prev_pos := bs.position
    	        FROM batterStats bs
    	        JOIN player p ON bs.playerId = p.id
    	        JOIN team t ON p.teamId = t.id
    	        CROSS JOIN (SELECT @rank := 0, @prev_pos := NULL) vars
    	        WHERE bs.season = :season
    	          AND bs.category = 'WAR'
    	        ORDER BY bs.position, COALESCE(bs.value, 0) DESC
    	    ) AS ranked
    	    WHERE ranked.row_num = 1
    	    ORDER BY ranked.position
    	    """, nativeQuery = true)
    	List<Object[]> findTopBattersByPosition(@Param("season") int season);

	// 시즌별 전체 타자 스탯 요약
	@Query(value = """
	    SELECT 
	        b.position AS position,
	        p.name AS playerName,
	        t.name AS teamName,
	        t.logoName AS logoName,
	        MAX(CASE WHEN b.category = 'WAR' THEN b.value ELSE NULL END) AS war,
	        MAX(CASE WHEN b.category = 'AVG' THEN b.value ELSE NULL END) AS avg,
	        MAX(CASE WHEN b.category = 'OPS' THEN b.value ELSE NULL END) AS ops,
	        MAX(CASE WHEN b.category = 'HR' THEN b.value ELSE NULL END) AS hr,
	        MAX(CASE WHEN b.category = 'SB' THEN b.value ELSE NULL END) AS sb,
	        MAX(CASE WHEN b.category = 'wRC+' THEN b.value ELSE NULL END) AS wrcPlus,
	        MAX(CASE WHEN b.category = 'G' THEN b.value ELSE NULL END) AS g,
	        MAX(CASE WHEN b.category = 'PA' THEN b.value ELSE NULL END) AS pa,
	        MAX(CASE WHEN b.category = 'H' THEN b.value ELSE NULL END) AS h,
	        MAX(CASE WHEN b.category = 'RBI' THEN b.value ELSE NULL END) AS rbi,
	        MAX(CASE WHEN b.category = 'BB' THEN b.value ELSE NULL END) AS bb,
	        MAX(CASE WHEN b.category = 'SO' THEN b.value ELSE NULL END) AS so,
	        MAX(CASE WHEN b.category = '2B' THEN b.value ELSE NULL END) AS twoB,
	        MAX(CASE WHEN b.category = '3B' THEN b.value ELSE NULL END) AS threeB,
	        MAX(CASE WHEN b.category = 'OBP' THEN b.value ELSE NULL END) AS obp,
	        MAX(CASE WHEN b.category = 'SLG' THEN b.value ELSE NULL END) AS slg,
	        t.id AS teamId
	    FROM batterStats b
	    JOIN player p ON b.playerId = p.id
	    JOIN team t ON p.teamId = t.id
	    WHERE b.season = :season
	    GROUP BY p.name, t.name, t.logoName, p.id, b.position, t.id
	    """, nativeQuery = true)
	List<Object[]> findAllBatters(@Param("season") int season);

	// 선수 ID, 시즌 기준 스탯 전체 조회
	@Query(value = """
		    SELECT category, value
		    FROM batterStats
		    WHERE playerId = :playerId AND season = :season
		    """, nativeQuery = true)
	List<Object[]> findStatsRawByPlayerIdAndSeason(@Param("playerId") Integer playerId, @Param("season") int season);

	// 선수 포지션 조회 - WAR 기준 포지션 1개
	@Query(value = """
		    SELECT position
		    FROM batterStats
		    WHERE playerId = :playerId AND season = :season AND category = 'WAR'
		    LIMIT 1
		    """, nativeQuery = true)
	String findPositionByPlayerIdAndSeason(@Param("playerId") Integer playerId, @Param("season") int season);


	// 특정 선수의 시즌별 스탯 목록 조회
	List<BatterStats> findByPlayerIdAndSeason(int id, int season);
	
	// 해당 시즌에 playerId가 있는지 확인
	boolean existsByPlayerIdAndSeason(Integer playerId, Integer season);
	
	// 시즌에 등록된 선수 ID 목록 조회
	@Query(value = "SELECT DISTINCT playerId FROM batterStats WHERE season = :season", nativeQuery = true)
	List<Integer> findDistinctPlayerIdsBySeason(@Param("season") int season);
	
	// 시즌 전체 스탯 조회
	@Query(value = "SELECT * FROM batterStats WHERE season = :season", nativeQuery = true)
	List<BatterStats> findBySeason(@Param("season") int season);
	
	// 팀이름,로고이름, 포지션만 조회
	@Query(value = """
		    SELECT 
		        bs.position,
		        t.name AS teamName,
		        t.logoName AS logoName
		    FROM batterStats bs
		    JOIN player p ON bs.playerId = p.id
		    JOIN team t ON p.teamId = t.id
		    WHERE bs.playerId = :playerId AND bs.season = :season AND bs.category = 'WAR'
		    LIMIT 1
		""", nativeQuery = true)
		Optional<Object[]> findTeamAndPosition(@Param("playerId") int playerId, @Param("season") int season);
}
