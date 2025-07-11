package com.kepg.BaseBallLOCK.modules.player.stats.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.BaseBallLOCK.modules.game.schedule.service.ScheduleService;
import com.kepg.BaseBallLOCK.modules.player.dto.TopPlayerCardView;
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
	
	// 투수 스탯 저장 또는 업데이트 (playerId + season + category 기준 중복 확인)
	@Transactional
	public void savePitcherStats(PitcherStatsDTO dto) {
	    Optional<PitcherStats> optional = pitcherStatsRepository
	            .findByPlayerIdAndSeasonAndCategory(dto.getPlayerId(), dto.getSeason(), dto.getCategory());

	    if (optional.isPresent()) {
	        pitcherStatsRepository.delete(optional.get());
	        System.out.println("[DELETE-AND-INSERT] 기존 데이터 삭제: playerId=" + dto.getPlayerId() + ", category=" + dto.getCategory());
	    }

	    PitcherStats entity = PitcherStats.builder()
	            .playerId(dto.getPlayerId())
	            .season(dto.getSeason())
	            .category(dto.getCategory())
	            .value(dto.getValue())
	            .ranking(dto.getRanking())
	            .position(dto.getPosition())
	            .build();

	    pitcherStatsRepository.save(entity);
	    System.out.println("[INSERT] playerId=" + dto.getPlayerId() + ", category=" + dto.getCategory()
	            + ", value=" + dto.getValue());
	}
	
	// 팀별 WAR 1위 투수 조회 (ERA, WHIP, W/SV/HLD 중 최고값 포함)
	public TopPlayerCardView getTopPitcher(int teamId, int season) {
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

	    double era = -1.0;
	    double whip = -1.0;
	    String bestStatLabel = "-";
	    int bestStatValue = -1;

	    Optional<String> eraOpt = pitcherStatsRepository.findStatValueByPlayerIdCategoryAndSeason(playerId, "ERA", season);
	    if (eraOpt.isPresent()) {
	        era = Double.parseDouble(eraOpt.get());
	    }

	    Optional<String> whipOpt = pitcherStatsRepository.findStatValueByPlayerIdCategoryAndSeason(playerId, "WHIP", season);
	    if (whipOpt.isPresent()) {
	        whip = Double.parseDouble(whipOpt.get());
	    }

	    Map<String, Integer> statMap = new HashMap<>();
	    for (String cat : List.of("W", "SV", "HLD")) {
	        Optional<String> statOpt = pitcherStatsRepository.findStatValueByPlayerIdCategoryAndSeason(playerId, cat, season);
	        if (statOpt.isPresent()) {
	            int statValue = (int) Double.parseDouble(statOpt.get());
	            statMap.put(cat, statValue);
	        }
	    }

	    for (Map.Entry<String, Integer> entry : statMap.entrySet()) {
	        if (entry.getValue() > bestStatValue) {
	            bestStatLabel = entry.getKey();
	            bestStatValue = entry.getValue();
	        }
	    }

	    return TopPlayerCardView.builder()
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

	    List<Object[]> projections = pitcherStatsRepository.findAllPitchers(season);
	    List<PitcherRankingDTO> result = new ArrayList<>();

	    
	    if (projections != null && !projections.isEmpty()) {
	        for (Object[] row : projections) {

	            if (row.length == 16) { 

	                String playerName = (String) row[0];
	                String teamName = (String) row[1];
	                String logoName = (String) row[2];

	                Double era = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;
	                Double whip = row[4] != null ? ((Number) row[4]).doubleValue() : 0.0;
	                Double wins = row[5] != null ? ((Number) row[5]).doubleValue() : 0.0;
	                Double losses = row[6] != null ? ((Number) row[6]).doubleValue() : 0.0;
	                Double saves = row[7] != null ? ((Number) row[7]).doubleValue() : 0.0;
	                Double holds = row[8] != null ? ((Number) row[8]).doubleValue() : 0.0;
	                Double strikeouts = row[9] != null ? ((Number) row[9]).doubleValue() : 0.0;
	                Double walks = row[10] != null ? ((Number) row[10]).doubleValue() : 0.0;
	                Double hitsAllowed = row[11] != null ? ((Number) row[11]).doubleValue() : 0.0;
	                Double homeRunsAllowed = row[12] != null ? ((Number) row[12]).doubleValue() : 0.0;
	                Double inningsPitched = row[13] != null ? ((Number) row[13]).doubleValue() : 0.0;
	                Double war = row[14] != null ? ((Number) row[14]).doubleValue() : 0.0;

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
	    sortPitcherRankingList(result, sort, direction);

	    return result;
	}
	
	// 규정 이닝을 충족한 투수 랭킹 리스트 조회 및 정렬
	public List<PitcherRankingDTO> getQualifiedPitchers(int season, String sort, String direction) {
	    // 팀별 경기 수를 가져오기
	    Map<Integer, Integer> teamGamesMap = scheduleService.getTeamGamesPlayedBySeason(season);

	    // 투수 전체 기록 가져오기 (조건 없이)
	    List<Object[]> projections = pitcherStatsRepository.findAllPitchers(season);

	    List<PitcherRankingDTO> result = new ArrayList<>();

	    if (projections != null && !projections.isEmpty()) {
	        for (Object[] row : projections) {

	            if (row.length == 16) {
	                String playerName = (String) row[0];
	                String teamName = (String) row[1];
	                String logoName = (String) row[2];
	                Double era = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;
	                Double whip = row[4] != null ? ((Number) row[4]).doubleValue() : 0.0;
	                Double wins = row[5] != null ? ((Number) row[5]).doubleValue() : 0.0;
	                Double losses = row[6] != null ? ((Number) row[6]).doubleValue() : 0.0;
	                Double saves = row[7] != null ? ((Number) row[7]).doubleValue() : 0.0;
	                Double holds = row[8] != null ? ((Number) row[8]).doubleValue() : 0.0;
	                Double strikeouts = row[9] != null ? ((Number) row[9]).doubleValue() : 0.0;
	                Double walks = row[10] != null ? ((Number) row[10]).doubleValue() : 0.0;
	                Double hitsAllowed = row[11] != null ? ((Number) row[11]).doubleValue() : 0.0;
	                Double homeRunsAllowed = row[12] != null ? ((Number) row[12]).doubleValue() : 0.0;
	                Double inningsPitched = row[13] != null ? ((Number) row[13]).doubleValue() : 0.0;
	                Double war = row[14] != null ? ((Number) row[14]).doubleValue() : 0.0;
	                int teamId = row[15] != null ? ((Number) row[15]).intValue() : 0;

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

	    sortPitcherRankingList(result, sort, direction);

	    return result;
	}
	
	// PitcherRankingDTO 정렬 기준 값 추출용 메서드
	private double getSortValue(PitcherRankingDTO dto, String sortKey) {
	    if ("ERA".equalsIgnoreCase(sortKey)) return dto.getEra() != null ? dto.getEra() : Double.MAX_VALUE;
	    if ("WHIP".equalsIgnoreCase(sortKey)) return dto.getWhip() != null ? dto.getWhip() : Double.MAX_VALUE;
	    if ("IP".equalsIgnoreCase(sortKey)) return dto.getIp() != null ? dto.getIp() : Double.MIN_VALUE;
	    if ("W".equalsIgnoreCase(sortKey)) return dto.getWins() != null ? dto.getWins() : Double.MIN_VALUE;
	    if ("L".equalsIgnoreCase(sortKey)) return dto.getLoses() != null ? dto.getLoses() : Double.MIN_VALUE;
	    if ("SV".equalsIgnoreCase(sortKey)) return dto.getSaves() != null ? dto.getSaves() : Double.MIN_VALUE;
	    if ("HLD".equalsIgnoreCase(sortKey)) return dto.getHolds() != null ? dto.getHolds() : Double.MIN_VALUE;
	    if ("SO".equalsIgnoreCase(sortKey)) return dto.getSo() != null ? dto.getSo() : Double.MIN_VALUE;
	    if ("BB".equalsIgnoreCase(sortKey)) return dto.getBb() != null ? dto.getBb() : Double.MIN_VALUE;
	    if ("H".equalsIgnoreCase(sortKey)) return dto.getH() != null ? dto.getH() : Double.MIN_VALUE;
	    if ("HR".equalsIgnoreCase(sortKey)) return dto.getHr() != null ? dto.getHr() : Double.MIN_VALUE;
	    if ("WAR".equalsIgnoreCase(sortKey)) return dto.getWar() != null ? dto.getWar() : Double.MIN_VALUE;
	    return 0.0;
	}
	
	// 투수 랭킹 수동 정렬 (정렬 키 및 방향 기반)
	public void sortPitcherRankingList(List<PitcherRankingDTO> list, String sort, String direction) {
	    if (sort == null || direction == null) return;

	    String sortKey = sort.trim().toUpperCase();
	    String sortDirection = direction.trim().toUpperCase();

	    for (int i = 0; i < list.size() - 1; i++) {
	        for (int j = 0; j < list.size() - i - 1; j++) {
	            boolean shouldSwap = false;

	            double value1 = getSortValue(list.get(j), sortKey);
	            double value2 = getSortValue(list.get(j + 1), sortKey);

	            if (sortDirection.equals("ASC")) {
	                if (value1 > value2) {
	                    shouldSwap = true;
	                }
	            } else { // DESC
	                if (value1 < value2) {
	                    shouldSwap = true;
	                }
	            }

	            if (shouldSwap) {
	                PitcherRankingDTO temp = list.get(j);
	                list.set(j, list.get(j + 1));
	                list.set(j + 1, temp);
	            }
	        }
	    }
	}
	
	// 시즌 및 팀 경기 수 기준 규정 이닝 계산
	public int getQualifiedInnings(int season, int teamGames) {
	    if (season == 2025) {
	        return teamGames;
	    } else {
	        return 144;
	    }
	}
	
	// 해당 playerId와 시즌에 대한 기록 존재 여부 확인
	public boolean existsByPlayerIdAndSeason(int playerId, int season) {
    	return pitcherStatsRepository.existsByPlayerIdAndSeason(playerId, season);
    }
}

