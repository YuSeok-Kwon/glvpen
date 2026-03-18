package com.kepg.glvpen.modules.team.teamRanking;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kepg.glvpen.common.validator.SeasonValidator;
import com.kepg.glvpen.modules.game.service.GameService;
import com.kepg.glvpen.modules.team.teamStats.service.TeamStatsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/team-rankings")
@RequiredArgsConstructor
public class TeamRankingRestController {

    private final GameService gameService;
    private final TeamStatsService teamStatsService;

    @GetMapping("/teamranking-view-json")
    public Map<String, Object> teamRankingViewJson(
            @RequestParam(name = "season", required = false) Integer season,
            @RequestParam(name = "sort", required = false, defaultValue = "OPS") String sort,
            @RequestParam(name = "direction", required = false, defaultValue = "DESC") String direction) {

        int validSeason = SeasonValidator.validateOrDefault(season, SeasonValidator.currentSeason());

        Map<String, Object> result = new HashMap<>();
        result.put("rankingList", gameService.getTeamRankingCardViews(validSeason));
        result.put("topBatterStats", teamStatsService.getTopBatterStats(validSeason));
        result.put("topPitcherStats", teamStatsService.getTopPitcherStats(validSeason));
        result.put("statRankingList", teamStatsService.getTeamRankingsSortedByStat(validSeason, sort, direction));
        result.put("currentSort", sort.trim().toUpperCase());
        result.put("sortDirection", direction);
        result.put("headers", TeamRankingConstants.getTeamRankingHeaders());
        result.put("categoryNameMap", TeamRankingConstants.getCategoryNameMap());

        return result;
    }
}
