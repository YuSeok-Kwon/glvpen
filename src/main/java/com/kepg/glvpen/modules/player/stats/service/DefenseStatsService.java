package com.kepg.glvpen.modules.player.stats.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.glvpen.common.enums.DefenseSortType;
import com.kepg.glvpen.common.enums.SortDirection;
import com.kepg.glvpen.modules.player.stats.domain.DefenseStats;
import com.kepg.glvpen.modules.player.stats.repository.DefenseStatsRepository;
import com.kepg.glvpen.modules.player.stats.statsDto.DefenseRankingDTO;
import com.kepg.glvpen.modules.player.stats.statsDto.DefenseStatsDTO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class DefenseStatsService {

    private final DefenseStatsRepository defenseStatsRepository;
    private final JdbcTemplate jdbcTemplate;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DefenseStatsService.class);

    // 배치 Upsert (ON DUPLICATE KEY UPDATE)
    @Transactional
    public void saveBatch(List<DefenseStatsDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) return;
        String sql = """
            INSERT INTO player_defense_stats (playerId, season, series, position, category, value, ranking)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE value = VALUES(value), ranking = VALUES(ranking)
            """;
        jdbcTemplate.batchUpdate(sql, dtos, 500, (ps, dto) -> {
            ps.setInt(1, dto.getPlayerId());
            ps.setInt(2, dto.getSeason());
            ps.setString(3, dto.getSeries() != null ? dto.getSeries() : "0");
            ps.setString(4, dto.getPosition() != null ? dto.getPosition() : "");
            ps.setString(5, dto.getCategory());
            ps.setDouble(6, dto.getValue());
            if (dto.getRanking() != null) ps.setInt(7, dto.getRanking());
            else ps.setNull(7, java.sql.Types.INTEGER);
        });
        log.info("[배치저장] 수비 스탯 {}건 저장 완료", dtos.size());
    }

    public void saveDefenseStats(DefenseStatsDTO dto) {
        String series = dto.getSeries() != null ? dto.getSeries() : "0";
        String position = dto.getPosition() != null ? dto.getPosition() : "";

        Optional<DefenseStats> optional = defenseStatsRepository.findByFullKey(
                dto.getPlayerId(), dto.getSeason(), dto.getCategory(), series, position);

        DefenseStats entity = optional.orElse(DefenseStats.builder()
                .playerId(dto.getPlayerId())
                .season(dto.getSeason())
                .series(series)
                .position(position)
                .category(dto.getCategory())
                .build());

        entity.setValue(dto.getValue());
        entity.setRanking(dto.getRanking());

        defenseStatsRepository.save(entity);
    }

    // 수비 랭킹 조회 및 정렬
    public List<DefenseRankingDTO> getDefenseRankingsSorted(int season, String sort, String direction) {
        DefenseSortType sortType = DefenseSortType.fromString(sort);
        SortDirection sortDirection = SortDirection.fromString(direction);

        List<Object[]> projections = defenseStatsRepository.findAllDefensePlayers(season);
        List<DefenseRankingDTO> result = new ArrayList<>();

        if (projections != null && !projections.isEmpty()) {
            for (Object[] row : projections) {
                DefenseRankingDTO dto = DefenseRankingDTO.builder()
                        .playerName((String) row[0])
                        .teamName((String) row[1])
                        .logoName((String) row[2])
                        .position((String) row[3])
                        .g(getDoubleOrDefault(row[4], 0.0))
                        .gs(getDoubleOrDefault(row[5], 0.0))
                        .ip(getDoubleOrDefault(row[6], 0.0))
                        .e(getDoubleOrDefault(row[7], 0.0))
                        .pko(getDoubleOrDefault(row[8], 0.0))
                        .po(getDoubleOrDefault(row[9], 0.0))
                        .a(getDoubleOrDefault(row[10], 0.0))
                        .dp(getDoubleOrDefault(row[11], 0.0))
                        .fpct(getDoubleOrDefault(row[12], 0.0))
                        .pb(getDoubleOrDefault(row[13], 0.0))
                        .sb(getDoubleOrDefault(row[14], 0.0))
                        .cs(getDoubleOrDefault(row[15], 0.0))
                        .csPct(getDoubleOrDefault(row[16], 0.0))
                        .build();
                result.add(dto);
            }
        }

        sortDefenseRankingList(result, sortType, sortDirection);
        return result;
    }

    // 수비 정렬 기준 값 추출
    private double getSortValue(DefenseRankingDTO dto, DefenseSortType sortType) {
        return switch (sortType) {
            case G -> dto.getG() != null ? dto.getG() : 0.0;
            case GS -> dto.getGs() != null ? dto.getGs() : 0.0;
            case IP -> dto.getIp() != null ? dto.getIp() : 0.0;
            case E -> dto.getE() != null ? dto.getE() : 0.0;
            case PKO -> dto.getPko() != null ? dto.getPko() : 0.0;
            case PO -> dto.getPo() != null ? dto.getPo() : 0.0;
            case A -> dto.getA() != null ? dto.getA() : 0.0;
            case DP -> dto.getDp() != null ? dto.getDp() : 0.0;
            case FPCT -> dto.getFpct() != null ? dto.getFpct() : 0.0;
            case PB -> dto.getPb() != null ? dto.getPb() : 0.0;
            case SB -> dto.getSb() != null ? dto.getSb() : 0.0;
            case CS -> dto.getCs() != null ? dto.getCs() : 0.0;
            case CS_PCT -> dto.getCsPct() != null ? dto.getCsPct() : 0.0;
        };
    }

    // 수비 랭킹 정렬
    public void sortDefenseRankingList(List<DefenseRankingDTO> list, DefenseSortType sortType, SortDirection direction) {
        if (sortType == null || direction == null) return;

        Comparator<DefenseRankingDTO> comparator = Comparator.comparingDouble(dto -> getSortValue(dto, sortType));

        if (direction == SortDirection.DESC) {
            comparator = comparator.reversed();
        }

        Collections.sort(list, comparator);
    }

    // 헬퍼 메서드: 배열 요소를 Double로 변환 (null 안전)
    private double getDoubleOrDefault(Object value, double defaultValue) {
        return value != null ? ((Number) value).doubleValue() : defaultValue;
    }
}
