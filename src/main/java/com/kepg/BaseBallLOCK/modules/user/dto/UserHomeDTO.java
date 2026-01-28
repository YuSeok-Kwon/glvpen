package com.kepg.BaseBallLOCK.modules.user.dto;

import java.util.List;

import com.kepg.BaseBallLOCK.modules.game.schedule.domain.Schedule;
import com.kepg.BaseBallLOCK.modules.player.dto.TopBatterCardView;
import com.kepg.BaseBallLOCK.modules.player.dto.TopPitcherCardView;
import com.kepg.BaseBallLOCK.modules.team.domain.Team;
import com.kepg.BaseBallLOCK.modules.team.teamRanking.dto.TeamRankingCardView;

import lombok.Builder;
import lombok.Getter;

/**
 * 사용자 홈 화면에 필요한 모든 데이터를 담는 DTO
 */
@Getter
@Builder
public class UserHomeDTO {

    // 내 팀 정보
    private Team myTeam;

    // 오늘 경기 일정
    private Schedule schedule;

    // 상대 팀 정보
    private Team opponentTeam;

    // 내 팀 최근 전적
    private List<String> myRecentResults;

    // 상대 팀 최근 전적
    private List<String> opponentRecentResults;

    // 내 팀 vs 상대 팀 전적
    private String myRecord;

    // 상대 팀 vs 내 팀 전적
    private String opponentRecord;

    // 주요 타자
    private TopBatterCardView topHitter;

    // 주요 투수
    private TopPitcherCardView topPitcher;

    // 팀 순위
    private List<TeamRankingCardView> rankingList;
}
