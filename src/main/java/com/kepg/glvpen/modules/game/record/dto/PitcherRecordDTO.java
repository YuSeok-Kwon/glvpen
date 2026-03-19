package com.kepg.glvpen.modules.game.record.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PitcherRecordDTO {
    private int scheduleId;
    private int teamId;
    private int playerId;
    private String playerName;
    private double innings;
    private int strikeouts;
    private int bb;
    private int hbp;
    private int runs;
    private int earnedRuns;
    private int hits;
    private int hr;
    private String decision;

    /**
     * 이닝을 야구 표기법으로 반환 (예: 5.333→"5 1/3", 1.667→"1 2/3", 7.0→"7")
     */
    public String getFormattedInnings() {
        int whole = (int) innings;
        double frac = innings - whole;
        if (frac < 0.1) {
            return String.valueOf(whole);
        } else if (frac < 0.4) {
            return whole == 0 ? "1/3" : whole + " 1/3";
        } else if (frac < 0.7) {
            return whole == 0 ? "2/3" : whole + " 2/3";
        } else {
            return String.valueOf(whole + 1);
        }
    }
}