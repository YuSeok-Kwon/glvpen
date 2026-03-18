package com.kepg.glvpen.modules.team.teamStats.service;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.glvpen.modules.team.teamStats.domain.TeamStats;
import com.kepg.glvpen.modules.team.teamStats.dto.TeamStatRankingDTO;
import com.kepg.glvpen.modules.team.teamStats.dto.TeamStatRankingInterface;
import com.kepg.glvpen.modules.team.teamStats.dto.TeamStatsDTO;
import com.kepg.glvpen.modules.team.teamStats.dto.TopStatTeamDTO;
import com.kepg.glvpen.modules.team.teamStats.dto.TopStatTeamInterface;
import com.kepg.glvpen.modules.team.teamStats.repository.TeamStatsRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamStatsService {

    private final TeamStatsRepository teamStatsRepository;
    private final JdbcTemplate jdbcTemplate;

    // 배치 Upsert (ON DUPLICATE KEY UPDATE)
    @Transactional
    public void saveBatch(List<TeamStatsDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) return;
        String sql = """
            INSERT INTO team_stats (teamId, season, category, value, ranking)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE value = VALUES(value), ranking = VALUES(ranking)
            """;
        jdbcTemplate.batchUpdate(sql, dtos, 500, (ps, dto) -> {
            ps.setInt(1, dto.getTeamId());
            ps.setInt(2, dto.getSeason());
            ps.setString(3, dto.getCategory());
            ps.setDouble(4, dto.getValue());
            String rankStr = dto.getRank();
            if (rankStr != null && !rankStr.isEmpty()) {
                ps.setInt(5, Integer.parseInt(rankStr));
            } else {
                ps.setNull(5, java.sql.Types.INTEGER);
            }
        });
        log.info("[배치저장] 팀 스탯 {}건 저장 완료", dtos.size());
    }

    // 팀 스탯 저장 또는 업데이트
    public void saveOrUpdate(int teamId, int season, String category, double value, Integer ranking) {
        Optional<TeamStats> optional = teamStatsRepository.findByTeamIdAndSeasonAndCategory(teamId, season, category);

        TeamStats stat = optional.orElseGet(() -> TeamStats.builder()
                .teamId(teamId)
                .season(season)
                .category(category)
                .build());

        stat.setValue(value);
        stat.setRanking(ranking);
        teamStatsRepository.save(stat);
    }

    // TopStatTeamInterface → TopStatTeamDTO 변환
    private TopStatTeamDTO convertToTopStatTeamDTO(TopStatTeamInterface projection) {
        if (projection == null) return null;

        Double value = projection.getValue();
        double rawValue = (value != null) ? value : 0.0;

        return TopStatTeamDTO.builder()
                .category(projection.getCategory())
                .teamId(projection.getTeamId())
                .teamName(projection.getTeamName())
                .teamLogo(projection.getTeamLogo())
                .value(rawValue)
                .formattedValue(formatValue(projection.getCategory(), rawValue))
                .build();
    }

    // TeamStatRankingInterface → TeamStatRankingDTO 변환
    private TeamStatRankingDTO convertToTeamStatRankingDTO(TeamStatRankingInterface projection) {
        if (projection == null) return null;

        return TeamStatRankingDTO.builder()
                .teamId(projection.getTeamId())
                .teamName(projection.getTeamName())
                .logoName(projection.getLogoName())
                .ops(projection.getOps())
                .avg(projection.getAvg())
                .hr(projection.getHr())
                .rbi(projection.getRbi())
                .sb(projection.getSb())
                .h(projection.getH())
                .era(projection.getEra())
                .whip(projection.getWhip())
                .w(projection.getW())
                .sv(projection.getSv())
                .so(projection.getSo())
                .hld(projection.getHld())
                .bb(projection.getBb())
                .build();
    }

    // 스탯값 포맷팅
    private String formatValue(String category, double value) {
        DecimalFormat threeDecimal = new DecimalFormat("0.000");
        DecimalFormat twoDecimal = new DecimalFormat("0.00");
        DecimalFormat integerFormat = new DecimalFormat("0");

        return switch (category) {
            case "OPS", "AVG" -> threeDecimal.format(value);
            case "HR", "SB", "SO", "BB", "H", "SV", "W", "RBI", "HLD" -> integerFormat.format(value);
            case "ERA", "WHIP" -> twoDecimal.format(value);
            default -> twoDecimal.format(value);
        };
    }

    // 타자 스탯 Top 팀 조회
    public List<TopStatTeamDTO> getTopBatterStats(int season) {
        List<String> batterOrder = Arrays.asList("OPS", "AVG", "HR", "RBI", "SB");
        List<TopStatTeamDTO> result = new ArrayList<>();

        for (String category : batterOrder) {
            TopStatTeamInterface record = teamStatsRepository.findTopByCategoryAndSeasonMax(season, category);
            if (record != null) {
                result.add(convertToTopStatTeamDTO(record));
            }
        }
        return result;
    }

    // 투수 스탯 Top 팀 조회
    public List<TopStatTeamDTO> getTopPitcherStats(int season) {
        List<String> pitcherOrder = Arrays.asList("ERA", "WHIP", "W", "SV", "SO");
        List<TopStatTeamDTO> result = new ArrayList<>();

        for (String category : pitcherOrder) {
            TopStatTeamInterface record;
            if (Arrays.asList("ERA", "WHIP").contains(category)) {
                record = teamStatsRepository.findTopByCategoryAndSeasonMin(season, category);
            } else {
                record = teamStatsRepository.findTopByCategoryAndSeasonMax(season, category);
            }
            if (record != null) {
                result.add(convertToTopStatTeamDTO(record));
            }
        }
        return result;
    }

    // 종합 정보 조회
    public List<TeamStatRankingDTO> getTeamRankingsSortedByStat(int season, String sort, String direction) {
        List<TeamStatRankingInterface> rawList = teamStatsRepository.findAllTeamStats(season);
        List<TeamStatRankingDTO> dtoList = new ArrayList<>();

        for (TeamStatRankingInterface record : rawList) {
            if (record != null) {
                dtoList.add(convertToTeamStatRankingDTO(record));
            }
        }

        if ("ASC".equalsIgnoreCase(direction)) {
            sortAscending(dtoList, sort);
        } else {
            sortDescending(dtoList, sort);
        }

        return dtoList;
    }

    // 오름차순 정렬
    private void sortAscending(List<TeamStatRankingDTO> list, String sort) {
        Comparator<TeamStatRankingDTO> comparator = (a, b) -> {
            Double valA = getValueByCategory(a, sort);
            Double valB = getValueByCategory(b, sort);
            if (valA == null && valB == null) return 0;
            if (valA == null) return 1;
            if (valB == null) return -1;
            return Double.compare(valA, valB);
        };
        Collections.sort(list, comparator);
    }

    // 내림차순 정렬
    private void sortDescending(List<TeamStatRankingDTO> list, String sort) {
        Comparator<TeamStatRankingDTO> comparator = (a, b) -> {
            Double valA = getValueByCategory(a, sort);
            Double valB = getValueByCategory(b, sort);
            if (valA == null && valB == null) return 0;
            if (valA == null) return 1;
            if (valB == null) return -1;
            return Double.compare(valB, valA);
        };
        Collections.sort(list, comparator);
    }

    // 카테고리에 따른 스탯 값 가져오기
    private Double getValueByCategory(TeamStatRankingDTO dto, String category) {
        return switch (category) {
            case "OPS" -> dto.getOps();
            case "AVG" -> dto.getAvg();
            case "HR" -> dto.getHr();
            case "RBI" -> dto.getRbi();
            case "SB" -> dto.getSb();
            case "H" -> dto.getH();
            case "ERA" -> dto.getEra();
            case "WHIP" -> dto.getWhip();
            case "W" -> dto.getW();
            case "SV" -> dto.getSv();
            case "SO" -> dto.getSo();
            case "HLD" -> dto.getHld();
            case "BB" -> dto.getBb();
            default -> null;
        };
    }
}
