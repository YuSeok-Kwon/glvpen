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
public class FatigueAnalysisDTO {

    private int playerId;
    private String playerName;
    private String teamName;
    private String logoName;
    private int season;
    private int totalAppearances;
    private double seasonEra;
    private double fatigueIndex;
    private List<RestGroupStats> restGroups;
    private HalfSeasonComparison halfSeason;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RestGroupStats {
        private String label;
        private int appearances;
        private double era;
        private double avgPitchCount;
        private double avgInnings;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HalfSeasonComparison {
        private double firstHalfEra;
        private int firstHalfAppearances;
        private double secondHalfEra;
        private int secondHalfAppearances;
    }

    // 피로도 랭킹용 간략 DTO
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FatigueRanking {
        private int playerId;
        private String playerName;
        private String teamName;
        private String logoName;
        private int totalAppearances;
        private double seasonEra;
        private double fatigueIndex;
        private int rank;
    }
}
