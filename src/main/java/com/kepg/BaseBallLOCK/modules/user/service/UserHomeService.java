package com.kepg.BaseBallLOCK.modules.user.service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.kepg.BaseBallLOCK.modules.game.schedule.domain.Schedule;
import com.kepg.BaseBallLOCK.modules.game.schedule.service.ScheduleService;
import com.kepg.BaseBallLOCK.modules.game.service.GameService;
import com.kepg.BaseBallLOCK.modules.player.dto.TopPlayerCardView;
import com.kepg.BaseBallLOCK.modules.player.stats.service.BatterStatsService;
import com.kepg.BaseBallLOCK.modules.player.stats.service.PitcherStatsService;
import com.kepg.BaseBallLOCK.modules.team.domain.Team;
import com.kepg.BaseBallLOCK.modules.team.service.TeamService;
import com.kepg.BaseBallLOCK.modules.team.teamRanking.dto.TeamRankingCardView;
import com.kepg.BaseBallLOCK.modules.user.domain.User;
import com.kepg.BaseBallLOCK.modules.user.dto.UserHomeDTO;

import lombok.RequiredArgsConstructor;

/**
 * 사용자 홈 화면 데이터 조회 서비스
 */
@Service
@RequiredArgsConstructor
public class UserHomeService {

    private static final int DEFAULT_TEAM_ID = 999;

    private final TeamService teamService;
    private final ScheduleService scheduleService;
    private final GameService gameService;
    private final BatterStatsService batterStatsService;
    private final PitcherStatsService pitcherStatsService;

    /**
     * 사용자 홈 화면에 필요한 모든 데이터를 조회합니다.
     *
     * @param user 로그인한 사용자
     * @return 홈 화면 데이터 DTO
     */
    public UserHomeDTO getUserHomeData(User user) {
        // NPE 방지: favoriteTeamId가 null인 경우 기본값 사용
        Integer favoriteTeamId = user != null ? user.getFavoriteTeamId() : null;
        int myTeamId = favoriteTeamId != null ? favoriteTeamId : DEFAULT_TEAM_ID;
        int season = LocalDate.now().getYear();

        // 내 팀 정보
        Team myTeam = teamService.getTeamById(myTeamId);

        // 오늘 경기
        Schedule schedule = scheduleService.getTodayScheduleByTeam(myTeamId);

        UserHomeDTO.UserHomeDTOBuilder builder = UserHomeDTO.builder()
                .myTeam(myTeam)
                .schedule(schedule);

        // 오늘 경기가 있는 경우 상대 팀 정보 및 전적 조회
        if (schedule != null) {
            int opponentId = schedule.getHomeTeamId() == myTeamId
                    ? schedule.getAwayTeamId()
                    : schedule.getHomeTeamId();

            Team opponentTeam = teamService.getTeamById(opponentId);

            // 최근 전적
            List<String> myRecentResults = scheduleService.getRecentResults(myTeamId);
            List<String> opponentRecentResults = scheduleService.getRecentResults(opponentId);
            Collections.reverse(opponentRecentResults);

            // 상대 전적
            String myRecord = scheduleService.getHeadToHeadRecord(myTeamId, opponentId);
            String opponentRecord = scheduleService.getHeadToHeadRecord(opponentId, myTeamId);

            builder.opponentTeam(opponentTeam)
                    .myRecentResults(myRecentResults)
                    .opponentRecentResults(opponentRecentResults)
                    .myRecord(myRecord)
                    .opponentRecord(opponentRecord);
        }

        // 주요 선수
        TopPlayerCardView hitter = batterStatsService.getTopHitter(myTeamId, season);
        TopPlayerCardView pitcher = pitcherStatsService.getTopPitcher(myTeamId, season);

        // 팀 순위
        List<TeamRankingCardView> rankingList = gameService.getTeamRankingCardViews(season);

        return builder
                .topHitter(hitter)
                .topPitcher(pitcher)
                .rankingList(rankingList)
                .build();
    }
}
