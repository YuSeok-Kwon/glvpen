package com.kepg.glvpen.modules.team.teamStats.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TeamStatRankingDTO {
    private Integer teamId;
    private String teamName;
    private String logoName;

    // 타자
    private Double ops;
    private Double avg;
    private Double hr;
    private Double rbi;
    private Double sb;
    private Double h;

    // 투수
    private Double era;
    private Double whip;
    private Double w;
    private Double sv;
    private Double so;
    private Double hld;
    private Double bb;
}
