package com.kepg.BaseBallLOCK.modules.team.teamRanking.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.BaseBallLOCK.modules.team.teamRanking.domain.TeamRanking;
import com.kepg.BaseBallLOCK.modules.team.teamRanking.dto.TeamRankingDTO;
import com.kepg.BaseBallLOCK.modules.team.teamRanking.repository.TeamRankingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TeamRankingService {

    private final TeamRankingRepository teamRankingRepository;
    
    // 팀 랭킹을 저장하거나, 기존 데이터가 존재하면 업데이트
    @Transactional
    public void saveOrUpdate(TeamRankingDTO dto) {
        Optional<TeamRanking> optional = teamRankingRepository.findBySeasonAndTeamId(dto.getSeason(), dto.getTeamId());

        TeamRanking entity;
        if (optional.isPresent()) {
            entity = optional.get();
            entity.setRanking(dto.getRanking());
            entity.setGames(dto.getGames());
            entity.setWins(dto.getWins());
            entity.setDraws(dto.getDraws());
            entity.setLosses(dto.getLosses());
            entity.setGamesBehind(dto.getGamesBehind());
            entity.setWinRate(dto.getWinRate());
        } else {
            entity = TeamRanking.builder()
                    .season(dto.getSeason())
                    .teamId(dto.getTeamId())
                    .ranking(dto.getRanking())
                    .games(dto.getGames())
                    .wins(dto.getWins())
                    .draws(dto.getDraws())
                    .losses(dto.getLosses())
                    .gamesBehind(dto.getGamesBehind())
                    .winRate(dto.getWinRate())
                    .build();
        }

        teamRankingRepository.save(entity);
    }
    
    // 특정 시즌의 팀 랭킹 목록을 랭킹 기준 오름차순으로 조회
    public List<TeamRanking> getTeamRankings(int season) {
        return teamRankingRepository.findBySeasonOrderByRankingAsc(season);
    }
}
