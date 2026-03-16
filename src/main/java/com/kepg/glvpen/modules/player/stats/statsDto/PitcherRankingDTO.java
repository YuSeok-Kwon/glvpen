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
public class PitcherRankingDTO {
	private String playerName;  // 선수 이름
    private String teamName;    // 팀 이름
    private String logoName;    // 로고 이름
    
    private Double era;         // ERA 
    private Double whip;        // WHIP 
    private Double ip;          // IP 
    private Double wins;       // wins 
    private Double loses;
    private Double saves;      // saves 
    private Double holds;      // holds 
    private Double war;         // WAR 
    private Double so;         // strikeouts 
    private Double bb;         // walks 
    private Double h;          // hits 
    private Double hr;         // homeruns
    private Double fip;        // FIP
    private Double xfip;       // xFIP
    private Double k9;         // K/9
    private Double bb9;        // BB/9
    private Double g;          // 경기 수
    private Double wpct;       // 승률
    private Double hbp;        // 사구
    private Double r;          // 실점
    private Double er;         // 자책점
    private Double cg;         // 완투
    private Double sho;        // 완봉
    private Double qs;         // QS
    private Double bsv;        // 블론세이브
    private Double tbf;        // 타자상대수
    private Double np;         // 투구수
    private Double avg;        // 피안타율
    private Double twoB;       // 피2루타
    private Double threeB;     // 피3루타
    private Double sac;        // 희생번트
    private Double sf;         // 희생플라이
    private Double ibb;        // 고의사구
    private Double wp;         // 폭투
    private Double bk;         // 보크
}