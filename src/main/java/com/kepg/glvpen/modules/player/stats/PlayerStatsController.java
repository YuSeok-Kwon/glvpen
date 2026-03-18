package com.kepg.glvpen.modules.player.stats;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.kepg.glvpen.common.validator.SeasonValidator;
import com.kepg.glvpen.modules.player.stats.service.BatterStatsService;
import com.kepg.glvpen.modules.player.stats.service.PitcherStatsService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/players")
public class PlayerStatsController {

    private final BatterStatsService batterStatsService;
    private final PitcherStatsService pitcherStatsService;

    @GetMapping("/rankings")
    public String playerRankingView(
            @RequestParam(name = "season", required = false) Integer season,
            @RequestParam(name = "sort", required = false, defaultValue = "OPS") String sort,
            @RequestParam(name = "direction", required = false, defaultValue = "DESC") String direction,
            Model model) {

        int validSeason = SeasonValidator.validateOrDefault(season, SeasonValidator.currentSeason());

        // 데이터를 모델에 추가
        model.addAttribute("season", validSeason);
        model.addAttribute("topBatters", batterStatsService.getTopBattersByPosition(validSeason));
        model.addAttribute("topPitchers", pitcherStatsService.getTopPitchers(validSeason));
        model.addAttribute("batterRankingList", batterStatsService.getPlayerRankingsSorted(validSeason, sort, direction));
        model.addAttribute("pitcherRankingList", pitcherStatsService.getPitcherRankingsSorted(validSeason, sort, direction));
        model.addAttribute("currentSort", sort.trim().toUpperCase());
        model.addAttribute("sortDirection", direction);

        return "ranking/playerranking";
    }
}