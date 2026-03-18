package com.kepg.glvpen.modules.analysis.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.kepg.glvpen.modules.analysis.dto.WinPredictionDTO;
import com.kepg.glvpen.modules.game.schedule.domain.Schedule;
import com.kepg.glvpen.modules.game.schedule.repository.ScheduleRepository;
import com.kepg.glvpen.modules.team.domain.Team;
import com.kepg.glvpen.modules.team.repository.TeamRepository;
import com.kepg.glvpen.modules.team.teamStats.dto.TeamStatRankingInterface;
import com.kepg.glvpen.modules.team.teamStats.repository.TeamStatsRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WinPredictionService {

    private final ScheduleRepository scheduleRepository;
    private final TeamStatsRepository teamStatsRepository;
    private final TeamRepository teamRepository;

    // 모델 파라미터 (학습 후 캐시)
    private volatile double[] weights;
    private volatile double bias;
    private volatile double accuracy;
    private volatile double[] featureMeans;
    private volatile double[] featureStds;
    private final Object trainLock = new Object();

    // Features: deltaERA(반전), deltaOPS, deltaHR, deltaWHIP(반전), homeAdvantage
    private static final int NUM_FEATURES = 5;
    private static final double LEARNING_RATE = 0.01;
    private static final int EPOCHS = 1000;
    private static final double HOME_ADVANTAGE_DEFAULT = 0.54;

    /**
     * 승리 확률 예측
     */
    public WinPredictionDTO predict(int homeTeamId, int awayTeamId, int season) {
        // 모델이 없으면 lazy 학습
        if (weights == null) {
            trainModel(2020, season - 1);
        }

        // 팀 정보
        Team homeTeam = teamRepository.findById(homeTeamId).orElse(null);
        Team awayTeam = teamRepository.findById(awayTeamId).orElse(null);
        if (homeTeam == null || awayTeam == null) return null;

        // 팀 스탯 조회
        List<TeamStatRankingInterface> allStats = teamStatsRepository.findAllTeamStats(season);
        TeamStatRankingInterface homeStat = findTeamStat(allStats, homeTeamId);
        TeamStatRankingInterface awayStat = findTeamStat(allStats, awayTeamId);

        double homeWinProb;
        Map<String, Double> deltas = new LinkedHashMap<>();

        if (homeStat == null || awayStat == null || weights == null) {
            homeWinProb = HOME_ADVANTAGE_DEFAULT;
        } else {
            double deltaEra = safeVal(awayStat.getEra()) - safeVal(homeStat.getEra()); // 반전: 홈이 낮으면 양수
            double deltaOps = safeVal(homeStat.getOps()) - safeVal(awayStat.getOps());
            double deltaHr = safeVal(homeStat.getHr()) - safeVal(awayStat.getHr());
            double deltaWhip = safeVal(awayStat.getWhip()) - safeVal(homeStat.getWhip()); // 반전: 홈이 낮으면 양수
            double homeAdv = 1.0;

            double[] features = {deltaEra, deltaOps, deltaHr, deltaWhip, homeAdv};

            // Z-score 정규화 (학습 때와 동일 스케일)
            double[] normalized = new double[NUM_FEATURES];
            for (int i = 0; i < NUM_FEATURES; i++) {
                normalized[i] = featureStds[i] > 0 ? (features[i] - featureMeans[i]) / featureStds[i] : 0;
            }

            homeWinProb = sigmoid(dotProduct(normalized, weights) + bias);
            homeWinProb = Math.max(0.25, Math.min(0.75, homeWinProb)); // 합리적 범위 제한

            deltas.put("ERA차", round(deltaEra));
            deltas.put("OPS차", round(deltaOps));
            deltas.put("HR차", round(deltaHr));
            deltas.put("WHIP차", round(deltaWhip));
        }

        return WinPredictionDTO.builder()
                .homeTeamId(homeTeamId)
                .homeTeamName(homeTeam.getName())
                .homeTeamLogo(homeTeam.getLogoName())
                .awayTeamId(awayTeamId)
                .awayTeamName(awayTeam.getName())
                .awayTeamLogo(awayTeam.getLogoName())
                .homeWinProbability(round(homeWinProb))
                .awayWinProbability(round(1.0 - homeWinProb))
                .modelAccuracy(accuracy)
                .season(season)
                .featureDeltas(deltas)
                .build();
    }

    /**
     * 로지스틱 회귀 모델 학습
     */
    public WinPredictionDTO.TrainResult trainModel(int startSeason, int endSeason) {
        synchronized (trainLock) {
            List<double[]> featureList = new ArrayList<>();
            List<Integer> labelList = new ArrayList<>();

            for (int season = startSeason; season <= endSeason; season++) {
                List<TeamStatRankingInterface> stats = teamStatsRepository.findAllTeamStats(season);
                if (stats.isEmpty()) continue;

                Map<Integer, TeamStatRankingInterface> statMap = new HashMap<>();
                for (TeamStatRankingInterface s : stats) {
                    statMap.put(s.getTeamId(), s);
                }

                List<Schedule> games = scheduleRepository.findAllFinishedBySeason(season);
                for (Schedule game : games) {
                    if (game.getHomeTeamScore() == null || game.getAwayTeamScore() == null) continue;
                    if (game.getHomeTeamScore().equals(game.getAwayTeamScore())) continue; // 동점 제외

                    TeamStatRankingInterface homeStat = statMap.get(game.getHomeTeamId());
                    TeamStatRankingInterface awayStat = statMap.get(game.getAwayTeamId());
                    if (homeStat == null || awayStat == null) continue;

                    double deltaEra = safeVal(awayStat.getEra()) - safeVal(homeStat.getEra());
                    double deltaOps = safeVal(homeStat.getOps()) - safeVal(awayStat.getOps());
                    double deltaHr = safeVal(homeStat.getHr()) - safeVal(awayStat.getHr());
                    double deltaWhip = safeVal(awayStat.getWhip()) - safeVal(homeStat.getWhip());
                    double homeAdv = 1.0;

                    featureList.add(new double[]{deltaEra, deltaOps, deltaHr, deltaWhip, homeAdv});
                    labelList.add(game.getHomeTeamScore() > game.getAwayTeamScore() ? 1 : 0);
                }
            }

            int n = featureList.size();
            if (n == 0) {
                return WinPredictionDTO.TrainResult.builder()
                        .trainingSamples(0).accuracy(0).trainedSeasons(startSeason + "~" + endSeason).build();
            }

            // Feature 정규화
            featureMeans = new double[NUM_FEATURES];
            featureStds = new double[NUM_FEATURES];

            for (double[] f : featureList) {
                for (int i = 0; i < NUM_FEATURES; i++) featureMeans[i] += f[i];
            }
            for (int i = 0; i < NUM_FEATURES; i++) featureMeans[i] /= n;

            for (double[] f : featureList) {
                for (int i = 0; i < NUM_FEATURES; i++) featureStds[i] += Math.pow(f[i] - featureMeans[i], 2);
            }
            for (int i = 0; i < NUM_FEATURES; i++) featureStds[i] = Math.sqrt(featureStds[i] / n);

            double[][] normalized = new double[n][NUM_FEATURES];
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < NUM_FEATURES; i++) {
                    normalized[j][i] = featureStds[i] > 0 ? (featureList.get(j)[i] - featureMeans[i]) / featureStds[i] : 0;
                }
            }

            // Gradient Descent
            double[] w = new double[NUM_FEATURES];
            double b = 0;

            for (int epoch = 0; epoch < EPOCHS; epoch++) {
                double[] gradW = new double[NUM_FEATURES];
                double gradB = 0;

                for (int j = 0; j < n; j++) {
                    double pred = sigmoid(dotProduct(normalized[j], w) + b);
                    double error = pred - labelList.get(j);

                    for (int i = 0; i < NUM_FEATURES; i++) {
                        gradW[i] += error * normalized[j][i];
                    }
                    gradB += error;
                }

                for (int i = 0; i < NUM_FEATURES; i++) {
                    w[i] -= LEARNING_RATE * gradW[i] / n;
                }
                b -= LEARNING_RATE * gradB / n;
            }

            weights = w;
            bias = b;

            // 정확도 계산
            int correct = 0;
            for (int j = 0; j < n; j++) {
                double pred = sigmoid(dotProduct(normalized[j], w) + b);
                int predicted = pred >= 0.5 ? 1 : 0;
                if (predicted == labelList.get(j)) correct++;
            }
            accuracy = round((double) correct / n);

            log.info("승리 예측 모델 학습 완료: {}~{} 시즌, {}경기, 정확도 {}", startSeason, endSeason, n, accuracy);

            return WinPredictionDTO.TrainResult.builder()
                    .trainingSamples(n)
                    .accuracy(accuracy)
                    .trainedSeasons(startSeason + "~" + endSeason)
                    .build();
        }
    }

    private TeamStatRankingInterface findTeamStat(List<TeamStatRankingInterface> stats, int teamId) {
        return stats.stream().filter(s -> s.getTeamId() == teamId).findFirst().orElse(null);
    }

    private double sigmoid(double z) {
        return 1.0 / (1.0 + Math.exp(-z));
    }

    private double dotProduct(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }

    private double safeVal(Double val) {
        return val != null ? val : 0.0;
    }

    private double round(double val) {
        return Math.round(val * 1000.0) / 1000.0;
    }
}
