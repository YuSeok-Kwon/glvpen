package com.kepg.glvpen.modules.futures.stats.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class FuturesBatterStatsDTO {
    private int playerId;
    private int season;
    private String league;
    private String category;
    private double value;
    private Integer ranking;
}
