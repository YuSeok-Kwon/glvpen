package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.simulation.dto;

import java.util.List;

import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.card.playerCard.dto.PlayerCardOverallDTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimulationRequestDTO {
    private List<PlayerCardOverallDTO> userLineup;
    private List<PlayerCardOverallDTO> botLineup;
    private String difficulty;
}
