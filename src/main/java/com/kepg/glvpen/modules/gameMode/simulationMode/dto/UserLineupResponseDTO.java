package com.kepg.glvpen.modules.gameMode.simulationMode.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class UserLineupResponseDTO {
    private Integer id;
    private Integer userId;
    private Integer playerId;
    private String position;
    private Integer orderNum;
    private Integer season;
    
    public static UserLineupResponseDTO fromEntity(com.kepg.glvpen.modules.gameMode.simulationMode.domain.UserLineup entity) {
        return UserLineupResponseDTO.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .playerId(entity.getPlayerId())
                .position(entity.getPosition())
                .orderNum(entity.getOrderNum())
                .season(entity.getSeason())
                .build();
    }
}
