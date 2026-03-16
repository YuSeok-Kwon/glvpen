package com.kepg.glvpen.modules.analysis.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.kepg.glvpen.modules.analysis.dto.ClutchIndexDTO;
import com.kepg.glvpen.modules.player.stats.repository.BatterStatsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClutchIndexService {

    private final BatterStatsRepository batterStatsRepository;

    // findAllBattersForAnalysis 컬럼 인덱스
    // 0:playerId, 1:playerName, 2:teamName, 3:logoName, 4:teamId,
    // 5:war, 6:avg, 7:ops, 8:hr, 9:sb, 10:kRate, 11:bbRate, 12:iso,
    // 13:g, 14:pa, 15:risp, 16:phBa, 17:rbi, 18:wrcPlus, 19:obp, 20:slg
    // [참고] KBO 사이트 BasicOld 전환으로 RISP, PH-BA 데이터 없음 → RBI, HR, OPS 기반으로 대체
    private static final int IDX_HR = 8;
    private static final int IDX_RBI = 17;
    private static final int IDX_OPS = 7;

    /**
     * 클러치 지수 랭킹 조회
     * Z-score 기반 가중합: 0.5*RBI + 0.3*HR + 0.2*OPS
     * (KBO 사이트 변경으로 RISP/PH-BA 데이터 없음 → 대체 지표 사용)
     */
    public ClutchIndexDTO getClutchRanking(int season, int limit) {
        List<Object[]> rows = batterStatsRepository.findAllBattersForAnalysis(season);
        if (rows.isEmpty()) {
            return ClutchIndexDTO.builder().season(season).rankings(List.of()).build();
        }

        // 유효 데이터 추출
        List<double[]> rawValues = new ArrayList<>();
        List<Object[]> validRows = new ArrayList<>();

        for (Object[] row : rows) {
            Double rbi = toDouble(row[IDX_RBI]);
            Double hr = toDouble(row[IDX_HR]);
            Double ops = toDouble(row[IDX_OPS]);

            if (rbi == null && hr == null) continue;

            rawValues.add(new double[]{
                rbi != null ? rbi : 0.0,
                hr != null ? hr : 0.0,
                ops != null ? ops : 0.0
            });
            validRows.add(row);
        }

        if (validRows.isEmpty()) {
            return ClutchIndexDTO.builder().season(season).rankings(List.of()).build();
        }

        // Z-score 계산
        int n = rawValues.size();
        double[] means = new double[3];
        double[] stds = new double[3];

        for (double[] vals : rawValues) {
            for (int i = 0; i < 3; i++) means[i] += vals[i];
        }
        for (int i = 0; i < 3; i++) means[i] /= n;

        for (double[] vals : rawValues) {
            for (int i = 0; i < 3; i++) stds[i] += Math.pow(vals[i] - means[i], 2);
        }
        for (int i = 0; i < 3; i++) stds[i] = Math.sqrt(stds[i] / n);

        // 가중합 계산 (0.5*RBI + 0.3*HR + 0.2*OPS)
        List<ClutchIndexDTO.ClutchPlayer> players = new ArrayList<>();
        for (int idx = 0; idx < n; idx++) {
            double[] vals = rawValues.get(idx);
            double zRbi = stds[0] > 0 ? (vals[0] - means[0]) / stds[0] : 0;
            double zHr = stds[1] > 0 ? (vals[1] - means[1]) / stds[1] : 0;
            double zOps = stds[2] > 0 ? (vals[2] - means[2]) / stds[2] : 0;

            double clutchIndex = 0.5 * zRbi + 0.3 * zHr + 0.2 * zOps;

            Object[] row = validRows.get(idx);
            players.add(ClutchIndexDTO.ClutchPlayer.builder()
                    .playerId(toInt(row[0]))
                    .playerName((String) row[1])
                    .teamName((String) row[2])
                    .logoName((String) row[3])
                    .rbi(toDouble(row[IDX_RBI]))
                    .hr(toDouble(row[IDX_HR]))
                    .ops(toDouble(row[IDX_OPS]))
                    .clutchIndex(Math.round(clutchIndex * 1000.0) / 1000.0)
                    .build());
        }

        // 클러치 지수 내림차순 정렬
        players.sort(Comparator.comparingDouble(ClutchIndexDTO.ClutchPlayer::getClutchIndex).reversed());

        // 랭크 + 백분위 부여
        for (int i = 0; i < players.size(); i++) {
            players.get(i).setRank(i + 1);
            players.get(i).setPercentile(Math.round((1.0 - (double) i / players.size()) * 100 * 10.0) / 10.0);
        }

        List<ClutchIndexDTO.ClutchPlayer> result = limit > 0 && limit < players.size()
                ? players.subList(0, limit) : players;

        return ClutchIndexDTO.builder().season(season).rankings(result).build();
    }

    /**
     * 특정 선수의 클러치 지수
     */
    public ClutchIndexDTO.ClutchPlayer getPlayerClutch(int playerId, int season) {
        ClutchIndexDTO ranking = getClutchRanking(season, 0);
        return ranking.getRankings().stream()
                .filter(p -> p.getPlayerId() == playerId)
                .findFirst()
                .orElse(null);
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
