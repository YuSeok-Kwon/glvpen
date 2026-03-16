package com.kepg.glvpen.modules.player.stats.statsDto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class PitcherStatsDTO {
    private int playerId;
    private int season;
    private String position;
    private String category;
    private double value;
    private Integer ranking;
    @Builder.Default
    private String series = "0";
    @Builder.Default
    private String situationType = "";
    @Builder.Default
    private String situationValue = "";
}
