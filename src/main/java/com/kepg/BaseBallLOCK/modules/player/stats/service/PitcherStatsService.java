package com.kepg.BaseBallLOCK.modules.player.stats.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.BaseBallLOCK.common.enums.PitcherSortType;
import com.kepg.BaseBallLOCK.common.enums.SortDirection;
import com.kepg.BaseBallLOCK.modules.game.schedule.service.ScheduleService;
import com.kepg.BaseBallLOCK.modules.player.dto.TopPitcherCardView;
import com.kepg.BaseBallLOCK.modules.player.service.PlayerService;
import com.kepg.BaseBallLOCK.modules.player.stats.domain.PitcherStats;
import com.kepg.BaseBallLOCK.modules.player.stats.repository.PitcherStatsRepository;
import com.kepg.BaseBallLOCK.modules.player.stats.statsDto.PitcherRankingDTO;
import com.kepg.BaseBallLOCK.modules.player.stats.statsDto.PitcherStatsDTO;
import com.kepg.BaseBallLOCK.modules.player.stats.statsDto.PitcherTopDTO;

import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class PitcherStatsService {

	private final PitcherStatsRepository pitcherStatsRepository;
	private final PlayerService playerService;
	private final ScheduleService scheduleService;

	// Magic Number 상수화
	private static final double STAT_NOT_AVAILABLE = -1.0;
	private static final int STAT_NOT_AVAILABLE_INT = -1;
	private static final int PITCHER_STATS_COLUMN_COUNT = 16;
	private static final int CURRENT_SEASON = 2025;
	private static final int STANDARD_QUALIFIED_IP = 144;

	// Logger 추가
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PitcherStatsService.class);
	
	// 투수 스탯 저장 또는 업데이트 (playerId + season + category 기준 중복 확인)
	@Transactional
	public void savePitcherStats(PitcherStatsDTO dto) {
	    Optional<PitcherStats> optional = pitcherStatsRepository
	            .findByPlayerIdAndSeasonAndCategory(dto.getPlayerId(), dto.getSeason(), dto.getCategory());

	    optional.ifPresent(existing -> {
	        pitcherStatsRepository.delete(existing);
	        log.info("[DELETE-AND-INSERT] 기존 데이터 삭제: playerId={}, category={}", dto.getPlayerId(), dto.getCategory());
	    });

	    PitcherStats entity = PitcherStats.builder()
	            .playerId(dto.getPlayerId())
	            .season(dto.getSeason())
	            .category(dto.getCategory())
	            .value(dto.getValue())
	            .ranking(dto.getRanking())
	            .position(dto.getPosition())
	            .build();

	    pitcherStatsRepository.save(entity);
	    log.info("[INSERT] playerId={}, category={}, value={}", dto.getPlayerId(), dto.getCategory(), dto.getValue());
	}
	
	// 팀별 WAR 1위 투수 조회 (ERA, WHIP, W/SV/HLD 중 최고값 포함)
	public TopPitcherCardView getTopPitcher(int teamId, int season) {
	    List<Object[]> result = playerService.getTopPitcherByTeamAndSeason(teamId, season);
	    if (result.isEmpty()) {
	        return null;
	    }

	    Object[] row = result.get(0);

	    String name = (String) row[0];
	    String position = (String) row[1];
	    double war = 0.0;
	    int ranking = 0;
	    int playerId = 0;

	    if (row[2] != null) {
	        war = Double.parseDouble(row[2].toString());
	    }

	    if (row[3] != null) {
	        ranking = Integer.parseInt(row[3].toString());
	    }

	    if (row[4] != null) {
	        playerId = Integer.parseInt(row[4].toString());
	    }

	    // Optional 체이닝 패턴으로 개선
	    double era = pitcherStatsRepository.findStatValueByPlayerIdCategoryAndSeason(playerId, "ERA", season)
	            .map(Double::parseDouble)
	            .orElse(STAT_NOT_AVAILABLE);

	    double whip = pitcherStatsRepository.findStatValueByPlayerIdCategoryAndSeason(playerId, "WHIP", season)
	            .map(Double::parseDouble)
	            .orElse(STAT_NOT_AVAILABLE);

	    Map<String, Integer> statMap = new HashMap<>();
	    for (String cat : List.of("W", "SV", "HLD")) {
	        pitcherStatsRepository.findStatValueByPlayerIdCategoryAndSeason(playerId, cat, season)
	                .map(value -> (int) Double.parseDouble(value))
	                .ifPresent(statValue -> statMap.put(cat, statValue));
	    }

	    String bestStatLabel = "-";
	    int bestStatValue = STAT_NOT_AVAILABLE_INT;

	    for (Map.Entry<String, Integer> entry : statMap.entrySet()) {
	        if (entry.getValue() > bestStatValue) {
	            bestStatLabel = entry.getKey();
	            bestStatValue = entry.getValue();
	        }
	    }

	    return TopPitcherCardView.builder()
	            .name(name)
	            .position(position)
	            .war(war)
	            .warRank(ranking)
	            .era(era)
	            .whip(whip)
	            .bestStatLabel(bestStatLabel)
	            .bestStatValue(bestStatValue)
	            .build();
	}

	// 기록별(ERA, IP, WHIP, WAR 등) 1위 투수 조회
	public List<PitcherTopDTO> getTopPitchers(int season) {
	    List<Object[]> ObjectLists = pitcherStatsRepository.findTopPitchersAsTuple(season);
	    List<PitcherTopDTO> result = new ArrayList<>();

	    for (Object[] list : ObjectLists) {
	        String category = (String) list[5]; // category
	        String playerName = (String) list[1]; // playerName
	        String teamName = (String) list[2]; // teamName
	        String logoName = (String) list[3]; // logoName
	        Double value = (Double) list[4]; // value

	        result.add(new PitcherTopDTO(category, playerName, teamName, logoName, value));
	    }

	    return result;
	}

	// 전체 투수 랭킹 리스트 정렬 (규정 조건 없이)
	public List<PitcherRankingDTO> getPitcherRankingsSorted(int season, String sort, String direction) {
	    PitcherSortType sortType = PitcherSortType.fromString(sort);
	    SortDirection sortDirection = SortDirection.fromString(direction);

	    List<Object[]> projections = pitcherStatsRepository.findAllPitchers(season);
	    List<PitcherRankingDTO> result = new ArrayList<>();


	    if (projections != null && !projections.isEmpty()) {
	        for (Object[] row : projections) {

	            if (row.length == PITCHER_STATS_COLUMN_COUNT) { 

	                String playerName = (String) row[0];
	                String teamName = (String) row[1];
	                String logoName = (String) row[2];

	                Double era = getDoubleOrDefault(row[3], 0.0);
	                Double whip = getDoubleOrDefault(row[4], 0.0);
	                Double wins = getDoubleOrDefault(row[5], 0.0);
	                Double losses = getDoubleOrDefault(row[6], 0.0);
	                Double saves = getDoubleOrDefault(row[7], 0.0);
	                Double holds = getDoubleOrDefault(row[8], 0.0);
	                Double strikeouts = getDoubleOrDefault(row[9], 0.0);
	                Double walks = getDoubleOrDefault(row[10], 0.0);
	                Double hitsAllowed = getDoubleOrDefault(row[11], 0.0);
	                Double homeRunsAllowed = getDoubleOrDefault(row[12], 0.0);
	                Double inningsPitched = getDoubleOrDefault(row[13], 0.0);
	                Double war = getDoubleOrDefault(row[14], 0.0);

	                PitcherRankingDTO dto = PitcherRankingDTO.builder()
	                        .playerName(playerName)
	                        .teamName(teamName)
	                        .logoName(logoName)
	                        .era(era)
	                        .whip(whip)
	                        .ip(inningsPitched)        // 이닝
	                        .wins(wins)                // 승
	                        .loses(losses)             // 패
	                        .saves(saves)              // 세이브
	                        .holds(holds)              // 홀드
	                        .so(strikeouts)            // 삼진
	                        .bb(walks)                 // 볼넷
	                        .h(hitsAllowed)            // 피안타
	                        .hr(homeRunsAllowed)       // 피홈런
	                        .war(war)                  // WAR
	                        .build();

	                result.add(dto);
	            }
	        }
	    }
	    sortPitcherRankingList(result, sortType, sortDirection);

	    return result;
	}
	
	// 규정 이닝을 충족한 투수 랭킹 리스트 조회 및 정렬
	public List<PitcherRankingDTO> getQualifiedPitchers(int season, String sort, String direction) {
	    PitcherSortType sortType = PitcherSortType.fromString(sort);
	    SortDirection sortDirection = SortDirection.fromString(direction);

	    // 팀별 경기 수를 가져오기
	    Map<Integer, Integer> teamGamesMap = scheduleService.getTeamGamesPlayedBySeason(season);

	    // 투수 전체 기록 가져오기 (조건 없이)
	    List<Object[]> projections = pitcherStatsRepository.findAllPitchers(season);

	    List<PitcherRankingDTO> result = new ArrayList<>();

	    if (projections != null && !projections.isEmpty()) {
	        for (Object[] row : projections) {

	            if (row.length == PITCHER_STATS_COLUMN_COUNT) {
	                String playerName = (String) row[0];
	                String teamName = (String) row[1];
	                String logoName = (String) row[2];
	                Double era = getDoubleOrDefault(row[3], 0.0);
	                Double whip = getDoubleOrDefault(row[4], 0.0);
	                Double wins = getDoubleOrDefault(row[5], 0.0);
	                Double losses = getDoubleOrDefault(row[6], 0.0);
	                Double saves = getDoubleOrDefault(row[7], 0.0);
	                Double holds = getDoubleOrDefault(row[8], 0.0);
	                Double strikeouts = getDoubleOrDefault(row[9], 0.0);
	                Double walks = getDoubleOrDefault(row[10], 0.0);
	                Double hitsAllowed = getDoubleOrDefault(row[11], 0.0);
	                Double homeRunsAllowed = getDoubleOrDefault(row[12], 0.0);
	                Double inningsPitched = getDoubleOrDefault(row[13], 0.0);
	                Double war = getDoubleOrDefault(row[14], 0.0);
	                int teamId = (int) getDoubleOrDefault(row[15], 0.0);

	                // 규정이닝 계산
	                int teamGames = teamGamesMap.getOrDefault(teamId, 0);
	                int requiredIP = getQualifiedInnings(season, teamGames);

	                boolean isQualified = inningsPitched >= requiredIP;

	                if (isQualified) {
	                    PitcherRankingDTO dto = PitcherRankingDTO.builder()
	                            .playerName(playerName)
	                            .teamName(teamName)
	                            .logoName(logoName)
	                            .era(era)
	                            .whip(whip)
	                            .ip(inningsPitched)
	                            .wins(wins)
	                            .loses(losses)
	                            .saves(saves)
	                            .holds(holds)
	                            .so(strikeouts)
	                            .bb(walks)
	                            .h(hitsAllowed)
	                            .hr(homeRunsAllowed)
	                            .war(war)
	                            .build();
	                    result.add(dto);
	                }
	            }
	        }
	    }

	    sortPitcherRankingList(result, sortType, sortDirection);

	    return result;
	}

	// PitcherRankingDTO 정렬 기준 값 추출용 메서드
	private double getSortValue(PitcherRankingDTO dto, PitcherSortType sortType) {
	    return switch (sortType) {
	        case ERA -> dto.getEra() != null ? dto.getEra() : Double.MAX_VALUE;
	        case WHIP -> dto.getWhip() != null ? dto.getWhip() : Double.MAX_VALUE;
	        case IP -> dto.getIp() != null ? dto.getIp() : Double.MIN_VALUE;
	        case W -> dto.getWins() != null ? dto.getWins() : Double.MIN_VALUE;
	        case L -> dto.getLoses() != null ? dto.getLoses() : Double.MIN_VALUE;
	        case SV -> dto.getSaves() != null ? dto.getSaves() : Double.MIN_VALUE;
	        case HLD -> dto.getHolds() != null ? dto.getHolds() : Double.MIN_VALUE;
	        case SO -> dto.getSo() != null ? dto.getSo() : Double.MIN_VALUE;
	        case BB -> dto.getBb() != null ? dto.getBb() : Double.MIN_VALUE;
	        case H -> dto.getH() != null ? dto.getH() : Double.MIN_VALUE;
	        case HR -> dto.getHr() != null ? dto.getHr() : Double.MIN_VALUE;
	        case WAR -> dto.getWar() != null ? dto.getWar() : Double.MIN_VALUE;
	    };
	}

	// 투수 랭킹 정렬 (Enum 기반) - O(n log n)
	public void sortPitcherRankingList(List<PitcherRankingDTO> list, PitcherSortType sortType, SortDirection direction) {
	    if (sortType == null || direction == null) return;

	    Comparator<PitcherRankingDTO> comparator = Comparator.comparingDouble(dto -> getSortValue(dto, sortType));

	    if (direction == SortDirection.DESC) {
	        comparator = comparator.reversed();
	    }

	    Collections.sort(list, comparator);
	}
	
	// 시즌 및 팀 경기 수 기준 규정 이닝 계산
	public int getQualifiedInnings(int season, int teamGames) {
	    if (season == CURRENT_SEASON) {
	        return teamGames;
	    } else {
	        return STANDARD_QUALIFIED_IP;
	    }
	}
	
	// 해당 playerId와 시즌에 대한 기록 존재 여부 확인
	public boolean existsByPlayerIdAndSeason(int playerId, int season) {
    	return pitcherStatsRepository.existsByPlayerIdAndSeason(playerId, season);
    }

	// 헬퍼 메서드: 배열 요소를 Double로 변환 (null 안전)
	private double getDoubleOrDefault(Object value, double defaultValue) {
	    return value != null ? ((Number) value).doubleValue() : defaultValue;
	}
}

