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

	// Magic Number 상수화
	private static final double STAT_NOT_AVAILABLE = -1.0;
	private static final int DEFAULT_HR_VALUE = 0;
	private static final int BATTER_STATS_COLUMN_COUNT = 21;
	private static final int CURRENT_SEASON = 2025;
	private static final double PA_MULTIPLIER = 3.1;
	private static final int STANDARD_QUALIFIED_PA = 446;

	// Logger 추가 (Lombok @Slf4j 없으므로)
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BatterStatsService.class);

	// 타자 스탯 저장 또는 업데이트 (playerId + season + category 기준 중복 확인)
	public void saveBatterStats(BatterStatsDTO dto) {
	    Optional<BatterStats> optional = batterStatsRepository.findByPlayerIdAndSeasonAndCategory(dto.getPlayerId(), dto.getSeason(), dto.getCategory());

	    optional.ifPresent(existing -> {
	        batterStatsRepository.delete(existing);
	        log.info("[DELETE-AND-INSERT] 기존 데이터 삭제: playerId={}, category={}", dto.getPlayerId(), dto.getCategory());
	    });

	    BatterStats entity = BatterStats.builder()
	            .playerId(dto.getPlayerId())
	            .season(dto.getSeason())
	            .position(dto.getPosition())
	            .category(dto.getCategory())
	            .value(dto.getValue())
	            .ranking(dto.getRanking())
	            .build();

	    batterStatsRepository.save(entity);
	    log.info("[INSERT] playerId={}, category={}, value={}", dto.getPlayerId(), dto.getCategory(), dto.getValue());
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

        // Optional 체이닝 패턴으로 개선
        double avg = batterStatsRepository.findStatValueByPlayerIdCategoryAndSeason(playerId, "AVG", season)
                .map(Double::parseDouble)
                .orElse(STAT_NOT_AVAILABLE);

        int hr = batterStatsRepository.findStatValueByPlayerIdCategoryAndSeason(playerId, "HR", season)
                .map(String::trim)
                .map(rawHr -> {
                    try {
                        return (int) Double.parseDouble(rawHr);
                    } catch (NumberFormatException e) {
                        log.warn("HR 파싱 오류: {}", rawHr);
                        return DEFAULT_HR_VALUE;
                    }
                })
                .orElse(DEFAULT_HR_VALUE);

        double ops = batterStatsRepository.findStatValueByPlayerIdCategoryAndSeason(playerId, "OPS", season)
                .map(Double::parseDouble)
                .orElse(STAT_NOT_AVAILABLE);

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
        	String playerName = (String) row[1];
        	String teamName = (String) row[2];
        	String logoName = (String) row[3];
        	Double war = getDoubleOrDefault(row[4], 0.0);
        	Double avg = getDoubleOrDefault(row[5], 0.0);
        	Double ops = getDoubleOrDefault(row[6], 0.0);
        	Double hr = getDoubleOrDefault(row[7], 0.0);
        	Double sb = getDoubleOrDefault(row[8], 0.0);
        	Double wrcPlus = getDoubleOrDefault(row[9], 0.0);
        	Double g = getDoubleOrDefault(row[10], 0.0);
        	Double pa = getDoubleOrDefault(row[11], 0.0);
        	Double h = getDoubleOrDefault(row[12], 0.0);
        	Double rbi = getDoubleOrDefault(row[13], 0.0);
        	Double bb = getDoubleOrDefault(row[14], 0.0);
        	Double so = getDoubleOrDefault(row[15], 0.0);
        	Double twoB = getDoubleOrDefault(row[16], 0.0);
        	Double threeB = getDoubleOrDefault(row[17], 0.0);
        	Double obp = getDoubleOrDefault(row[18], 0.0);
        	Double slg = getDoubleOrDefault(row[19], 0.0);
            
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
            	
            	if (row.length == BATTER_STATS_COLUMN_COUNT) {
            	    int teamId = (int) getDoubleOrDefault(row[20], 0.0);
            	    double pa = getDoubleOrDefault(row[11], 0.0);

            	    // 규정 타석 계산
            	    int teamGames = teamGamesMap.getOrDefault(teamId, 0);
            	    int requiredPA = getQualifiedPA(season, teamGames);

            	    if (pa >= requiredPA) {
            	        BatterRankingDTO dto = BatterRankingDTO.builder()
            	            .position((String) row[0])
            	            .playerName((String) row[1])
            	            .teamName((String) row[2])
            	            .logoName((String) row[3])
            	            .war(getDoubleOrDefault(row[4], 0.0))
            	            .avg(getDoubleOrDefault(row[5], 0.0))
            	            .ops(getDoubleOrDefault(row[6], 0.0))
            	            .hr(getDoubleOrDefault(row[7], 0.0))
            	            .sb(getDoubleOrDefault(row[8], 0.0))
            	            .wrcPlus(getDoubleOrDefault(row[9], 0.0))
            	            .g(getDoubleOrDefault(row[10], 0.0))
            	            .pa(pa)
            	            .h(getDoubleOrDefault(row[12], 0.0))
            	            .rbi(getDoubleOrDefault(row[13], 0.0))
            	            .bb(getDoubleOrDefault(row[14], 0.0))
            	            .so(getDoubleOrDefault(row[15], 0.0))
            	            .twoB(getDoubleOrDefault(row[16], 0.0))
            	            .threeB(getDoubleOrDefault(row[17], 0.0))
            	            .obp(getDoubleOrDefault(row[18], 0.0))
            	            .slg(getDoubleOrDefault(row[19], 0.0))
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
        if (season == CURRENT_SEASON) {
            return (int) Math.floor(teamGames * PA_MULTIPLIER);
        } else {
            return STANDARD_QUALIFIED_PA;
        }
    }
    
    // playerId + season 기준 데이터 존재 여부 확인
    public boolean existsByPlayerIdAndSeason(int playerId, int season) {
    	return batterStatsRepository.existsByPlayerIdAndSeason(playerId, season);
    }

    // 헬퍼 메서드: 배열 요소를 Double로 변환 (null 안전)
    private double getDoubleOrDefault(Object value, double defaultValue) {
        return value != null ? ((Number) value).doubleValue() : defaultValue;
    }
}