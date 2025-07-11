package com.kepg.BaseBallLOCK.modules.review.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GameInfoDTO {

    private Integer scheduleId; 
    private String homeTeamName; 
    private String awayTeamName; 
    private String homeTeamLogo;
    private String awayTeamLogo;
    private Integer homeScore;
    private Integer awayScore;
}