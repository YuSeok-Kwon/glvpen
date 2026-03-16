package com.kepg.glvpen.modules.team.teamRanking.repository;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kepg.glvpen.modules.team.teamRanking.domain.TeamRanking;
import com.kepg.glvpen.modules.team.teamStats.domain.TeamStats;

public interface TeamRankingRepository extends JpaRepository<TeamRanking, Integer> {
	
	// 시즌 기준으로 팀 랭킹을 오름차순 정렬하여 조회
    List<TeamRanking> findBySeasonOrderByRankingAsc(int season);
    
    // 시즌과 팀 ID 기준으로 팀 랭킹 1건 조회
    Optional<TeamRanking> findBySeasonAndTeamId(int season, int teamId);
    
    // 시즌별 각 category에서 최고 수치를 기록한 팀 통계 목록 조회
    @Query(value = """
        SELECT ts.*
        FROM team_stats ts
        JOIN (
            SELECT category, MAX(value) AS max_value
            FROM team_stats
            WHERE season = :season
            GROUP BY category
        ) max_stats
        ON ts.category = max_stats.category AND ts.value = max_stats.max_value
        WHERE ts.season = :season
    """, nativeQuery = true)
    List<TeamStats> findTopStatsBySeason(@Param("season") int season);

}
