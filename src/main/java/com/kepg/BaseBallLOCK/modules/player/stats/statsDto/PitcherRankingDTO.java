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
}