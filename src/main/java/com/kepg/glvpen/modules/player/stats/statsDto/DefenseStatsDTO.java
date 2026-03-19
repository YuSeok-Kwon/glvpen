package com.kepg.glvpen.modules.player.stats.statsDto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class DefenseStatsDTO {
    private int playerId;
    private int season;
    @Builder.Default
    private String series = "0";
    private String position;
    private String category;
    private double value;
    private Integer ranking;
}
