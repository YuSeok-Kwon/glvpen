package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.card.playerCard.overall.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class RawBatterScore {
	private Integer season;
    private Integer playerId;
    private double power;
    private double contact;
    private double discipline;
    private double speed;
    private double overall;
}
