package com.kepg.glvpen.modules.gameMode.simulationMode.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlayerCardDTO {
    private Integer playerId;
    private String playerName;
    private String teamName;
    private String teamLogo;
    private String grade;
    private String position;
    private Integer season;

    private Double war;
    private Double era;
    private Double whip;
    private Integer wins;
    private Integer saves;
    private Integer holds;

    private Double avg;
    private Double ops;
    private Integer hr;
    private Integer sb;

    // 능력치 요약
    private Integer overall;

    private Integer control;
    private Integer stuff;
    private Integer stamina;

    private Integer power;
    private Integer contact;
    private Integer discipline;
    private Integer speed;

    private String imagePath;
    private String teamColor;
    
}
