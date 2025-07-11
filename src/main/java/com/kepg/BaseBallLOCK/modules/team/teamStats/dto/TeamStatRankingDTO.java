package com.kepg.BaseBallLOCK.modules.team.teamStats.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TeamStatRankingDTO {
    private Integer teamId;
    private String teamName;
    private String logoName;

    private Double totalWar;
    private Double ops;
    private Double avg;
    private Double hr;
    private Double sb;
    private Double betterWar;
    private Double pitcherWar;
    private Double so;
    private Double w;
    private Double h;
    private Double sv;
    private Double era;
    private Double whip;
    private Double bb;
    private Double battingWaa;
    private Double baserunningWaa;
    private Double defenseWaa;
    private Double startingWaa;
    private Double bullpenWaa;
}