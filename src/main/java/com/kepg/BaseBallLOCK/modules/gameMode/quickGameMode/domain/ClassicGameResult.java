package com.kepg.BaseBallLOCK.modules.gameMode.quickGameMode.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Classic Mode 게임 결과를 저장하는 엔티티
 */
@Entity
@Table(name = "classic_game_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassicGameResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer userId;

    @Column(length = 20, nullable = false)
    private String difficulty;  // EASY, NORMAL, HARD

    @Column(nullable = false)
    private Integer userScore;

    @Column(nullable = false)
    private Integer botScore;

    @Column(nullable = false)
    private Boolean isWin;

    @Column(columnDefinition = "TEXT")
    private String highlights;  // JSON 형태의 하이라이트 데이터

    @Column(length = 100)
    private String mvpPlayerName;

    private Double mvpScore;

    // 게임 통계
    private Integer totalHits;
    private Integer totalHomeRuns;
    private Integer totalStrikeouts;
    private String gameDuration;

    // 보상 정보
    private Integer rewardPoints;
    private Integer experienceGained;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
