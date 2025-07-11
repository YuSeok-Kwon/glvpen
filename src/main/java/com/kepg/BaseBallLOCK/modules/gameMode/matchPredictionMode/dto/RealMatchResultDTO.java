package com.kepg.BaseBallLOCK.modules.gameMode.matchPredictionMode.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RealMatch 게임 결과 DTO
 * - 실제 KBO 경기 결과와 예측 비교
 */
@Data
@Builder
public class RealMatchResultDTO {
    
    private Long gameId;
    private Integer userId;
    private Long scheduleId;
    
    // 게임 기본 정보
    private String userTeamName;
    private String opponentTeamName;
    private LocalDateTime gameTime;
    private String stadium;
    
    // 실제 경기 결과
    private Integer actualUserScore;
    private Integer actualOpponentScore;
    private String actualWinner;
    private String actualMvp;
    private List<String> actualHighlights;
    
    // 사용자 예측
    private Integer predictedUserScore;
    private Integer predictedOpponentScore;
    private String predictedWinner;
    private String predictedMvp;
    
    // 예측 정확도
    private boolean scoreExactMatch;
    private boolean winnerCorrect;
    private boolean mvpCorrect;
    private double predictionAccuracy;  // 0.0 ~ 1.0
    
    // 포인트/보상
    private Integer earnedPoints;
    private Integer betPoints;
    private String rewardType;
    private List<String> achievements;
    
    // 게임 통계
    private String gameLog;
    private String matchSummary;
    private LocalDateTime playedAt;
    
    // 리그 정보
    private String season;
    private Integer round;
    private String matchType;  // "정규시즌", "포스트시즌" 등
}
