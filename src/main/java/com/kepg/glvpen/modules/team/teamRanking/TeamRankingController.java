package com.kepg.glvpen.modules.team.teamRanking;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.kepg.glvpen.common.validator.SeasonValidator;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/ranking")
@RequiredArgsConstructor
public class TeamRankingController {


    @GetMapping("/rankings")
    public String teamRankingView(
            @RequestParam(name = "season", required = false) Integer season,
            @RequestParam(name = "sort", required = false, defaultValue = "TotalWAR") String sort,
            @RequestParam(name = "direction", required = false, defaultValue = "DESC") String direction,
            Model model) {

        int validSeason = SeasonValidator.validateOrDefault(season, SeasonValidator.currentSeason());

        model.addAttribute("season", validSeason);
        model.addAttribute("currentSort", sort.trim().toUpperCase());
        model.addAttribute("sortDirection", direction);
        model.addAttribute("categoryNameMap", TeamRankingConstants.getCategoryNameMap());
        model.addAttribute("headers", TeamRankingConstants.getTeamRankingHeaders());

        return "ranking/teamranking";
    }

    @GetMapping("/teamranking-view")
    public String teamRankingViewPage(
            @RequestParam(name = "season", required = false) Integer season,
            @RequestParam(name = "sort", required = false, defaultValue = "TotalWAR") String sort,
            @RequestParam(name = "direction", required = false, defaultValue = "DESC") String direction,
            Model model) {

        int validSeason = SeasonValidator.validateOrDefault(season, SeasonValidator.currentSeason());

        model.addAttribute("season", validSeason);
        model.addAttribute("currentSort", sort.trim().toUpperCase());
        model.addAttribute("sortDirection", direction);
        model.addAttribute("categoryNameMap", TeamRankingConstants.getCategoryNameMap());
        model.addAttribute("headers", TeamRankingConstants.getTeamRankingHeaders());

        return "ranking/teamranking";
    }

    @GetMapping("/playerranking-view")
    public String playerRankingViewPage(Model model) {
        return "ranking/playerranking";
    }
}
