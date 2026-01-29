package com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 커스텀 팀 엔티티
 * - 사용자가 커스텀 선수들로 구성한 팀
 */
@Entity
@Table(name = "custom_teams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer userId;

    @Column(nullable = false, length = 100)
    private String teamName;

    // 라인업 정보 (JSON 형태로 저장)
    // 예: {"1": {"playerId": 1, "position": "CF"}, "2": {"playerId": 2, "position": "SS"}, ...}
    @Column(columnDefinition = "TEXT")
    private String lineupData;

    // 팀 설명
    @Column(length = 500)
    private String description;

    // 팀 엠블럼 URL
    @Column(length = 2048)
    private String emblemUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
