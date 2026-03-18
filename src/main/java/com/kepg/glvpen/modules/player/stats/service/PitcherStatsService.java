package com.kepg.glvpen.modules.player.stats.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.glvpen.common.enums.PitcherSortType;
import com.kepg.glvpen.common.enums.SortDirection;
import com.kepg.glvpen.common.validator.SeasonValidator;
import com.kepg.glvpen.modules.game.schedule.service.ScheduleService;
import com.kepg.glvpen.modules.player.dto.TopPitcherCardView;
import com.kepg.glvpen.modules.player.service.PlayerService;
import com.kepg.glvpen.modules.player.stats.domain.PitcherStats;
import com.kepg.glvpen.modules.player.stats.repository.PitcherStatsRepository;
import com.kepg.glvpen.modules.player.stats.statsDto.PitcherRankingDTO;
import com.kepg.glvpen.modules.player.stats.statsDto.PitcherStatsDTO;
import com.kepg.glvpen.modules.player.stats.statsDto.PitcherTopDTO;

import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
@Transactional
public class PitcherStatsService {

	private final PitcherStatsRepository pitcherStatsRepository;
	private final PlayerService playerService;
	private final ScheduleService scheduleService;
	private final JdbcTemplate jdbcTemplate;

	// Magic Number 상수화
	private static final double STAT_NOT_AVAILABLE = -1.0;
	private static final int STAT_NOT_AVAILABLE_INT = -1;
	private static final int PITCHER_STATS_COLUMN_COUNT = 38;
	private static final int STANDARD_QUALIFIED_IP = 144;

