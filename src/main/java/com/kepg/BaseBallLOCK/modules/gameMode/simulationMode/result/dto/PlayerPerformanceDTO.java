package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.result.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PlayerPerformanceDTO {
    public String name;
    public int hits;
    public int doublesOrTriples;
    public int homeRuns;

    public PlayerPerformanceDTO(String name) {
        this.name = name;
    }

    public void recordResult(String result) {
        switch (result) {
            case "홈런":
                homeRuns++;
                break;
            case "2루타":
            case "3루타":
                doublesOrTriples++;
                break;
            case "안타":
                hits++;
                break;
            case "삼진":
                hits--;
                break;
        }
    }

    public int getScore() {
        return hits + (2 * doublesOrTriples) + (3 * homeRuns);
    }
}