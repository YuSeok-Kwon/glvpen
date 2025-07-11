package com.kepg.BaseBallLOCK.modules.game.record.dto;

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
}