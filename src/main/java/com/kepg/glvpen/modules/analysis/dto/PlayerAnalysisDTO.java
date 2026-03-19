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
public class PlayerAnalysisDTO {
    private Integer playerId;
    private String playerName;
    private String teamName;
    private String position;

    // 시즌별 지표 추이 (카테고리 -> 시즌별 값 리스트)
    private Map<String, List<SeasonValue>> seasonTrend;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SeasonValue {
        private int season;
        private Double value;
    }
}
