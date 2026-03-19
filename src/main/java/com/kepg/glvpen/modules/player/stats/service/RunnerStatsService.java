package com.kepg.glvpen.modules.player.stats.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.glvpen.common.enums.RunnerSortType;
import com.kepg.glvpen.common.enums.SortDirection;
import com.kepg.glvpen.modules.player.stats.domain.RunnerStats;
import com.kepg.glvpen.modules.player.stats.repository.RunnerStatsRepository;
import com.kepg.glvpen.modules.player.stats.statsDto.RunnerRankingDTO;
import com.kepg.glvpen.modules.player.stats.statsDto.RunnerStatsDTO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class RunnerStatsService {

    private final RunnerStatsRepository runnerStatsRepository;
    private final JdbcTemplate jdbcTemplate;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RunnerStatsService.class);

    // 배치 Upsert (ON DUPLICATE KEY UPDATE)
    @Transactional
    public void saveBatch(List<RunnerStatsDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) return;
        String sql = """
            INSERT INTO player_runner_stats (playerId, season, series, category, value, ranking)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE value = VALUES(value), ranking = VALUES(ranking)
            """;
        jdbcTemplate.batchUpdate(sql, dtos, 500, (ps, dto) -> {
            ps.setInt(1, dto.getPlayerId());
            ps.setInt(2, dto.getSeason());
            ps.setString(3, dto.getSeries() != null ? dto.getSeries() : "0");
            ps.setString(4, dto.getCategory());
            ps.setDouble(5, dto.getValue());
            if (dto.getRanking() != null) ps.setInt(6, dto.getRanking());
            else ps.setNull(6, java.sql.Types.INTEGER);
        });
        log.info("[배치저장] 주루 스탯 {}건 저장 완료", dtos.size());
    }

    public void saveRunnerStats(RunnerStatsDTO dto) {
        String series = dto.getSeries() != null ? dto.getSeries() : "0";

        Optional<RunnerStats> optional = runnerStatsRepository.findByFullKey(
                dto.getPlayerId(), dto.getSeason(), dto.getCategory(), series);

        RunnerStats entity = optional.orElse(RunnerStats.builder()
                .playerId(dto.getPlayerId())
                .season(dto.getSeason())
                .series(series)
                .category(dto.getCategory())
                .build());

        entity.setValue(dto.getValue());
        entity.setRanking(dto.getRanking());

        runnerStatsRepository.save(entity);
    }

    // 주루 랭킹 조회 및 정렬
    public List<RunnerRankingDTO> getRunnerRankingsSorted(int season, String sort, String direction) {
        RunnerSortType sortType = RunnerSortType.fromString(sort);
        SortDirection sortDirection = SortDirection.fromString(direction);

        List<Object[]> projections = runnerStatsRepository.findAllRunners(season);
        List<RunnerRankingDTO> result = new ArrayList<>();

        if (projections != null && !projections.isEmpty()) {
            for (Object[] row : projections) {
                RunnerRankingDTO dto = RunnerRankingDTO.builder()
                        .playerName((String) row[0])
                        .teamName((String) row[1])
                        .logoName((String) row[2])
                        .g(getDoubleOrDefault(row[3], 0.0))
                        .sba(getDoubleOrDefault(row[4], 0.0))
                        .sb(getDoubleOrDefault(row[5], 0.0))
                        .cs(getDoubleOrDefault(row[6], 0.0))
                        .sbPct(getDoubleOrDefault(row[7], 0.0))
                        .oob(getDoubleOrDefault(row[8], 0.0))
                        .pko(getDoubleOrDefault(row[9], 0.0))
                        .build();
                result.add(dto);
            }
        }

        sortRunnerRankingList(result, sortType, sortDirection);
        return result;
    }

    // 주루 정렬 기준 값 추출
    private double getSortValue(RunnerRankingDTO dto, RunnerSortType sortType) {
        return switch (sortType) {
            case G -> dto.getG() != null ? dto.getG() : 0.0;
            case SBA -> dto.getSba() != null ? dto.getSba() : 0.0;
            case SB -> dto.getSb() != null ? dto.getSb() : 0.0;
            case CS -> dto.getCs() != null ? dto.getCs() : 0.0;
            case SB_PCT -> dto.getSbPct() != null ? dto.getSbPct() : 0.0;
            case OOB -> dto.getOob() != null ? dto.getOob() : 0.0;
            case PKO -> dto.getPko() != null ? dto.getPko() : 0.0;
        };
    }

    // 주루 랭킹 정렬
    public void sortRunnerRankingList(List<RunnerRankingDTO> list, RunnerSortType sortType, SortDirection direction) {
        if (sortType == null || direction == null) return;

        Comparator<RunnerRankingDTO> comparator = Comparator.comparingDouble(dto -> getSortValue(dto, sortType));

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
