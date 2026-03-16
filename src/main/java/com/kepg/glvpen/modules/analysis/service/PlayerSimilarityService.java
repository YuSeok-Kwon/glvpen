package com.kepg.glvpen.modules.analysis.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.kepg.glvpen.modules.analysis.dto.PlayerSimilarityDTO;
import com.kepg.glvpen.modules.player.stats.repository.BatterStatsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlayerSimilarityService {

    private final BatterStatsRepository batterStatsRepository;

    // 유사도 비교에 사용할 지표 (인덱스 매핑)
    private static final String[] COMPARE_LABELS = {"WAR", "AVG", "OPS", "HR", "SB", "K%", "BB%", "ISO"};
    private static final int[] COMPARE_INDICES = {5, 6, 7, 8, 9, 10, 11, 12};

    /**
     * 특정 선수와 유사한 타자 찾기 (코사인 유사도)
     */
    public PlayerSimilarityDTO findSimilarBatters(int playerId, int season, int topN) {
        List<Object[]> rows = batterStatsRepository.findAllBattersForAnalysis(season);
        if (rows.isEmpty()) {
            return PlayerSimilarityDTO.builder().season(season).similarPlayers(List.of()).build();
        }

        // 데이터 추출
        List<double[]> vectors = new ArrayList<>();
        List<Object[]> playerRows = new ArrayList<>();
        int baseIndex = -1;

        for (Object[] row : rows) {
            double[] vec = extractVector(row);
            if (hasValidData(vec)) {
                if (toInt(row[0]) == playerId) {
                    baseIndex = vectors.size();
                }
                vectors.add(vec);
                playerRows.add(row);
            }
        }

        if (baseIndex == -1) {
            return PlayerSimilarityDTO.builder().season(season).similarPlayers(List.of()).build();
        }

        // Z-score 정규화
        int n = vectors.size();
        int dim = COMPARE_LABELS.length;
        double[] means = new double[dim];
        double[] stds = new double[dim];

        for (double[] vec : vectors) {
            for (int i = 0; i < dim; i++) means[i] += vec[i];
        }
        for (int i = 0; i < dim; i++) means[i] /= n;

        for (double[] vec : vectors) {
            for (int i = 0; i < dim; i++) stds[i] += Math.pow(vec[i] - means[i], 2);
        }
        for (int i = 0; i < dim; i++) stds[i] = Math.sqrt(stds[i] / n);

        // 정규화된 벡터
        double[][] normalized = new double[n][dim];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < dim; i++) {
                normalized[j][i] = stds[i] > 0 ? (vectors.get(j)[i] - means[i]) / stds[i] : 0;
            }
        }

        // 코사인 유사도 계산
        double[] baseVec = normalized[baseIndex];
        List<double[]> similarities = new ArrayList<>(); // [index, similarity]
        for (int j = 0; j < n; j++) {
            if (j == baseIndex) continue;
            double sim = cosineSimilarity(baseVec, normalized[j]);
            similarities.add(new double[]{j, sim});
        }
        similarities.sort((a, b) -> Double.compare(b[1], a[1]));

        // 결과 구성
        Object[] baseRow = playerRows.get(baseIndex);
        PlayerSimilarityDTO.PlayerInfo basePlayer = PlayerSimilarityDTO.PlayerInfo.builder()
                .playerId(toInt(baseRow[0]))
                .playerName((String) baseRow[1])
                .teamName((String) baseRow[2])
                .logoName((String) baseRow[3])
                .stats(buildStatsMap(baseRow))
                .build();

        List<PlayerSimilarityDTO.SimilarPlayer> similarPlayers = new ArrayList<>();
        int limit = Math.min(topN, similarities.size());
        for (int i = 0; i < limit; i++) {
            int idx = (int) similarities.get(i)[0];
            double sim = similarities.get(i)[1];
            Object[] row = playerRows.get(idx);
            similarPlayers.add(PlayerSimilarityDTO.SimilarPlayer.builder()
                    .playerId(toInt(row[0]))
                    .playerName((String) row[1])
                    .teamName((String) row[2])
                    .logoName((String) row[3])
                    .similarity(Math.round(sim * 1000.0) / 1000.0)
                    .stats(buildStatsMap(row))
                    .build());
        }

        return PlayerSimilarityDTO.builder()
                .season(season)
                .basePlayer(basePlayer)
                .similarPlayers(similarPlayers)
                .radarLabels(List.of(COMPARE_LABELS))
                .build();
    }

    /**
     * 두 선수 직접 비교
     */
    public PlayerSimilarityDTO compareTwoPlayers(int player1Id, int player2Id, int season) {
        List<Object[]> rows = batterStatsRepository.findAllBattersForAnalysis(season);

        Object[] row1 = null, row2 = null;
        for (Object[] row : rows) {
            int pid = toInt(row[0]);
            if (pid == player1Id) row1 = row;
            if (pid == player2Id) row2 = row;
        }

        if (row1 == null || row2 == null) {
            return PlayerSimilarityDTO.builder().season(season).similarPlayers(List.of()).build();
        }

        double[] vec1 = extractVector(row1);
        double[] vec2 = extractVector(row2);

        // 단순 코사인 유사도 (정규화 없이 원본 값)
        double sim = cosineSimilarity(vec1, vec2);

        PlayerSimilarityDTO.PlayerInfo basePlayer = PlayerSimilarityDTO.PlayerInfo.builder()
                .playerId(toInt(row1[0]))
                .playerName((String) row1[1])
                .teamName((String) row1[2])
                .logoName((String) row1[3])
                .stats(buildStatsMap(row1))
                .build();

        PlayerSimilarityDTO.SimilarPlayer compared = PlayerSimilarityDTO.SimilarPlayer.builder()
                .playerId(toInt(row2[0]))
                .playerName((String) row2[1])
                .teamName((String) row2[2])
                .logoName((String) row2[3])
                .similarity(Math.round(sim * 1000.0) / 1000.0)
                .stats(buildStatsMap(row2))
                .build();

        return PlayerSimilarityDTO.builder()
                .season(season)
                .basePlayer(basePlayer)
                .similarPlayers(List.of(compared))
                .radarLabels(List.of(COMPARE_LABELS))
                .build();
    }

    private double[] extractVector(Object[] row) {
        double[] vec = new double[COMPARE_INDICES.length];
        for (int i = 0; i < COMPARE_INDICES.length; i++) {
            Double val = toDouble(row[COMPARE_INDICES[i]]);
            vec[i] = val != null ? val : 0.0;
        }
        return vec;
    }

    private boolean hasValidData(double[] vec) {
        for (double v : vec) {
            if (v != 0.0) return true;
        }
        return false;
    }

    private Map<String, Double> buildStatsMap(Object[] row) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < COMPARE_LABELS.length; i++) {
            Double val = toDouble(row[COMPARE_INDICES[i]]);
            map.put(COMPARE_LABELS[i], val);
        }
        return map;
    }

    private double cosineSimilarity(double[] a, double[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom > 0 ? dot / denom : 0;
    }

    private Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return null; }
    }

    private int toInt(Object val) {
        if (val instanceof Number) return ((Number) val).intValue();
        return Integer.parseInt(val.toString());
    }
}
