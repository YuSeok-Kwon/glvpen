package com.kepg.glvpen.modules.analysis.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.kepg.glvpen.modules.analysis.dto.MarcelProjectionDTO;
import com.kepg.glvpen.modules.player.domain.Player;
import com.kepg.glvpen.modules.player.repository.PlayerRepository;
import com.kepg.glvpen.modules.player.stats.repository.BatterStatsRepository;
import com.kepg.glvpen.modules.player.stats.repository.PitcherStatsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MarcelProjectionService {

    private final BatterStatsRepository batterStatsRepository;
    private final PitcherStatsRepository pitcherStatsRepository;
    private final PlayerRepository playerRepository;

    // Marcel 가중치: Year-1 * 5, Year-2 * 4, Year-3 * 3 → 총 12
    private static final int[] WEIGHTS = {5, 4, 3};
    private static final int WEIGHT_SUM = 12;
    // 평균 회귀 PA (타자 1200, 투수 130 IP 기준)
    private static final double BATTER_REGRESSION_PA = 1200;
    private static final double PITCHER_REGRESSION_IP = 130;
    // 노화 곡선: 피크 27~28세
    private static final int PEAK_AGE = 27;
    private static final double RATE_AGING_PER_YEAR = 0.006;
    private static final double COUNT_AGING_PER_YEAR = 0.003;

    private static final String[] BATTER_CATEGORIES = {"AVG", "OPS", "HR", "wOBA", "SB", "RBI", "OBP", "SLG", "BB%", "K%"};
    private static final String[] PITCHER_CATEGORIES = {"ERA", "WHIP", "FIP", "K/9", "BB/9", "W", "SO"};

    /**
     * 타자 성적 예측
     */
    public MarcelProjectionDTO projectBatter(int playerId, int targetSeason) {
        Player player = playerRepository.findById(playerId).orElse(null);
        if (player == null) return null;

        // 최근 3시즌 데이터 수집
        Map<Integer, Map<String, Double>> seasonStats = new HashMap<>();
        double[] seasonPA = new double[3];
        int dataSeasons = 0;

        for (int i = 0; i < 3; i++) {
            int season = targetSeason - 1 - i;
            List<Object[]> rawStats = batterStatsRepository.findStatsRawByPlayerIdAndSeason(playerId, season);
            if (!rawStats.isEmpty()) {
                Map<String, Double> stats = new HashMap<>();
                for (Object[] row : rawStats) {
                    String cat = (String) row[0];
                    Double val = toDouble(row[1]);
                    if (val != null) stats.put(cat, val);
                }
                seasonStats.put(i, stats);
                seasonPA[i] = stats.getOrDefault("PA", 0.0);
                dataSeasons++;
            }
        }

        if (dataSeasons == 0) return null;

        // 리그 평균
        Map<String, Double> leagueAvg = getLeagueAvg(targetSeason - 1, true);

        // 카테고리별 Marcel 예측
        List<MarcelProjectionDTO.ProjectionDetail> projections = new ArrayList<>();
        for (String cat : BATTER_CATEGORIES) {
            double weighted = 0;
            double weightedPA = 0;

            for (int i = 0; i < 3; i++) {
                Map<String, Double> stats = seasonStats.get(i);
                if (stats != null && stats.containsKey(cat)) {
                    weighted += stats.get(cat) * WEIGHTS[i];
                    weightedPA += seasonPA[i] * WEIGHTS[i];
                }
            }

            if (weightedPA == 0) continue;
            double marcelBase = weighted / WEIGHT_SUM;
            double effectivePA = weightedPA / WEIGHT_SUM;

            // 평균 회귀
            double lgAvg = leagueAvg.getOrDefault(cat, 0.0);
            double projected;
            if (isRateStat(cat)) {
                projected = (marcelBase * effectivePA + lgAvg * BATTER_REGRESSION_PA)
                        / (effectivePA + BATTER_REGRESSION_PA);
            } else {
                projected = marcelBase;
            }

            // 노화 곡선 적용
            if (player.getBirthDate() != null) {
                int age = targetSeason - player.getBirthDate().getYear();
                int ageDiff = age - PEAK_AGE;
                double agingFactor = isRateStat(cat) ? RATE_AGING_PER_YEAR : COUNT_AGING_PER_YEAR;
                if (ageDiff > 0) {
                    projected *= (1.0 - agingFactor * ageDiff);
                } else if (ageDiff < 0) {
                    projected *= (1.0 + agingFactor * Math.abs(ageDiff) * 0.5);
                }
            }

            Double lastSeason = seasonStats.containsKey(0) ? seasonStats.get(0).get(cat) : null;
            Double changeRate = (lastSeason != null && lastSeason != 0)
                    ? Math.round(((projected - lastSeason) / Math.abs(lastSeason)) * 100 * 10.0) / 10.0 : null;

            projections.add(MarcelProjectionDTO.ProjectionDetail.builder()
                    .category(cat)
                    .projected(round(projected))
                    .lastSeason(lastSeason != null ? round(lastSeason) : null)
                    .leagueAvg(lgAvg != 0 ? round(lgAvg) : null)
                    .changeRate(changeRate)
                    .build());
        }

        // 팀/로고 정보 조회 (WAR 없으면 다른 시즌에서 조회)
        String teamName = "";
        String logoName = "";
        for (int s = targetSeason - 1; s >= targetSeason - 3; s--) {
            var teamOpt = batterStatsRepository.findTeamAndPosition(playerId, s);
            if (teamOpt.isPresent()) {
                Object[] ti = teamOpt.get();
                if (ti.length >= 3) {
                    teamName = ti[1] != null ? (String) ti[1] : "";
                    logoName = ti[2] != null ? (String) ti[2] : "";
                    break;
                }
            }
        }

        double confidence = Math.min(1.0, dataSeasons / 3.0);

        return MarcelProjectionDTO.builder()
                .playerId(playerId)
                .playerName(player.getName())
                .teamName(teamName)
                .logoName(logoName)
                .playerType("batter")
                .targetSeason(targetSeason)
                .confidence(confidence)
                .projections(projections)
                .build();
    }

    /**
     * 투수 성적 예측
     */
    public MarcelProjectionDTO projectPitcher(int playerId, int targetSeason) {
        Player player = playerRepository.findById(playerId).orElse(null);
        if (player == null) return null;

        Map<Integer, Map<String, Double>> seasonStats = new HashMap<>();
        double[] seasonIP = new double[3];
        int dataSeasons = 0;

        for (int i = 0; i < 3; i++) {
            int season = targetSeason - 1 - i;
            List<Object[]> rawStats = pitcherStatsRepository.findStatsRawByPlayerIdAndSeason(playerId, season);
            if (!rawStats.isEmpty()) {
                Map<String, Double> stats = new HashMap<>();
                for (Object[] row : rawStats) {
                    String cat = (String) row[0];
                    Double val = toDouble(row[1]);
                    if (val != null) stats.put(cat, val);
                }
                seasonStats.put(i, stats);
                seasonIP[i] = stats.getOrDefault("IP", 0.0);
                dataSeasons++;
            }
        }

        if (dataSeasons == 0) return null;

        Map<String, Double> leagueAvg = getLeagueAvg(targetSeason - 1, false);

        List<MarcelProjectionDTO.ProjectionDetail> projections = new ArrayList<>();
        for (String cat : PITCHER_CATEGORIES) {
            double weighted = 0;
            double weightedIP = 0;

            for (int i = 0; i < 3; i++) {
                Map<String, Double> stats = seasonStats.get(i);
                if (stats != null && stats.containsKey(cat)) {
                    weighted += stats.get(cat) * WEIGHTS[i];
                    weightedIP += seasonIP[i] * WEIGHTS[i];
                }
            }

            if (weightedIP == 0) continue;
            double marcelBase = weighted / WEIGHT_SUM;
            double effectiveIP = weightedIP / WEIGHT_SUM;

            double lgAvg = leagueAvg.getOrDefault(cat, 0.0);
            double projected;
            if (isPitcherRateStat(cat)) {
                projected = (marcelBase * effectiveIP + lgAvg * PITCHER_REGRESSION_IP)
                        / (effectiveIP + PITCHER_REGRESSION_IP);
            } else {
                projected = marcelBase;
            }

            // 노화 보정
            if (player.getBirthDate() != null) {
                int age = targetSeason - player.getBirthDate().getYear();
                int ageDiff = age - (PEAK_AGE + 1); // 투수 피크 28세
                double agingFactor = isPitcherRateStat(cat) ? RATE_AGING_PER_YEAR : COUNT_AGING_PER_YEAR;
                boolean isLowerBetter = cat.equals("ERA") || cat.equals("WHIP") || cat.equals("FIP") || cat.equals("BB/9");
                if (ageDiff > 0) {
                    projected *= isLowerBetter ? (1.0 + agingFactor * ageDiff) : (1.0 - agingFactor * ageDiff);
                }
            }

            Double lastSeason = seasonStats.containsKey(0) ? seasonStats.get(0).get(cat) : null;
            Double changeRate = (lastSeason != null && lastSeason != 0)
                    ? Math.round(((projected - lastSeason) / Math.abs(lastSeason)) * 100 * 10.0) / 10.0 : null;

            projections.add(MarcelProjectionDTO.ProjectionDetail.builder()
                    .category(cat)
                    .projected(round(projected))
                    .lastSeason(lastSeason != null ? round(lastSeason) : null)
                    .leagueAvg(lgAvg != 0 ? round(lgAvg) : null)
                    .changeRate(changeRate)
                    .build());
        }

        String teamName = "";
        String logoName = "";
        var teamOpt = batterStatsRepository.findTeamAndPosition(playerId, targetSeason - 1);
        if (teamOpt.isPresent()) {
            Object[] ti = teamOpt.get();
            teamName = (String) ti[1];
            logoName = (String) ti[2];
        }

        double confidence = Math.min(1.0, dataSeasons / 3.0);

        return MarcelProjectionDTO.builder()
                .playerId(playerId)
                .playerName(player.getName())
                .teamName(teamName)
                .logoName(logoName)
                .playerType("pitcher")
                .targetSeason(targetSeason)
                .confidence(confidence)
                .projections(projections)
                .build();
    }

    /**
     * 전체 타자 wOBA 예측 랭킹
     */
    public List<MarcelProjectionDTO.ProjectionRanking> projectAllBatters(int targetSeason, int limit) {
        List<Integer> playerIds = batterStatsRepository.findDistinctPlayerIdsBySeason(targetSeason - 1);
        List<MarcelProjectionDTO.ProjectionRanking> rankings = new ArrayList<>();

        for (int pid : playerIds) {
            MarcelProjectionDTO proj = projectBatter(pid, targetSeason);
            if (proj == null) continue;

            Double projWoba = proj.getProjections().stream()
                    .filter(p -> "wOBA".equals(p.getCategory()))
                    .map(MarcelProjectionDTO.ProjectionDetail::getProjected)
                    .findFirst().orElse(null);

            if (projWoba == null) continue;

            Double lastWoba = proj.getProjections().stream()
                    .filter(p -> "wOBA".equals(p.getCategory()))
                    .map(MarcelProjectionDTO.ProjectionDetail::getLastSeason)
                    .findFirst().orElse(null);

            rankings.add(MarcelProjectionDTO.ProjectionRanking.builder()
                    .playerId(pid)
                    .playerName(proj.getPlayerName())
                    .teamName(proj.getTeamName())
                    .logoName(proj.getLogoName())
                    .projectedWar(projWoba)
                    .lastSeasonWar(lastWoba)
                    .confidence(proj.getConfidence())
                    .build());
        }

        rankings.sort(Comparator.comparingDouble(MarcelProjectionDTO.ProjectionRanking::getProjectedWar).reversed());
        for (int i = 0; i < rankings.size(); i++) {
            rankings.get(i).setRank(i + 1);
        }

        return limit > 0 && limit < rankings.size() ? rankings.subList(0, limit) : rankings;
    }

    private Map<String, Double> getLeagueAvg(int season, boolean isBatter) {
        List<Object[]> rows = isBatter
                ? batterStatsRepository.findLeagueAvgBySeason(season)
                : pitcherStatsRepository.findLeagueAvgBySeason(season);

        Map<String, Double> avg = new HashMap<>();
        for (Object[] row : rows) {
            String cat = (String) row[0];
            Double val = toDouble(row[1]);
            if (val != null) avg.put(cat, val);
        }
        return avg;
    }

    private boolean isRateStat(String cat) {
        return List.of("AVG", "OPS", "OBP", "SLG", "BB%", "K%", "ISO", "BABIP").contains(cat);
    }

    private boolean isPitcherRateStat(String cat) {
        return List.of("ERA", "WHIP", "FIP", "xFIP", "K/9", "BB/9").contains(cat);
    }

    private double round(double val) {
        return Math.round(val * 1000.0) / 1000.0;
    }

    private Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return null; }
    }
}
