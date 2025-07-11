package com.kepg.BaseBallLOCK.modules.game.scoreBoard.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ScoreBoardDTO {

    private Integer scheduleId;

    private Integer homeScore;
    private Integer awayScore;

    private String homeInningScores; // 예: "1,0,2,0,0,1,0,0,0"
    private String awayInningScores;

    private Integer homeR;
    private Integer homeH;
    private Integer homeE;
    private Integer homeB;

    private Integer awayR;
    private Integer awayH;
    private Integer awayE;
    private Integer awayB;

    private String winPitcher;
    private String losePitcher;
}
