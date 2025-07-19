package com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CustomPlayer 게임 결과 DTO
 * - 커스텀 선수 기반 게임 결과
 */
@Data
@Builder
public class CustomPlayerResultDTO {
    
    private Long gameId;
    private Integer userId;
    
    // 게임 기본 정보
    private String gameMode;
    private String difficulty;
    private String userTeamName;
    private String opponentTeamName;
    private LocalDateTime playedAt;
    
    // 경기 결과
    private Integer userScore;
    private Integer opponentScore;
    private String winner;
    private boolean isWin;
    
    // 커스텀 선수 성과
    private List<CustomPlayerPerformance> playerPerformances;
    private String mvpPlayer;
    private List<String> gameHighlights;
    
    // 통계 정보
    private CustomTeamStats userTeamStats;
    private CustomTeamStats opponentTeamStats;
    
    // 보상 및 경험치
    private Integer earnedExperience;
    private Integer earnedCoins;
    private List<String> unlockedFeatures;
    private List<String> achievements;
    
    // 게임 로그
    private List<String> gameLog;
    private String gameSummary;
    private LocalDateTime gameStartTime;
    private LocalDateTime gameEndTime;
    private LocalDateTime startTime; // 추가된 필드
    private LocalDateTime endTime;   // 추가된 필드
    
    /**
     * 커스텀 선수 경기 성과
     */
    @Data
    @Builder
    public static class CustomPlayerPerformance {
        private Long playerId; // 추가된 필드
        private String playerName;
        private String position;
        
        // 타격 성과
        private Integer atBats;
        private Integer hits;
        private Integer homeRuns;
        private Integer rbis;
        private Integer runs;
        private Double battingAverage;
        private Integer stolenBases; // 추가된 필드
        
        // 투구 성과 (투수인 경우)
        private Double inningsPitched;
        private Integer strikeouts;
        private Integer walks;
        private Integer earnedRuns;
        private Double era;
        
        // 수비 성과
        private Integer fieldingChances;
        private Integer errors;
        private Integer assists;
        
        // 특수 플레이
        private List<String> specialPlays;
        private Integer performanceRating;  // 1-10 점
        private String performanceDescription;
    }
    
    /**
     * 팀 통계
     */
    @Data
    @Builder
    public static class CustomTeamStats {
        private Integer totalHits;
        private Integer hits;      // 추가된 필드
        private Integer totalRuns;
        private Integer runs;      // 추가된 필드
        private Integer totalErrors;
        private Integer errors;    // 추가된 필드
        private Double teamBattingAverage;
        private Double teamEra;
        private Integer leftOnBase;
        private String teamMvp;
    }
}
