package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.card.userCard.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCardViewDTO {
    private Integer playerId;
    private String playerName;
    private String teamName;
    private String teamColor;
    private String teamLogo;
    private String position;
    private Integer season;
    private String grade; // S, A, B, C
    private String imagePath;

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
    
    private Integer overall;

    
}