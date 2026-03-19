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
public class DefenseRankingDTO {
    private String playerName;  // 선수 이름
    private String teamName;    // 팀 이름
    private String logoName;    // 로고 이름
    private String position;    // 포지션
    private Double g;           // 경기 수
    private Double gs;          // 선발 출장
    private Double ip;          // 이닝
    private Double e;           // 실책
    private Double pko;         // 견제사
    private Double po;          // 자살
    private Double a;           // 보살
    private Double dp;          // 병살
    private Double fpct;        // 수비율
    private Double pb;          // 포일
    private Double sb;          // 도루 허용
    private Double cs;          // 도루 저지
    private Double csPct;       // 도루 저지율
}
