package com.kepg.glvpen.modules.game.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.kepg.glvpen.modules.team.domain.Team;
import com.kepg.glvpen.modules.team.service.TeamService;
import com.kepg.glvpen.modules.team.teamRanking.domain.TeamRanking;
import com.kepg.glvpen.modules.team.teamRanking.dto.TeamRankingCardView;
import com.kepg.glvpen.modules.team.teamRanking.service.TeamRankingService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GameService {

    private final TeamRankingService teamRankingService;
    private final TeamService teamService;

    // 시즌별 팀 순위(teamRanking)와 팀 정보(team)를 합쳐 카드 뷰 리스트로 반환
    public List<TeamRankingCardView> getTeamRankingCardViews(int season) {
        List<TeamRanking> rankings = teamRankingService.getTeamRankings(season);
        List<TeamRankingCardView> cardViewList = new ArrayList<>();

        for (TeamRanking ranking : rankings) {
            Team team = teamService.getTeamById(ranking.getTeamId());
            if (team == null) continue;

            TeamRankingCardView cardView = TeamRankingCardView.builder()
                    .ranking(ranking.getRanking())
                    .games(ranking.getGames())
                    .wins(ranking.getWins())
                    .losses(ranking.getLosses())
                    .draws(ranking.getDraws())
                    .winRate(ranking.getWinRate())
                    .gamesBehind(ranking.getGamesBehind())
                    .teamName(team.getName())
                    .logoName(team.getLogoName())
                    .build();

            cardViewList.add(cardView);
        }

        return cardViewList;
    }

}