	// Logger 추가
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PitcherStatsService.class);
	
	// 배치 Upsert (ON DUPLICATE KEY UPDATE)
	@Transactional
	public void saveBatch(List<PitcherStatsDTO> dtos) {
		if (dtos == null || dtos.isEmpty()) return;
		String sql = """
			INSERT INTO player_pitcher_stats (playerId, season, category, value, ranking, series, situationType, situationValue)
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
		log.info("[배치저장] 투수 스탯 {}건 저장 완료", dtos.size());
	}

	// 투수 스탯 저장 또는 업데이트 (6-key 기준 Upsert)
	public void savePitcherStats(PitcherStatsDTO dto) {
	    String series = dto.getSeries() != null ? dto.getSeries() : "0";
	    String sitType = dto.getSituationType() != null ? dto.getSituationType() : "";
	    String sitValue = dto.getSituationValue() != null ? dto.getSituationValue() : "";

	    Optional<PitcherStats> optional = pitcherStatsRepository.findByFullKey(
	            dto.getPlayerId(), dto.getSeason(), dto.getCategory(), series, sitType, sitValue);

	    PitcherStats entity = optional.orElse(PitcherStats.builder()
	            .playerId(dto.getPlayerId())
	            .season(dto.getSeason())
	            .category(dto.getCategory())
	            .series(series)
	            .situationType(sitType)
	            .situationValue(sitValue)
	            .build());

	    entity.setValue(dto.getValue());
	    entity.setRanking(dto.getRanking());
	    entity.setPosition(dto.getPosition());

	    pitcherStatsRepository.save(entity);
	}
	
	// 팀별 ERA 최우수 투수 조회 (WHIP, W/SV/HLD 중 최고값 포함)
	@Transactional(readOnly = true)
	public TopPitcherCardView getTopPitcher(int teamId, int season) {
	    List<Object[]> result = playerService.getTopPitcherByTeamAndSeason(teamId, season);
	    if (result.isEmpty()) {
	        return null;
	    }

	    Object[] row = result.get(0);

	    String name = (String) row[0];
	    String position = (String) row[1];
	    double era = 0.0;
	    int ranking = 0;
	    int playerId = 0;

	    if (row[2] != null) {
	        era = Double.parseDouble(row[2].toString());
	    }

	    if (row[3] != null) {
	        ranking = Integer.parseInt(row[3].toString());
	    }

	    if (row[4] != null) {
	        playerId = Integer.parseInt(row[4].toString());
	    }

	    double whip = pitcherStatsRepository.findStatValueByPlayerIdCategoryAndSeason(playerId, "WHIP", season)
	            .map(Double::parseDouble)
	            .orElse(STAT_NOT_AVAILABLE);

	    // FIP 데이터 있으면 가져오기
	    double fip = pitcherStatsRepository.findStatValueByPlayerIdCategoryAndSeason(playerId, "FIP", season)
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
	            .fip(fip)
	            .fipRank(ranking)
	            .era(era)
	            .whip(whip)
	            .bestStatLabel(bestStatLabel)
	            .bestStatValue(bestStatValue)
	            .build();
	}

	// 기록별(ERA, IP, WHIP, WAR 등) 1위 투수 조회
	@Transactional(readOnly = true)
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
	@Transactional(readOnly = true)
	public List<PitcherRankingDTO> getPitcherRankingsSorted(int season, String sort, String direction) {
	    PitcherSortType sortType = PitcherSortType.fromString(sort);
	    SortDirection sortDirection = SortDirection.fromString(direction);

	    List<Object[]> projections = pitcherStatsRepository.findAllPitchers(season);
	    List<PitcherRankingDTO> result = new ArrayList<>();


	    if (projections != null && !projections.isEmpty()) {
	        for (Object[] row : projections) {

	            if (row.length >= PITCHER_STATS_COLUMN_COUNT) {

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
	                // row[14] = teamId (스킵)
	                Double fip = getDoubleOrNull(row[15]);
	                Double xfip = getDoubleOrNull(row[16]);
	                Double k9 = getDoubleOrNull(row[17]);
	                Double bb9 = getDoubleOrNull(row[18]);
	                Double g = getDoubleOrDefault(row[19], 0.0);
	                Double wpct = getDoubleOrDefault(row[20], 0.0);
	                Double hbp = getDoubleOrDefault(row[21], 0.0);
	                Double r = getDoubleOrDefault(row[22], 0.0);
	                Double er = getDoubleOrDefault(row[23], 0.0);
	                Double cg = getDoubleOrDefault(row[24], 0.0);
	                Double sho = getDoubleOrDefault(row[25], 0.0);
	                Double qs = getDoubleOrDefault(row[26], 0.0);
	                Double bsv = getDoubleOrDefault(row[27], 0.0);
	                Double tbf = getDoubleOrDefault(row[28], 0.0);
	                Double np = getDoubleOrDefault(row[29], 0.0);
	                Double avg = getDoubleOrDefault(row[30], 0.0);
	                Double twoB = getDoubleOrDefault(row[31], 0.0);
	                Double threeB = getDoubleOrDefault(row[32], 0.0);
	                Double sac = getDoubleOrDefault(row[33], 0.0);
	                Double sf = getDoubleOrDefault(row[34], 0.0);
	                Double ibb = getDoubleOrDefault(row[35], 0.0);
	                Double wp = getDoubleOrDefault(row[36], 0.0);
	                Double bk = getDoubleOrDefault(row[37], 0.0);

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
	                        .fip(fip)
	                        .xfip(xfip)
	                        .k9(k9)
	                        .bb9(bb9)
	                        .g(g)
	                        .wpct(wpct)
	                        .hbp(hbp)
	                        .r(r)
	                        .er(er)
	                        .cg(cg)
	                        .sho(sho)
	                        .qs(qs)
	                        .bsv(bsv)
	                        .tbf(tbf)
	                        .np(np)
	                        .avg(avg)
	                        .twoB(twoB)
	                        .threeB(threeB)
	                        .sac(sac)
	                        .sf(sf)
	                        .ibb(ibb)
	                        .wp(wp)
	                        .bk(bk)
	                        .build();

	                result.add(dto);
	            }
	        }
	    }
	    sortPitcherRankingList(result, sortType, sortDirection);

	    return result;
	}

	// 규정 이닝을 충족한 투수 랭킹 리스트 조회 및 정렬 (100% 기준)
	@Transactional(readOnly = true)
	public List<PitcherRankingDTO> getQualifiedPitchers(int season, String sort, String direction) {
	    return getQualifiedPitchers(season, sort, direction, 100);
	}

	// 규정 이닝을 충족한 투수 랭킹 리스트 조회 및 정렬 (레벨 지정: 100/75/50)
	@Transactional(readOnly = true)
	public List<PitcherRankingDTO> getQualifiedPitchers(int season, String sort, String direction, int qualifiedLevel) {
	    PitcherSortType sortType = PitcherSortType.fromString(sort);
	    SortDirection sortDirection = SortDirection.fromString(direction);

	    // 팀별 경기 수를 가져오기
	    Map<Integer, Integer> teamGamesMap = scheduleService.getTeamGamesPlayedBySeason(season);

	    // 투수 전체 기록 가져오기 (조건 없이)
	    List<Object[]> projections = pitcherStatsRepository.findAllPitchers(season);

	    List<PitcherRankingDTO> result = new ArrayList<>();

	    if (projections != null && !projections.isEmpty()) {
	        for (Object[] row : projections) {

	            if (row.length >= PITCHER_STATS_COLUMN_COUNT) {
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
	                int teamId = (int) getDoubleOrDefault(row[14], 0.0);

	                // 규정이닝 계산 (레벨 비율 적용)
	                int teamGames = teamGamesMap.getOrDefault(teamId, 0);
	                int fullIP = getQualifiedInnings(season, teamGames);
	                int requiredIP = (int) Math.floor(fullIP * qualifiedLevel / 100.0);

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
	                            .fip(getDoubleOrNull(row[15]))
	                            .xfip(getDoubleOrNull(row[16]))
	                            .k9(getDoubleOrNull(row[17]))
	                            .bb9(getDoubleOrNull(row[18]))
	                            .g(getDoubleOrDefault(row[19], 0.0))
	                            .wpct(getDoubleOrDefault(row[20], 0.0))
	                            .hbp(getDoubleOrDefault(row[21], 0.0))
	                            .r(getDoubleOrDefault(row[22], 0.0))
	                            .er(getDoubleOrDefault(row[23], 0.0))
	                            .cg(getDoubleOrDefault(row[24], 0.0))
	                            .sho(getDoubleOrDefault(row[25], 0.0))
	                            .qs(getDoubleOrDefault(row[26], 0.0))
	                            .bsv(getDoubleOrDefault(row[27], 0.0))
	                            .tbf(getDoubleOrDefault(row[28], 0.0))
	                            .np(getDoubleOrDefault(row[29], 0.0))
	                            .avg(getDoubleOrDefault(row[30], 0.0))
	                            .twoB(getDoubleOrDefault(row[31], 0.0))
	                            .threeB(getDoubleOrDefault(row[32], 0.0))
	                            .sac(getDoubleOrDefault(row[33], 0.0))
	                            .sf(getDoubleOrDefault(row[34], 0.0))
	                            .ibb(getDoubleOrDefault(row[35], 0.0))
	                            .wp(getDoubleOrDefault(row[36], 0.0))
	                            .bk(getDoubleOrDefault(row[37], 0.0))
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
	        case FIP -> dto.getFip() != null ? dto.getFip() : Double.MAX_VALUE;
	        case XFIP -> dto.getXfip() != null ? dto.getXfip() : Double.MAX_VALUE;
	        case K9 -> dto.getK9() != null ? dto.getK9() : Double.MIN_VALUE;
	        case BB9 -> dto.getBb9() != null ? dto.getBb9() : Double.MAX_VALUE;
	        case G -> dto.getG() != null ? dto.getG() : Double.MIN_VALUE;
	        case WPCT -> dto.getWpct() != null ? dto.getWpct() : Double.MIN_VALUE;
	        case HBP -> dto.getHbp() != null ? dto.getHbp() : Double.MIN_VALUE;
	        case R -> dto.getR() != null ? dto.getR() : Double.MIN_VALUE;
	        case ER -> dto.getEr() != null ? dto.getEr() : Double.MIN_VALUE;
	        case CG -> dto.getCg() != null ? dto.getCg() : Double.MIN_VALUE;
	        case SHO -> dto.getSho() != null ? dto.getSho() : Double.MIN_VALUE;
	        case QS -> dto.getQs() != null ? dto.getQs() : Double.MIN_VALUE;
	        case BSV -> dto.getBsv() != null ? dto.getBsv() : Double.MIN_VALUE;
	        case TBF -> dto.getTbf() != null ? dto.getTbf() : Double.MIN_VALUE;
	        case NP -> dto.getNp() != null ? dto.getNp() : Double.MIN_VALUE;
	        case AVG -> dto.getAvg() != null ? dto.getAvg() : Double.MAX_VALUE;
	        case TWO_B -> dto.getTwoB() != null ? dto.getTwoB() : Double.MIN_VALUE;
	        case THREE_B -> dto.getThreeB() != null ? dto.getThreeB() : Double.MIN_VALUE;
	        case SAC -> dto.getSac() != null ? dto.getSac() : Double.MIN_VALUE;
	        case SF -> dto.getSf() != null ? dto.getSf() : Double.MIN_VALUE;
	        case IBB -> dto.getIbb() != null ? dto.getIbb() : Double.MIN_VALUE;
	        case WP -> dto.getWp() != null ? dto.getWp() : Double.MIN_VALUE;
	        case BK -> dto.getBk() != null ? dto.getBk() : Double.MIN_VALUE;
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
	    if (SeasonValidator.isCurrentSeason(season)) {
	        return teamGames;
	    } else {
	        return STANDARD_QUALIFIED_IP;
	    }
	}
	
	// 해당 playerId와 시즌에 대한 기록 존재 여부 확인
	@Transactional(readOnly = true)
	public boolean existsByPlayerIdAndSeason(int playerId, int season) {
    	return pitcherStatsRepository.existsByPlayerIdAndSeason(playerId, season);
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

