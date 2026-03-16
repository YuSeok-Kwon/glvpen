package com.kepg.glvpen.modules.analysis.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChartDataDTO {
    private String chartType; // bar, line, radar, stackedBar
    private String title;
    private List<String> labels;
    private List<DatasetDTO> datasets;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DatasetDTO {
        private String label;
        private List<Double> data;
        private String backgroundColor;
        private String borderColor;
        private String yAxisID;
    }
}
