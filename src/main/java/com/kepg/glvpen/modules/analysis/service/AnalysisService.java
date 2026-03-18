package com.kepg.glvpen.modules.analysis.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.kepg.glvpen.modules.analysis.dto.ChartDataDTO;
import com.kepg.glvpen.modules.analysis.dto.DashboardDTO;
import com.kepg.glvpen.modules.analysis.dto.PlayerAnalysisDTO;
import com.kepg.glvpen.modules.analysis.dto.TeamAnalysisDTO;
import com.kepg.glvpen.modules.analysis.repository.AnalysisColumnRepository;
import com.kepg.glvpen.modules.game.summaryRecord.repository.GameSummaryRecordRepository;
import com.kepg.glvpen.modules.player.repository.PlayerRepository;
import com.kepg.glvpen.modules.player.stats.domain.BatterStats;
import com.kepg.glvpen.modules.player.stats.domain.PitcherStats;
import com.kepg.glvpen.modules.player.stats.repository.BatterStatsRepository;
import com.kepg.glvpen.modules.player.stats.repository.PitcherStatsRepository;
import com.kepg.glvpen.modules.team.teamRanking.domain.TeamRanking;
import com.kepg.glvpen.modules.team.teamRanking.repository.TeamRankingRepository;
import com.kepg.glvpen.modules.team.teamStats.repository.TeamStatsRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisService {

    private final BatterStatsRepository batterStatsRepository;
    private final PitcherStatsRepository pitcherStatsRepository;
    private final TeamRankingRepository teamRankingRepository;
    private final AnalysisColumnRepository columnRepository;
    private final TeamStatsRepository teamStatsRepository;
    private final PlayerRepository playerRepository;
    private final GameSummaryRecordRepository gameSummaryRecordRepository;

    /**
     * 대시보드 데이터 집계
     */
    public DashboardDTO getDashboardData(int season) {
        // 등록된 선수 수 조회
        List<Integer> batterIds = batterStatsRepository.findDistinctPlayerIdsBySeason(season);
        List<Integer> pitcherIds = pitcherStatsRepository.findDistinctPlayerIdsBySeason(season);

        // 포지션별 wOBA 평균
        Map<String, Double> positionWobaAvg = calculatePositionWobaAvg(season);

        // 최근 컬럼
        var recentColumns = columnRepository.findTop5ByOrderByPublishDateDesc().stream()
                .map(c -> DashboardDTO.ColumnSummaryDTO.builder()
                        .id(c.getId())
                        .title(c.getTitle())
                        .category(c.getCategory())
                        .publishDate(c.getPublishDate().toString())
                        .viewCount(c.getViewCount())
                        .build())
                .collect(Collectors.toList());

        return DashboardDTO.builder()
                .season(season)
                .totalBatters(batterIds.size())
                .totalPitchers(pitcherIds.size())
                .totalTeams(10)
                .positionWobaAvg(positionWobaAvg)
                .recentColumns(recentColumns)
                .build();
    }

    /**
     * 선수 시즌별 지표 추이 (분석 차트용)
     */
    public PlayerAnalysisDTO getPlayerTrend(int playerId, String category, int startYear, int endYear) {
        Map<String, List<PlayerAnalysisDTO.SeasonValue>> trend = new HashMap<>();
        List<PlayerAnalysisDTO.SeasonValue> values = new ArrayList<>();

        for (int year = startYear; year <= endYear; year++) {
            var statOpt = batterStatsRepository.findStatValueByPlayerIdCategoryAndSeason(playerId, category, year);
            double val = statOpt.map(Double::parseDouble).orElse(0.0);
            values.add(PlayerAnalysisDTO.SeasonValue.builder().season(year).value(val).build());
        }

        trend.put(category, values);

        return PlayerAnalysisDTO.builder()
                .playerId(playerId)
                .seasonTrend(trend)
                .build();
    }

    /**
     * 팀 전력 밸런스 분석 (레이더 차트용)
     */
    public TeamAnalysisDTO getTeamBalance(int teamId, int season) {
        // 팀의 타자 wOBA 평균 / 투수 FIP 평균
        List<Object[]> batters = batterStatsRepository.findAllBatters(season);
        List<Object[]> pitchers = pitcherStatsRepository.findAllPitchers(season);

        double totalBatterWoba = 0, totalPitcherFip = 0;
        double totalSb = 0, totalHr = 0;
        int batterCount = 0, pitcherCount = 0;

        // 타자 인덱스: row[20] = teamId (findAllBatters 쿼리 기준)
        for (Object[] row : batters) {
            Integer tid = ((Number) row[20]).intValue();
            if (tid == teamId) {
                Double woba = row[4] != null ? ((Number) row[4]).doubleValue() : 0;
                Double sb = row[8] != null ? ((Number) row[8]).doubleValue() : 0;
                Double hr = row[7] != null ? ((Number) row[7]).doubleValue() : 0;
                totalBatterWoba += woba;
                totalSb += sb;
                totalHr += hr;
                batterCount++;
            }
        }

        // 투수 인덱스: row[14] = teamId (findAllPitchers 쿼리 기준, WAR 제거 후)
        for (Object[] row : pitchers) {
            Integer tid = ((Number) row[14]).intValue();
            if (tid == teamId) {
                Double fip = row[15] != null ? ((Number) row[15]).doubleValue() : 0;
                totalPitcherFip += fip;
                pitcherCount++;
            }
        }

        double avgBatterWoba = batterCount > 0 ? totalBatterWoba / batterCount : 0;
        double avgPitcherFip = pitcherCount > 0 ? totalPitcherFip / pitcherCount : 0;

        return TeamAnalysisDTO.builder()
                .teamId(teamId)
                .season(season)
                .battingWar(avgBatterWoba)
                .pitchingWar(avgPitcherFip)
                .totalBatterWar(avgBatterWoba)
                .totalPitcherWar(avgPitcherFip)
                .speedScore(totalSb)
                .powerScore(totalHr > 0 ? totalHr / Math.max(batterCount, 1) : 0)
                .build();
    }

    /**
     * 전체 팀 비교 데이터 (타투 밸런스 차트용)
     */
    public List<TeamAnalysisDTO> getTeamComparison(int season) {
        List<TeamAnalysisDTO> result = new ArrayList<>();
        for (int teamId = 1; teamId <= 10; teamId++) {
            result.add(getTeamBalance(teamId, season));
        }
        return result;
    }

    /**
     * wOBA 분포 차트 데이터
     */
    public ChartDataDTO getWarDistribution(int season) {
        Map<String, Double> posWobaAvg = calculatePositionWobaAvg(season);

        List<String> labels = new ArrayList<>(posWobaAvg.keySet());
        List<Double> values = new ArrayList<>(posWobaAvg.values());

        return ChartDataDTO.builder()
                .chartType("bar")
                .title("포지션별 wOBA 분포 (" + season + ")")
                .labels(labels)
                .datasets(List.of(
                        ChartDataDTO.DatasetDTO.builder()
                                .label("wOBA 평균")
                                .data(values)
                                .backgroundColor("rgba(54, 162, 235, 0.6)")
                                .borderColor("rgba(54, 162, 235, 1)")
                                .build()
                ))
                .build();
    }

    // ====== 시즌/트렌드 분석 메서드 ======

    /**
     * 리그 타고투저 추이 (이중 Y축: ERA + OPS)
     */
    public ChartDataDTO getLeagueHitPitchTrend(int startYear, int endYear) {
        List<Object[]> rows = teamStatsRepository.findLeagueAvgEraOps(startYear, endYear);

        List<String> labels = new ArrayList<>();
        List<Double> eraData = new ArrayList<>();
        List<Double> opsData = new ArrayList<>();

        for (Object[] row : rows) {
            labels.add(String.valueOf(((Number) row[0]).intValue()));
            eraData.add(row[1] != null ? ((Number) row[1]).doubleValue() : 0.0);
            opsData.add(row[2] != null ? ((Number) row[2]).doubleValue() : 0.0);
        }

        return ChartDataDTO.builder()
                .chartType("line")
                .title("리그 평균 ERA / OPS 추이")
                .labels(labels)
                .datasets(List.of(
                        ChartDataDTO.DatasetDTO.builder()
                                .label("ERA")
                                .data(eraData)
                                .backgroundColor("rgba(255, 99, 132, 0.2)")
                                .borderColor("rgba(255, 99, 132, 1)")
                                .yAxisID("y")
                                .build(),
                        ChartDataDTO.DatasetDTO.builder()
                                .label("OPS")
                                .data(opsData)
                                .backgroundColor("rgba(54, 162, 235, 0.2)")
                                .borderColor("rgba(54, 162, 235, 1)")
                                .yAxisID("y1")
                                .build()
                ))
                .build();
    }

    /**
     * 시즌별 리그 총 홈런 트렌드
     */
    public ChartDataDTO getHomerunTrend(int startYear, int endYear) {
        List<Object[]> rows = teamStatsRepository.findLeagueTotalHrBySeason(startYear, endYear);

        List<String> labels = new ArrayList<>();
        List<Double> hrData = new ArrayList<>();

        for (Object[] row : rows) {
            labels.add(String.valueOf(((Number) row[0]).intValue()));
            hrData.add(row[1] != null ? ((Number) row[1]).doubleValue() : 0.0);
        }

        return ChartDataDTO.builder()
                .chartType("bar")
                .title("시즌별 리그 총 홈런 수")
                .labels(labels)
                .datasets(List.of(
                        ChartDataDTO.DatasetDTO.builder()
                                .label("홈런")
                                .data(hrData)
                                .backgroundColor("rgba(255, 159, 64, 0.6)")
                                .borderColor("rgba(255, 159, 64, 1)")
                                .build()
                ))
                .build();
    }

    /**
     * 정규시즌 vs 포스트시즌 비교
     */
    public ChartDataDTO getSeriesComparison(int season) {
        List<Object[]> rows = batterStatsRepository.findSeriesComparison(season);

        Map<String, String> seriesNameMap = new LinkedHashMap<>();
        seriesNameMap.put("0", "정규시즌");
        seriesNameMap.put("WC", "와일드카드");
        seriesNameMap.put("PS", "준플레이오프");
        seriesNameMap.put("KS", "한국시리즈");

        List<String> labels = new ArrayList<>();
        List<Double> avgData = new ArrayList<>();
        List<Double> opsData = new ArrayList<>();
        List<Double> hrData = new ArrayList<>();

        for (Object[] row : rows) {
            String series = (String) row[0];
            labels.add(seriesNameMap.getOrDefault(series, series));
            avgData.add(row[1] != null ? ((Number) row[1]).doubleValue() : 0.0);
            opsData.add(row[2] != null ? ((Number) row[2]).doubleValue() : 0.0);
            hrData.add(row[3] != null ? ((Number) row[3]).doubleValue() : 0.0);
        }

        if (labels.isEmpty()) {
            labels.add("데이터 없음");
            avgData.add(0.0);
            opsData.add(0.0);
            hrData.add(0.0);
        }

        return ChartDataDTO.builder()
                .chartType("bar")
                .title(season + " 정규시즌 vs 포스트시즌")
                .labels(labels)
                .datasets(List.of(
                        ChartDataDTO.DatasetDTO.builder()
                                .label("AVG").data(avgData)
                                .backgroundColor("rgba(75, 192, 192, 0.6)")
                                .borderColor("rgba(75, 192, 192, 1)")
                                .build(),
                        ChartDataDTO.DatasetDTO.builder()
                                .label("OPS").data(opsData)
                                .backgroundColor("rgba(54, 162, 235, 0.6)")
                                .borderColor("rgba(54, 162, 235, 1)")
                                .build(),
                        ChartDataDTO.DatasetDTO.builder()
                                .label("HR").data(hrData)
                                .backgroundColor("rgba(255, 99, 132, 0.6)")
                                .borderColor("rgba(255, 99, 132, 1)")
                                .build()
                ))
                .build();
    }

    /**
     * 신인 임팩트 분석 (타자+투수 TOP)
     */
    public Map<String, Object> getRookieImpact(int debutYear) {
        List<Object[]> batters = playerRepository.findRookieBattersByDebutYear(debutYear);
        List<Object[]> pitchers = playerRepository.findRookiePitchersByDebutYear(debutYear);

        // 차트용: 신인 타자 wOBA TOP 10
        List<String> labels = new ArrayList<>();
        List<Double> wobaData = new ArrayList<>();
        int limit = Math.min(batters.size(), 10);
        for (int i = 0; i < limit; i++) {
            Object[] row = batters.get(i);
            labels.add((String) row[1]); // name
            wobaData.add(row[5] != null ? ((Number) row[5]).doubleValue() : 0.0); // woba
        }

        ChartDataDTO chart = ChartDataDTO.builder()
                .chartType("bar")
                .title(debutYear + " 신인 타자 wOBA TOP " + limit)
                .labels(labels)
                .datasets(List.of(
                        ChartDataDTO.DatasetDTO.builder()
                                .label("wOBA").data(wobaData)
                                .backgroundColor("rgba(153, 102, 255, 0.6)")
                                .borderColor("rgba(153, 102, 255, 1)")
                                .build()
                ))
                .build();

        // 테이블용 데이터
        List<Map<String, Object>> batterList = new ArrayList<>();
        for (Object[] row : batters) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ((Number) row[0]).intValue());
            m.put("name", row[1]);
            m.put("debutYear", ((Number) row[2]).intValue());
            m.put("teamName", row[3]);
            m.put("logoName", row[4]);
            m.put("woba", row[5] != null ? ((Number) row[5]).doubleValue() : 0.0);
            m.put("avg", row[6] != null ? ((Number) row[6]).doubleValue() : 0.0);
            m.put("hr", row[7] != null ? ((Number) row[7]).doubleValue() : 0.0);
            m.put("ops", row[8] != null ? ((Number) row[8]).doubleValue() : 0.0);
            batterList.add(m);
        }

        List<Map<String, Object>> pitcherList = new ArrayList<>();
        for (Object[] row : pitchers) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ((Number) row[0]).intValue());
            m.put("name", row[1]);
            m.put("debutYear", ((Number) row[2]).intValue());
            m.put("teamName", row[3]);
            m.put("logoName", row[4]);
            m.put("fip", row[5] != null ? ((Number) row[5]).doubleValue() : 0.0);
            m.put("era", row[6] != null ? ((Number) row[6]).doubleValue() : 0.0);
            m.put("wins", row[7] != null ? ((Number) row[7]).doubleValue() : 0.0);
            m.put("so", row[8] != null ? ((Number) row[8]).doubleValue() : 0.0);
            pitcherList.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chart", chart);
        result.put("batters", batterList);
        result.put("pitchers", pitcherList);
        result.put("debutYear", debutYear);
        return result;
    }

    /**
     * 출신교 분류별 선수 수 추이 (stacked bar)
     */
    public ChartDataDTO getSchoolTypeAnalysis(int startYear, int endYear) {
        List<Object[]> rows = playerRepository.findSchoolTypeDistributionAll(startYear, endYear);

        // season별 schoolType별 count 집계
        Map<Integer, Map<String, Double>> seasonMap = new LinkedHashMap<>();
        for (Object[] row : rows) {
            int season = ((Number) row[0]).intValue();
            String schoolType = (String) row[1];
            double count = ((Number) row[2]).doubleValue();
            seasonMap.computeIfAbsent(season, k -> new LinkedHashMap<>()).put(schoolType, count);
        }

        List<String> labels = seasonMap.keySet().stream().map(String::valueOf).collect(Collectors.toList());
        String[] types = {"고졸", "대졸", "기타"};
        String[][] colors = {
                {"rgba(255, 99, 132, 0.6)", "rgba(255, 99, 132, 1)"},
                {"rgba(54, 162, 235, 0.6)", "rgba(54, 162, 235, 1)"},
                {"rgba(255, 206, 86, 0.6)", "rgba(255, 206, 86, 1)"}
        };

        List<ChartDataDTO.DatasetDTO> datasets = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            String type = types[i];
            List<Double> data = new ArrayList<>();
            for (Integer season : seasonMap.keySet()) {
                data.add(seasonMap.get(season).getOrDefault(type, 0.0));
            }
            datasets.add(ChartDataDTO.DatasetDTO.builder()
                    .label(type).data(data)
                    .backgroundColor(colors[i][0])
                    .borderColor(colors[i][1])
                    .build());
        }

        return ChartDataDTO.builder()
                .chartType("stackedBar")
                .title("출신교 분류별 선수 수 추이")
                .labels(labels)
                .datasets(datasets)
                .build();
    }

    /**
     * 게임 이벤트(카테고리) 발생 트렌드
     */
    public ChartDataDTO getGameEventTrend(int startYear, int endYear) {
        List<Object[]> rows = gameSummaryRecordRepository.findEventTrendBySeason(startYear, endYear);

        // season별 category별 count
        Map<Integer, Map<String, Double>> seasonMap = new LinkedHashMap<>();
        java.util.Set<String> allCategories = new java.util.LinkedHashSet<>();

        for (Object[] row : rows) {
            int season = ((Number) row[0]).intValue();
            String category = (String) row[1];
            double count = ((Number) row[2]).doubleValue();
            seasonMap.computeIfAbsent(season, k -> new LinkedHashMap<>()).put(category, count);
            allCategories.add(category);
        }

        List<String> labels = seasonMap.keySet().stream().map(String::valueOf).collect(Collectors.toList());

        String[] colorPalette = {
                "rgba(255, 99, 132, 1)", "rgba(54, 162, 235, 1)", "rgba(255, 206, 86, 1)",
                "rgba(75, 192, 192, 1)", "rgba(153, 102, 255, 1)", "rgba(255, 159, 64, 1)",
                "rgba(199, 199, 199, 1)", "rgba(83, 102, 255, 1)", "rgba(255, 99, 255, 1)"
        };

        List<ChartDataDTO.DatasetDTO> datasets = new ArrayList<>();
        int colorIdx = 0;
        for (String category : allCategories) {
            List<Double> data = new ArrayList<>();
            for (Integer season : seasonMap.keySet()) {
                data.add(seasonMap.get(season).getOrDefault(category, 0.0));
            }
            String color = colorPalette[colorIdx % colorPalette.length];
            datasets.add(ChartDataDTO.DatasetDTO.builder()
                    .label(category).data(data)
                    .backgroundColor(color.replace("1)", "0.2)"))
                    .borderColor(color)
                    .build());
            colorIdx++;
        }

        return ChartDataDTO.builder()
                .chartType("line")
                .title("시즌별 경기 이벤트 발생 추이")
                .labels(labels)
                .datasets(datasets)
                .build();
    }

    // ====== 내부 메서드 ======

    private Map<String, Double> calculatePositionWobaAvg(int season) {
        List<Object[]> topByPosition = batterStatsRepository.findTopBattersByPosition(season);
        Map<String, Double> posMap = new LinkedHashMap<>();
        for (Object[] row : topByPosition) {
            String pos = (String) row[0];
            Double woba = row[4] != null ? ((Number) row[4]).doubleValue() : 0.0;
            posMap.put(pos, woba);
        }
        return posMap;
    }
}
