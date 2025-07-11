package com.kepg.BaseBallLOCK.modules.gameMode.quickGameMode.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.kepg.BaseBallLOCK.modules.gameMode.quickGameMode.dto.ClassicGameRequestDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.quickGameMode.dto.ClassicGameResultDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.quickGameMode.service.ClassicSimulationService;

import lombok.RequiredArgsConstructor;

/**
 * Quick Game Mode 게임 컨트롤러
 * - 기본 난이도 기반 게임
 * - 빠른 게임, 일반 게임 모드 지원
 * - 경험치 및 보상 시스템
 */
@Controller
@RequestMapping("/quick-game")
@RequiredArgsConstructor
public class QuickGameController {
    
    private final ClassicSimulationService classicSimulationService;
    
    /**
     * Classic Mode 메인 페이지
     */
    @GetMapping
    public String classicHome(Model model) {
        model.addAttribute("pageTitle", "Classic Mode");
        model.addAttribute("gameMode", "classic");
        return "classic/home";
    }
    
    /**
     * 난이도 선택 페이지
     */
    @GetMapping("/difficulty")
    public String selectDifficulty(Model model) {
        model.addAttribute("difficulties", ClassicSimulationService.Difficulty.values());
        return "classic/difficulty";
    }
    
    /**
     * 게임 시작 페이지
     */
    @GetMapping("/game")
    public String gameSetup(
        @RequestParam(required = false) String difficulty,
        @RequestParam(required = false) Integer teamId,
        Model model
    ) {
        model.addAttribute("difficulty", difficulty);
        model.addAttribute("teamId", teamId);
        model.addAttribute("fastMode", false);
        return "classic/game";
    }
    
    /**
     * 게임 실행 API
     */
    @PostMapping("/play")
    @ResponseBody
    public ResponseEntity<ClassicGameResultDTO> playGame(@RequestBody ClassicGameRequestDTO request) {
        try {
            ClassicGameResultDTO result = classicSimulationService.playClassicGame(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 빠른 게임 실행 API
     */
    @PostMapping("/quick-play")
    @ResponseBody
    public ResponseEntity<ClassicGameResultDTO> quickPlay(@RequestBody ClassicGameRequestDTO request) {
        try {
            request.setFastMode(true);
            ClassicGameResultDTO result = classicSimulationService.playClassicGame(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 사용자 통계 조회 API
     */
    @GetMapping("/stats/{userId}")
    @ResponseBody
    public ResponseEntity<ClassicSimulationService.PlayerStatistics> getUserStats(@PathVariable Integer userId) {
        try {
            ClassicSimulationService.PlayerStatistics stats = classicSimulationService.getUserStats(userId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 리더보드 페이지
     */
    @GetMapping("/leaderboard")
    public String leaderboard(Model model) {
        model.addAttribute("pageTitle", "Classic Leaderboard");
        return "classic/leaderboard";
    }
    
    /**
     * 게임 결과 페이지
     */
    @GetMapping("/result/{gameId}")
    public String gameResult(@PathVariable Long gameId, Model model) {
        // 게임 결과 조회 로직 필요
        model.addAttribute("gameId", gameId);
        return "classic/result";
    }
}
