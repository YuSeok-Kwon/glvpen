package com.kepg.glvpen.modules.analysis.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kepg.glvpen.common.validator.SeasonValidator;
import com.kepg.glvpen.modules.analysis.dto.ClutchIndexDTO;
import com.kepg.glvpen.modules.analysis.dto.FatigueAnalysisDTO;
import com.kepg.glvpen.modules.analysis.dto.MarcelProjectionDTO;
import com.kepg.glvpen.modules.analysis.dto.PlayerSimilarityDTO;
import com.kepg.glvpen.modules.analysis.dto.WinPredictionDTO;
import com.kepg.glvpen.modules.analysis.service.ClutchIndexService;
import com.kepg.glvpen.modules.analysis.service.FatigueAnalysisService;
import com.kepg.glvpen.modules.analysis.service.MarcelProjectionService;
import com.kepg.glvpen.modules.analysis.service.PlayerSimilarityService;
import com.kepg.glvpen.modules.analysis.service.WinPredictionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/analysis/advanced")
@RequiredArgsConstructor
public class AdvancedAnalysisRestController {

    private final WinPredictionService winPredictionService;
    private final MarcelProjectionService marcelProjectionService;
    private final PlayerSimilarityService playerSimilarityService;
    private final ClutchIndexService clutchIndexService;
    private final FatigueAnalysisService fatigueAnalysisService;

    // ====== 승리 예측 ======

    @GetMapping("/win-prediction")
    public ResponseEntity<WinPredictionDTO> predict(
            @RequestParam int homeTeamId,
            @RequestParam int awayTeamId,
            @RequestParam(required = false) Integer season) {
        int validSeason = SeasonValidator.validateOrDefault(season, SeasonValidator.currentSeason());
        WinPredictionDTO result = winPredictionService.predict(homeTeamId, awayTeamId, validSeason);
        if (result == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/win-prediction/train")
    public ResponseEntity<WinPredictionDTO.TrainResult> trainModel(
            @RequestParam(defaultValue = "2020") int startSeason,
            @RequestParam(required = false) Integer endSeason) {
        int validEnd = SeasonValidator.validateOrDefault(endSeason, SeasonValidator.currentSeason() - 1);
        return ResponseEntity.ok(winPredictionService.trainModel(startSeason, validEnd));
    }

    // ====== 성적 예측 (Marcel) ======

    @GetMapping("/projection/batter/{id}")
    public ResponseEntity<MarcelProjectionDTO> projectBatter(
            @PathVariable int id,
            @RequestParam(required = false) Integer targetSeason) {
        int validTarget = SeasonValidator.validateOrDefault(targetSeason, SeasonValidator.currentSeason());
        MarcelProjectionDTO result = marcelProjectionService.projectBatter(id, validTarget);
        if (result == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/projection/pitcher/{id}")
    public ResponseEntity<MarcelProjectionDTO> projectPitcher(
            @PathVariable int id,
            @RequestParam(required = false) Integer targetSeason) {
        int validTarget = SeasonValidator.validateOrDefault(targetSeason, SeasonValidator.currentSeason());
        MarcelProjectionDTO result = marcelProjectionService.projectPitcher(id, validTarget);
        if (result == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/projection/batters")
    public ResponseEntity<List<MarcelProjectionDTO.ProjectionRanking>> projectAllBatters(
            @RequestParam(required = false) Integer targetSeason,
            @RequestParam(defaultValue = "20") int limit) {
        int validTarget = SeasonValidator.validateOrDefault(targetSeason, SeasonValidator.currentSeason());
        return ResponseEntity.ok(marcelProjectionService.projectAllBatters(validTarget, limit));
    }

    // ====== 선수 유사도 ======

    @GetMapping("/similarity/{id}")
    public ResponseEntity<PlayerSimilarityDTO> findSimilarBatters(
            @PathVariable int id,
            @RequestParam(required = false) Integer season,
            @RequestParam(defaultValue = "10") int topN) {
        int validSeason = SeasonValidator.validateOrDefault(season, SeasonValidator.currentSeason());
        PlayerSimilarityDTO result = playerSimilarityService.findSimilarBatters(id, validSeason, topN);
        if (result == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/similarity/compare")
    public ResponseEntity<PlayerSimilarityDTO> compareTwoPlayers(
            @RequestParam int player1,
            @RequestParam int player2,
            @RequestParam(required = false) Integer season) {
        int validSeason = SeasonValidator.validateOrDefault(season, SeasonValidator.currentSeason());
        return ResponseEntity.ok(playerSimilarityService.compareTwoPlayers(player1, player2, validSeason));
    }

    // ====== 클러치 지수 ======

    @GetMapping("/clutch")
    public ResponseEntity<ClutchIndexDTO> getClutchRanking(
            @RequestParam(required = false) Integer season,
            @RequestParam(defaultValue = "20") int limit) {
        int validSeason = SeasonValidator.validateOrDefault(season, SeasonValidator.currentSeason());
        return ResponseEntity.ok(clutchIndexService.getClutchRanking(validSeason, limit));
    }

    @GetMapping("/clutch/{id}")
    public ResponseEntity<ClutchIndexDTO.ClutchPlayer> getPlayerClutch(
            @PathVariable int id,
            @RequestParam(required = false) Integer season) {
        int validSeason = SeasonValidator.validateOrDefault(season, SeasonValidator.currentSeason());
        ClutchIndexDTO.ClutchPlayer result = clutchIndexService.getPlayerClutch(id, validSeason);
        if (result == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/clutch/chart")
    public ResponseEntity<ClutchIndexDTO> getClutchChart(
            @RequestParam(required = false) Integer season) {
        int validSeason = SeasonValidator.validateOrDefault(season, SeasonValidator.currentSeason());
        return ResponseEntity.ok(clutchIndexService.getClutchRanking(validSeason, 0));
    }

    // ====== 피로도 분석 ======

    @GetMapping("/fatigue/{id}")
    public ResponseEntity<FatigueAnalysisDTO> analyzePitcherFatigue(
            @PathVariable int id,
            @RequestParam(required = false) Integer season) {
        int validSeason = SeasonValidator.validateOrDefault(season, SeasonValidator.currentSeason());
        FatigueAnalysisDTO result = fatigueAnalysisService.analyzePitcherFatigue(id, validSeason);
        if (result == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/fatigue/ranking")
    public ResponseEntity<List<FatigueAnalysisDTO.FatigueRanking>> getFatigueRanking(
            @RequestParam(required = false) Integer season,
            @RequestParam(defaultValue = "20") int limit) {
        int validSeason = SeasonValidator.validateOrDefault(season, SeasonValidator.currentSeason());
        return ResponseEntity.ok(fatigueAnalysisService.getFatigueRanking(validSeason, limit));
    }

    @GetMapping("/fatigue/{id}/chart")
    public ResponseEntity<FatigueAnalysisDTO> getFatigueChart(
            @PathVariable int id,
            @RequestParam(required = false) Integer season) {
        int validSeason = SeasonValidator.validateOrDefault(season, SeasonValidator.currentSeason());
        FatigueAnalysisDTO result = fatigueAnalysisService.analyzePitcherFatigue(id, validSeason);
        if (result == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(result);
    }
}
