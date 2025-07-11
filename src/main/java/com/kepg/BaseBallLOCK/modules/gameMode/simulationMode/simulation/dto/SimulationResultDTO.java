package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.simulation.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class SimulationResultDTO {

    private List<InningResultDTO> innings = new ArrayList<>();
    private int userTotalScore;
    private int botTotalScore;
    private int scheduleId;

    // 타순별 결과 저장
    private Map<String, Map<Integer, List<String>>> outcomes = new HashMap<>();

    public SimulationResultDTO() {
        outcomes.put("user", new HashMap<>());
        outcomes.put("bot", new HashMap<>());
    }

    public void addInning(InningResultDTO inning) {
        innings.add(inning);
        userTotalScore += inning.getUserScore();
        botTotalScore += inning.getBotScore();
    }

    public void addOutcome(String team, int orderNum, String result) {
        Map<Integer, List<String>> teamOutcomes = outcomes.get(team);
        if (!teamOutcomes.containsKey(orderNum)) {
            teamOutcomes.put(orderNum, new ArrayList<>());
        }
        teamOutcomes.get(orderNum).add(result);
    }
    
    private List<String> gameLog = new ArrayList<>();
}