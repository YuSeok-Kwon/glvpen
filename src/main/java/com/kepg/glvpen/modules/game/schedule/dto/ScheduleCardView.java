package com.kepg.glvpen.modules.game.schedule.dto;

import java.sql.Timestamp;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
@Getter
public class ScheduleCardView {
	private Integer id;
	private Integer statizId;
	private Timestamp matchDate;
    private String stadium;
    private String homeTeamName;
    private String homeTeamLogo;
    private Integer homeTeamScore;
    private String awayTeamName;
    private String awayTeamLogo;
    private Integer awayTeamScore;
    private String status;

    // 더블헤더 관련 필드
    private Boolean isDoubleHeader;      // 더블헤더 여부
    private Integer doubleHeaderSeq;     // 더블헤더 순서 (1, 2)
    private String matchTime;            // 경기 시간 (HH:mm)
}
