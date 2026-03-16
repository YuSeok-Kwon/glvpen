package com.kepg.glvpen.modules.game.highlight.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GameHighlightDTO {
    private Integer scheduleId;
    private Integer ranking;
    private String inning;
    private String pitcherName;
    private String batterName;
    private String pitchCount;
    private String result;
    private String beforeSituation;
    private String afterSituation;
}