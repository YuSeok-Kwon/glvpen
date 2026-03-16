package com.kepg.glvpen.modules.futures.schedule.dto;

import java.sql.Timestamp;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class FuturesScheduleDTO {
    private int season;
    private Timestamp matchDate;
    private Integer homeTeamId;
    private Integer awayTeamId;
    private Integer homeTeamScore;
    private Integer awayTeamScore;
    private String stadium;
    private String status;
    private String leagueType;
    private String note;
}
