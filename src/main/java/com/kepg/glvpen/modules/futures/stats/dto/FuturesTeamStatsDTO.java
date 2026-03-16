package com.kepg.glvpen.modules.futures.stats.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class FuturesTeamStatsDTO {
    private int teamId;
    private int season;
    private String league;
    private String statType;
    private String category;
    private double value;
    private Integer ranking;
}
