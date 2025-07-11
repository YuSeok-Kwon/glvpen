package com.kepg.BaseBallLOCK.modules.player.stats.service;

import java.util.ArrayList;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.BaseBallLOCK.modules.game.schedule.service.ScheduleService;
import com.kepg.BaseBallLOCK.modules.player.dto.TopPlayerCardView;
import com.kepg.BaseBallLOCK.modules.player.service.PlayerService;
import com.kepg.BaseBallLOCK.modules.player.stats.domain.BatterStats;
import com.kepg.BaseBallLOCK.modules.player.stats.repository.BatterStatsRepository;
import com.kepg.BaseBallLOCK.modules.player.stats.statsDto.BatterRankingDTO;
import com.kepg.BaseBallLOCK.modules.player.stats.statsDto.BatterStatsDTO;
import com.kepg.BaseBallLOCK.modules.player.stats.statsDto.BatterTopDTO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class BatterStatsService {

    private final BatterStatsRepository batterStatsRepository;
    private final PlayerService playerService; 
	private final ScheduleService scheduleService;

	// 타자 스탯 저장 또는 업데이트 (playerId + season + category 기준 중복 확인)
	public void saveBatterStats(BatterStatsDTO dto) {
	    Optional<BatterStats> optional = batterStatsRepository.findByPlayerIdAndSeasonAndCategory(dto.getPlayerId(), dto.getSeason(), dto.getCategory());

	    if (optional.isPresent()) {
	        batterStatsRepository.delete(optional.get());
	        System.out.println("[DELETE-AND-INSERT] 기존 데이터 삭제: playerId=" + dto.getPlayerId() + ", category=" + dto.getCategory());
	    }

	    BatterStats entity = BatterStats.builder()
	            .playerId(dto.getPlayerId())
	            .season(dto.getSeason())
	            .position(dto.getPosition())
	            .category(dto.getCategory())
	            .value(dto.getValue())
	            .ranking(dto.getRanking())
	            .build();

	    batterStatsRepository.save(entity);
	    System.out.println("[INSERT] playerId=" + dto.getPlayerId() + ", category=" + dto.getCategory()
	            + ", value=" + dto.getValue());
	}
    
    // 팀별 WAR 1위 타자 조회 (AVG, OPS, HR 포함)
    public TopPlayerCardView getTopHitter(int teamId, int season) {
        List<Object[]> result = playerService.getTopHitterByTeamAndSeason(teamId, season);
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

        double avg = -1.0;
        int hr = 1;
        double ops = -1.0;

        Optional<String> avgOptional = batterStatsRepository.findStatValueByPlayerIdCategoryAndSeason(playerId, "AVG", season);
        if (avgOptional.isPresent()) {
            avg = Double.parseDouble(avgOptional.get());
        }

        Optional<String> hrOptional = batterStatsRepository.findStatValueByPlayerIdCategoryAndSeason(playerId, "HR", season);
        if (hrOptional.isPresent()) {
            String rawHr = hrOptional.get().trim();
            try {
                hr = (int) Double.parseDouble(rawHr);
            } catch (NumberFormatException e) {
                System.out.println("오류: " + rawHr);
            }
        }

        Optional<String> opsOptional = batterStatsRepository.findStatValueByPlayerIdCategoryAndSeason(playerId, "OPS", season);
        if (opsOptional.isPresent()) {
            ops = Double.parseDouble(opsOptional.get());
        }

        return TopPlayerCardView.builder()
                .name(name)
                .position(position)
                .war(war)
                .warRank(ranking)
                .avg(avg)
                .hr(hr)
                .ops(ops)
                .build();
    }
        
    // 시즌별 포지션 WAR 1위 타자 목록 조회
    public List<BatterTopDTO> getTopBattersByPosition(int season) {
        List<Object[]> projections = batterStatsRepository.findTopBattersByPosition(season);
        List<BatterTopDTO> result = new ArrayList<>();

        for (Object[] row : projections) {
            String position = (String) row[0];  // 포지션
            String playerName = (String) row[1];  // 선수 이름
            String teamName = (String) row[2];  // 팀 이름
            String logoName = (String) row[3];  // 로고 이름
            Double war = (Double) row[4];  // WAR

            // DTO 객체 빌드
            BatterTopDTO dto = BatterTopDTO.builder()
                    .position(position)
                    .playerName(playerName)
                    .teamName(teamName)
                    .logoName(logoName)
                    .war(war)
                    .build();

            result.add(dto);
        }

        return result;
    }
   
    // 전체 타자 랭킹 조회 및 정렬 (필터 없음)
    public List<BatterRankingDTO> getPlayerRankingsSorted(int season, String sort, String direction) {
        List<Object[]> projections = batterStatsRepository.findAllBatters(season);
        List<BatterRankingDTO> result = new ArrayList<>();

        for (Object[] row : projections) {

        	String position = (String) row[0];     
        	String playerName = (String) row[1];                       // 선수 이름
        	String teamName = (String) row[2];                         // 팀 이름
        	String logoName = (String) row[3];                         // 로고 이름
        	Double war = row[4] != null ? (Double) row[4] : 0.0;       // WAR
        	Double avg = row[5] != null ? (Double) row[5] : 0.0;       // 타율 (AVG)
        	Double ops = row[6] != null ? (Double) row[6] : 0.0;       // OPS
        	Double hr = row[7] != null ? (Double) row[7] : 0.0;        // 홈런 (HR)
        	Double sb = row[8] != null ? (Double) row[8] : 0.0;        // 도루 (SB)
        	Double wrcPlus = row[9] != null ? (Double) row[9] : 0.0;   // wRC+
        	Double g = row[10] != null ? (Double) row[10] : 0.0;       // 경기 수 (G)
        	Double pa = row[11] != null ? (Double) row[11] : 0.0;      // 타석 (PA)
        	Double h = row[12] != null ? (Double) row[12] : 0.0;       // 안타 (H)
        	Double rbi = row[13] != null ? (Double) row[13] : 0.0;     // 타점 (RBI)
        	Double bb = row[14] != null ? (Double) row[14] : 0.0;      // 볼넷 (BB)
        	Double so = row[15] != null ? (Double) row[15] : 0.0;      // 삼진 (SO)
        	Double twoB = row[16] != null ? (Double) row[16] : 0.0;    // 2루타 (2B)
        	Double threeB = row[17] != null ? (Double) row[17] : 0.0;  // 3루타 (3B)
        	Double obp = row[18] != null ? (Double) row[18] : 0.0;     // 출루율 (OBP)
        	Double slg = row[19] != null ? (Double) row[19] : 0.0;     // 장타율 (SLG)
            
            BatterRankingDTO dto = BatterRankingDTO.builder()
                    .g(g)
                    .pa(pa)
                    .playerName(playerName)
                    .teamName(teamName)
                    .logoName(logoName)
                    .position(position)
                    .war(war)
                    .h(h)
                    .twoB(twoB)
                    .threeB(threeB)
                    .hr(hr)
                    .rbi(rbi)
                    .sb(sb)
                    .bb(bb)
                    .so(so)
                    .avg(avg)
                    .obp(obp)
                    .slg(slg)
                    .ops(ops)
                    .wrcPlus(wrcPlus)
                    .build();

            result.add(dto);
        }
        
        sortBatterRankingList(result, sort, direction);

        return result;
    }

    // 규정 타석 충족한 타자만 필터링하여 랭킹 조회 및 정렬
    public List<BatterRankingDTO> getQualifiedBatters(int season, String sort, String direction) {
        // 팀별 경기 수 가져오기
        Map<Integer, Integer> teamGamesMap = scheduleService.getTeamGamesPlayedBySeason(season);

        // 타자 전체 기록 가져오기
        List<Object[]> rows = batterStatsRepository.findAllBatters(season);

        List<BatterRankingDTO> result = new ArrayList<>();

        if (rows != null && !rows.isEmpty()) {
            for (Object[] row : rows) {
            	
            	if (row.length == 21) {
            	    int teamId = row[20] != null ? ((Number) row[20]).intValue() : 0;
            	    double pa = row[11] != null ? ((Number) row[11]).doubleValue() : 0.0;

            	    // 규정 타석 계산
            	    int teamGames = teamGamesMap.getOrDefault(teamId, 0);
            	    int requiredPA = getQualifiedPA(season, teamGames);

            	    if (pa >= requiredPA) {
            	        BatterRankingDTO dto = BatterRankingDTO.builder()
            	            .position((String) row[0])
            	            .playerName((String) row[1])
            	            .teamName((String) row[2])
            	            .logoName((String) row[3])
            	            .war(row[4] != null ? ((Number) row[4]).doubleValue() : 0.0)
            	            .avg(row[5] != null ? ((Number) row[5]).doubleValue() : 0.0)
            	            .ops(row[6] != null ? ((Number) row[6]).doubleValue() : 0.0)
            	            .hr(row[7] != null ? ((Number) row[7]).doubleValue() : 0.0)
            	            .sb(row[8] != null ? ((Number) row[8]).doubleValue() : 0.0)
            	            .wrcPlus(row[9] != null ? ((Number) row[9]).doubleValue() : 0.0)
            	            .g(row[10] != null ? ((Number) row[10]).doubleValue() : 0.0)
            	            .pa(pa)
            	            .h(row[12] != null ? ((Number) row[12]).doubleValue() : 0.0)
            	            .rbi(row[13] != null ? ((Number) row[13]).doubleValue() : 0.0)
            	            .bb(row[14] != null ? ((Number) row[14]).doubleValue() : 0.0)
            	            .so(row[15] != null ? ((Number) row[15]).doubleValue() : 0.0)
            	            .twoB(row[16] != null ? ((Number) row[16]).doubleValue() : 0.0)
            	            .threeB(row[17] != null ? ((Number) row[17]).doubleValue() : 0.0)
            	            .obp(row[18] != null ? ((Number) row[18]).doubleValue() : 0.0)
            	            .slg(row[19] != null ? ((Number) row[19]).doubleValue() : 0.0)
            	            .build();
            	        result.add(dto);
            	    }
            	}
            }
        }

        sortBatterRankingList(result, sort, direction);

        return result;
    }

    // 정렬 기준별 값 추출 (WAR, AVG, OPS 등)
    private double getSortValue(BatterRankingDTO dto, String sortKey) {
    	if ("WAR".equals(sortKey)) return dto.getWar() != null ? dto.getWar() : 0.0;  // WAR는 많을수록 좋음
    	if ("AVG".equals(sortKey)) return dto.getAvg() != null ? dto.getAvg() : 0.0;  // 타율 (AVG)
    	if ("OPS".equals(sortKey)) return dto.getOps() != null ? dto.getOps() : 0.0;  // OPS
    	if ("HR".equals(sortKey)) return dto.getHr() != null ? (double) dto.getHr() : 0.0;  // 홈런 (HR)
    	if ("SB".equals(sortKey)) return dto.getSb() != null ? (double) dto.getSb() : 0.0;  // 도루 (SB)
    	if ("G".equals(sortKey)) return (double) dto.getG();  // 경기 수 (G)
    	if ("PA".equals(sortKey)) return (double) dto.getPa();  // 타석 수 (PA)
    	if ("H".equals(sortKey)) return (double) dto.getH();  // 안타 수 (H)
    	if ("2B".equals(sortKey)) return dto.getTwoB() != null ? (double) dto.getTwoB() : 0.0;  // 2루타 (2B)
    	if ("3B".equals(sortKey)) return dto.getThreeB() != null ? (double) dto.getThreeB() : 0.0;  // 3루타 (3B)
    	if ("RBI".equals(sortKey)) return (double) dto.getRbi();  // 타점 (RBI)
    	if ("BB".equals(sortKey)) return (double) dto.getBb();  // 볼넷 (BB)
    	if ("SO".equals(sortKey)) return (double) dto.getSo();  // 삼진 (SO)
    	if ("OBP".equals(sortKey)) return dto.getObp() != null ? dto.getObp() : 0.0;  // 출루율 (OBP)
    	if ("SLG".equals(sortKey)) return dto.getSlg() != null ? dto.getSlg() : 0.0;  // 장타율 (SLG)
    	if ("WRCPLUS".equalsIgnoreCase(sortKey)) return dto.getWrcPlus() != null ? dto.getWrcPlus() : 0.0;
    	return 0.0; // 기본값
    }
        
    // DTO 리스트 수동 정렬 (sortKey, direction 기준)
    public void sortBatterRankingList(List<BatterRankingDTO> list, String sort, String direction) {
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
    				BatterRankingDTO temp = list.get(j);
    				list.set(j, list.get(j + 1));
    				list.set(j + 1, temp);
    			}
    		}
    	}
    }
        
    // 시즌 및 경기 수 기준으로 규정 타석 계산
    public int getQualifiedPA(int season, int teamGames) {
        if (season == 2025) {
            return (int) Math.floor(teamGames * 3.1);
        } else {
            return 446;
        }
    }
    
    // playerId + season 기준 데이터 존재 여부 확인
    public boolean existsByPlayerIdAndSeason(int playerId, int season) {
    	return batterStatsRepository.existsByPlayerIdAndSeason(playerId, season);
    }
}