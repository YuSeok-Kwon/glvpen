package com.kepg.glvpen.modules.team.teamHeadToHead.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kepg.glvpen.modules.team.teamHeadToHead.domain.TeamHeadToHead;

public interface TeamHeadToHeadRepository extends JpaRepository<TeamHeadToHead, Integer> {

    Optional<TeamHeadToHead> findBySeasonAndTeamIdAndOpponentTeamId(int season, int teamId, int opponentTeamId);

    List<TeamHeadToHead> findBySeasonAndTeamId(int season, int teamId);

    List<TeamHeadToHead> findBySeasonOrderByTeamIdAscOpponentTeamIdAsc(int season);
}
