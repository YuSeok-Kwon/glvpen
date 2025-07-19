package com.kepg.BaseBallLOCK.modules.gameMode.realMatch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 실제 경기 예측 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealMatchRequestDTO {
    
    private Integer userId;
    private Long scheduleId;
    private String homeTeamPrediction; // "win", "lose", "draw"
    private String awayTeamPrediction; // "win", "lose", "draw"
    private Integer homeScorePrediction;
    private Integer awayScorePrediction;
    private String mvpPrediction; // 예상 MVP 선수 이름
    private Integer betAmount; // 베팅 금액
    private String predictionType; // "score", "winner", "mvp"
    private LocalDate gameDate;
    
    // 추가 예측 옵션들
    private Boolean homeTeamFirstScore; // 홈팀 선제골 여부
    private Integer totalScore; // 총 득점 예측
    private String winnerPrediction; // 승리팀 예측
}
