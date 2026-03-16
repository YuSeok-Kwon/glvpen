package com.kepg.glvpen.modules.analysis.service;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.kepg.glvpen.modules.crowd.domain.CrowdStats;
import com.kepg.glvpen.modules.crowd.repository.CrowdStatsRepository;
import com.kepg.glvpen.modules.futures.stats.domain.FuturesBatterStats;
import com.kepg.glvpen.modules.futures.stats.domain.FuturesPitcherStats;
import com.kepg.glvpen.modules.futures.stats.repository.FuturesBatterStatsRepository;
import com.kepg.glvpen.modules.futures.stats.repository.FuturesPitcherStatsRepository;
import com.kepg.glvpen.modules.game.scoreBoard.domain.ScoreBoard;
import com.kepg.glvpen.modules.game.scoreBoard.repository.ScoreBoardRepository;
import com.kepg.glvpen.modules.game.schedule.domain.Schedule;
import com.kepg.glvpen.modules.game.schedule.repository.ScheduleRepository;
import com.kepg.glvpen.modules.player.domain.Player;
import com.kepg.glvpen.modules.player.repository.PlayerRepository;
import com.kepg.glvpen.modules.player.stats.repository.BatterStatsRepository;
import com.kepg.glvpen.modules.player.stats.repository.DefenseStatsRepository;
import com.kepg.glvpen.modules.player.stats.repository.PitcherStatsRepository;
import com.kepg.glvpen.modules.player.stats.repository.RunnerStatsRepository;
import com.kepg.glvpen.modules.team.repository.TeamRepository;
import com.kepg.glvpen.modules.team.teamHeadToHead.domain.TeamHeadToHead;
import com.kepg.glvpen.modules.team.teamHeadToHead.repository.TeamHeadToHeadRepository;
import com.kepg.glvpen.modules.team.teamRanking.repository.TeamRankingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 15개 주제별 분석 데이터 사전 계산 서비스.
 * 모든 메서드는 String(프롬프트 데이터 블록)을 반환하며,
 * Gemini는 이 데이터를 기반으로 멘트만 작성한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ColumnDataCalculator {

    private final BatterStatsRepository batterStatsRepository;
    private final PitcherStatsRepository pitcherStatsRepository;
    private final TeamRankingRepository teamRankingRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final TeamHeadToHeadRepository headToHeadRepository;
    private final CrowdStatsRepository crowdStatsRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScoreBoardRepository scoreBoardRepository;
    private final DefenseStatsRepository defenseStatsRepository;
    private final RunnerStatsRepository runnerStatsRepository;
    private final FuturesBatterStatsRepository futuresBatterStatsRepository;
    private final FuturesPitcherStatsRepository futuresPitcherStatsRepository;

    // ==================== findAllBatters 인덱스 ====================
    // 0:position, 1:playerName, 2:teamName, 3:logoName, 4:war, 5:avg, 6:ops,
    // 7:hr, 8:sb, 9:wrcPlus, 10:g, 11:pa, 12:h, 13:rbi, 14:bb, 15:so,
    // 16:2B, 17:3B, 18:obp, 19:slg, 20:teamId, 21:babip, 22:iso, 23:kRate, 24:bbRate,
    // 25:ab, 26:r, 27:tb, 28:sac, 29:sf, 30:ibb, 31:hbp, 32:gdp, 33:mh, 34:risp, 35:phBa

    // ==================== findAllPitchers 인덱스 ====================
    // 0:playerName, 1:teamName, 2:logoName, 3:era, 4:whip, 5:wins, 6:losses,
    // 7:saves, 8:holds, 9:strikeouts, 10:walks, 11:hitsAllowed, 12:homeRunsAllowed,
    // 13:inningsPitched, 14:war, 15:teamId, 16:fip, 17:xfip, 18:k9, 19:bb9,
    // 20:g, 21:wpct, 22:hbp, 23:r, 24:er, 25:cg, 26:sho, 27:qs, 28:bsv,
    // 29:tbf, 30:np, 31:avg, 32:2B, 33:3B, 34:sac, 35:sf, 36:ibb, 37:wp, 38:bk

    // ==================== 주제 0: 세이버메트릭스 트렌드 ====================
    public String calcSabermetricsTrend(int season) {
        StringBuilder sb = new StringBuilder();
        try {
            List<Object[]> batters = batterStatsRepository.findAllBatters(season);
            List<Object[]> pitchers = pitcherStatsRepository.findAllPitchers(season);

            if (batters.isEmpty() && pitchers.isEmpty()) return "[데이터 없음]";

            // 리그 평균 타자 지표
            double avgWrcPlus = avg(batters, 9);
            double avgBabip = avg(batters, 21);
            double avgIso = avg(batters, 22);
            double avgKRate = avg(batters, 23);
            double avgBbRate = avg(batters, 24);
            double avgOps = avg(batters, 6);

            sb.append("[타자 리그 평균]\n");
            sb.append("wRC+: ").append(fmt(avgWrcPlus)).append(", ");
            sb.append("BABIP: ").append(fmt3(avgBabip)).append(", ");
            sb.append("ISO: ").append(fmt3(avgIso)).append(", ");
            sb.append("K%: ").append(fmt(avgKRate)).append("%, ");
            sb.append("BB%: ").append(fmt(avgBbRate)).append("%, ");
            sb.append("OPS: ").append(fmt3(avgOps)).append("\n\n");

            // 리그 평균 투수 지표
            double avgFip = avg(pitchers, 16);
            double avgXfip = avg(pitchers, 17);
            double avgK9 = avg(pitchers, 18);
            double avgBb9 = avg(pitchers, 19);
            double avgEra = avg(pitchers, 3);

            sb.append("[투수 리그 평균]\n");
            sb.append("FIP: ").append(fmt(avgFip)).append(", ");
            sb.append("xFIP: ").append(fmt(avgXfip)).append(", ");
            sb.append("K/9: ").append(fmt(avgK9)).append(", ");
            sb.append("BB/9: ").append(fmt(avgBb9)).append(", ");
            sb.append("ERA: ").append(fmt(avgEra)).append("\n\n");

            // WAR 상위 5명 타자
            sb.append("[타자 WAR 상위 5명]\n");
            batters.stream()
                    .sorted((a, b) -> Double.compare(dbl(b[4]), dbl(a[4])))
                    .limit(5)
                    .forEach(b -> sb.append("- ").append(b[1]).append(" (").append(b[2])
                            .append(") WAR: ").append(b[4])
                            .append(", wRC+: ").append(b[9])
                            .append(", OPS: ").append(b[6]).append("\n"));

            // WAR 상위 5명 투수
            sb.append("\n[투수 WAR 상위 5명]\n");
            pitchers.stream()
                    .sorted((a, b) -> Double.compare(dbl(b[14]), dbl(a[14])))
                    .limit(5)
                    .forEach(p -> sb.append("- ").append(p[0]).append(" (").append(p[1])
                            .append(") WAR: ").append(p[14])
                            .append(", FIP: ").append(p[16])
                            .append(", ERA: ").append(p[3]).append("\n"));

        } catch (Exception e) {
            log.warn("calcSabermetricsTrend 실패: {}", e.getMessage());
            return "[계산 오류: " + e.getMessage() + "]";
        }
        return sb.toString();
    }

    // ==================== 주제 1: WAR 스포트라이트 ====================
    public String calcWarSpotlight(int season) {
        StringBuilder sb = new StringBuilder();
        try {
            List<Object[]> batters = batterStatsRepository.findAllBatters(season);
            List<Object[]> pitchers = pitcherStatsRepository.findAllPitchers(season);
            List<Object[]> prevBatters = safeLoadBatters(season - 1);
            List<Object[]> prevPitchers = safeLoadPitchers(season - 1);

            sb.append("[타자 WAR 상위 10명 - ").append(season).append("시즌]\n");
            sb.append(String.format("%-4s %-8s %-6s %-4s %-6s %-6s %-7s %-8s\n",
                    "순위", "선수명", "팀", "포지션", "WAR", "OPS", "wRC+", "전년WAR"));

            Map<String, Double> prevBatterWar = buildNameWarMap(prevBatters, 1, 4);
            List<Object[]> topBatters = batters.stream()
                    .sorted((a, b) -> Double.compare(dbl(b[4]), dbl(a[4])))
                    .limit(10).toList();
            for (int i = 0; i < topBatters.size(); i++) {
                Object[] b = topBatters.get(i);
                String name = str(b[1]);
                double prevWar = prevBatterWar.getOrDefault(name, 0.0);
                sb.append(String.format("%-4d %-8s %-6s %-4s %-6s %-6s %-7s %-8s\n",
                        i + 1, name, b[2], b[0], b[4], b[6], b[9],
                        prevWar == 0.0 ? "-" : fmt(prevWar)));
            }

            sb.append("\n[투수 WAR 상위 10명 - ").append(season).append("시즌]\n");
            sb.append(String.format("%-4s %-8s %-6s %-6s %-6s %-6s %-8s\n",
                    "순위", "선수명", "팀", "WAR", "ERA", "FIP", "전년WAR"));

            Map<String, Double> prevPitcherWar = buildNameWarMap(prevPitchers, 0, 14);
            List<Object[]> topPitchers = pitchers.stream()
                    .sorted((a, b) -> Double.compare(dbl(b[14]), dbl(a[14])))
                    .limit(10).toList();
            for (int i = 0; i < topPitchers.size(); i++) {
                Object[] p = topPitchers.get(i);
                String name = str(p[0]);
                double prevWar = prevPitcherWar.getOrDefault(name, 0.0);
                sb.append(String.format("%-4d %-8s %-6s %-6s %-6s %-6s %-8s\n",
                        i + 1, name, p[1], p[14], p[3], p[16],
                        prevWar == 0.0 ? "-" : fmt(prevWar)));
            }
        } catch (Exception e) {
            log.warn("calcWarSpotlight 실패: {}", e.getMessage());
            return "[계산 오류: " + e.getMessage() + "]";
        }
        return sb.toString();
    }

    // ==================== 주제 2: 브레이크아웃 후보 ====================
    public String calcBreakoutCandidates(int season) {
        StringBuilder sb = new StringBuilder();
        try {
            List<Object[]> batters = batterStatsRepository.findAllBatters(season);
            List<Object[]> prevBatters = safeLoadBatters(season - 1);

            if (prevBatters.isEmpty()) return "[전년도 데이터 없음]";

            Map<String, double[]> prevMap = new HashMap<>();
            for (Object[] b : prevBatters) {
                prevMap.put(str(b[1]), new double[]{dbl(b[4]), dbl(b[9]), dbl(b[22])});
            }

            // WAR 상승폭 기준 상위 5명
            List<Object[]> candidates = batters.stream()
                    .filter(b -> prevMap.containsKey(str(b[1])))
                    .sorted((a, b) -> {
                        double diffA = dbl(a[4]) - prevMap.get(str(a[1]))[0];
                        double diffB = dbl(b[4]) - prevMap.get(str(b[1]))[0];
                        return Double.compare(diffB, diffA);
                    })
                    .limit(5).toList();

            sb.append("[브레이크아웃 후보 - WAR 상승폭 상위 5명]\n");
            sb.append(String.format("%-8s %-6s %-18s %-20s %-18s\n",
                    "선수명", "팀", "WAR(전년→올해)", "wRC+(전년→올해)", "ISO(전년→올해)"));

            for (Object[] b : candidates) {
                String name = str(b[1]);
                double[] prev = prevMap.get(name);
                sb.append(String.format("%-8s %-6s %-18s %-20s %-18s\n",
                        name, b[2],
                        fmt(prev[0]) + "→" + b[4] + " (+" + fmt(dbl(b[4]) - prev[0]) + ")",
                        fmt(prev[1]) + "→" + b[9] + " (+" + fmt(dbl(b[9]) - prev[1]) + ")",
                        fmt3(prev[2]) + "→" + b[22] + " (+" + fmt3(dbl(b[22]) - prev[2]) + ")"));
            }
        } catch (Exception e) {
            log.warn("calcBreakoutCandidates 실패: {}", e.getMessage());
            return "[계산 오류: " + e.getMessage() + "]";
        }
        return sb.toString();
    }

    // ==================== 주제 3: 행운 보정 분석 ====================
    public String calcLuckAdjusted(int season) {
        StringBuilder sb = new StringBuilder();
        try {
            List<Object[]> batters = batterStatsRepository.findAllBatters(season);
            List<Object[]> pitchers = pitcherStatsRepository.findAllPitchers(season);

            double leagueAvgBabip = avg(batters, 21);

            sb.append("[리그 평균 BABIP: ").append(fmt3(leagueAvgBabip)).append("]\n\n");

            // BABIP 행운 수혜 상위 5명 (BABIP이 리그 평균보다 높은 타자)
            sb.append("[행운 수혜 타자 (BABIP 리그평균 대비 상위 편차 5명)]\n");
            batters.stream()
                    .filter(b -> dbl(b[11]) >= 100) // PA 100 이상
                    .sorted((a, b) -> Double.compare(dbl(b[21]) - leagueAvgBabip, dbl(a[21]) - leagueAvgBabip))
                    .limit(5)
                    .forEach(b -> sb.append("- ").append(b[1]).append(" (").append(b[2])
                            .append(") BABIP: ").append(b[21])
                            .append(", 편차: +").append(fmt3(dbl(b[21]) - leagueAvgBabip))
                            .append(", AVG: ").append(b[5]).append("\n"));

            // BABIP 불운 하위 5명
            sb.append("\n[불운 타자 (BABIP 리그평균 대비 하위 편차 5명)]\n");
            batters.stream()
                    .filter(b -> dbl(b[11]) >= 100)
                    .sorted((a, b) -> Double.compare(dbl(a[21]) - leagueAvgBabip, dbl(b[21]) - leagueAvgBabip))
                    .limit(5)
                    .forEach(b -> sb.append("- ").append(b[1]).append(" (").append(b[2])
                            .append(") BABIP: ").append(b[21])
                            .append(", 편차: ").append(fmt3(dbl(b[21]) - leagueAvgBabip))
                            .append(", AVG: ").append(b[5]).append("\n"));

            // FIP-ERA 갭 (과대평가 - ERA가 FIP보다 낮은)
            sb.append("\n[과대평가 투수 (ERA < FIP, 갭 큰 순 5명)]\n");
            pitchers.stream()
                    .filter(p -> dbl(p[13]) >= 30) // IP 30 이상
                    .sorted((a, b) -> Double.compare(
                            dbl(b[16]) - dbl(b[3]),
                            dbl(a[16]) - dbl(a[3])))
                    .limit(5)
                    .forEach(p -> sb.append("- ").append(p[0]).append(" (").append(p[1])
                            .append(") ERA: ").append(p[3])
                            .append(", FIP: ").append(p[16])
                            .append(", 갭: +").append(fmt(dbl(p[16]) - dbl(p[3]))).append("\n"));

            // 과소평가 (ERA가 FIP보다 높은)
            sb.append("\n[과소평가 투수 (ERA > FIP, 갭 큰 순 5명)]\n");
            pitchers.stream()
                    .filter(p -> dbl(p[13]) >= 30)
                    .sorted((a, b) -> Double.compare(
                            dbl(a[16]) - dbl(a[3]),
                            dbl(b[16]) - dbl(b[3])))
                    .limit(5)
                    .forEach(p -> sb.append("- ").append(p[0]).append(" (").append(p[1])
                            .append(") ERA: ").append(p[3])
                            .append(", FIP: ").append(p[16])
                            .append(", 갭: ").append(fmt(dbl(p[16]) - dbl(p[3]))).append("\n"));

        } catch (Exception e) {
            log.warn("calcLuckAdjusted 실패: {}", e.getMessage());
            return "[계산 오류: " + e.getMessage() + "]";
        }
        return sb.toString();
    }

    // ==================== 주제 4: 상대전적 패턴 ====================
    public String calcHeadToHead(int season) {
        StringBuilder sb = new StringBuilder();
        try {
            List<TeamHeadToHead> records = headToHeadRepository.findBySeasonOrderByTeamIdAscOpponentTeamIdAsc(season);
            if (records.isEmpty()) return "[상대전적 데이터 없음]";

            // 팀명 캐시
            Map<Integer, String> teamNames = new HashMap<>();
            records.forEach(r -> {
                teamNames.computeIfAbsent(r.getTeamId(), id -> {
                    String name = teamRepository.findTeamNameById(id);
                    return name != null ? name : "팀" + id;
                });
                teamNames.computeIfAbsent(r.getOpponentTeamId(), id -> {
                    String name = teamRepository.findTeamNameById(id);
                    return name != null ? name : "팀" + id;
                });
            });

            // 승률 격차 큰 매칭 5개 (최소 5경기 이상)
            sb.append("[가장 편파적 상대전적 상위 5개 매칭]\n");
            records.stream()
                    .filter(r -> (r.getWins() + r.getLosses()) >= 5)
                    .sorted((a, b) -> {
                        double rateA = (double) a.getWins() / (a.getWins() + a.getLosses());
                        double rateB = (double) b.getWins() / (b.getWins() + b.getLosses());
                        return Double.compare(rateB, rateA);
                    })
                    .limit(5)
                    .forEach(r -> {
                        int total = r.getWins() + r.getLosses();
                        double winRate = (double) r.getWins() / total;
                        sb.append("- ").append(teamNames.get(r.getTeamId()))
                                .append(" vs ").append(teamNames.get(r.getOpponentTeamId()))
                                .append(": ").append(r.getWins()).append("승 ")
                                .append(r.getLosses()).append("패 ")
                                .append(r.getDraws()).append("무")
                                .append(" (승률 ").append(fmt3(winRate)).append(")\n");
                    });

            // 전체 상대전적 요약
            sb.append("\n[전체 상대전적 매트릭스 요약]\n");
            sb.append("총 ").append(records.size()).append("건의 상대전적 데이터\n");

        } catch (Exception e) {
            log.warn("calcHeadToHead 실패: {}", e.getMessage());
            return "[계산 오류: " + e.getMessage() + "]";
        }
        return sb.toString();
    }

    // ==================== 주제 5: 이닝별 득점 패턴 ====================
    public String calcInningScoring(int season) {
        StringBuilder sb = new StringBuilder();
        try {
            List<Schedule> games = scheduleRepository.findAllFinishedBySeason(season);
            List<ScoreBoard> scoreBoards = scoreBoardRepository.findAllBySeasonFinished(season);

            if (games.isEmpty() || scoreBoards.isEmpty()) return "[이닝 스코어 데이터 없음]";

            // scheduleId → Schedule 매핑
            Map<Integer, Schedule> scheduleMap = new HashMap<>();
            games.forEach(g -> scheduleMap.put(g.getId(), g));

            // 팀별 이닝별 득점 합산
            Map<Integer, double[]> teamInningTotals = new HashMap<>();
            Map<Integer, int[]> teamInningCounts = new HashMap<>();

            for (ScoreBoard board : scoreBoards) {
                Schedule sch = scheduleMap.get(board.getScheduleId());
                if (sch == null) continue;

                parseAndAccumInnings(teamInningTotals, teamInningCounts,
                        sch.getHomeTeamId(), board.getHomeInningScores());
                parseAndAccumInnings(teamInningTotals, teamInningCounts,
                        sch.getAwayTeamId(), board.getAwayInningScores());
            }

            sb.append("[팀별 이닝 구간 평균 득점 - ").append(season).append("시즌]\n");
            sb.append(String.format("%-8s %-12s %-12s %-12s %-8s\n",
                    "팀", "초반(1-3회)", "중반(4-6회)", "후반(7-9회)", "유형"));

            teamInningTotals.forEach((teamId, totals) -> {
                int[] counts = teamInningCounts.get(teamId);
                String teamName = teamRepository.findTeamNameById(teamId);
                if (teamName == null) teamName = "팀" + teamId;

                double early = avgRange(totals, counts, 0, 3);
                double mid = avgRange(totals, counts, 3, 6);
                double late = avgRange(totals, counts, 6, 9);
                String type = classifyTeamType(early, mid, late);

                sb.append(String.format("%-8s %-12s %-12s %-12s %-8s\n",
                        teamName, fmt(early) + "점", fmt(mid) + "점", fmt(late) + "점", type));
            });

        } catch (Exception e) {
            log.warn("calcInningScoring 실패: {}", e.getMessage());
            return "[계산 오류: " + e.getMessage() + "]";
        }
        return sb.toString();
    }

    // ==================== 주제 6: 홈/원정 & 파크팩터 ====================
    public String calcHomeAwayAdvantage(int season) {
        StringBuilder sb = new StringBuilder();
        try {
            List<Schedule> games = scheduleRepository.findAllFinishedBySeason(season);
            if (games.isEmpty()) return "[경기 데이터 없음]";

            // 팀별 홈/원정 승패
            Map<Integer, int[]> teamHomeRecord = new HashMap<>(); // [wins, losses]
            Map<Integer, int[]> teamAwayRecord = new HashMap<>();
            // 구장별 총 득점
            Map<String, double[]> stadiumScores = new HashMap<>(); // [totalRuns, gameCount]

            for (Schedule g : games) {
                int homeScore = g.getHomeTeamScore() != null ? g.getHomeTeamScore() : 0;
                int awayScore = g.getAwayTeamScore() != null ? g.getAwayTeamScore() : 0;
                int totalRuns = homeScore + awayScore;

                // 홈팀 기록
                int[] homeRec = teamHomeRecord.computeIfAbsent(g.getHomeTeamId(), k -> new int[2]);
                if (homeScore > awayScore) homeRec[0]++;
                else if (homeScore < awayScore) homeRec[1]++;

                // 원정팀 기록
                int[] awayRec = teamAwayRecord.computeIfAbsent(g.getAwayTeamId(), k -> new int[2]);
                if (awayScore > homeScore) awayRec[0]++;
                else if (awayScore < homeScore) awayRec[1]++;

                // 구장별 득점
                String stadium = g.getStadium() != null ? g.getStadium() : "미상";
                double[] stadRec = stadiumScores.computeIfAbsent(stadium, k -> new double[2]);
                stadRec[0] += totalRuns;
                stadRec[1]++;
            }

            double leagueAvgRuns = stadiumScores.values().stream()
                    .mapToDouble(v -> v[0]).sum() / Math.max(stadiumScores.values().stream()
                    .mapToDouble(v -> v[1]).sum(), 1);

            sb.append("[팀별 홈/원정 승률 - ").append(season).append("시즌]\n");
            sb.append(String.format("%-8s %-14s %-14s\n", "팀", "홈 승률", "원정 승률"));
            teamHomeRecord.forEach((teamId, home) -> {
                int[] away = teamAwayRecord.getOrDefault(teamId, new int[]{0, 0});
                String name = teamRepository.findTeamNameById(teamId);
                if (name == null) name = "팀" + teamId;
                double homeRate = home[0] + home[1] > 0 ? (double) home[0] / (home[0] + home[1]) : 0;
                double awayRate = away[0] + away[1] > 0 ? (double) away[0] / (away[0] + away[1]) : 0;
                sb.append(String.format("%-8s %-14s %-14s\n",
                        name, fmt3(homeRate), fmt3(awayRate)));
            });

            sb.append("\n[구장별 파크팩터 근사 (리그 평균 경기당 득점: ").append(fmt(leagueAvgRuns)).append("점)]\n");
            stadiumScores.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue()[0] / b.getValue()[1],
                            a.getValue()[0] / a.getValue()[1]))
                    .forEach(e -> {
                        double avgRuns = e.getValue()[0] / e.getValue()[1];
                        double parkFactor = avgRuns / leagueAvgRuns;
                        String label = parkFactor >= 1.03 ? "타자 친화" : parkFactor <= 0.97 ? "투수 친화" : "중립";
                        sb.append("- ").append(e.getKey())
                                .append(": 평균 ").append(fmt(avgRuns)).append("점")
                                .append(", 파크팩터 ").append(fmt(parkFactor))
                                .append(" (").append(label).append(")").append("\n");
                    });

        } catch (Exception e) {
            log.warn("calcHomeAwayAdvantage 실패: {}", e.getMessage());
            return "[계산 오류: " + e.getMessage() + "]";
        }
        return sb.toString();
    }

    // ==================== 주제 7: 관중 동원 트렌드 ====================
    public String calcCrowdTrend(int season) {
        StringBuilder sb = new StringBuilder();
        try {
            List<CrowdStats> crowdList = crowdStatsRepository.findBySeasonOrderByGameDateAsc(season);
            if (crowdList.isEmpty()) return "[관중 데이터 없음]";

            // 팀별 홈 평균 관중
            Map<Integer, List<Integer>> teamCrowds = new HashMap<>();
            // 요일별 평균 관중
            Map<String, List<Integer>> dayOfWeekCrowds = new HashMap<>();

            for (CrowdStats cs : crowdList) {
                teamCrowds.computeIfAbsent(cs.getHomeTeamId(), k -> new ArrayList<>()).add(cs.getCrowd());
                String dow = cs.getDayOfWeek() != null ? cs.getDayOfWeek() : "미상";
                dayOfWeekCrowds.computeIfAbsent(dow, k -> new ArrayList<>()).add(cs.getCrowd());
            }

            sb.append("[팀별 홈 경기 평균 관중 - ").append(season).append("시즌]\n");
            teamCrowds.entrySet().stream()
                    .sorted((a, b) -> Double.compare(
                            b.getValue().stream().mapToInt(i -> i).average().orElse(0),
                            a.getValue().stream().mapToInt(i -> i).average().orElse(0)))
                    .forEach(e -> {
                        String name = teamRepository.findTeamNameById(e.getKey());
                        if (name == null) name = "팀" + e.getKey();
                        double avgCrowd = e.getValue().stream().mapToInt(i -> i).average().orElse(0);
                        sb.append("- ").append(name)
                                .append(": 평균 ").append(String.format("%,.0f", avgCrowd)).append("명")
                                .append(" (").append(e.getValue().size()).append("경기)\n");
                    });

            sb.append("\n[요일별 평균 관중]\n");
            dayOfWeekCrowds.entrySet().stream()
                    .sorted((a, b) -> Double.compare(
                            b.getValue().stream().mapToInt(i -> i).average().orElse(0),
                            a.getValue().stream().mapToInt(i -> i).average().orElse(0)))
                    .forEach(e -> {
                        double avgCrowd = e.getValue().stream().mapToInt(i -> i).average().orElse(0);
                        sb.append("- ").append(e.getKey())
                                .append(": 평균 ").append(String.format("%,.0f", avgCrowd)).append("명\n");
                    });

            // 순위와 관중 상관관계
            var rankings = teamRankingRepository.findBySeasonOrderByRankingAsc(season);
            if (!rankings.isEmpty()) {
                sb.append("\n[순위 vs 평균 관중]\n");
                rankings.forEach(r -> {
                    String name = teamRepository.findTeamNameById(r.getTeamId());
                    if (name == null) name = "팀" + r.getTeamId();
                    double avgCrowd = teamCrowds.getOrDefault(r.getTeamId(), List.of()).stream()
                            .mapToInt(i -> i).average().orElse(0);
                    sb.append("- ").append(r.getRanking()).append("위 ").append(name)
                            .append(": 평균 ").append(String.format("%,.0f", avgCrowd)).append("명\n");
                });
            }

        } catch (Exception e) {
            log.warn("calcCrowdTrend 실패: {}", e.getMessage());
            return "[계산 오류: " + e.getMessage() + "]";
        }
        return sb.toString();
    }

    // ==================== 주제 8: 퓨처스 유망주 ====================
    public String calcFuturesProspects(int season) {
        StringBuilder sb = new StringBuilder();
        try {
            List<FuturesBatterStats> fBatters = futuresBatterStatsRepository.findBySeason(season);
            List<FuturesPitcherStats> fPitchers = futuresPitcherStatsRepository.findBySeason(season);

            if (fBatters.isEmpty() && fPitchers.isEmpty()) return "[퓨처스 데이터 없음]";

            // 타자: playerId별 스탯 그룹핑
            Map<Integer, Map<String, Double>> batterStatsMap = new HashMap<>();
            for (FuturesBatterStats fs : fBatters) {
                batterStatsMap.computeIfAbsent(fs.getPlayerId(), k -> new HashMap<>())
                        .put(fs.getCategory(), fs.getValue() != null ? fs.getValue() : 0.0);
            }

            // 투수: playerId별 스탯 그룹핑
            Map<Integer, Map<String, Double>> pitcherStatsMap = new HashMap<>();
            for (FuturesPitcherStats fs : fPitchers) {
                pitcherStatsMap.computeIfAbsent(fs.getPlayerId(), k -> new HashMap<>())
                        .put(fs.getCategory(), fs.getValue() != null ? fs.getValue() : 0.0);
            }

            // Player 정보 로딩
            Map<Integer, Player> playerMap = new HashMap<>();
            playerRepository.findAll().forEach(p -> playerMap.put(p.getId(), p));
            LocalDate today = LocalDate.now();

            // 유망 타자 상위 5명 (AVG 기준, PA 50 이상)
            sb.append("[퓨처스 유망 타자 상위 5명 - ").append(season).append("시즌]\n");
            batterStatsMap.entrySet().stream()
                    .filter(e -> e.getValue().getOrDefault("PA", 0.0) >= 50)
                    .sorted((a, b) -> Double.compare(
                            b.getValue().getOrDefault("AVG", 0.0),
                            a.getValue().getOrDefault("AVG", 0.0)))
                    .limit(5)
                    .forEach(e -> {
                        Player p = playerMap.get(e.getKey());
                        Map<String, Double> stats = e.getValue();
                        String name = p != null ? p.getName() : "ID:" + e.getKey();
                        int age = p != null && p.getBirthDate() != null ?
                                Period.between(p.getBirthDate(), today).getYears() : 0;
                        sb.append("- ").append(name)
                                .append(age > 0 ? " (" + age + "세)" : "")
                                .append(" AVG: ").append(fmt3(stats.getOrDefault("AVG", 0.0)))
                                .append(", OPS: ").append(fmt3(stats.getOrDefault("OPS", 0.0)))
                                .append(", HR: ").append(stats.getOrDefault("HR", 0.0).intValue())
                                .append(", PA: ").append(stats.getOrDefault("PA", 0.0).intValue())
                                .append("\n");
                    });

            // 유망 투수 상위 5명 (ERA 기준, IP 20 이상)
            sb.append("\n[퓨처스 유망 투수 상위 5명 - ").append(season).append("시즌]\n");
            pitcherStatsMap.entrySet().stream()
                    .filter(e -> e.getValue().getOrDefault("IP", 0.0) >= 20)
                    .sorted((a, b) -> Double.compare(
                            a.getValue().getOrDefault("ERA", 99.0),
                            b.getValue().getOrDefault("ERA", 99.0)))
                    .limit(5)
                    .forEach(e -> {
                        Player p = playerMap.get(e.getKey());
                        Map<String, Double> stats = e.getValue();
                        String name = p != null ? p.getName() : "ID:" + e.getKey();
                        int age = p != null && p.getBirthDate() != null ?
                                Period.between(p.getBirthDate(), today).getYears() : 0;
                        sb.append("- ").append(name)
                                .append(age > 0 ? " (" + age + "세)" : "")
                                .append(" ERA: ").append(fmt(stats.getOrDefault("ERA", 0.0)))
                                .append(", IP: ").append(fmt(stats.getOrDefault("IP", 0.0)))
                                .append(", SO: ").append(stats.getOrDefault("SO", 0.0).intValue())
                                .append(", W: ").append(stats.getOrDefault("W", 0.0).intValue())
                                .append("\n");
                    });

        } catch (Exception e) {
            log.warn("calcFuturesProspects 실패: {}", e.getMessage());
            return "[계산 오류: " + e.getMessage() + "]";
        }
        return sb.toString();
    }

    // ==================== 주제 9: ABS 도입 영향 ====================
    public String calcAbsImpact(int season) {
        StringBuilder sb = new StringBuilder();
        try {
            List<Object[]> batters = batterStatsRepository.findAllBatters(season);
            List<Object[]> prevBatters = safeLoadBatters(season - 1);
            List<Object[]> pitchers = pitcherStatsRepository.findAllPitchers(season);
            List<Object[]> prevPitchers = safeLoadPitchers(season - 1);

            double curKRate = avg(batters, 23);
            double curBbRate = avg(batters, 24);
            double prevKRate = prevBatters.isEmpty() ? 0 : avg(prevBatters, 23);
            double prevBbRate = prevBatters.isEmpty() ? 0 : avg(prevBatters, 24);

            double curK9 = avg(pitchers, 18);
            double curBb9 = avg(pitchers, 19);
            double prevK9 = prevPitchers.isEmpty() ? 0 : avg(prevPitchers, 18);
            double prevBb9 = prevPitchers.isEmpty() ? 0 : avg(prevPitchers, 19);

            sb.append("[리그 평균 변화 (전년→올해)]\n");
            sb.append("타자 K%: ").append(fmt(prevKRate)).append("% → ").append(fmt(curKRate))
                    .append("% (").append(signedFmt(curKRate - prevKRate)).append("%p)\n");
            sb.append("타자 BB%: ").append(fmt(prevBbRate)).append("% → ").append(fmt(curBbRate))
                    .append("% (").append(signedFmt(curBbRate - prevBbRate)).append("%p)\n");
            sb.append("투수 K/9: ").append(fmt(prevK9)).append(" → ").append(fmt(curK9))
                    .append(" (").append(signedFmt(curK9 - prevK9)).append(")\n");
            sb.append("투수 BB/9: ").append(fmt(prevBb9)).append(" → ").append(fmt(curBb9))
                    .append(" (").append(signedFmt(curBb9 - prevBb9)).append(")\n\n");

            // K% 변화가 큰 타자 5명
            if (!prevBatters.isEmpty()) {
                Map<String, Double> prevKRateMap = new HashMap<>();
                prevBatters.forEach(b -> prevKRateMap.put(str(b[1]), dbl(b[23])));

                sb.append("[K% 변화 큰 타자 상위 5명]\n");
                batters.stream()
                        .filter(b -> prevKRateMap.containsKey(str(b[1])))
                        .sorted((a, b) -> Double.compare(
                                Math.abs(dbl(b[23]) - prevKRateMap.get(str(b[1]))),
                                Math.abs(dbl(a[23]) - prevKRateMap.get(str(a[1])))))
                        .limit(5)
                        .forEach(b -> {
                            String name = str(b[1]);
                            double prev = prevKRateMap.get(name);
                            sb.append("- ").append(name).append(" (").append(b[2])
                                    .append(") K%: ").append(fmt(prev)).append("% → ").append(b[23])
                                    .append("% (").append(signedFmt(dbl(b[23]) - prev)).append("%p)\n");
                        });
            }

            // K/9 변화가 큰 투수 5명
            if (!prevPitchers.isEmpty()) {
                Map<String, Double> prevK9Map = new HashMap<>();
                prevPitchers.forEach(p -> prevK9Map.put(str(p[0]), dbl(p[18])));

                sb.append("\n[K/9 변화 큰 투수 상위 5명]\n");
                pitchers.stream()
                        .filter(p -> prevK9Map.containsKey(str(p[0])))
                        .sorted((a, b) -> Double.compare(
                                Math.abs(dbl(b[18]) - prevK9Map.get(str(b[0]))),
                                Math.abs(dbl(a[18]) - prevK9Map.get(str(a[0])))))
                        .limit(5)
                        .forEach(p -> {
                            String name = str(p[0]);
                            double prev = prevK9Map.get(name);
                            sb.append("- ").append(name).append(" (").append(p[1])
                                    .append(") K/9: ").append(fmt(prev)).append(" → ").append(p[18])
                                    .append(" (").append(signedFmt(dbl(p[18]) - prev)).append(")\n");
                        });
            }

        } catch (Exception e) {
            log.warn("calcAbsImpact 실패: {}", e.getMessage());
            return "[계산 오류: " + e.getMessage() + "]";
        }
        return sb.toString();
    }

    // ==================== 주제 10: 클러치/초커 ====================
    // [참고] KBO 사이트 BasicOld 전환으로 RISP 데이터 없음 → RBI, HR, OPS 기반으로 대체
    public String calcClutchChoker(int season) {
        StringBuilder sb = new StringBuilder();
        try {
            List<Object[]> batters = batterStatsRepository.findAllBatters(season);
            if (batters.isEmpty()) return "[타자 데이터 없음]";

            // RBI(idx13) 기반 클러치 타자 (PA 100 이상)
            sb.append("[클러치 타자 상위 5명 (RBI 상위, PA 100 이상)]\n");
            batters.stream()
                    .filter(b -> dbl(b[11]) >= 100) // PA 100 이상
                    .sorted((a, b) -> Double.compare(dbl(b[13]), dbl(a[13]))) // RBI 내림차순
                    .limit(5)
                    .forEach(b -> sb.append("- ").append(b[1]).append(" (").append(b[2])
                            .append(") RBI: ").append((int) dbl(b[13]))
                            .append(", HR: ").append((int) dbl(b[7]))
                            .append(", OPS: ").append(fmt3(dbl(b[6]))).append("\n"));

            sb.append("\n[비클러치 타자 (PA 100+ 이상, RBI 하위)]\n");
            batters.stream()
                    .filter(b -> dbl(b[11]) >= 100)
                    .sorted((a, b) -> Double.compare(dbl(a[13]), dbl(b[13]))) // RBI 오름차순
                    .limit(5)
                    .forEach(b -> sb.append("- ").append(b[1]).append(" (").append(b[2])
                            .append(") RBI: ").append((int) dbl(b[13]))
                            .append(", HR: ").append((int) dbl(b[7]))
                            .append(", OPS: ").append(fmt3(dbl(b[6]))).append("\n"));

        } catch (Exception e) {
            log.warn("calcClutchChoker 실패: {}", e.getMessage());
            return "[계산 오류: " + e.getMessage() + "]";
        }
        return sb.toString();
    }

    // ==================== 주제 11: 나이-성과 커브 ====================
    public String calcAgeCurve(int season) {
        StringBuilder sb = new StringBuilder();
        try {
            List<Object[]> batters = batterStatsRepository.findAllBatters(season);
            List<Object[]> pitchers = pitcherStatsRepository.findAllPitchers(season);

            Map<Integer, Player> playerMap = new HashMap<>();
            playerRepository.findAll().forEach(p -> playerMap.put(p.getId(), p));

            LocalDate today = LocalDate.now();
            // 나이 구간: 20-23, 24-26, 27-29, 30-32, 33+
            String[] ageLabels = {"20-23세", "24-26세", "27-29세", "30-32세", "33세+"};

            // 타자 나이-WAR
            Map<String, List<Double>> batterAgeWar = initAgeMap(ageLabels);
            for (Object[] b : batters) {
                String name = str(b[1]);
                int teamId = intVal(b[20]);
                Player p = findPlayerByNameAndTeam(playerMap, name, teamId);
                if (p == null || p.getBirthDate() == null) continue;
                int age = Period.between(p.getBirthDate(), today).getYears();
                String bucket = ageBucket(age, ageLabels);
                if (bucket != null) batterAgeWar.get(bucket).add(dbl(b[4]));
            }

            // 투수 나이-WAR
            Map<String, List<Double>> pitcherAgeWar = initAgeMap(ageLabels);
            for (Object[] p : pitchers) {
                String name = str(p[0]);
                int teamId = intVal(p[15]);
                Player player = findPlayerByNameAndTeam(playerMap, name, teamId);
                if (player == null || player.getBirthDate() == null) continue;
                int age = Period.between(player.getBirthDate(), today).getYears();
                String bucket = ageBucket(age, ageLabels);
                if (bucket != null) pitcherAgeWar.get(bucket).add(dbl(p[14]));
            }

            sb.append("[타자 나이 구간별 평균 WAR - ").append(season).append("시즌]\n");
            String batterPeak = "";
            double batterPeakWar = -99;
            for (String label : ageLabels) {
                List<Double> wars = batterAgeWar.get(label);
                double avgWar = wars.isEmpty() ? 0 : wars.stream().mapToDouble(d -> d).average().orElse(0);
                sb.append("- ").append(label).append(": 평균 WAR ").append(fmt(avgWar))
                        .append(" (").append(wars.size()).append("명)\n");
                if (avgWar > batterPeakWar) {
                    batterPeakWar = avgWar;
                    batterPeak = label;
                }
            }
            sb.append("→ 타자 피크 구간: ").append(batterPeak).append(" (평균 WAR ").append(fmt(batterPeakWar)).append(")\n\n");

            sb.append("[투수 나이 구간별 평균 WAR - ").append(season).append("시즌]\n");
            String pitcherPeak = "";
            double pitcherPeakWar = -99;
            for (String label : ageLabels) {
                List<Double> wars = pitcherAgeWar.get(label);
                double avgWar = wars.isEmpty() ? 0 : wars.stream().mapToDouble(d -> d).average().orElse(0);
                sb.append("- ").append(label).append(": 평균 WAR ").append(fmt(avgWar))
                        .append(" (").append(wars.size()).append("명)\n");
                if (avgWar > pitcherPeakWar) {
                    pitcherPeakWar = avgWar;
                    pitcherPeak = label;
                }
            }
            sb.append("→ 투수 피크 구간: ").append(pitcherPeak).append(" (평균 WAR ").append(fmt(pitcherPeakWar)).append(")\n");

        } catch (Exception e) {
            log.warn("calcAgeCurve 실패: {}", e.getMessage());
            return "[계산 오류: " + e.getMessage() + "]";
        }
        return sb.toString();
    }

    // ==================== 주제 12: 라인업 효율성 ====================
    public String calcLineupEfficiency(int season) {
        StringBuilder sb = new StringBuilder();
        try {
            List<Object[]> batters = batterStatsRepository.findAllBatters(season);
            if (batters.isEmpty()) return "[타자 데이터 없음]";

            // 포지션 그룹 분류
            Map<String, String> posGroupMap = Map.of(
                    "C", "포수", "1B", "내야", "2B", "내야", "3B", "내야", "SS", "내야",
                    "LF", "외야", "CF", "외야", "RF", "외야", "DH", "DH"
            );

            // 팀별 포지션 그룹별 wRC+, OPS, WAR 집계
            Map<Integer, Map<String, List<double[]>>> teamPosData = new HashMap<>();

            for (Object[] b : batters) {
                String pos = str(b[0]);
                String group = posGroupMap.getOrDefault(pos, "기타");
                int teamId = intVal(b[20]);
                teamPosData.computeIfAbsent(teamId, k -> new HashMap<>())
                        .computeIfAbsent(group, k -> new ArrayList<>())
                        .add(new double[]{dbl(b[9]), dbl(b[6]), dbl(b[4])}); // wRC+, OPS, WAR
            }

            // 리그 전체 포지션 그룹별 평균
            Map<String, List<double[]>> leaguePosData = new HashMap<>();
            for (Object[] b : batters) {
                String pos = str(b[0]);
                String group = posGroupMap.getOrDefault(pos, "기타");
                leaguePosData.computeIfAbsent(group, k -> new ArrayList<>())
                        .add(new double[]{dbl(b[9]), dbl(b[6]), dbl(b[4])});
            }

            sb.append("[리그 포지션 그룹별 평균 - ").append(season).append("시즌]\n");
            sb.append(String.format("%-6s %-8s %-8s %-8s %-6s\n", "그룹", "wRC+", "OPS", "WAR", "인원"));
            for (String group : List.of("포수", "내야", "외야", "DH")) {
                List<double[]> data = leaguePosData.getOrDefault(group, List.of());
                if (data.isEmpty()) continue;
                double avgWrc = data.stream().mapToDouble(d -> d[0]).average().orElse(0);
                double avgOps = data.stream().mapToDouble(d -> d[1]).average().orElse(0);
                double avgWar = data.stream().mapToDouble(d -> d[2]).average().orElse(0);
                sb.append(String.format("%-6s %-8s %-8s %-8s %-6d\n",
                        group, fmt(avgWrc), fmt3(avgOps), fmt(avgWar), data.size()));
            }

            // 팀별 포지션 그룹 생산성
            sb.append("\n[팀별 포지션 그룹 wRC+ 요약]\n");
            sb.append(String.format("%-8s %-8s %-8s %-8s %-8s\n", "팀", "포수", "내야", "외야", "DH"));
            teamPosData.forEach((teamId, posData) -> {
                String name = teamRepository.findTeamNameById(teamId);
                if (name == null) name = "팀" + teamId;
                sb.append(String.format("%-8s ", name));
                for (String group : List.of("포수", "내야", "외야", "DH")) {
                    List<double[]> data = posData.getOrDefault(group, List.of());
                    double avgWrc = data.isEmpty() ? 0 : data.stream().mapToDouble(d -> d[0]).average().orElse(0);
                    sb.append(String.format("%-8s ", fmt(avgWrc)));
                }
                sb.append("\n");
            });

        } catch (Exception e) {
            log.warn("calcLineupEfficiency 실패: {}", e.getMessage());
            return "[계산 오류: " + e.getMessage() + "]";
        }
        return sb.toString();
    }

    // ==================== 주제 13: 월별/계절별 패턴 ====================
    public String calcSeasonalPatterns(int season) {
        StringBuilder sb = new StringBuilder();
        try {
            List<Schedule> games = scheduleRepository.findAllFinishedBySeason(season);
            if (games.isEmpty()) return "[경기 데이터 없음]";

            // 팀별 월별 승패
            Map<Integer, Map<Integer, int[]>> teamMonthRecord = new HashMap<>(); // teamId → month → [wins, losses]

            for (Schedule g : games) {
                int month = g.getMatchDate().toLocalDateTime().getMonthValue();
                int homeScore = g.getHomeTeamScore() != null ? g.getHomeTeamScore() : 0;
                int awayScore = g.getAwayTeamScore() != null ? g.getAwayTeamScore() : 0;

                if (homeScore == awayScore) continue;

                // 홈팀
                int[] homeRec = teamMonthRecord
                        .computeIfAbsent(g.getHomeTeamId(), k -> new HashMap<>())
                        .computeIfAbsent(month, k -> new int[2]);
                if (homeScore > awayScore) homeRec[0]++;
                else homeRec[1]++;

                // 원정팀
                int[] awayRec = teamMonthRecord
                        .computeIfAbsent(g.getAwayTeamId(), k -> new HashMap<>())
                        .computeIfAbsent(month, k -> new int[2]);
                if (awayScore > homeScore) awayRec[0]++;
                else awayRec[1]++;
            }

            sb.append("[팀별 월별 승률 - ").append(season).append("시즌]\n");
            sb.append(String.format("%-8s", "팀"));
            for (int m = 3; m <= 10; m++) sb.append(String.format("%-8s", m + "월"));
            sb.append("\n");

            // 상반기/하반기 승률 변화 추적
            List<Object[]> teamHalfChanges = new ArrayList<>();

            teamMonthRecord.forEach((teamId, monthData) -> {
                String name = teamRepository.findTeamNameById(teamId);
                if (name == null) name = "팀" + teamId;
                sb.append(String.format("%-8s", name));
                int firstHalfW = 0, firstHalfL = 0, secondHalfW = 0, secondHalfL = 0;
                for (int m = 3; m <= 10; m++) {
                    int[] rec = monthData.getOrDefault(m, new int[]{0, 0});
                    double rate = rec[0] + rec[1] > 0 ? (double) rec[0] / (rec[0] + rec[1]) : 0;
                    sb.append(String.format("%-8s", rec[0] + rec[1] > 0 ? fmt3(rate) : "-"));
                    if (m >= 4 && m <= 6) { firstHalfW += rec[0]; firstHalfL += rec[1]; }
                    if (m >= 7 && m <= 9) { secondHalfW += rec[0]; secondHalfL += rec[1]; }
                }
                sb.append("\n");

                double firstRate = firstHalfW + firstHalfL > 0 ?
                        (double) firstHalfW / (firstHalfW + firstHalfL) : 0;
                double secondRate = secondHalfW + secondHalfL > 0 ?
                        (double) secondHalfW / (secondHalfW + secondHalfL) : 0;
                teamHalfChanges.add(new Object[]{name, firstRate, secondRate, secondRate - firstRate});
            });

            // 상반기→하반기 변화 큰 팀
            sb.append("\n[상반기(4-6월) vs 하반기(7-9월) 승률 변화]\n");
            teamHalfChanges.stream()
                    .sorted((a, b) -> Double.compare(
                            Math.abs((double) b[3]), Math.abs((double) a[3])))
                    .forEach(t -> {
                        double diff = (double) t[3];
                        String label = diff > 0.05 ? "급등" : diff < -0.05 ? "급락" : "유지";
                        sb.append("- ").append(t[0])
                                .append(": 상반기 ").append(fmt3((double) t[1]))
                                .append(" → 하반기 ").append(fmt3((double) t[2]))
                                .append(" (").append(signedFmt3(diff)).append(", ").append(label).append(")\n");
                    });

        } catch (Exception e) {
            log.warn("calcSeasonalPatterns 실패: {}", e.getMessage());
            return "[계산 오류: " + e.getMessage() + "]";
        }
        return sb.toString();
    }

    // ==================== 주제 14: 포지션별 가치 ====================
    public String calcPositionValue(int season) {
        StringBuilder sb = new StringBuilder();
        try {
            List<Object[]> batters = batterStatsRepository.findAllBatters(season);
            if (batters.isEmpty()) return "[타자 데이터 없음]";

            // 포지션별 WAR, wRC+, OPS 집계
            Map<String, List<double[]>> posStats = new LinkedHashMap<>();
            for (Object[] b : batters) {
                String pos = str(b[0]);
                if (pos == null || pos.isEmpty()) continue;
                posStats.computeIfAbsent(pos, k -> new ArrayList<>())
                        .add(new double[]{dbl(b[4]), dbl(b[9]), dbl(b[6])}); // WAR, wRC+, OPS
            }

            sb.append("[포지션별 평균 지표 - ").append(season).append("시즌]\n");
            sb.append(String.format("%-5s %-8s %-8s %-8s %-6s\n", "포지션", "WAR", "wRC+", "OPS", "인원"));

            posStats.entrySet().stream()
                    .sorted((a, b) -> Double.compare(
                            b.getValue().stream().mapToDouble(d -> d[0]).average().orElse(0),
                            a.getValue().stream().mapToDouble(d -> d[0]).average().orElse(0)))
                    .forEach(e -> {
                        List<double[]> data = e.getValue();
                        double avgWar = data.stream().mapToDouble(d -> d[0]).average().orElse(0);
                        double avgWrc = data.stream().mapToDouble(d -> d[1]).average().orElse(0);
                        double avgOps = data.stream().mapToDouble(d -> d[2]).average().orElse(0);
                        sb.append(String.format("%-5s %-8s %-8s %-8s %-6d\n",
                                e.getKey(), fmt(avgWar), fmt(avgWrc), fmt3(avgOps), data.size()));
                    });

            // 포지션별 대체 수준 (하위 20% 평균)
            sb.append("\n[포지션별 대체 수준 (하위 20% 평균 WAR)]\n");
            posStats.forEach((pos, data) -> {
                if (data.size() < 5) return;
                List<Double> wars = data.stream().mapToDouble(d -> d[0]).sorted().boxed().toList();
                int cutoff = Math.max(1, wars.size() / 5);
                double replacementWar = wars.subList(0, cutoff).stream().mapToDouble(d -> d).average().orElse(0);
                sb.append("- ").append(pos).append(": 대체 수준 WAR ").append(fmt(replacementWar)).append("\n");
            });

            // 포지션별 상위 3명
            sb.append("\n[포지션별 WAR 상위 3명]\n");
            posStats.forEach((pos, data) -> {
                sb.append("[").append(pos).append("]\n");
                // 원본 batters에서 해당 포지션 상위 3명
                batters.stream()
                        .filter(b -> pos.equals(str(b[0])))
                        .sorted((a, b) -> Double.compare(dbl(b[4]), dbl(a[4])))
                        .limit(3)
                        .forEach(b -> sb.append("  - ").append(b[1]).append(" (").append(b[2])
                                .append(") WAR: ").append(b[4])
                                .append(", wRC+: ").append(b[9]).append("\n"));
            });

        } catch (Exception e) {
            log.warn("calcPositionValue 실패: {}", e.getMessage());
            return "[계산 오류: " + e.getMessage() + "]";
        }
        return sb.toString();
    }

    // ==================== 공통 헬퍼 메서드 ====================

    private List<Object[]> safeLoadBatters(int season) {
        try {
            return batterStatsRepository.findAllBatters(season);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Object[]> safeLoadPitchers(int season) {
        try {
            return pitcherStatsRepository.findAllPitchers(season);
        } catch (Exception e) {
            return List.of();
        }
    }

    private double dbl(Object val) {
        if (val == null) return 0.0;
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private int intVal(Object val) {
        if (val == null) return 0;
        try {
            return ((Number) val).intValue();
        } catch (Exception e) {
            try { return Integer.parseInt(val.toString()); } catch (Exception ex) { return 0; }
        }
    }

    private String str(Object val) {
        return val != null ? val.toString() : "";
    }

    private double avg(List<Object[]> rows, int idx) {
        if (rows.isEmpty()) return 0.0;
        return rows.stream().mapToDouble(r -> dbl(r[idx])).average().orElse(0.0);
    }

    private String fmt(double val) {
        return String.format("%.2f", val);
    }

    private String fmt3(double val) {
        return String.format("%.3f", val);
    }

    private String signedFmt(double val) {
        return val >= 0 ? "+" + fmt(val) : fmt(val);
    }

    private String signedFmt3(double val) {
        return val >= 0 ? "+" + fmt3(val) : fmt3(val);
    }

    private Map<String, Double> buildNameWarMap(List<Object[]> rows, int nameIdx, int warIdx) {
        Map<String, Double> map = new HashMap<>();
        for (Object[] r : rows) {
            map.put(str(r[nameIdx]), dbl(r[warIdx]));
        }
        return map;
    }

    private void parseAndAccumInnings(Map<Integer, double[]> totals, Map<Integer, int[]> counts,
                                       int teamId, String inningScores) {
        if (inningScores == null || inningScores.isEmpty()) return;
        String[] parts = inningScores.split(",");
        double[] teamTotals = totals.computeIfAbsent(teamId, k -> new double[9]);
        int[] teamCounts = counts.computeIfAbsent(teamId, k -> new int[9]);
        for (int i = 0; i < Math.min(parts.length, 9); i++) {
            try {
                teamTotals[i] += Double.parseDouble(parts[i].trim());
                teamCounts[i]++;
            } catch (NumberFormatException ignored) {}
        }
    }

    private double avgRange(double[] totals, int[] counts, int from, int to) {
        double sum = 0;
        int cnt = 0;
        for (int i = from; i < Math.min(to, totals.length); i++) {
            sum += totals[i];
            cnt += counts[i];
        }
        return cnt > 0 ? sum / (cnt / (to - from)) : 0;
    }

    private String classifyTeamType(double early, double mid, double late) {
        if (late > early && late > mid) return "역전형";
        if (early > mid && early > late) return "선행형";
        return "균형형";
    }

    private Map<String, List<Double>> initAgeMap(String[] labels) {
        Map<String, List<Double>> map = new LinkedHashMap<>();
        for (String l : labels) map.put(l, new ArrayList<>());
        return map;
    }

    private String ageBucket(int age, String[] labels) {
        if (age >= 20 && age <= 23) return labels[0];
        if (age >= 24 && age <= 26) return labels[1];
        if (age >= 27 && age <= 29) return labels[2];
        if (age >= 30 && age <= 32) return labels[3];
        if (age >= 33) return labels[4];
        return null;
    }

    private Player findPlayerByNameAndTeam(Map<Integer, Player> playerMap, String name, int teamId) {
        return playerMap.values().stream()
                .filter(p -> name.equals(p.getName()) && (p.getTeamId() != null && p.getTeamId() == teamId))
                .findFirst().orElse(null);
    }
}
