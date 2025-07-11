package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.card.playerCard.dto;

import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.userLineup.domain.UserLineup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlayerCardOverallDTO {
	
	private Integer id;

    private Integer playerId;
    private Integer season;
    private String teamName;
    private String logoName;
    private String playerName;
    private String position;

    private String type;
    private Integer overall;
    private String grade;
    
    private UserLineup lineup;
    
    // 타자용
    private Double war;
    private Double avg;
    private Integer hr;
    private Double ops;
    private Integer sb;
    private Integer power;       // 파워
    private Integer contact;     // 정확
    private Integer discipline;  // 선구
    private Integer speed;       // 주루
    
    // 투수용
    private Double era;
    private Double whip;
    private Integer wins;
    private Integer saves;
    private Integer holds;
    private Integer control;     // 제구
    private Integer stuff;       // 구위
    private Integer stamina;     // 체력
    


}
