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
public class RunnerRankingDTO {
    private String playerName;  // 선수 이름
    private String teamName;    // 팀 이름
    private String logoName;    // 로고 이름
    private Double g;           // 경기 수
    private Double sba;         // 도루 시도
    private Double sb;          // 도루 성공
    private Double cs;          // 도루 실패
    private Double sbPct;       // 도루 성공률
    private Double oob;         // 주루사
    private Double pko;         // 견제사
}
