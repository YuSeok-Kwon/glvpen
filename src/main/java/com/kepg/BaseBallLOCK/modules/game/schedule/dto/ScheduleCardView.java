package com.kepg.BaseBallLOCK.modules.game.schedule.dto;

import java.sql.Timestamp;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
@Getter
public class ScheduleCardView {
	private Integer id;
	private Timestamp matchDate;
    private String stadium;
    private String homeTeamName;
    private String homeTeamLogo;
    private Integer homeTeamScore;
    private String awayTeamName;
    private String awayTeamLogo;
    private Integer awayTeamScore;
    private String status;
}
