package com.kepg.glvpen.modules.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiInsightResponseDTO {
    private String chartType;
    private String insight;
    private boolean success;
    private String errorMessage;
}
