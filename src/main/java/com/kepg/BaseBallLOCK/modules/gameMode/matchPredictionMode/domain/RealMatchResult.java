package com.kepg.BaseBallLOCK.modules.gameMode.matchPredictionMode.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * RealMatch 게임 결과 엔티티
 * - 실제 KBO 경기 기반 예측 게임 결과
 */
@Entity
@Table(name = "real_match_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RealMatchResult {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Integer userId;
    
    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;
    
    @Column(name = "user_team_id", nullable = false)
    private Integer userTeamId;
    
    @Column(name = "opponent_team_id", nullable = false)
    private Integer opponentTeamId;
    
    // 실제 경기 결과
    @Column(name = "actual_user_score")
    private Integer actualUserScore;
    
    @Column(name = "actual_opponent_score")
    private Integer actualOpponentScore;
    
    @Column(name = "actual_winner")
    private String actualWinner;
    
    @Column(name = "actual_mvp")
    private String actualMvp;
    
    // 사용자 예측
    @Column(name = "predicted_user_score")
    private Integer predictedUserScore;
    
    @Column(name = "predicted_opponent_score")
    private Integer predictedOpponentScore;
    
    @Column(name = "predicted_winner")
    private String predictedWinner;
    
    @Column(name = "predicted_mvp")
    private String predictedMvp;
    
    // 예측 결과
    @Column(name = "score_exact_match")
    private Boolean scoreExactMatch;
    
    @Column(name = "winner_correct")
    private Boolean winnerCorrect;
    
    @Column(name = "mvp_correct")
    private Boolean mvpCorrect;
    
    @Column(name = "prediction_accuracy")
    private Double predictionAccuracy;
    
    // 포인트/베팅
    @Column(name = "bet_points")
    private Integer betPoints;
    
    @Column(name = "earned_points")
    private Integer earnedPoints;
    
    @Column(name = "bet_type")
    private String betType;
    
    // 게임 정보
    @Column(name = "game_time")
    private LocalDateTime gameTime;
    
    @Column(name = "played_at")
    private LocalDateTime playedAt;
    
    @Column(name = "fast_mode")
    private Boolean fastMode;
    
    @Column(name = "season")
    private String season;
    
    @Column(name = "round_number")
    private Integer roundNumber;
    
    @Column(name = "match_type")
    private String matchType;
    
    @Column(name = "stadium")
    private String stadium;
    
    @Column(name = "game_log", columnDefinition = "TEXT")
    private String gameLog;
    
    @Column(name = "match_summary", columnDefinition = "TEXT")
    private String matchSummary;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.playedAt == null) {
            this.playedAt = now;
        }
    }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
