package com.kepg.glvpen.modules.gameMode.simulationMode.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class InningOutcome {
    private List<String> plays;
    private int score;
    private int nextBatterIndex;
}
