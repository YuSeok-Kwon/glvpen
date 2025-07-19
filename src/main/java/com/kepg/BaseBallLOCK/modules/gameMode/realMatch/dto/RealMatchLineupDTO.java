package com.kepg.BaseBallLOCK.modules.gameMode.realMatch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RealMatchLineupDTO {
    private Long scheduleId;
    private String homeTeamName;
    private String awayTeamName;
    private List<LineupPlayerDTO> homeLineup;
    private List<LineupPlayerDTO> awayLineup;
    private String homeStarterPitcher;
    private String awayStarterPitcher;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class LineupPlayerDTO {
    private String playerName;
    private String position;
    private Integer battingOrder;
    private String teamName;
}
