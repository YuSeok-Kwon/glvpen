package com.kepg.glvpen.modules.team.teamHeadToHead.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.glvpen.modules.team.teamHeadToHead.domain.TeamHeadToHead;
import com.kepg.glvpen.modules.team.teamHeadToHead.dto.TeamHeadToHeadDTO;
import com.kepg.glvpen.modules.team.teamHeadToHead.repository.TeamHeadToHeadRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TeamHeadToHeadService {

    private final TeamHeadToHeadRepository teamHeadToHeadRepository;

    @Transactional
    public void saveOrUpdate(TeamHeadToHeadDTO dto) {
        Optional<TeamHeadToHead> optional = teamHeadToHeadRepository
                .findBySeasonAndTeamIdAndOpponentTeamId(dto.getSeason(), dto.getTeamId(), dto.getOpponentTeamId());

        TeamHeadToHead entity;
        if (optional.isPresent()) {
            entity = optional.get();
            entity.setWins(dto.getWins());
            entity.setLosses(dto.getLosses());
            entity.setDraws(dto.getDraws());
        } else {
            entity = TeamHeadToHead.builder()
                    .season(dto.getSeason())
                    .teamId(dto.getTeamId())
                    .opponentTeamId(dto.getOpponentTeamId())
                    .wins(dto.getWins())
                    .losses(dto.getLosses())
                    .draws(dto.getDraws())
                    .build();
        }

        teamHeadToHeadRepository.save(entity);
    }

    public List<TeamHeadToHead> getBySeasonAndTeam(int season, int teamId) {
        return teamHeadToHeadRepository.findBySeasonAndTeamId(season, teamId);
    }

    public List<TeamHeadToHead> getBySeason(int season) {
        return teamHeadToHeadRepository.findBySeasonOrderByTeamIdAscOpponentTeamIdAsc(season);
    }
}
