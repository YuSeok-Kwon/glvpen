package com.kepg.glvpen.modules.analysis.service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.kepg.glvpen.modules.analysis.dto.FatigueAnalysisDTO;
import com.kepg.glvpen.modules.game.record.repository.PitcherRecordRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FatigueAnalysisService {

    private final PitcherRecordRepository pitcherRecordRepository;

    /**
     * 특정 투수의 피로도 분석
     */
    public FatigueAnalysisDTO analyzePitcherFatigue(int playerId, int season) {
        List<Object[]> allRows = pitcherRecordRepository.findAllPitcherAppearancesBySeason(season);

        // 해당 투수 등판 기록 필터
        List<Object[]> pitcherRows = allRows.stream()
                .filter(r -> toInt(r[0]) == playerId)
                .toList();

        if (pitcherRows.isEmpty()) return null;

        Object[] first = pitcherRows.get(0);
        String playerName = (String) first[1];
        String teamName = (String) first[2];
        String logoName = (String) first[3];

        // 등판 간 휴식일 계산 + 구간별 ERA 그룹핑
        List<AppearanceData> appearances = parseAppearances(pitcherRows);

        Map<String, List<AppearanceData>> restGroups = new LinkedHashMap<>();
        restGroups.put("0~2일", new ArrayList<>());
        restGroups.put("3~4일", new ArrayList<>());
        restGroups.put("5일+", new ArrayList<>());

        double totalER = 0, totalIP = 0;

        for (int i = 0; i < appearances.size(); i++) {
            AppearanceData app = appearances.get(i);
            totalER += app.earnedRuns;
            totalIP += app.realInnings;

            if (i == 0) {
                restGroups.get("5일+").add(app); // 시즌 첫 등판
                continue;
            }

            long restDays = ChronoUnit.DAYS.between(appearances.get(i - 1).date, app.date);
            if (restDays <= 2) {
                restGroups.get("0~2일").add(app);
            } else if (restDays <= 4) {
                restGroups.get("3~4일").add(app);
            } else {
                restGroups.get("5일+").add(app);
            }
        }

        double seasonEra = totalIP > 0 ? (totalER / totalIP) * 9.0 : 0;

        // 구간별 통계
        List<FatigueAnalysisDTO.RestGroupStats> groups = new ArrayList<>();
        for (Map.Entry<String, List<AppearanceData>> entry : restGroups.entrySet()) {
            List<AppearanceData> apps = entry.getValue();
            if (apps.isEmpty()) {
                groups.add(FatigueAnalysisDTO.RestGroupStats.builder()
                        .label(entry.getKey()).appearances(0).era(0).avgPitchCount(0).avgInnings(0).build());
                continue;
            }

            double groupER = 0, groupIP = 0, groupPC = 0;
            for (AppearanceData a : apps) {
                groupER += a.earnedRuns;
                groupIP += a.realInnings;
                groupPC += a.pitchCount;
            }

            double groupEra = groupIP > 0 ? (groupER / groupIP) * 9.0 : 0;
            groups.add(FatigueAnalysisDTO.RestGroupStats.builder()
                    .label(entry.getKey())
                    .appearances(apps.size())
                    .era(round(groupEra))
                    .avgPitchCount(round(groupPC / apps.size()))
                    .avgInnings(round(groupIP / apps.size()))
                    .build());
        }

        // 전반기/후반기 비교 (올스타 브레이크 기준: 7월 15일)
        LocalDate midPoint = LocalDate.of(season, 7, 15);
        double firstER = 0, firstIP = 0;
        int firstCount = 0;
        double secondER = 0, secondIP = 0;
        int secondCount = 0;

        for (AppearanceData app : appearances) {
            if (app.date.isBefore(midPoint)) {
                firstER += app.earnedRuns;
                firstIP += app.realInnings;
                firstCount++;
            } else {
                secondER += app.earnedRuns;
                secondIP += app.realInnings;
                secondCount++;
            }
        }

        FatigueAnalysisDTO.HalfSeasonComparison halfSeason = FatigueAnalysisDTO.HalfSeasonComparison.builder()
                .firstHalfEra(firstIP > 0 ? round((firstER / firstIP) * 9.0) : 0)
                .firstHalfAppearances(firstCount)
                .secondHalfEra(secondIP > 0 ? round((secondER / secondIP) * 9.0) : 0)
                .secondHalfAppearances(secondCount)
                .build();

        // 피로도 지수: 후반기 ERA / 전반기 ERA (1보다 크면 후반 피로 증가)
        double firstEra = firstIP > 0 ? (firstER / firstIP) * 9.0 : 0;
        double secondEra = secondIP > 0 ? (secondER / secondIP) * 9.0 : 0;
        double fatigueIndex = firstEra > 0 ? round(secondEra / firstEra) : 1.0;

        return FatigueAnalysisDTO.builder()
                .playerId(playerId)
                .playerName(playerName)
                .teamName(teamName)
                .logoName(logoName)
                .season(season)
                .totalAppearances(appearances.size())
                .seasonEra(round(seasonEra))
                .fatigueIndex(fatigueIndex)
                .restGroups(groups)
                .halfSeason(halfSeason)
                .build();
    }

    /**
     * 피로도 랭킹 (등판 수 20 이상 투수)
     */
    public List<FatigueAnalysisDTO.FatigueRanking> getFatigueRanking(int season, int limit) {
        List<Object[]> allRows = pitcherRecordRepository.findAllPitcherAppearancesBySeason(season);

        // 투수별 그룹핑
        Map<Integer, List<Object[]>> byPitcher = new LinkedHashMap<>();
        for (Object[] row : allRows) {
            int pid = toInt(row[0]);
            byPitcher.computeIfAbsent(pid, k -> new ArrayList<>()).add(row);
        }

        List<FatigueAnalysisDTO.FatigueRanking> rankings = new ArrayList<>();

        for (Map.Entry<Integer, List<Object[]>> entry : byPitcher.entrySet()) {
            List<Object[]> pitcherRows = entry.getValue();
            if (pitcherRows.size() < 20) continue; // 최소 등판 수 필터

            List<AppearanceData> appearances = parseAppearances(pitcherRows);
            Object[] first = pitcherRows.get(0);

            double totalER = 0, totalIP = 0;
            for (AppearanceData a : appearances) {
                totalER += a.earnedRuns;
                totalIP += a.realInnings;
            }
            double seasonEra = totalIP > 0 ? (totalER / totalIP) * 9.0 : 0;

            // 전/후반기 ERA로 피로도 계산
            LocalDate midPoint = LocalDate.of(season, 7, 15);
            double firstER = 0, firstIP = 0, secondER2 = 0, secondIP2 = 0;
            for (AppearanceData a : appearances) {
                if (a.date.isBefore(midPoint)) { firstER += a.earnedRuns; firstIP += a.realInnings; }
                else { secondER2 += a.earnedRuns; secondIP2 += a.realInnings; }
            }
            double firstEra = firstIP > 0 ? (firstER / firstIP) * 9.0 : 0;
            double secondEra = secondIP2 > 0 ? (secondER2 / secondIP2) * 9.0 : 0;
            double fatigueIndex = firstEra > 0 ? round(secondEra / firstEra) : 1.0;

            rankings.add(FatigueAnalysisDTO.FatigueRanking.builder()
                    .playerId(entry.getKey())
                    .playerName((String) first[1])
                    .teamName((String) first[2])
                    .logoName((String) first[3])
                    .totalAppearances(appearances.size())
                    .seasonEra(round(seasonEra))
                    .fatigueIndex(fatigueIndex)
                    .build());
        }

        // 피로도 지수 내림차순 (높을수록 후반 피로 심함)
        rankings.sort(Comparator.comparingDouble(FatigueAnalysisDTO.FatigueRanking::getFatigueIndex).reversed());
        for (int i = 0; i < rankings.size(); i++) {
            rankings.get(i).setRank(i + 1);
        }

        return limit > 0 && limit < rankings.size() ? rankings.subList(0, limit) : rankings;
    }

    private List<AppearanceData> parseAppearances(List<Object[]> rows) {
        List<AppearanceData> list = new ArrayList<>();
        for (Object[] row : rows) {
            AppearanceData app = new AppearanceData();
            app.innings = toDoubleVal(row[4]);
            app.realInnings = convertInnings(app.innings);
            app.earnedRuns = toIntVal(row[5]);
            app.pitchCount = toIntVal(row[6]);
            app.entryType = (String) row[7];
            app.date = toLocalDate(row[8]);
            list.add(app);
        }
        return list;
    }

    /**
     * innings 변환: 2.1 → 2 + 1/3 = 2.333
     */
    private double convertInnings(double ip) {
        double whole = Math.floor(ip);
        double fraction = ip - whole;
        return whole + (fraction * 10) / 3.0;
    }

    private LocalDate toLocalDate(Object val) {
        if (val instanceof Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        if (val instanceof java.sql.Date d) return d.toLocalDate();
        if (val instanceof LocalDate ld) return ld;
        return LocalDate.parse(val.toString().substring(0, 10));
    }

    private double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }

    private double toDoubleVal(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0; }
    }

    private int toIntVal(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return 0; }
    }

    private int toInt(Object val) {
        if (val instanceof Number) return ((Number) val).intValue();
        return Integer.parseInt(val.toString());
    }

    private static class AppearanceData {
        double innings;
        double realInnings;
        int earnedRuns;
        int pitchCount;
        String entryType;
        LocalDate date;
    }
}
