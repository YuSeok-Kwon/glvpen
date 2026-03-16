package com.kepg.glvpen.modules.analysis.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kepg.glvpen.common.validator.SeasonValidator;
import com.kepg.glvpen.modules.analysis.domain.AnalysisColumn;
import com.kepg.glvpen.modules.analysis.dto.ChartDataDTO;
import com.kepg.glvpen.modules.analysis.dto.DashboardDTO;
import com.kepg.glvpen.modules.analysis.dto.PlayerAnalysisDTO;
import com.kepg.glvpen.modules.analysis.dto.TeamAnalysisDTO;
import com.kepg.glvpen.modules.analysis.service.AiColumnGeneratorService;
import com.kepg.glvpen.modules.analysis.service.AnalysisService;
import com.kepg.glvpen.modules.analysis.service.ColumnService;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisRestController {

    private final AnalysisService analysisService;
    private final AiColumnGeneratorService aiColumnGeneratorService;
    private final ColumnService columnService;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDTO> getDashboard(@RequestParam(required = false) Integer season) {
        int validSeason = SeasonValidator.validateOrDefault(season, SeasonValidator.currentSeason());
        return ResponseEntity.ok(analysisService.getDashboardData(validSeason));
    }

    @GetMapping("/player/{id}/trend")
    public ResponseEntity<PlayerAnalysisDTO> getPlayerTrend(
            @PathVariable int id,
            @RequestParam(defaultValue = "WAR") String category,
            @RequestParam(defaultValue = "2020") int startYear,
            @RequestParam(required = false) Integer endYear) {
        int validEndYear = SeasonValidator.validateOrDefault(endYear, SeasonValidator.currentSeason());
        return ResponseEntity.ok(analysisService.getPlayerTrend(id, category, startYear, validEndYear));
    }

    @GetMapping("/team/{id}/balance")
    public ResponseEntity<TeamAnalysisDTO> getTeamBalance(
            @PathVariable int id,
            @RequestParam(required = false) Integer season) {
        int validSeason = SeasonValidator.validateOrDefault(season, SeasonValidator.currentSeason());
        return ResponseEntity.ok(analysisService.getTeamBalance(id, validSeason));
    }

    @GetMapping("/war-distribution")
    public ResponseEntity<ChartDataDTO> getWarDistribution(@RequestParam(required = false) Integer season) {
        int validSeason = SeasonValidator.validateOrDefault(season, SeasonValidator.currentSeason());
        return ResponseEntity.ok(analysisService.getWarDistribution(validSeason));
    }

    @GetMapping("/team-comparison")
    public ResponseEntity<List<TeamAnalysisDTO>> getTeamComparison(@RequestParam(required = false) Integer season) {
        int validSeason = SeasonValidator.validateOrDefault(season, SeasonValidator.currentSeason());
        return ResponseEntity.ok(analysisService.getTeamComparison(validSeason));
    }

    // ====== 시즌/트렌드 분석 API ======

    @GetMapping("/league-trend")
    public ResponseEntity<ChartDataDTO> getLeagueTrend(
            @RequestParam(defaultValue = "2020") int startYear,
            @RequestParam(required = false) Integer endYear) {
        int validEndYear = SeasonValidator.validateOrDefault(endYear, SeasonValidator.currentSeason());
        return ResponseEntity.ok(analysisService.getLeagueHitPitchTrend(startYear, validEndYear));
    }

    @GetMapping("/homerun-trend")
    public ResponseEntity<ChartDataDTO> getHomerunTrend(
            @RequestParam(defaultValue = "2020") int startYear,
            @RequestParam(required = false) Integer endYear) {
        int validEndYear = SeasonValidator.validateOrDefault(endYear, SeasonValidator.currentSeason());
        return ResponseEntity.ok(analysisService.getHomerunTrend(startYear, validEndYear));
    }

    @GetMapping("/series-comparison")
    public ResponseEntity<ChartDataDTO> getSeriesComparison(@RequestParam(required = false) Integer season) {
        int validSeason = SeasonValidator.validateOrDefault(season, SeasonValidator.currentSeason());
        return ResponseEntity.ok(analysisService.getSeriesComparison(validSeason));
    }

    @GetMapping("/rookie-impact")
    public ResponseEntity<Map<String, Object>> getRookieImpact(@RequestParam(required = false) Integer debutYear) {
        int validYear = SeasonValidator.validateOrDefault(debutYear, SeasonValidator.currentSeason());
        return ResponseEntity.ok(analysisService.getRookieImpact(validYear));
    }

    @GetMapping("/school-type")
    public ResponseEntity<ChartDataDTO> getSchoolType(
            @RequestParam(defaultValue = "2020") int startYear,
            @RequestParam(required = false) Integer endYear) {
        int validEndYear = SeasonValidator.validateOrDefault(endYear, SeasonValidator.currentSeason());
        return ResponseEntity.ok(analysisService.getSchoolTypeAnalysis(startYear, validEndYear));
    }

    @GetMapping("/game-event-trend")
    public ResponseEntity<ChartDataDTO> getGameEventTrend(
            @RequestParam(defaultValue = "2020") int startYear,
            @RequestParam(required = false) Integer endYear) {
        int validEndYear = SeasonValidator.validateOrDefault(endYear, SeasonValidator.currentSeason());
        return ResponseEntity.ok(analysisService.getGameEventTrend(startYear, validEndYear));
    }

    @PostMapping("/columns/save")
    public ResponseEntity<AnalysisColumn> saveColumn(@RequestBody Map<String, String> request) {
        AnalysisColumn column = AnalysisColumn.builder()
                .title(request.get("title"))
                .content(request.get("content"))
                .category(request.get("category"))
                .season(Integer.parseInt(request.getOrDefault("season", "2025")))
                .publishDate(LocalDateTime.now())
                .autoGenerated(false)
                .viewCount(0)
                .summary(ColumnService.extractSummary(request.get("content")))
                .build();
        return ResponseEntity.ok(columnService.saveColumn(column));
    }

    @PostMapping("/columns/generate")
    public ResponseEntity<AnalysisColumn> generateAiColumn(@RequestBody Map<String, String> request) {
        String topic = request.getOrDefault("topic", "주간 시즌 분석");
        String category = request.getOrDefault("category", "trend");
        int season = Integer.parseInt(request.getOrDefault("season", String.valueOf(SeasonValidator.currentSeason())));

        AnalysisColumn column = aiColumnGeneratorService.generateColumnByTopic(topic, category, season);
        return ResponseEntity.ok(column);
    }
}
