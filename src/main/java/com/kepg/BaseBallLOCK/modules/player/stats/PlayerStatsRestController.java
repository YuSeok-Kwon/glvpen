package com.kepg.BaseBallLOCK.modules.player.stats;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kepg.BaseBallLOCK.modules.player.stats.service.BatterStatsService;
import com.kepg.BaseBallLOCK.modules.player.stats.service.PitcherStatsService;
import com.kepg.BaseBallLOCK.modules.player.stats.statsDto.BatterRankingDTO;
import com.kepg.BaseBallLOCK.modules.player.stats.statsDto.PitcherRankingDTO;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ranking")
public class PlayerStatsRestController {

    private final BatterStatsService batterStatsService;
    private final PitcherStatsService pitcherStatsService;

    @GetMapping("/playerranking-view-json")
    public Map<String, Object> playerRankingViewJson(
            @RequestParam(name = "season", required = false, defaultValue = "2025") int season,
            @RequestParam(name = "sort", required = false, defaultValue = "WAR") String sort,
            @RequestParam(name = "direction", required = false, defaultValue = "DESC") String direction,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "qualified", required = false, defaultValue = "false") boolean qualified) {
    	

        List<BatterRankingDTO> batters = qualified
            ? batterStatsService.getQualifiedBatters(season, sort, direction)
            : batterStatsService.getPlayerRankingsSorted(season, sort, direction);

        List<PitcherRankingDTO> pitchers = qualified
            ? pitcherStatsService.getQualifiedPitchers(season, sort, direction)
            : pitcherStatsService.getPitcherRankingsSorted(season, sort, direction);

        Map<String, Object> result = new HashMap<>();

        result.put("topBatters", batterStatsService.getTopBattersByPosition(season));
        result.put("topPitchers", pitcherStatsService.getTopPitchers(season));
        result.put("batterRankingList", batters); 
        result.put("pitcherRankingList", pitchers); 


        result.put("currentSort", sort.trim().toUpperCase());
        result.put("sortDirection", direction);
        result.put("season", season);

        
        return result;
    }
    
    
}