package com.kepg.glvpen.modules.team.teamRanking;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kepg.glvpen.common.validator.SeasonValidator;
import com.kepg.glvpen.modules.game.schedule.service.ScheduleService;
import com.kepg.glvpen.modules.game.service.GameService;
import com.kepg.glvpen.modules.team.teamStats.service.TeamStatsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/team-rankings")
@RequiredArgsConstructor
public class TeamRankingRestController {

    private final GameService gameService;
    private final TeamStatsService teamStatsService;
    private final ScheduleService scheduleService;

    @GetMapping("/teamranking-view-json")
    public Map<String, Object> teamRankingViewJson(
            @RequestParam(name = "season", required = false) Integer season,
            @RequestParam(name = "sort", required = false, defaultValue = "OPS") String sort,
            @RequestParam(name = "direction", required = false, defaultValue = "DESC") String direction) {

        int validSeason = SeasonValidator.validateOrDefault(season, SeasonValidator.currentSeason());

        // 현재 시즌 데이터가 없으면 전년도로 fallback
        var rankingList = gameService.getTeamRankingCardViews(validSeason);
        if (rankingList.isEmpty() && validSeason == SeasonValidator.currentSeason()) {
            validSeason = SeasonValidator.fallbackSeason();
            rankingList = gameService.getTeamRankingCardViews(validSeason);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("rankingList", rankingList);
        result.put("topBatterStats", teamStatsService.getTopBatterStats(validSeason));
        result.put("topPitcherStats", teamStatsService.getTopPitcherStats(validSeason));
        result.put("statRankingList", teamStatsService.getTeamRankingsSortedByStat(validSeason, sort, direction));
        result.put("currentSort", sort.trim().toUpperCase());
        result.put("sortDirection", direction);
        // 시리즈 라벨: 현재 시즌 데이터면 활성 시리즈, 과거 시즌이면 정규시즌
        String seriesLabel = (validSeason == SeasonValidator.currentSeason())
                ? scheduleService.getActiveSeriesLabel()
                : "정규시즌";
        result.put("season", validSeason);
        result.put("seriesLabel", seriesLabel);
        result.put("headers", TeamRankingConstants.getTeamRankingHeaders());
        result.put("categoryNameMap", TeamRankingConstants.getCategoryNameMap());

        return result;
    }
}
