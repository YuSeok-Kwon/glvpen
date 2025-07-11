package com.kepg.BaseBallLOCK.modules.game.schedule.dto;

import java.sql.Timestamp;
import java.util.List;

import com.kepg.BaseBallLOCK.modules.game.highlight.dto.GameHighlightDTO;
import com.kepg.BaseBallLOCK.modules.game.lineUp.dto.BatterLineupDTO;
import com.kepg.BaseBallLOCK.modules.game.record.dto.BatterRecordDTO;
import com.kepg.BaseBallLOCK.modules.game.record.dto.PitcherRecordDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GameDetailCardView {
    private Timestamp matchDate;
    private String stadium;
    private String status;
    
    private String homeTeamName;
    private String homeTeamLogo;
    private Integer homeScore;
    private List<Integer> homeInningScores;
    private Integer homeHits;
    private Integer homeErrors;

    private String awayTeamName;
    private String awayTeamLogo;
    private Integer awayScore;
    private List<Integer> awayInningScores;
    private Integer awayHits;
    private Integer awayErrors;

    private List<BatterLineupDTO> homeBatterLineup;
    private List<BatterLineupDTO> awayBatterLineup;

    private List<BatterRecordDTO> homeBatterRecords;
    private List<BatterRecordDTO> awayBatterRecords;
    private List<PitcherRecordDTO> homePitcherRecords;
    private List<PitcherRecordDTO> awayPitcherRecords;
    
    private String winPitcher;
    private String losePitcher;
    private List<String> holdPitchers;
    private String savePitcher;
    
    private String homeTeamColor;
    private String awayTeamColor;

    private List<GameHighlightDTO> highlights;
}
