package com.kepg.glvpen.modules.gameMode.simulationMode.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimulationRequestDTO {
    private List<PlayerCardOverallDTO> userLineup;
    private List<PlayerCardOverallDTO> botLineup;
    private String difficulty;
}
