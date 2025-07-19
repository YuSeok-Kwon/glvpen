package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter @Setter @Builder
public class RawPitcherScore {
	private Integer season;
    private Integer playerId;
    private double control;
    private double stuff;
    private double stamina;
    private double overall;
}
