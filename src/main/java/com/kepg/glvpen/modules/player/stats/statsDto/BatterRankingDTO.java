package com.kepg.glvpen.modules.player.stats.statsDto;

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
    private Double babip;   // BABIP
    private Double iso;     // ISO (Isolated Power)
    private Double kRate;   // K% (삼진율)
    private Double bbRate;  // BB% (볼넷율)
    private Double ab;      // 타수
    private Double r;       // 득점
    private Double tb;      // 루타
    private Double sac;     // 희생번트
    private Double sf;      // 희생플라이
    private Double ibb;     // 고의사구
    private Double hbp;     // 사구
    private Double gdp;     // 병살
    private Double mh;      // 멀티히트
    private Double risp;    // 득점권타율
    private Double phBa;    // 대타타율
}