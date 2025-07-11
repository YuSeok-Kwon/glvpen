package com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 커스텀 플레이어 엔티티
 * RPG 형태의 플레이어 성장 시스템
 */
@Entity
@Table(name = "custom_players")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer userId;

    @Column(length = 50, nullable = false)
    private String playerName;

    @Column(nullable = false)
    @Builder.Default
    private Integer level = 1;

    @Column(nullable = false)
    @Builder.Default
    private Integer experience = 0;

    // 기본 능력치
    @Column(nullable = false)
    @Builder.Default
    private Integer power = 50;       // 파워

    @Column(nullable = false)
    @Builder.Default
    private Integer contact = 50;     // 컨택

    @Column(nullable = false)
    @Builder.Default
    private Integer speed = 50;       // 스피드

    @Column(nullable = false)
    @Builder.Default
    private Integer fielding = 50;    // 수비

    @Column(nullable = false)
    @Builder.Default
    private Integer arm = 50;         // 어깨

    // 특성 시스템
    @Column(length = 100)
    private String specialTraits;     // 특성 (JSON 형태로 저장)

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
