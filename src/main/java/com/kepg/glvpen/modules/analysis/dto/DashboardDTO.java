package com.kepg.glvpen.modules.analysis.dto;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DashboardDTO {
    private int season;
    private int totalBatters;
    private int totalPitchers;
    private int totalTeams;
    private double leagueAvgEra;
    private double leagueAvgOps;

    // 포지션별 WAR 평균
    private Map<String, Double> positionWarAvg;

    // 팀별 승률 (시즌 추이용)
    private Map<String, List<Double>> teamWinRateTrend;

    // 최근 분석 컬럼
    private List<ColumnSummaryDTO> recentColumns;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ColumnSummaryDTO {
        private Long id;
        private String title;
        private String category;
        private String publishDate;
        private Integer viewCount;
    }
}
