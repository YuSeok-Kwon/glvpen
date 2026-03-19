package com.kepg.glvpen.modules.analysis.dto;

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
public class WinPredictionDTO {

    private int homeTeamId;
    private String homeTeamName;
    private String homeTeamLogo;
    private int awayTeamId;
    private String awayTeamName;
    private String awayTeamLogo;
    private double homeWinProbability;
    private double awayWinProbability;
    private double modelAccuracy;
    private int season;
    private Map<String, Double> featureDeltas;  // ERA차, OPS차, 타자WAR차, 투수WAR차 등

    // 모델 학습 결과
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrainResult {
        private int trainingSamples;
        private double accuracy;
        private String trainedSeasons;
    }
}
