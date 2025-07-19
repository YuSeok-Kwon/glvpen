package com.kepg.BaseBallLOCK.modules.gameMode.realMatch.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.kepg.BaseBallLOCK.modules.gameMode.realMatch.dto.RealMatchRequestDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.realMatch.dto.RealMatchResultDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.realMatch.service.RealMatchService;

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
     * 특정 경기 예측 페이지
     */
    @GetMapping("/predict/{scheduleId}")
    public String predictGame(@PathVariable Long scheduleId, Model model) {
        model.addAttribute("scheduleId", scheduleId);
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
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 사용자 예측 통계 조회 API
     */
    @GetMapping("/stats/{userId}")
    @ResponseBody
    public ResponseEntity<?> getUserPredictionStats(@PathVariable Integer userId) {
        try {
            var stats = realMatchService.getUserPredictionStats(userId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
