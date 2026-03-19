package com.kepg.glvpen.modules.analysis.dto;

import java.util.List;

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
public class ClutchIndexDTO {

    private int season;
    private List<ClutchPlayer> rankings;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClutchPlayer {
        private int playerId;
        private String playerName;
        private String teamName;
        private String logoName;
        private Double rbi;
        private Double hr;
        private Double ops;
        private double clutchIndex;
        private int rank;
        private double percentile;
    }
}
