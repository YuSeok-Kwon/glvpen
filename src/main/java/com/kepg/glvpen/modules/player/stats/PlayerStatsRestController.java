package com.kepg.glvpen.modules.player.stats;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kepg.glvpen.common.validator.SeasonValidator;
import com.kepg.glvpen.modules.player.stats.service.BatterStatsService;
import com.kepg.glvpen.modules.player.stats.service.DefenseStatsService;
import com.kepg.glvpen.modules.player.stats.service.PitcherStatsService;
import com.kepg.glvpen.modules.player.stats.service.RunnerStatsService;
import com.kepg.glvpen.modules.player.stats.statsDto.BatterRankingDTO;
import com.kepg.glvpen.modules.player.stats.statsDto.DefenseRankingDTO;
import com.kepg.glvpen.modules.player.stats.statsDto.PitcherRankingDTO;
import com.kepg.glvpen.modules.player.stats.statsDto.RunnerRankingDTO;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/player-rankings")
public class PlayerStatsRestController {

    private final BatterStatsService batterStatsService;
    private final PitcherStatsService pitcherStatsService;
    private final DefenseStatsService defenseStatsService;
    private final RunnerStatsService runnerStatsService;

    @GetMapping("/playerranking-view-json")
    public Map<String, Object> playerRankingViewJson(
            @RequestParam(name = "season", required = false) Integer season,
            @RequestParam(name = "sort", required = false, defaultValue = "OPS") String sort,
            @RequestParam(name = "direction", required = false, defaultValue = "DESC") String direction,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "qualified", required = false, defaultValue = "false") boolean qualified,
            @RequestParam(name = "qualifiedLevel", required = false, defaultValue = "100") int qualifiedLevel) {

        int validSeason = SeasonValidator.validateOrDefault(season, SeasonValidator.currentSeason());

        List<BatterRankingDTO> batters = qualified
            ? batterStatsService.getQualifiedBatters(validSeason, sort, direction, qualifiedLevel)
            : batterStatsService.getPlayerRankingsSorted(validSeason, sort, direction);

        List<PitcherRankingDTO> pitchers = qualified
            ? pitcherStatsService.getQualifiedPitchers(validSeason, sort, direction, qualifiedLevel)
            : pitcherStatsService.getPitcherRankingsSorted(validSeason, sort, direction);

        List<DefenseRankingDTO> defense = defenseStatsService.getDefenseRankingsSorted(validSeason, sort, direction);
        List<RunnerRankingDTO> runners = runnerStatsService.getRunnerRankingsSorted(validSeason, sort, direction);

        Map<String, Object> result = new HashMap<>();

        result.put("topBatters", batterStatsService.getTopBattersByPosition(validSeason));
        result.put("topPitchers", pitcherStatsService.getTopPitchers(validSeason));
        result.put("batterRankingList", batters);
        result.put("pitcherRankingList", pitchers);
        result.put("defenseRankingList", defense);
        result.put("runnerRankingList", runners);

        result.put("currentSort", sort.trim().toUpperCase());
        result.put("sortDirection", direction);
        result.put("season", validSeason);


        return result;
    }


}
