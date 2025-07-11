package com.kepg.BaseBallLOCK.modules.team.teamStats.service;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.kepg.BaseBallLOCK.modules.team.teamStats.domain.TeamStats;
import com.kepg.BaseBallLOCK.modules.team.teamStats.dto.TeamStatRankingDTO;
import com.kepg.BaseBallLOCK.modules.team.teamStats.dto.TeamStatRankingInterface;
import com.kepg.BaseBallLOCK.modules.team.teamStats.dto.TopStatTeamDTO;
import com.kepg.BaseBallLOCK.modules.team.teamStats.dto.TopStatTeamInterface;
import com.kepg.BaseBallLOCK.modules.team.teamStats.repository.TeamStatsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TeamStatsService {

    private final TeamStatsRepository teamStatsRepository;

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

        Double betterWar = projection.getBetterWar() != null ? projection.getBetterWar() : 0.0;
        Double pitcherWar = projection.getPitcherWar() != null ? projection.getPitcherWar() : 0.0;
        double totalWar = betterWar + pitcherWar;

        return TeamStatRankingDTO.builder()
                .teamId(projection.getTeamId())
                .teamName(projection.getTeamName())
                .logoName(projection.getLogoName())
                .totalWar(totalWar)
                .ops(projection.getOps())
                .avg(projection.getAvg())
                .hr(projection.getHr())
                .sb(projection.getSb())
                .betterWar(projection.getBetterWar())
                .pitcherWar(projection.getPitcherWar())
                .so(projection.getSo())
                .w(projection.getW())
                .h(projection.getH())
                .sv(projection.getSv())
                .era(projection.getEra())
                .whip(projection.getWhip())
                .bb(projection.getBb())
                .battingWaa(projection.getBattingWaa())
                .baserunningWaa(projection.getBaserunningWaa())
                .defenseWaa(projection.getDefenseWaa())
                .startingWaa(projection.getStartingWaa())
                .bullpenWaa(projection.getBullpenWaa())
                .build();
    }

    // 스탯값 포맷팅
    private String formatValue(String category, double value) {
        DecimalFormat threeDecimal = new DecimalFormat("0.000");
        DecimalFormat twoDecimal = new DecimalFormat("0.00");
        DecimalFormat integerFormat = new DecimalFormat("0");

        if (category.equals("OPS") || category.equals("AVG")) {
            return threeDecimal.format(value);
        } else if (Arrays.asList("BetterWAR", "PitcherWAR", "ERA", "WHIP", "타격", "주루", "수비", "선발", "불펜").contains(category)) {
            return twoDecimal.format(value);
        } else if (Arrays.asList("HR", "SB", "SO", "BB", "H", "SV", "W").contains(category)) {
            return integerFormat.format(value);
        } else {
            return twoDecimal.format(value);
        }
    }

    // 타자 스탯 Top 팀 조회
    public List<TopStatTeamDTO> getTopBatterStats(int season) {
        List<String> batterOrder = Arrays.asList("BetterWAR", "AVG", "OPS", "HR", "SB");
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
        List<String> pitcherOrder = Arrays.asList("PitcherWAR", "ERA", "WHIP", "SO", "BB");
        List<TopStatTeamDTO> result = new ArrayList<>();

        for (String category : pitcherOrder) {
            TopStatTeamInterface record;
            if (Arrays.asList("ERA", "WHIP", "BB").contains(category)) {
                record = teamStatsRepository.findTopByCategoryAndSeasonMin(season, category); // ERA, WHIP, BB는 낮을수록 좋음
            } else {
                record = teamStatsRepository.findTopByCategoryAndSeasonMax(season, category);
            }
            if (record != null) {
                result.add(convertToTopStatTeamDTO(record));
            }
        }
        return result;
    }

    // WAA 스탯 Top 팀 조회
    public List<TopStatTeamDTO> getTopWaaStats(int season) {
        List<String> waaOrder = Arrays.asList("타격", "선발", "불펜", "수비", "주루");
        List<TopStatTeamDTO> result = new ArrayList<>();

        for (String category : waaOrder) {
            TopStatTeamInterface record = teamStatsRepository.findTopByCategoryAndSeasonMax(season, category);
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
        for (int i = 0; i < list.size() - 1; i++) {
            for (int j = i + 1; j < list.size(); j++) {
                Double a = getValueByCategory(list.get(i), sort);
                Double b = getValueByCategory(list.get(j), sort);
                if (a != null && b != null && a > b) {
                    TeamStatRankingDTO temp = list.get(i);
                    list.set(i, list.get(j));
                    list.set(j, temp);
                }
            }
        }
    }

    // 내림차순 정렬
    private void sortDescending(List<TeamStatRankingDTO> list, String sort) {
        for (int i = 0; i < list.size() - 1; i++) {
            for (int j = i + 1; j < list.size(); j++) {
                Double a = getValueByCategory(list.get(i), sort);
                Double b = getValueByCategory(list.get(j), sort);
                if (a != null && b != null && a < b) {
                    TeamStatRankingDTO temp = list.get(i);
                    list.set(i, list.get(j));
                    list.set(j, temp);
                }
            }
        }
    }

    // 카테고리에 따른 스탯 값 가져오기
    private Double getValueByCategory(TeamStatRankingDTO dto, String category) {
    	if (category.equals("TotalWAR")) return dto.getTotalWar(); 
        if (category.equals("OPS")) return dto.getOps();
        if (category.equals("AVG")) return dto.getAvg();
        if (category.equals("HR")) return dto.getHr();
        if (category.equals("SB")) return dto.getSb();
        if (category.equals("BetterWAR")) return dto.getBetterWar();
        if (category.equals("PitcherWAR")) return dto.getPitcherWar();
        if (category.equals("SO")) return dto.getSo();
        if (category.equals("W")) return dto.getW();
        if (category.equals("H")) return dto.getH();
        if (category.equals("SV")) return dto.getSv();
        if (category.equals("ERA")) return dto.getEra();
        if (category.equals("WHIP")) return dto.getWhip();
        if (category.equals("BB")) return dto.getBb();
        if (category.equals("타격")) return dto.getBattingWaa();
        if (category.equals("주루")) return dto.getBaserunningWaa();
        if (category.equals("수비")) return dto.getDefenseWaa();
        if (category.equals("선발")) return dto.getStartingWaa();
        if (category.equals("불펜")) return dto.getBullpenWaa();
        return null;
    }
}