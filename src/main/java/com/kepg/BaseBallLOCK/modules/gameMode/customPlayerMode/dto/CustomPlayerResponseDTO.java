package com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto;

import lombok.Builder;
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
}
