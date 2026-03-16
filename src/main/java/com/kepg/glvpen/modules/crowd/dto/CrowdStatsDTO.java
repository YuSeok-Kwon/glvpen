package com.kepg.glvpen.modules.crowd.dto;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class CrowdStatsDTO {
    private int season;
    private LocalDate gameDate;
    private String dayOfWeek;
    private int homeTeamId;
    private int awayTeamId;
    private String stadium;
    private int crowd;
}
