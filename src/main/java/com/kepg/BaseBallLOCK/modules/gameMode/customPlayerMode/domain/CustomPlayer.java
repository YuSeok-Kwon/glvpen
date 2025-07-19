package com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 통합 커스텀 플레이어 엔티티
 * - 간단한 커스텀 타자 모드와 RPG 모드를 모두 지원
 * - mode 필드로 구분: "HITTER", "RPG"
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

    // 게임 모드 구분
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String mode = "HITTER"; // "HITTER" 또는 "RPG"

    // 기본 정보 (두 모드 공통)
    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private Integer age;

    private String gender;
    private String skin;
    private String hair;
    private String batting;
    private Integer height;
    private Integer weight;
    private String position;

    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;

    // 기본 능력치 (두 모드 공통)
    @Column(nullable = false)
    @Builder.Default
    private Integer power = 50;

    @Column(nullable = false)
    @Builder.Default
    private Integer contact = 50;

    @Column(nullable = false)
    @Builder.Default
    private Integer speed = 50;

    @Column(nullable = false)
    @Builder.Default
    private Integer defense = 50;

    // RPG 모드 전용 필드
    private Integer userId;        // RPG 모드에서만 사용
    private String playerName;     // RPG 모드에서는 별도 플레이어명

    @Builder.Default
    private Integer level = 1;     // RPG 모드 레벨 시스템

    @Builder.Default
    private Integer experience = 0; // RPG 모드 경험치

    @Builder.Default
    private Integer fielding = 50; // RPG 모드 수비력

    @Builder.Default
    private Integer arm = 50;      // RPG 모드 어깨

    @Column(length = 200)
    private String specialTraits;  // RPG 모드 특성

    // 공통 시간 정보
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

    // 편의 메서드
    public boolean isHitterMode() {
        return "HITTER".equals(mode);
    }

    public boolean isRpgMode() {
        return "RPG".equals(mode);
    }

    // RPG 모드용 팩토리 메서드
    public static CustomPlayer createRpgPlayer(Integer userId, String playerName) {
        return CustomPlayer.builder()
            .mode("RPG")
            .userId(userId)
            .playerName(playerName)
            .level(1)
            .experience(0)
            .build();
    }

    // 히터 모드용 팩토리 메서드
    public static CustomPlayer createHitterPlayer(String name, Integer age, String position) {
        return CustomPlayer.builder()
            .mode("HITTER")
            .name(name)
            .age(age)
            .position(position)
            .build();
    }
}