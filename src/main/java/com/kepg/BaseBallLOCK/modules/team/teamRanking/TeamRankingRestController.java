package com.kepg.BaseBallLOCK.modules.team.teamRanking;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kepg.BaseBallLOCK.modules.game.service.GameService;
import com.kepg.BaseBallLOCK.modules.team.teamStats.service.TeamStatsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/ranking")
@RequiredArgsConstructor
public class TeamRankingRestController {

    private final GameService gameService;
    private final TeamStatsService teamStatsService;

    @GetMapping("/teamranking-view-json")
    public Map<String, Object> teamRankingViewJson(
            @RequestParam(name = "season", required = false, defaultValue = "2025") int season,
            @RequestParam(name = "sort", required = false, defaultValue = "TotalWAR") String sort,
            @RequestParam(name = "direction", required = false, defaultValue = "DESC") String direction) {

        Map<String, Object> result = new HashMap<>();
        result.put("rankingList", gameService.getTeamRankingCardViews(season));
        result.put("topBatterStats", teamStatsService.getTopBatterStats(season));
        result.put("topPitcherStats", teamStatsService.getTopPitcherStats(season));
        result.put("topWaaStats", teamStatsService.getTopWaaStats(season));
        result.put("statRankingList", teamStatsService.getTeamRankingsSortedByStat(season, sort, direction));
        result.put("currentSort", sort.trim().toUpperCase());
        result.put("sortDirection", direction);
        result.put("headers", TeamRankingConstants.getTeamRankingHeaders());
        result.put("categoryNameMap", TeamRankingConstants.getCategoryNameMap());

        return result;
    }
}