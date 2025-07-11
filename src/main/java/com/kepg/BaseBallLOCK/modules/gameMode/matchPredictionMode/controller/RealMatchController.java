package com.kepg.BaseBallLOCK.modules.gameMode.matchPredictionMode.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.kepg.BaseBallLOCK.modules.gameMode.matchPredictionMode.dto.RealMatchRequestDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.matchPredictionMode.dto.RealMatchResultDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.matchPredictionMode.service.RealMatchService;

import lombok.RequiredArgsConstructor;

/**
 * RealMatch Mode 컨트롤러
 * - 실제 KBO 경기 기반 예측 게임
 * - 경기 일정 연동 및 베팅 시스템
 */
@Controller
@RequestMapping("/realmatch")
@RequiredArgsConstructor
public class RealMatchController {
    
    private final RealMatchService realMatchService;
    
    /**
     * RealMatch Mode 메인 페이지
     */
    @GetMapping
    public String realMatchHome(Model model) {
        model.addAttribute("pageTitle", "Real Match Mode");
        model.addAttribute("gameMode", "realmatch");
        return "realmatch/home";
    }
    
    /**
     * 경기 일정 목록 페이지
     */
    @GetMapping("/schedule")
    public String scheduleList(Model model) {
        // TODO: 실제 KBO 경기 일정 조회
        model.addAttribute("schedules", "경기 일정 목록");
        return "realmatch/schedule";
    }
    
    /**
     * 특정 경기 예측 페이지
     */
    @GetMapping("/predict/{scheduleId}")
    public String predictGame(@PathVariable Long scheduleId, Model model) {
        // TODO: 경기 정보 조회
        model.addAttribute("scheduleId", scheduleId);
        model.addAttribute("gameInfo", "경기 정보");
        return "realmatch/predict";
    }
    
    /**
     * 예측 게임 실행 API
     */
    @PostMapping("/predict")
    @ResponseBody
    public ResponseEntity<RealMatchResultDTO> submitPrediction(@RequestBody RealMatchRequestDTO request) {
        try {
            RealMatchResultDTO result = realMatchService.playPredictionGame(request);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 실제 경기 결과 비교 API
     */
    @PostMapping("/compare/{scheduleId}")
    @ResponseBody
    public ResponseEntity<RealMatchResultDTO> compareWithActual(@PathVariable Long scheduleId) {
        try {
            RealMatchResultDTO result = realMatchService.compareWithActualResult(scheduleId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 사용자 예측 통계 조회 API
     */
    @GetMapping("/stats/{userId}")
    @ResponseBody
    public ResponseEntity<RealMatchService.PredictionStatistics> getUserPredictionStats(@PathVariable Integer userId) {
        try {
            RealMatchService.PredictionStatistics stats = realMatchService.getUserPredictionStats(userId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 예측 리더보드 페이지
     */
    @GetMapping("/leaderboard")
    public String leaderboard(Model model) {
        model.addAttribute("pageTitle", "Prediction Leaderboard");
        return "realmatch/leaderboard";
    }
    
    /**
     * 예측 결과 상세 페이지
     */
    @GetMapping("/result/{gameId}")
    public String predictionResult(@PathVariable Long gameId, Model model) {
        // TODO: 예측 결과 상세 조회
        model.addAttribute("gameId", gameId);
        return "realmatch/result";
    }
    
    /**
     * 사용자의 예측 기록 페이지
     */
    @GetMapping("/history/{userId}")
    public String predictionHistory(@PathVariable Integer userId, Model model) {
        // TODO: 사용자 예측 기록 조회
        model.addAttribute("userId", userId);
        return "realmatch/history";
    }
    
    /**
     * 라이브 경기 예측 페이지
     */
    @GetMapping("/live")
    public String liveGames(Model model) {
        // TODO: 진행 중인 경기 목록
        model.addAttribute("liveGames", "라이브 경기 목록");
        return "realmatch/live";
    }
}
