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
public class AiInsightRequestDTO {
    private String chartType;
    private String title;
    private List<String> labels;
    private List<DatasetSummary> datasets;
    private Map<String, Object> extra;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DatasetSummary {
        private String label;
        private List<Double> data;
    }
}
