package com.kepg.BaseBallLOCK.modules.gameMode.realMatch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 실제 경기 예측 결과 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealMatchResultDTO {
    
    private Long predictionId;
    private Integer userId;
    private Long scheduleId;
    private LocalDateTime predictionTime;
    
    // 실제 경기 결과
    private Integer actualHomeScore;
    private Integer actualAwayScore;
    private String actualWinner; // "home", "away", "draw"
    private String actualMvp;
    
    // 사용자 예측
    private Integer predictedHomeScore;
    private Integer predictedAwayScore;
    private String predictedWinner;
    private String predictedMvp;
    
    // 예측 결과
    private Boolean scoreCorrect;
    private Boolean winnerCorrect;
    private Boolean mvpCorrect;
    private Integer accuracy; // 정확도 (0-100)
    private Integer pointsEarned; // 획득 포인트
    
    // 베팅 관련
    private Integer betAmount;
    private Integer payout; // 배당금
    private Boolean isWin; // 전체 예측 성공 여부
    
    // 통계 정보
    private PredictionStats stats;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionStats {
        private Integer totalPredictions;
        private Integer correctPredictions;
        private Double successRate;
        private Integer totalPoints;
        private Integer ranking;
    }
}
