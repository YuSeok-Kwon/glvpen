package com.kepg.glvpen.modules.game.record.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatterRecordDTO {
    private int scheduleId;
    private int teamId;
    private int playerId;
    private String playerName;
    private int pa;
    private int ab;
    private int hits;
    private int rbi;
    private int hr;
    private int sb;
    private int so;
    private int bb;
}
