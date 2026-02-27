package com.kepg.BaseBallLOCK.modules.player.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kepg.BaseBallLOCK.modules.player.domain.Player;

public interface PlayerRepository extends JpaRepository<Player, Integer> {

    // 팀별 최고 WAR 타자 조회 (포지션이 투수가 아닌 경우)
    @Query(value = """
            SELECT p.name, s.position, s.value AS war, s.ranking, p.id
            FROM batterStats s
            JOIN player p ON s.playerId = p.id
            WHERE s.category = 'WAR'
              AND s.season = :season
              AND p.teamId = :teamId
              AND s.position != 'P'
              AND s.series = '0' AND s.situationType = '' AND s.situationValue = ''
            ORDER BY s.value DESC
            LIMIT 1
            """, nativeQuery = true)
    List<Object[]> findTopHitterByTeamIdAndSeason(@Param("teamId") int teamId, @Param("season") int season);

    // 팀별 최고 WAR 투수 조회 (포지션이 투수인 경우)
    @Query(value = """
            SELECT p.name, s.position, s.value AS war, s.ranking, p.id
            FROM pitcherStats s
            JOIN player p ON s.playerId = p.id
            WHERE s.category = 'WAR'
              AND s.season = :season
              AND p.teamId = :teamId
              AND s.position = 'P'
              AND s.series = '0' AND s.situationType = '' AND s.situationValue = ''
            ORDER BY s.value DESC
            LIMIT 1
            """, nativeQuery = true)
    List<Object[]> findTopPitcherByTeamIdAndSeason(@Param("teamId") int teamId, @Param("season") int season);

    // 특정 선수의 시즌/카테고리별 스탯 값 조회 (ex. WAR, AVG 등)
    @Query(value = """
            SELECT value
            FROM batterStats
            WHERE playerId = :playerId
              AND category = :category
              AND season = :season
              AND series = '0' AND situationType = '' AND situationValue = ''
            """, nativeQuery = true)
    Optional<String> findStatValueByPlayerIdCategoryAndSeason(
            @Param("playerId") int playerId, @Param("category") String category, @Param("season") int season);

    // 이름 + 팀 ID로 선수 정보 조회
    Optional<Player> findByNameAndTeamId(String name, int teamId);

    // 포지션별 최고 WAR 타자 리스트 조회 (페이징 포함)
    @Query(value = """
            SELECT p.*
            FROM player p
            JOIN batterStats b ON p.id = b.playerId
            WHERE b.position = :position
              AND b.season = :season
              AND b.category = 'WAR'
              AND b.series = '0' AND b.situationType = '' AND b.situationValue = ''
            ORDER BY b.value DESC
            """, nativeQuery = true)
    List<Player> findTopBattersByPositionAndSeason(
            @Param("position") String position,
            @Param("season") int season,
            Pageable pageable);

    // 포지션별 최고 WAR 투수 리스트 조회 (페이징 포함)
    @Query(value = """
            SELECT p.*
            FROM player p
            JOIN pitcherStats ps ON p.id = ps.playerId
            WHERE ps.position = :position
              AND ps.season = :season
              AND ps.category = 'WAR'
              AND ps.series = '0' AND ps.situationType = '' AND ps.situationValue = ''
            ORDER BY ps.value DESC
            """, nativeQuery = true)
    List<Player> findTopPitchersByPositionAndSeason(
            @Param("position") String position,
            @Param("season") int season,
            Pageable pageable);

    // 해당 시즌에 WAR 기록이 있는 타자 수 조회
    @Query(value = """
            SELECT COUNT(DISTINCT playerId)
            FROM batterStats
            WHERE season = :season
              AND category = 'WAR'
              AND series = '0' AND situationType = '' AND situationValue = ''
            """, nativeQuery = true)
    int countBattersInSeason(@Param("season") int season);

    // 해당 시즌에 WAR 기록이 있는 투수 수 조회
    @Query(value = """
            SELECT COUNT(DISTINCT playerId)
            FROM pitcherStats
            WHERE season = :season
              AND category = 'WAR'
              AND series = '0' AND situationType = '' AND situationValue = ''
            """, nativeQuery = true)
    int countPitchersInSeason(@Param("season") int season);
}
