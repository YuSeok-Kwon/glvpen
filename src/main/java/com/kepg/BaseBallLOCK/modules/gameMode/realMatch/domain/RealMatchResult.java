package com.kepg.BaseBallLOCK.modules.gameMode.realMatch.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 실제 경기 예측 결과 엔티티
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
    
    @Column(nullable = false)
    private Integer userId;
    
    @Column(nullable = false)
    private Long scheduleId;
    
    @CreationTimestamp
    @Column(name = "prediction_time", nullable = false)
    private LocalDateTime predictionTime;
    
    // 예측 내용
    private Integer predictedHomeScore;
    private Integer predictedAwayScore;
    
    @Column(length = 20)
    private String predictedWinner; // "home", "away", "draw"
    
    @Column(length = 50)
    private String predictedMvp;
    
    // 실제 결과 (경기 종료 후 업데이트)
    private Integer actualHomeScore;
    private Integer actualAwayScore;
    
    @Column(length = 20)
    private String actualWinner;
    
    @Column(length = 50)
    private String actualMvp;
    
    // 예측 결과
    private Boolean scoreCorrect;
    private Boolean winnerCorrect;
    private Boolean mvpCorrect;
    private Integer accuracy; // 정확도 (0-100)
    
    // 베팅 관련
    private Integer betAmount;
    private Integer pointsEarned;
    private Integer payout;
    
    @Builder.Default
    private Boolean isProcessed = false; // 결과 처리 여부
}
