package com.kepg.BaseBallLOCK.modules.gameMode.realMatch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RealMatchGameDTO {
    private Long scheduleId;
    private String homeTeamName;
    private String awayTeamName;
    private String stadium;
    private LocalDateTime gameTime;
    private LocalDate gameDate;
    private String status; // "scheduled", "completed", "cancelled"
    private Integer homeScore;
    private Integer awayScore;
    private boolean hasLineup; // 라인업 데이터 존재 여부
}
