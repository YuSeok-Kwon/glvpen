package com.kepg.glvpen.modules.player.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class TopPlayerCardView {
    private String name;
    private String position;
    private double war;
    private int warRank;

    // 타자용
    private Double avg;
    private Integer hr;
    private Double ops;

    // 투수용
    private Double era;
    private Double whip;
    private String bestStatLabel;  // W, SV, HOLD 중 최고
    private Integer bestStatValue;
}
