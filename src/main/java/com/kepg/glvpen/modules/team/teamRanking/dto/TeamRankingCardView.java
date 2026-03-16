package com.kepg.glvpen.modules.team.teamRanking.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TeamRankingCardView {

    private Integer ranking;

    private Integer games;
    private Integer wins;
    private Integer losses;
    private Integer draws;
    private Double winRate;
    private Double gamesBehind;

    // team 테이블 정보
    private String teamName;
    private String logoName;
}
