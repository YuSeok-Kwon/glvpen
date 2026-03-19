package com.kepg.glvpen.modules.analysis.dto;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerSimilarityDTO {

    private int season;
    private PlayerInfo basePlayer;
    private List<SimilarPlayer> similarPlayers;
    private List<String> radarLabels;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerInfo {
        private int playerId;
        private String playerName;
        private String teamName;
        private String logoName;
        private Map<String, Double> stats;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimilarPlayer {
        private int playerId;
        private String playerName;
        private String teamName;
        private String logoName;
        private double similarity;
        private Map<String, Double> stats;
    }
}
