package com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class CustomPlayerResponseDTO {
    private Long id;
    private Integer userId;
    private String playerName;
    private Integer level;
    private Integer experience;
    private Integer power;
    private Integer contact;
    private Integer speed;
    private Integer fielding;
    private Integer arm;
    private String specialTraits;

    public static CustomPlayerResponseDTO fromEntity(com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.domain.CustomPlayer entity) {
        return CustomPlayerResponseDTO.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .playerName(entity.getPlayerName())
                .level(entity.getLevel())
                .experience(entity.getExperience())
                .power(entity.getPower())
                .contact(entity.getContact())
                .speed(entity.getSpeed())
                .fielding(entity.getFielding())
                .arm(entity.getArm())
                .specialTraits(entity.getSpecialTraits())
                .build();
    }

    /**
     * 훈련 결과 응답
     */
    @Data
    @Builder
    public static class TrainingResult {
        private Long playerId;
        private String playerName;
        private String trainingType;
        private boolean success;
        private String message;

        // 훈련 전 능력치
        private Stats statsBefore;

        // 훈련 후 능력치
        private Stats statsAfter;

        // 레벨업 여부
        private boolean leveledUp;
        private Integer currentLevel;
        private Integer currentExperience;
        private Integer experienceToNextLevel;

        // 남은 훈련 포인트
        private Integer remainingTrainingPoints;
    }

    /**
     * 능력치 정보
     */
    @Data
    @Builder
    public static class Stats {
        private Integer power;
        private Integer contact;
        private Integer speed;
        private Integer fielding;
        private Integer arm;
        private Integer defense;
    }
}
