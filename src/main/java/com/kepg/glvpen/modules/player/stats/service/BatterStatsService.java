package com.kepg.glvpen.modules.player.stats.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.glvpen.common.enums.BatterSortType;
import com.kepg.glvpen.common.enums.SortDirection;
import com.kepg.glvpen.common.validator.SeasonValidator;
import com.kepg.glvpen.modules.game.schedule.service.ScheduleService;
import com.kepg.glvpen.modules.player.dto.TopBatterCardView;
import com.kepg.glvpen.modules.player.service.PlayerService;
import com.kepg.glvpen.modules.player.stats.domain.BatterStats;
import com.kepg.glvpen.modules.player.stats.repository.BatterStatsRepository;
import com.kepg.glvpen.modules.player.stats.statsDto.BatterRankingDTO;
import com.kepg.glvpen.modules.player.stats.statsDto.BatterStatsDTO;
import com.kepg.glvpen.modules.player.stats.statsDto.BatterTopDTO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class BatterStatsService {

    private final BatterStatsRepository batterStatsRepository;
    private final PlayerService playerService;
	private final ScheduleService scheduleService;
	private final JdbcTemplate jdbcTemplate;

	// Magic Number 상수화
	private static final double STAT_NOT_AVAILABLE = -1.0;
	private static final int DEFAULT_HR_VALUE = 0;
	private static final int BATTER_STATS_COLUMN_COUNT = 36;
	private static final double PA_MULTIPLIER = 3.1;
	private static final int STANDARD_QUALIFIED_PA = 446;

	// Logger 추가 (Lombok @Slf4j 없으므로)
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BatterStatsService.class);

	// 배치 Upsert (ON DUPLICATE KEY UPDATE)
	@Transactional
	public void saveBatch(List<BatterStatsDTO> dtos) {
		if (dtos == null || dtos.isEmpty()) return;
		String sql = """
			INSERT INTO player_batter_stats (playerId, season, category, value, ranking, series, situationType, situationValue)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?)
			ON DUPLICATE KEY UPDATE value = VALUES(value), ranking = VALUES(ranking)
			""";
		jdbcTemplate.batchUpdate(sql, dtos, 500, (ps, dto) -> {
			ps.setInt(1, dto.getPlayerId());
			ps.setInt(2, dto.getSeason());
			ps.setString(3, dto.getCategory());
			ps.setDouble(4, dto.getValue());
			if (dto.getRanking() != null) ps.setInt(5, dto.getRanking());
			else ps.setNull(5, java.sql.Types.INTEGER);
			ps.setString(6, dto.getSeries() != null ? dto.getSeries() : "0");
			ps.setString(7, dto.getSituationType() != null ? dto.getSituationType() : "");
			ps.setString(8, dto.getSituationValue() != null ? dto.getSituationValue() : "");
		});
		log.info("[배치저장] 타자 스탯 {}건 저장 완료", dtos.size());
	}

	// 타자 스탯 저장 또는 업데이트 (6-key 기준 Upsert)
	public void saveBatterStats(BatterStatsDTO dto) {
	    String series = dto.getSeries() != null ? dto.getSeries() : "0";
	    String sitType = dto.getSituationType() != null ? dto.getSituationType() : "";
	    String sitValue = dto.getSituationValue() != null ? dto.getSituationValue() : "";

	    Optional<BatterStats> optional = batterStatsRepository.findByFullKey(
	            dto.getPlayerId(), dto.getSeason(), dto.getCategory(), series, sitType, sitValue);

	    BatterStats entity = optional.orElse(BatterStats.builder()
	            .playerId(dto.getPlayerId())
	            .season(dto.getSeason())
	            .category(dto.getCategory())
	            .series(series)
	            .situationType(sitType)
	            .situationValue(sitValue)
	            .build());

	    entity.setPosition(dto.getPosition());
	    entity.setValue(dto.getValue());
	    entity.setRanking(dto.getRanking());

	    batterStatsRepository.save(entity);
	}
    
    // 팀별 wOBA 1위 타자 조회 (AVG, OPS, HR 포함)
    public TopBatterCardView getTopHitter(int teamId, int season) {
        List<Object[]> result = playerService.getTopHitterByTeamAndSeason(teamId, season);
        if (result.isEmpty()) {
            return null;
        }

        Object[] row = result.get(0);

        String name = (String) row[0];
        String position = (String) row[1];
        double woba = 0.0;
        int ranking = 0;
        int playerId = 0;

        if (row[2] != null) {
            woba = Double.parseDouble(row[2].toString());
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

        return TopBatterCardView.builder()
                .name(name)
                .position(position)
                .woba(woba)
                .wobaRank(ranking)
                .avg(avg)
                .hr(hr)
                .ops(ops)
                .build();
    }
        
    // 시즌별 포지션 wOBA 1위 타자 목록 조회
    public List<BatterTopDTO> getTopBattersByPosition(int season) {
        List<Object[]> projections = batterStatsRepository.findTopBattersByPosition(season);
        List<BatterTopDTO> result = new ArrayList<>();

        for (Object[] row : projections) {
            String position = (String) row[0];
            String playerName = (String) row[1];
            String teamName = (String) row[2];
            String logoName = (String) row[3];
            Double woba = (Double) row[4];

            BatterTopDTO dto = BatterTopDTO.builder()
                    .position(position)
                    .playerName(playerName)
                    .teamName(teamName)
                    .logoName(logoName)
                    .woba(woba != null ? woba : 0.0)
                    .build();

            result.add(dto);
        }

        return result;
    }
   
    // 전체 타자 랭킹 조회 및 정렬 (필터 없음)
    public List<BatterRankingDTO> getPlayerRankingsSorted(int season, String sort, String direction) {
        BatterSortType sortType = BatterSortType.fromString(sort);
        SortDirection sortDirection = SortDirection.fromString(direction);

        List<Object[]> projections = batterStatsRepository.findAllBatters(season);
        List<BatterRankingDTO> result = new ArrayList<>();

        for (Object[] row : projections) {

        	String position = (String) row[0];
        	String playerName = (String) row[1];
        	String teamName = (String) row[2];
        	String logoName = (String) row[3];
        	Double woba = getDoubleOrNull(row[4]);
        	Double avg = getDoubleOrDefault(row[5], 0.0);
        	Double ops = getDoubleOrDefault(row[6], 0.0);
        	Double hr = getDoubleOrDefault(row[7], 0.0);
        	Double sb = getDoubleOrDefault(row[8], 0.0);
        	Double wrcPlus = getDoubleOrNull(row[9]);
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
        	// row[20] = teamId (스킵)
        	Double babip = getDoubleOrNull(row[21]);
        	Double iso = getDoubleOrNull(row[22]);
        	Double kRate = getDoubleOrNull(row[23]);
        	Double bbRate = getDoubleOrNull(row[24]);
        	Double ab = getDoubleOrDefault(row[25], 0.0);
        	Double r = getDoubleOrDefault(row[26], 0.0);
        	Double tb = getDoubleOrDefault(row[27], 0.0);
        	Double sac = getDoubleOrDefault(row[28], 0.0);
        	Double sf = getDoubleOrDefault(row[29], 0.0);
        	Double ibb = getDoubleOrDefault(row[30], 0.0);
        	Double hbp = getDoubleOrDefault(row[31], 0.0);
        	Double gdp = getDoubleOrDefault(row[32], 0.0);
        	Double mh = getDoubleOrDefault(row[33], 0.0);
        	Double risp = getDoubleOrDefault(row[34], 0.0);
        	Double phBa = getDoubleOrDefault(row[35], 0.0);

            BatterRankingDTO dto = BatterRankingDTO.builder()
                    .g(g)
                    .pa(pa)
                    .playerName(playerName)
                    .teamName(teamName)
                    .logoName(logoName)
                    .position(position)
                    .woba(woba)
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
                    .babip(babip)
                    .iso(iso)
                    .kRate(kRate)
                    .bbRate(bbRate)
                    .ab(ab)
                    .r(r)
                    .tb(tb)
                    .sac(sac)
                    .sf(sf)
                    .ibb(ibb)
                    .hbp(hbp)
                    .gdp(gdp)
                    .mh(mh)
                    .risp(risp)
                    .phBa(phBa)
                    .build();

            result.add(dto);
        }

        sortBatterRankingList(result, sortType, sortDirection);

        return result;
    }

    // 규정 타석 충족한 타자만 필터링하여 랭킹 조회 및 정렬 (100% 기준)
    public List<BatterRankingDTO> getQualifiedBatters(int season, String sort, String direction) {
        return getQualifiedBatters(season, sort, direction, 100);
    }

    // 규정 타석 충족한 타자만 필터링하여 랭킹 조회 및 정렬 (레벨 지정: 100/75/50)
    public List<BatterRankingDTO> getQualifiedBatters(int season, String sort, String direction, int qualifiedLevel) {
        BatterSortType sortType = BatterSortType.fromString(sort);
        SortDirection sortDirection = SortDirection.fromString(direction);

        // 팀별 경기 수 가져오기
        Map<Integer, Integer> teamGamesMap = scheduleService.getTeamGamesPlayedBySeason(season);

        // 타자 전체 기록 가져오기
        List<Object[]> rows = batterStatsRepository.findAllBatters(season);

        List<BatterRankingDTO> result = new ArrayList<>();

        if (rows != null && !rows.isEmpty()) {
            for (Object[] row : rows) {
            	
            	if (row.length >= BATTER_STATS_COLUMN_COUNT) {
            	    int teamId = (int) getDoubleOrDefault(row[20], 0.0);
            	    double pa = getDoubleOrDefault(row[11], 0.0);

            	    // 규정 타석 계산 (레벨 비율 적용)
            	    int teamGames = teamGamesMap.getOrDefault(teamId, 0);
            	    int fullPA = getQualifiedPA(season, teamGames);
            	    int requiredPA = (int) Math.floor(fullPA * qualifiedLevel / 100.0);

            	    if (pa >= requiredPA) {
            	        BatterRankingDTO dto = BatterRankingDTO.builder()
            	            .position((String) row[0])
            	            .playerName((String) row[1])
            	            .teamName((String) row[2])
            	            .logoName((String) row[3])
            	            .woba(getDoubleOrNull(row[4]))
            	            .avg(getDoubleOrDefault(row[5], 0.0))
            	            .ops(getDoubleOrDefault(row[6], 0.0))
            	            .hr(getDoubleOrDefault(row[7], 0.0))
            	            .sb(getDoubleOrDefault(row[8], 0.0))
            	            .wrcPlus(getDoubleOrNull(row[9]))
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
            	            .babip(getDoubleOrNull(row[21]))
            	            .iso(getDoubleOrNull(row[22]))
            	            .kRate(getDoubleOrNull(row[23]))
            	            .bbRate(getDoubleOrNull(row[24]))
            	            .ab(getDoubleOrDefault(row[25], 0.0))
            	            .r(getDoubleOrDefault(row[26], 0.0))
            	            .tb(getDoubleOrDefault(row[27], 0.0))
            	            .sac(getDoubleOrDefault(row[28], 0.0))
            	            .sf(getDoubleOrDefault(row[29], 0.0))
            	            .ibb(getDoubleOrDefault(row[30], 0.0))
            	            .hbp(getDoubleOrDefault(row[31], 0.0))
            	            .gdp(getDoubleOrDefault(row[32], 0.0))
            	            .mh(getDoubleOrDefault(row[33], 0.0))
            	            .risp(getDoubleOrDefault(row[34], 0.0))
            	            .phBa(getDoubleOrDefault(row[35], 0.0))
            	            .build();
            	        result.add(dto);
            	    }
            	}
            }
        }

        sortBatterRankingList(result, sortType, sortDirection);

        return result;
    }

    // 정렬 기준별 값 추출 (WAR, AVG, OPS 등)
    private double getSortValue(BatterRankingDTO dto, BatterSortType sortType) {
        return switch (sortType) {
            case WOBA -> dto.getWoba() != null ? dto.getWoba() : 0.0;
            case AVG -> dto.getAvg() != null ? dto.getAvg() : 0.0;
            case OPS -> dto.getOps() != null ? dto.getOps() : 0.0;
            case HR -> dto.getHr() != null ? dto.getHr() : 0.0;
            case SB -> dto.getSb() != null ? dto.getSb() : 0.0;
            case G -> dto.getG();
            case PA -> dto.getPa();
            case H -> dto.getH();
            case TWO_B -> dto.getTwoB() != null ? dto.getTwoB() : 0.0;
            case THREE_B -> dto.getThreeB() != null ? dto.getThreeB() : 0.0;
            case RBI -> dto.getRbi();
            case BB -> dto.getBb();
            case SO -> dto.getSo();
            case OBP -> dto.getObp() != null ? dto.getObp() : 0.0;
            case SLG -> dto.getSlg() != null ? dto.getSlg() : 0.0;
            case WRCPLUS -> dto.getWrcPlus() != null ? dto.getWrcPlus() : 0.0;
            case BABIP -> dto.getBabip() != null ? dto.getBabip() : 0.0;
            case ISO -> dto.getIso() != null ? dto.getIso() : 0.0;
            case K_RATE -> dto.getKRate() != null ? dto.getKRate() : 0.0;
            case BB_RATE -> dto.getBbRate() != null ? dto.getBbRate() : 0.0;
            case AB -> dto.getAb() != null ? dto.getAb() : 0.0;
            case R -> dto.getR() != null ? dto.getR() : 0.0;
            case TB -> dto.getTb() != null ? dto.getTb() : 0.0;
            case SAC -> dto.getSac() != null ? dto.getSac() : 0.0;
            case SF -> dto.getSf() != null ? dto.getSf() : 0.0;
            case IBB -> dto.getIbb() != null ? dto.getIbb() : 0.0;
            case HBP -> dto.getHbp() != null ? dto.getHbp() : 0.0;
            case GDP -> dto.getGdp() != null ? dto.getGdp() : 0.0;
            case MH -> dto.getMh() != null ? dto.getMh() : 0.0;
            case RISP -> dto.getRisp() != null ? dto.getRisp() : 0.0;
            case PH_BA -> dto.getPhBa() != null ? dto.getPhBa() : 0.0;
        };
    }

    // DTO 리스트 정렬 (Enum 기반) - O(n log n)
    public void sortBatterRankingList(List<BatterRankingDTO> list, BatterSortType sortType, SortDirection direction) {
        if (sortType == null || direction == null) return;

        Comparator<BatterRankingDTO> comparator = Comparator.comparingDouble(dto -> getSortValue(dto, sortType));

        if (direction == SortDirection.DESC) {
            comparator = comparator.reversed();
        }

        Collections.sort(list, comparator);
    }
        
    // 시즌 및 경기 수 기준으로 규정 타석 계산
    public int getQualifiedPA(int season, int teamGames) {
        if (SeasonValidator.isCurrentSeason(season)) {
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

    // 헬퍼 메서드: 배열 요소를 Double로 변환 (null이면 null 반환 — 세이버 지표용)
    private Double getDoubleOrNull(Object value) {
        return value != null ? ((Number) value).doubleValue() : null;
    }
}