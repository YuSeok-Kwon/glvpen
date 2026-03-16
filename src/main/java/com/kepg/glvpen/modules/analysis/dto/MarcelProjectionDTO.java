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
public class MarcelProjectionDTO {

    private int playerId;
    private String playerName;
    private String teamName;
    private String logoName;
    private String playerType;  // "batter" or "pitcher"
    private int targetSeason;
    private double confidence;  // 0.0 ~ 1.0 (데이터 충분성)
    private List<ProjectionDetail> projections;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectionDetail {
        private String category;
        private double projected;
        private Double lastSeason;
        private Double leagueAvg;
        private Double changeRate;  // (projected - lastSeason) / lastSeason * 100
    }

    // 전체 타자 예측 랭킹용
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectionRanking {
        private int playerId;
        private String playerName;
        private String teamName;
        private String logoName;
        private double projectedWar;
        private Double lastSeasonWar;
        private double confidence;
        private int rank;
    }
}
