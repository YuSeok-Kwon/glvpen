package com.kepg.glvpen.modules.player.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class TopPitcherCardView {
    private String name;
    private String position;
    private double fip;
    private int fipRank;
    private Double era;
    private Double whip;
    private String bestStatLabel;  // W, SV, HOLD 중 최고
    private Integer bestStatValue;
}
