package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@AllArgsConstructor @NoArgsConstructor
@Getter @Setter
public class UserLineupDTO {
    private Integer playerId;
    private String playerName;
    private String position;
    private String orderNum;
    private Integer season;
}
