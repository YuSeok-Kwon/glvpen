package com.kepg.glvpen.modules.player.stats.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.glvpen.common.stats.SabermetricsCalculator;
import com.kepg.glvpen.modules.player.stats.domain.BatterStats;
import com.kepg.glvpen.modules.player.stats.domain.PitcherStats;
import com.kepg.glvpen.modules.player.stats.repository.BatterStatsRepository;
import com.kepg.glvpen.modules.player.stats.repository.PitcherStatsRepository;
import com.kepg.glvpen.modules.player.stats.statsDto.BatterStatsDTO;
import com.kepg.glvpen.modules.player.stats.statsDto.PitcherStatsDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 시즌별 전체 선수에 대해 세이버메트릭스 파생 지표를 일괄 산출하여 저장
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SabermetricsBatchService {

    private final BatterStatsRepository batterStatsRepository;
    private final PitcherStatsRepository pitcherStatsRepository;
    private final BatterStatsService batterStatsService;
    private final PitcherStatsService pitcherStatsService;

    /**
     * 시즌별 타자 세이버메트릭스 지표 일괄 계산
     * [기본 산출] TB, SLG, OBP, OPS (KBO BasicOld 전환으로 직접 크롤링 불가)
     * [세이버] BABIP, ISO, K%, BB%, wOBA, AB/HR, PA/K
     */
    @Transactional
    public void calculateBatterSabermetrics(int season) {
        log.info("=== 타자 세이버메트릭스 계산 시작: 시즌 {} ===", season);

        List<Integer> playerIds = batterStatsRepository.findDistinctPlayerIdsBySeason(season);
        int count = 0;

        for (Integer playerId : playerIds) {
            try {
                List<BatterStats> stats = batterStatsRepository.findByPlayerIdAndSeason(playerId, season);

                double h = getStatValue(stats, "H");
                double hr = getStatValue(stats, "HR");
                double ab = getStatValue(stats, "AB");
                double so = getStatValue(stats, "SO");
                double bb = getStatValue(stats, "BB");
                double hbp = getStatValue(stats, "HBP");
                double pa = getStatValue(stats, "PA");
                double sf = getStatValue(stats, "SF"); // BasicOld에서 미제공 시 0.0
                double avg = getStatValue(stats, "AVG");
                double doubles = getStatValue(stats, "2B");
                double triples = getStatValue(stats, "3B");

                String position = stats.stream()
                        .map(BatterStats::getPosition)
                        .filter(p -> p != null && !p.isBlank())
                        .findFirst().orElse("DH");

                // ===== 기본 산출 지표 (KBO BasicOld 전환 대응) =====
                // TB (Total Bases) — BasicOld에서 직접 제공하지 않으므로 계산
                double tb = SabermetricsCalculator.calcTB(h, doubles, triples, hr);
                saveBatterStat(playerId, season, position, "TB", round(tb, 0));

                // SLG (Slugging Percentage)
                double slg = SabermetricsCalculator.calcSLG(tb, ab);
                saveBatterStat(playerId, season, position, "SLG", round(slg, 3));

                // OBP (On-Base Percentage) — SF 미제공 시 근사값
                double obp = SabermetricsCalculator.calcOBP(h, bb, hbp, ab, sf);
                saveBatterStat(playerId, season, position, "OBP", round(obp, 3));

                // OPS (On-base Plus Slugging)
                double ops = SabermetricsCalculator.calcOPS(obp, slg);
                saveBatterStat(playerId, season, position, "OPS", round(ops, 3));

                // ===== 세이버메트릭스 =====
                // BABIP
                double babip = SabermetricsCalculator.calcBABIP(h, hr, ab, so, sf);
                saveBatterStat(playerId, season, position, "BABIP", round(babip, 3));

                // ISO (계산된 SLG 사용)
                double iso = SabermetricsCalculator.calcISO(slg, avg);
                saveBatterStat(playerId, season, position, "ISO", round(iso, 3));

                // K%
                double kRate = SabermetricsCalculator.calcKRate(so, pa);
                saveBatterStat(playerId, season, position, "K%", round(kRate * 100, 1));

                // BB%
                double bbRate = SabermetricsCalculator.calcBBRate(bb, pa);
                saveBatterStat(playerId, season, position, "BB%", round(bbRate * 100, 1));

                // wOBA (가중 출루율)
                double singles = h - doubles - triples - hr;
                double woba = SabermetricsCalculator.calcWOBA(bb, hbp, singles, doubles, triples, hr, ab, sf);
                saveBatterStat(playerId, season, position, "wOBA", round(woba, 3));

                // AB/HR (홈런 빈도 — HR > 0인 경우만)
                double abPerHr = SabermetricsCalculator.calcABperHR(ab, hr);
                if (abPerHr > 0) {
                    saveBatterStat(playerId, season, position, "AB/HR", round(abPerHr, 1));
                }

                // PA/K (컨택 능력 — SO > 0인 경우만)
                double paPerK = SabermetricsCalculator.calcPAperK(pa, so);
                if (paPerK > 0) {
                    saveBatterStat(playerId, season, position, "PA/K", round(paPerK, 1));
                }

                count++;
            } catch (Exception e) {
                log.error("타자 {} 세이버메트릭스 계산 실패: {}", playerId, e.getMessage());
            }
        }

        log.info("=== 타자 세이버메트릭스 계산 완료: {}명 처리 ===", count);
    }

    /**
     * 시즌별 투수 세이버메트릭스 지표 일괄 계산 (K/9, BB/9, HR/9)
     */
    @Transactional
    public void calculatePitcherSabermetrics(int season) {
        log.info("=== 투수 세이버메트릭스 계산 시작: 시즌 {} ===", season);

        List<Integer> playerIds = pitcherStatsRepository.findDistinctPlayerIdsBySeason(season);
        int count = 0;

        for (Integer playerId : playerIds) {
            try {
                List<PitcherStats> stats = pitcherStatsRepository.findByPlayerIdAndSeason(playerId, season);

                double so = getPitcherStatValue(stats, "SO");
                double bb = getPitcherStatValue(stats, "BB");
                double hr = getPitcherStatValue(stats, "HR");
                double ip = getPitcherStatValue(stats, "IP");
                double h = getPitcherStatValue(stats, "H");
                double hbp = getPitcherStatValue(stats, "HBP");
                double r = getPitcherStatValue(stats, "R");

                String position = stats.stream()
                        .map(PitcherStats::getPosition)
                        .filter(p -> p != null && !p.isBlank())
                        .findFirst().orElse("P");

                // K/9
                double k9 = SabermetricsCalculator.calcK9(so, ip);
                savePitcherStat(playerId, season, position, "K/9", round(k9, 2));

                // BB/9
                double bb9 = SabermetricsCalculator.calcBB9(bb, ip);
                savePitcherStat(playerId, season, position, "BB/9", round(bb9, 2));

                // HR/9
                double hr9 = SabermetricsCalculator.calcHR9(hr, ip);
                savePitcherStat(playerId, season, position, "HR/9", round(hr9, 2));

                // LOB% (잔루율 — 높을수록 위기 관리 우수)
                double lobPct = SabermetricsCalculator.calcLOBPercent(h, bb, hbp, r, hr);
                if (lobPct > 0) {
                    savePitcherStat(playerId, season, position, "LOB%", round(lobPct * 100, 1));
                }

                count++;
            } catch (Exception e) {
                log.error("투수 {} 세이버메트릭스 계산 실패: {}", playerId, e.getMessage());
            }
        }

        log.info("=== 투수 세이버메트릭스 계산 완료: {}명 처리 ===", count);
    }

    /**
     * 전 시즌 배치 실행
     */
    public void calculateAllSabermetrics(int startYear, int endYear) {
        for (int year = startYear; year <= endYear; year++) {
            calculateBatterSabermetrics(year);
            calculatePitcherSabermetrics(year);
        }
    }

    // ====== 헬퍼 ======

    private double getStatValue(List<BatterStats> stats, String category) {
        return stats.stream()
                .filter(s -> category.equals(s.getCategory()))
                .mapToDouble(s -> s.getValue() != null ? s.getValue() : 0.0)
                .findFirst().orElse(0.0);
    }

    private double getPitcherStatValue(List<PitcherStats> stats, String category) {
        return stats.stream()
                .filter(s -> category.equals(s.getCategory()))
                .mapToDouble(s -> s.getValue() != null ? s.getValue() : 0.0)
                .findFirst().orElse(0.0);
    }

    private void saveBatterStat(int playerId, int season, String position, String category, double value) {
        BatterStatsDTO dto = BatterStatsDTO.builder()
                .playerId(playerId)
                .season(season)
                .position(position)
                .category(category)
                .value(value)
                .ranking(null)
                .build();
        batterStatsService.saveBatterStats(dto);
    }

    private void savePitcherStat(int playerId, int season, String position, String category, double value) {
        PitcherStatsDTO dto = PitcherStatsDTO.builder()
                .playerId(playerId)
                .season(season)
                .position(position)
                .category(category)
                .value(value)
                .ranking(null)
                .build();
        pitcherStatsService.savePitcherStats(dto);
    }

    private double round(double value, int places) {
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }
}
