package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.domain.PlayerCardOverall;

public interface PlayerCardOverallRepository extends JpaRepository<PlayerCardOverall, Integer> {

    // 특정 시즌의 특정 선수 카드 정보 조회
    Optional<PlayerCardOverall> findByPlayerIdAndSeason(Integer playerId, Integer season);

    // 특정 시즌 전체 카드 조회
    List<PlayerCardOverall> findBySeason(Integer season);

    // 특정 시즌 + 타자/투수 구분 없이 playerId 리스트로 전체 조회 (예: 라인업 화면에서 보유 카드 표시용)
    List<PlayerCardOverall> findByPlayerIdInAndSeason(List<Integer> playerIdList, Integer season);
    
    // 특정 시즌의 투수 카드 전체를 오버롤 순으로 조회
    @Query(value = """
    	    SELECT o.*
    	    FROM playerCardOverall o
    	    WHERE o.type = 'PITCHER' AND o.season = :season
    	    ORDER BY o.overall DESC
    	    """, nativeQuery = true)
    List<PlayerCardOverall> findPitchersBySeason(@Param("season") Integer season);
    
    // 특정 시즌+포지션의 타자 카드 전체를 오버롤 순으로 조회
    @Query(value = """
    	    SELECT o.*
    	    FROM playerCardOverall o
    	    JOIN batterStats b ON o.playerId = b.playerId AND o.season = b.season
    	    WHERE b.position = :position AND o.season = :season
    	    ORDER BY o.overall DESC
    	    """, nativeQuery = true)
    List<PlayerCardOverall> findByBatterPositionAndSeason(@Param("position") String position, @Param("season") Integer season);
    
    // 특정 선수의 시즌 카드 삭제
    @Modifying
    @Query("DELETE FROM PlayerCardOverall p WHERE p.playerId = :playerId AND p.season = :season")
    void deleteByPlayerIdAndSeason(@Param("playerId") Integer playerId, @Param("season") Integer season);
    
    // 카드 타입(PITCHER/BATTER)별 전체 목록 조회
    List<PlayerCardOverall> findByType(String type);
    
    @Query(value = """
    	    SELECT o.*
    	    FROM playerCardOverall o
    	    JOIN batterStats b ON o.playerId = b.playerId AND o.season = b.season
    	    WHERE b.position = :position AND o.season = :season AND o.grade = 'S'
    	    """, nativeQuery = true)
	List<PlayerCardOverall> findSGradeByPositionAndSeason(@Param("position") String position,
	                                                      @Param("season") Integer season);
    
	@Query(value = """
		    SELECT o.*
		    FROM playerCardOverall o
		    JOIN batterStats b ON o.playerId = b.playerId AND o.season = b.season
		    WHERE b.position = :position AND o.season = :season
		    ORDER BY o.overall DESC
		    """, nativeQuery = true)
	List<PlayerCardOverall> findByPositionAndSeason(@Param("position") String position, @Param("season") Integer season);

	
	//특정 시즌+포지션 기준으로 S등급 중 오버롤 높은 카드 조회 (JPQL)
	@Query(value = """
	SELECT o.*
	FROM playerCardOverall o
	JOIN batterStats b ON o.playerId = b.playerId AND o.season = b.season
	WHERE o.grade = 'S'
	  AND b.position = :position
	  AND o.season = :season
	GROUP BY o.playerId
	ORDER BY o.overall DESC
	""", nativeQuery = true)
	List<PlayerCardOverall> findTopSGradeByPositionAndSeason(@Param("position") String position,
	                                                     @Param("season") Integer season);
	
	//오버롤 범위 내 무작위 타자 카드 1개 조회 (포지션 조건 포함)
	@Query(value = """
	    SELECT pco.*
	    FROM playerCardOverall pco
	    JOIN player p ON pco.playerId = p.id
	    JOIN batterStats bs ON pco.playerId = bs.playerId
	    WHERE bs.position = :position
	      AND pco.overall BETWEEN :minOverall AND :maxOverall
	    ORDER BY RAND()
	    LIMIT 1
	    """, nativeQuery = true)
	PlayerCardOverall findRandomByPositionAndOverallRange(
	    @Param("position") String position,
	    @Param("minOverall") double minOverall,
	    @Param("maxOverall") double maxOverall);

	//오버롤 범위 내 무작위 투수 카드 1개 조회
	@Query(value = """
		    SELECT pco.*
		    FROM playerCardOverall pco
		    JOIN player p ON pco.playerId = p.id
		    JOIN pitcherStats ps ON pco.playerId = ps.playerId
		    WHERE pco.overall BETWEEN :minOverall AND :maxOverall
		    ORDER BY RAND()
		    LIMIT 1
		    """, nativeQuery = true)
	PlayerCardOverall findRandomPitcherByOverallRange(
    	    @Param("minOverall") double minOverall,
    	    @Param("maxOverall") double maxOverall);
    
    
}