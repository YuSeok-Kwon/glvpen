package com.kepg.glvpen.modules.team.teamHeadToHead.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kepg.glvpen.modules.team.teamHeadToHead.domain.TeamHeadToHead;

public interface TeamHeadToHeadRepository extends JpaRepository<TeamHeadToHead, Integer> {

    // 크롤러 saveOrUpdate용: series 포함
    Optional<TeamHeadToHead> findBySeasonAndTeamIdAndOpponentTeamIdAndSeries(int season, int teamId, int opponentTeamId, String series);

    // 화면 조회용: series 포함
    List<TeamHeadToHead> findBySeasonAndTeamIdAndSeries(int season, int teamId, String series);

    List<TeamHeadToHead> findBySeasonAndSeriesOrderByTeamIdAscOpponentTeamIdAsc(int season, String series);
}
