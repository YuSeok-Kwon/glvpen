package com.kepg.BaseBallLOCK.modules.player.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class TopBatterCardView {
    private String name;
    private String position;
    private double war;
    private int warRank;
    private Double avg;
    private Integer hr;
    private Double ops;
}
