package com.kepg.glvpen.modules.team.teamStats.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TopStatTeamDTO {
    private String category;   
    private double value;  
    private String formattedValue;
    private int teamId;
    private String teamName;
    private String teamLogo;
}
