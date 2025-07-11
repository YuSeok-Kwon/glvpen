package com.kepg.BaseBallLOCK.modules.player.stats.statsDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@Getter @Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BatterRankingDTO {
    private String playerName;
    private String teamName;
    private String logoName;
    private String position;
    private Double g;          // 경기 수
    private Double pa;         // 타석
    private Double war;     // WAR
    private Double h;          // 안타
    private Double twoB;	// 2루타
    private Double threeB;  // 3루타
    private Double hr;         // 홈런
    private Double rbi;        // 타점
    private Double sb;         // 도루
    private Double bb;         // 볼넷
    private Double so;         // 삼진
    private Double avg;     // 타율
    private Double obp;     // 출루율
    private Double slg;     // 장타율
    private Double ops;     // OPS
    private Double wrcPlus; // wRC+
}