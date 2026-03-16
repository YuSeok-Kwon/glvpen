package com.kepg.glvpen.modules.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TeamAnalysisDTO {
    private Integer teamId;
    private String teamName;
    private String logoName;
    private int season;

    // 5축 레이더 차트용
    private Double battingWar;
    private Double pitchingWar;
    private Double defenseScore;
    private Double speedScore;
    private Double powerScore;

    // 타투 밸런스
    private Double totalBatterWar;
    private Double totalPitcherWar;
    private Double winRate;
}
