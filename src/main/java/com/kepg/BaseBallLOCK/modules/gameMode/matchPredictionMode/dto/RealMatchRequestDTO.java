package com.kepg.BaseBallLOCK.modules.gameMode.matchPredictionMode.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RealMatch 게임 요청 DTO
 * - 실제 KBO 경기 일정/결과 기반 게임
 */
@Data
@Builder
public class RealMatchRequestDTO {
    
    private Integer userId;
    private Long scheduleId;        // 실제 KBO 경기 일정 ID
    private Integer userTeamId;     // 사용자가 선택한 팀
    private String predictionType;  // "SCORE", "WINNER", "MVP" 등
    private String userPrediction;  // 사용자의 예측 값
    private boolean fastMode;       // 빠른 게임 모드
    private LocalDateTime gameTime; // 실제 경기 시간
    
    // 추가 예측 옵션들
    private Integer predictedUserScore;
    private Integer predictedOpponentScore;
    private String predictedMvp;
    private String predictedHighlight;
    
    // 베팅/포인트 시스템
    private Integer betPoints;      // 베팅한 포인트
    private String betType;         // 베팅 타입 ("WIN", "SCORE_EXACT", "SCORE_RANGE", "MVP")
}
