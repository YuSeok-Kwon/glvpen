package com.kepg.glvpen.modules.gameMode.simulationMode.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class InningResultDTO {
    private int inningNumber;

    private List<String> userPlays = new ArrayList<>();
    private int userScore;

    private List<String> botPlays = new ArrayList<>();
    private int botScore;
}
