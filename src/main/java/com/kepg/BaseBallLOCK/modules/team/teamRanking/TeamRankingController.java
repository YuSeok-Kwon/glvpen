package com.kepg.BaseBallLOCK.modules.team.teamRanking;

import java.util.Arrays;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/ranking")
@RequiredArgsConstructor
public class TeamRankingController {


    @GetMapping("/teamranking-view")
    public String teamRankingView(
            @RequestParam(name = "season", required = false, defaultValue = "2025") int season,
            @RequestParam(name = "sort", required = false, defaultValue = "TotalWAR") String sort,
            @RequestParam(name = "direction", required = false, defaultValue = "DESC") String direction,
            Model model) {

        model.addAttribute("season", season);
        model.addAttribute("currentSort", sort.trim().toUpperCase());
        model.addAttribute("sortDirection", direction);
        model.addAttribute("categoryNameMap", getCategoryNameMap());
        model.addAttribute("headers", getTeamRankingHeaders());

        return "ranking/teamranking"; // 이제 첫 화면은 빈 틀만 보여줘
    }

    private Map<String, String> getCategoryNameMap() {
        Map<String, String> map = new HashMap<>();
        map.put("TotalWAR", "종합 WAR");
        map.put("OPS", "팀 OPS");
        map.put("AVG", "팀 타율");
        map.put("HR", "팀 홈런");
        map.put("SB", "팀 도루");
        map.put("BetterWAR", "타자 WAR");
        map.put("PitcherWAR", "투수 WAR");
        map.put("SO", "팀 탈삼진");
        map.put("ERA", "팀 ERA");
        map.put("WHIP", "팀 WHIP");
        map.put("BB", "팀 볼넷");
        map.put("타격", "타격 WAA");
        map.put("주루", "주루 WAA");
        map.put("수비", "수비 WAA");
        map.put("선발", "선발 WAA");
        map.put("불펜", "불펜 WAA");
        return map;
    }

    private List<String> getTeamRankingHeaders() {
        return Arrays.asList(
            "TotalWAR", "BetterWAR", "OPS", "AVG", "HR", "SB",
            "PitcherWAR", "SO", "ERA", "WHIP", "BB",
            "타격", "주루", "수비", "선발", "불펜"
        );
    }
}