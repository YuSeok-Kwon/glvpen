package com.kepg.glvpen.modules.game.schedule.dto;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import com.kepg.glvpen.modules.game.highlight.dto.GameHighlightDTO;
import com.kepg.glvpen.modules.game.keyPlayer.domain.GameKeyPlayer;
import com.kepg.glvpen.modules.game.lineUp.dto.BatterLineupDTO;
import com.kepg.glvpen.modules.game.record.dto.BatterRecordDTO;
import com.kepg.glvpen.modules.game.record.dto.PitcherRecordDTO;
import com.kepg.glvpen.modules.game.summaryRecord.domain.GameSummaryRecord;

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
    private Integer id;
    private Integer externalId;
    private Timestamp matchDate;
    private String stadium;
    private String status;
    
    private Integer homeTeamId;
    private Integer awayTeamId;
    private String homeTeamName;
    private String homeTeamLogo;
    private Integer homeTeamScore;
    private List<Integer> homeInningScores;
    private Integer homeR;
    private Integer homeH;
    private Integer homeE;
    private Integer homeB;

    private String awayTeamName;
    private String awayTeamLogo;
    private Integer awayTeamScore;
    private List<Integer> awayInningScores;
    private Integer awayR;
    private Integer awayH;
    private Integer awayE;
    private Integer awayB;

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

    private List<GameKeyPlayer> mvpPlayers;
    private List<GameKeyPlayer> keyMoments;

    // 전체 키 플레이어 (metric별 그룹)
    private Map<String, List<GameKeyPlayer>> keyPlayersByMetric;

    // 상세기록 (결승타, 홈런, 2루타, 도루, 심판 등)
    private List<GameSummaryRecord> summaryRecords;

    // 스코어보드 메타정보
    private String crowd;
    private String startTime;
    private String endTime;
    private String gameTime;
}
