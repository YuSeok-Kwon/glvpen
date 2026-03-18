package com.kepg.glvpen.modules.user.service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.kepg.glvpen.common.validator.SeasonValidator;
import com.kepg.glvpen.modules.analysis.domain.AnalysisColumn;
import com.kepg.glvpen.modules.analysis.repository.AnalysisColumnRepository;
import com.kepg.glvpen.modules.game.schedule.domain.Schedule;
import com.kepg.glvpen.modules.game.schedule.dto.ScheduleCardView;
import com.kepg.glvpen.modules.game.schedule.service.ScheduleService;
import com.kepg.glvpen.modules.game.service.GameService;
import com.kepg.glvpen.modules.player.dto.TopBatterCardView;
import com.kepg.glvpen.modules.player.dto.TopPitcherCardView;
import com.kepg.glvpen.modules.player.stats.service.BatterStatsService;
import com.kepg.glvpen.modules.player.stats.service.PitcherStatsService;
import com.kepg.glvpen.modules.team.domain.Team;
import com.kepg.glvpen.modules.team.service.TeamService;
import com.kepg.glvpen.modules.team.teamRanking.dto.TeamRankingCardView;
import com.kepg.glvpen.modules.user.domain.User;
import com.kepg.glvpen.modules.user.dto.UserHomeDTO;

import lombok.RequiredArgsConstructor;

/**
 * 사용자 홈 화면 데이터 조회 서비스
 */
@Service
@RequiredArgsConstructor
public class UserHomeService {

    private final TeamService teamService;
    private final ScheduleService scheduleService;
    private final GameService gameService;
    private final BatterStatsService batterStatsService;
    private final PitcherStatsService pitcherStatsService;
    private final AnalysisColumnRepository analysisColumnRepository;

    /**
     * 사용자 홈 화면에 필요한 모든 데이터를 조회합니다.
     *
     * @param user 로그인한 사용자
     * @return 홈 화면 데이터 DTO
     */
    public UserHomeDTO getUserHomeData(User user) {
        Integer favoriteTeamId = user != null ? user.getFavoriteTeamId() : null;
        int season = SeasonValidator.currentSeason();

        // 현재 시즌 데이터가 없으면 전년도로 fallback
        List<TeamRankingCardView> rankingCheck = gameService.getTeamRankingCardViews(season);
        if (rankingCheck == null || rankingCheck.isEmpty()) {
            season = SeasonValidator.fallbackSeason();
        }

        UserHomeDTO.UserHomeDTOBuilder builder = UserHomeDTO.builder();

        // favoriteTeamId가 있는 경우에만 팀 관련 데이터 조회
        if (favoriteTeamId != null) {
            int myTeamId = favoriteTeamId;

            // 내 팀 정보
            Team myTeam = teamService.getTeamById(myTeamId);
            builder.myTeam(myTeam);

            // 오늘 경기
            Schedule schedule = scheduleService.getTodayScheduleByTeam(myTeamId);
            builder.schedule(schedule);

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
            TopBatterCardView hitter = batterStatsService.getTopHitter(myTeamId, season);
            TopPitcherCardView pitcher = pitcherStatsService.getTopPitcher(myTeamId, season);
            builder.topHitter(hitter).topPitcher(pitcher);
        }

        // 팀 순위 (favoriteTeamId 유무와 관계없이 항상 조회)
        List<TeamRankingCardView> rankingList = gameService.getTeamRankingCardViews(season);

        // 오늘 전체 KBO 경기 일정
        List<ScheduleCardView> todayAllGames = scheduleService.getSchedulesByDate(LocalDate.now());

        // 최근 분석 컬럼 5개
        List<AnalysisColumn> recentColumns = analysisColumnRepository.findTop5ByOrderByPublishDateDesc();

        return builder
                .rankingList(rankingList)
                .todayAllGames(todayAllGames)
                .recentColumns(recentColumns)
                .build();
    }
}
