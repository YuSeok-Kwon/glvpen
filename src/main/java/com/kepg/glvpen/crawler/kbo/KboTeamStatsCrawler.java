package com.kepg.glvpen.crawler.kbo;

import static com.kepg.glvpen.crawler.kbo.util.KboConstants.*;
import static com.kepg.glvpen.crawler.kbo.util.PlaywrightFactory.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import com.kepg.glvpen.crawler.kbo.util.AbstractPlaywrightCrawler;
import com.kepg.glvpen.modules.team.teamHeadToHead.dto.TeamHeadToHeadDTO;
import com.kepg.glvpen.modules.team.teamHeadToHead.service.TeamHeadToHeadService;
import com.kepg.glvpen.modules.team.teamRanking.dto.TeamRankingDTO;
import com.kepg.glvpen.modules.team.teamRanking.service.TeamRankingService;
import com.kepg.glvpen.modules.team.teamStats.dto.TeamStatsDTO;
import com.kepg.glvpen.modules.team.teamStats.service.TeamStatsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * KBO 공식 사이트 팀 순위/기록 크롤러
 * - 팀 순위: TeamRankDaily.aspx
 * - 팀 타격: Team/Hitter/Basic1.aspx + Basic2.aspx
 * - 팀 투수: Team/Pitcher/Basic1.aspx
 *
 * ASP.NET PostBack 기반 페이지:
 * select.change → __doPostBack → 페이지 리로드
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KboTeamStatsCrawler extends AbstractPlaywrightCrawler {

    private final TeamRankingService teamRankingService;
    private final TeamStatsService teamStatsService;
    private final TeamHeadToHeadService teamHeadToHeadService;

    /**
     * 현재 시즌 모든 팀 데이터 크롤링 (3월 이전이면 직전 시즌)
     */
    public void crawlAllTeamData() {
        int season = LocalDate.now().getMonthValue() < 3
                ? LocalDate.now().getYear() - 1
                : LocalDate.now().getYear();
        crawlAllTeamData(season);
    }

    /**
     * 특정 시즌 모든 팀 데이터 크롤링
     */
    public void crawlAllTeamData(int season) {
        log.info("=== KBO 팀 데이터 크롤링 시작 ({}시즌) ===", season);
        crawlTeamRankings(season);
        crawlTeamHeadToHead(season);
        crawlTeamBatterStats(season);
        crawlTeamPitcherStats(season);
        crawlTeamDefenseStats(season);
        crawlTeamRunnerStats(season);
        log.info("=== KBO 팀 데이터 크롤링 완료 ({}시즌) ===", season);
    }

    /**
     * 다시즌 배치 크롤링 (역순: 최신→과거)
     */
    public void crawlMultiSeasons(int startYear, int endYear) {
        log.info("=== KBO 팀 다시즌 크롤링 시작: {}→{} (역순) ===", endYear, startYear);
        for (int year = endYear; year >= startYear; year--) {
            log.info("--- {}시즌 팀 데이터 크롤링 ---", year);
            crawlTeamRankings(year);
            crawlTeamHeadToHead(year);
            crawlTeamBatterStats(year);
            crawlTeamPitcherStats(year);
            crawlTeamDefenseStats(year);
            crawlTeamRunnerStats(year);
        }
        log.info("=== KBO 팀 다시즌 크롤링 완료 ===");
    }

    // ==================== 팀 순위 ====================

    public void crawlTeamRankings(int season) {
        log.info("[KBO 팀순위] {}시즌 크롤링 시작", season);

        withBrowser("KBO 팀순위", (page, browser) -> {
            navigateAndWait(page, TEAM_RANKING_URL);
            selectSeasonDropdown(page, season);

            Document doc = getJsoupDocument(page);
            Elements rows = findFirstTableRows(doc);

            int teamCount = 0;
            for (Element row : rows) {
                try {
                    Elements cells = row.select("td");
                    if (cells.size() < 8) continue;

                    String firstCell = cells.get(0).text().trim();
                    if (firstCell.equals("순위") || firstCell.isEmpty()) continue;

                    int ranking = parseInt(cells.get(0));
                    String teamName = cells.get(1).text().trim();
                    int games = parseInt(cells.get(2));
                    int wins = parseInt(cells.get(3));
                    int losses = parseInt(cells.get(4));
                    int draws = parseInt(cells.get(5));
                    double winRate = parseDouble(cells.get(6));
                    double gamesBehind = parseDouble(cells.get(7));

                    Integer teamId = resolveTeamId(teamName);
                    if (teamId == null) {
                        log.warn("팀순위 - 팀 매핑 실패: {}", teamName);
                        continue;
                    }

                    TeamRankingDTO dto = new TeamRankingDTO();
                    dto.setTeamId(teamId);
                    dto.setSeason(season);
                    dto.setRanking(ranking);
                    dto.setGames(games);
                    dto.setWins(wins);
                    dto.setLosses(losses);
                    dto.setDraws(draws);
                    dto.setWinRate(winRate);
                    dto.setGamesBehind(gamesBehind);

                    teamRankingService.saveOrUpdate(dto);
                    teamCount++;

                    log.debug("팀순위 저장: {}위 {} ({}승 {}패 {}무)", ranking, teamName, wins, losses, draws);

                } catch (Exception e) {
                    log.warn("팀순위 행 처리 오류: {}", e.getMessage());
                }
            }

            log.info("[KBO 팀순위] {}시즌 완료: {}팀 처리", season, teamCount);
        });
    }

    // ==================== 팀 타격 ====================

    public void crawlTeamBatterStats(int season) {
        log.info("[KBO 팀타격] {}시즌 크롤링 시작 (Basic1 + Basic2)", season);

        withBrowser("KBO 팀타격", (page, browser) -> {
            // ---- Basic1: AVG, R, H, HR, RBI ----
            navigateAndWait(page, TEAM_BATTER_URL);

            if (!selectSeasonDropdown(page, season)) {
                log.warn("[KBO 팀타격] {}시즌 선택 불가 — 스킵", season);
                return;
            }

            Document doc = getJsoupDocument(page);
            Elements rows = findFirstTableRows(doc);

            List<TeamStatsDTO> batch = new ArrayList<>();
            int teamCount = 0;
            for (Element row : rows) {
                try {
                    Elements cells = row.select("td");
                    if (cells.size() < 15) continue;

                    String firstCell = cells.get(0).text().trim();
                    if (firstCell.equals("순위") || firstCell.isEmpty()) continue;

                    int ranking = parseInt(cells.get(0));
                    String teamName = cells.get(1).text().trim();
                    Integer teamId = resolveTeamId(teamName);
                    if (teamId == null) {
                        log.warn("팀타격 Basic1 - 팀 매핑 실패: {}", teamName);
                        continue;
                    }

                    addTeamStat(batch, teamId, season, "AVG", parseDouble(cells.get(2)), ranking);
                    addTeamStat(batch, teamId, season, "G", parseDouble(cells.get(3)), null);
                    addTeamStat(batch, teamId, season, "PA", parseDouble(cells.get(4)), null);
                    addTeamStat(batch, teamId, season, "AB", parseDouble(cells.get(5)), null);
                    addTeamStat(batch, teamId, season, "R", parseDouble(cells.get(6)), null);
                    addTeamStat(batch, teamId, season, "H", parseDouble(cells.get(7)), null);
                    addTeamStat(batch, teamId, season, "2B", parseDouble(cells.get(8)), null);
                    addTeamStat(batch, teamId, season, "3B", parseDouble(cells.get(9)), null);
                    addTeamStat(batch, teamId, season, "HR", parseDouble(cells.get(10)), null);
                    addTeamStat(batch, teamId, season, "TB", parseDouble(cells.get(11)), null);
                    addTeamStat(batch, teamId, season, "RBI", parseDouble(cells.get(12)), null);
                    addTeamStat(batch, teamId, season, "SAC", parseDouble(cells.get(13)), null);
                    addTeamStat(batch, teamId, season, "SF", parseDouble(cells.get(14)), null);

                    teamCount++;
                } catch (Exception e) {
                    log.warn("팀타격 Basic1 행 처리 오류: {}", e.getMessage());
                }
            }
            log.info("[KBO 팀타격] Basic1 완료: {}팀", teamCount);

            // ---- Basic2: BB, SO, SLG, OBP, OPS ----
            navigateAndWait(page, TEAM_BATTER_URL2);

            if (!selectSeasonDropdown(page, season)) {
                log.warn("[KBO 팀타격 Basic2] {}시즌 선택 불가 — 스킵", season);
                return;
            }

            doc = getJsoupDocument(page);
            rows = findFirstTableRows(doc);

            int teamCount2 = 0;
            for (Element row : rows) {
                try {
                    Elements cells = row.select("td");
                    if (cells.size() < 14) continue;

                    String firstCell = cells.get(0).text().trim();
                    if (firstCell.equals("순위") || firstCell.isEmpty()) continue;

                    String teamName = cells.get(1).text().trim();
                    Integer teamId = resolveTeamId(teamName);
                    if (teamId == null) {
                        log.warn("팀타격 Basic2 - 팀 매핑 실패: {}", teamName);
                        continue;
                    }

                    addTeamStat(batch, teamId, season, "BB", parseDouble(cells.get(3)), null);
                    addTeamStat(batch, teamId, season, "IBB", parseDouble(cells.get(4)), null);
                    addTeamStat(batch, teamId, season, "HBP", parseDouble(cells.get(5)), null);
                    addTeamStat(batch, teamId, season, "SO", parseDouble(cells.get(6)), null);
                    addTeamStat(batch, teamId, season, "GDP", parseDouble(cells.get(7)), null);
                    addTeamStat(batch, teamId, season, "SLG", parseDouble(cells.get(8)), null);
                    addTeamStat(batch, teamId, season, "OBP", parseDouble(cells.get(9)), null);
                    addTeamStat(batch, teamId, season, "OPS", parseDouble(cells.get(10)), null);
                    addTeamStat(batch, teamId, season, "RISP", parseDouble(cells.get(12)), null);

                    teamCount2++;
                } catch (Exception e) {
                    log.warn("팀타격 Basic2 행 처리 오류: {}", e.getMessage());
                }
            }

            // 배치 저장
            if (!batch.isEmpty()) {
                teamStatsService.saveBatch(batch);
            }

            log.info("[KBO 팀타격] Basic2 완료: {}팀", teamCount2);
            log.info("[KBO 팀타격] {}시즌 크롤링 완료", season);
        });
    }

    // ==================== 팀 투수 ====================

    public void crawlTeamPitcherStats(int season) {
        log.info("[KBO 팀투수] {}시즌 크롤링 시작 (Basic1 + Basic2)", season);

        withBrowser("KBO 팀투수", (page, browser) -> {
            List<TeamStatsDTO> batch = new ArrayList<>();

            // ---- Basic1 ----
            navigateAndWait(page, TEAM_PITCHER_URL);

            if (!selectSeasonDropdown(page, season)) {
                log.warn("[KBO 팀투수] {}시즌 선택 불가 — 스킵", season);
                return;
            }

            Document doc = getJsoupDocument(page);
            Elements rows = findFirstTableRows(doc);

            int teamCount = 0;
            for (Element row : rows) {
                try {
                    Elements cells = row.select("td");
                    if (cells.size() < 18) continue;

                    String firstCell = cells.get(0).text().trim();
                    if (firstCell.equals("순위") || firstCell.isEmpty()) continue;

                    int ranking = parseInt(cells.get(0));
                    String teamName = cells.get(1).text().trim();
                    Integer teamId = resolveTeamId(teamName);
                    if (teamId == null) {
                        log.warn("팀투수 Basic1 - 팀 매핑 실패: {}", teamName);
                        continue;
                    }

                    addTeamStat(batch, teamId, season, "ERA", parseDouble(cells.get(2)), ranking);
                    addTeamStat(batch, teamId, season, "투G", parseDouble(cells.get(3)), null);
                    addTeamStat(batch, teamId, season, "W", parseDouble(cells.get(4)), null);
                    addTeamStat(batch, teamId, season, "L", parseDouble(cells.get(5)), null);
                    addTeamStat(batch, teamId, season, "SV", parseDouble(cells.get(6)), null);
                    addTeamStat(batch, teamId, season, "HLD", parseDouble(cells.get(7)), null);
                    addTeamStat(batch, teamId, season, "WPCT", parseDouble(cells.get(8)), null);
                    addTeamStat(batch, teamId, season, "IP", parseInningsPitched(cells.get(9)), null);
                    addTeamStat(batch, teamId, season, "피안타", parseDouble(cells.get(10)), null);
                    addTeamStat(batch, teamId, season, "피홈런", parseDouble(cells.get(11)), null);
                    addTeamStat(batch, teamId, season, "투BB", parseDouble(cells.get(12)), null);
                    addTeamStat(batch, teamId, season, "투SO", parseDouble(cells.get(14)), null);
                    addTeamStat(batch, teamId, season, "실점", parseDouble(cells.get(15)), null);
                    addTeamStat(batch, teamId, season, "자책점", parseDouble(cells.get(16)), null);
                    addTeamStat(batch, teamId, season, "WHIP", parseDouble(cells.get(17)), null);

                    teamCount++;
                } catch (Exception e) {
                    log.warn("팀투수 Basic1 행 처리 오류: {}", e.getMessage());
                }
            }
            log.info("[KBO 팀투수] Basic1 완료: {}팀", teamCount);

            // ---- Basic2 ----
            navigateAndWait(page, TEAM_PITCHER_URL2);

            if (!selectSeasonDropdown(page, season)) {
                log.warn("[KBO 팀투수 Basic2] {}시즌 선택 불가 — 스킵", season);
                return;
            }

            doc = getJsoupDocument(page);
            rows = findFirstTableRows(doc);

            int teamCount2 = 0;
            for (Element row : rows) {
                try {
                    Elements cells = row.select("td");
                    if (cells.size() < 17) continue;

                    String firstCell = cells.get(0).text().trim();
                    if (firstCell.equals("순위") || firstCell.isEmpty()) continue;

                    String teamName = cells.get(1).text().trim();
                    Integer teamId = resolveTeamId(teamName);
                    if (teamId == null) {
                        log.warn("팀투수 Basic2 - 팀 매핑 실패: {}", teamName);
                        continue;
                    }

                    addTeamStat(batch, teamId, season, "CG", parseDouble(cells.get(3)), null);
                    addTeamStat(batch, teamId, season, "SHO", parseDouble(cells.get(4)), null);
                    addTeamStat(batch, teamId, season, "QS", parseDouble(cells.get(5)), null);
                    addTeamStat(batch, teamId, season, "BSV", parseDouble(cells.get(6)), null);
                    addTeamStat(batch, teamId, season, "TBF", parseDouble(cells.get(7)), null);
                    addTeamStat(batch, teamId, season, "NP", parseDouble(cells.get(8)), null);
                    addTeamStat(batch, teamId, season, "피안타율", parseDouble(cells.get(9)), null);
                    addTeamStat(batch, teamId, season, "투IBB", parseDouble(cells.get(14)), null);
                    addTeamStat(batch, teamId, season, "WP", parseDouble(cells.get(15)), null);
                    addTeamStat(batch, teamId, season, "BK", parseDouble(cells.get(16)), null);

                    teamCount2++;
                } catch (Exception e) {
                    log.warn("팀투수 Basic2 행 처리 오류: {}", e.getMessage());
                }
            }

            // 배치 저장
            if (!batch.isEmpty()) {
                teamStatsService.saveBatch(batch);
            }

            log.info("[KBO 팀투수] Basic2 완료: {}팀", teamCount2);
            log.info("[KBO 팀투수] {}시즌 크롤링 완료", season);
        });
    }

    // ==================== 팀 수비 ====================

    public void crawlTeamDefenseStats(int season) {
        log.info("[KBO 팀수비] {}시즌 크롤링 시작", season);

        withBrowser("KBO 팀수비", (page, browser) -> {
            navigateAndWait(page, TEAM_DEFENSE_URL);

            if (!selectSeasonDropdown(page, season)) {
                log.warn("[KBO 팀수비] {}시즌 선택 불가 — 스킵", season);
                return;
            }

            Document doc = getJsoupDocument(page);
            Elements rows = findFirstTableRows(doc);

            List<TeamStatsDTO> batch = new ArrayList<>();
            int teamCount = 0;
            for (Element row : rows) {
                try {
                    Elements cells = row.select("td");
                    if (cells.size() < 13) continue;

                    String firstCell = cells.get(0).text().trim();
                    if (firstCell.equals("순위") || firstCell.isEmpty()) continue;

                    int ranking = parseInt(cells.get(0));
                    String teamName = cells.get(1).text().trim();
                    Integer teamId = resolveTeamId(teamName);
                    if (teamId == null) {
                        log.warn("팀수비 - 팀 매핑 실패: {}", teamName);
                        continue;
                    }

                    addTeamStat(batch, teamId, season, "E", parseDouble(cells.get(3)), null);
                    addTeamStat(batch, teamId, season, "DP", parseDouble(cells.get(7)), null);
                    addTeamStat(batch, teamId, season, "FPCT", parseDouble(cells.get(8)), ranking);
                    addTeamStat(batch, teamId, season, "PB", parseDouble(cells.get(9)), null);
                    addTeamStat(batch, teamId, season, "도루허용", parseDouble(cells.get(10)), null);
                    addTeamStat(batch, teamId, season, "도루저지", parseDouble(cells.get(11)), null);
                    addTeamStat(batch, teamId, season, "CS%", parseDouble(cells.get(12)), null);

                    teamCount++;
                } catch (Exception e) {
                    log.warn("팀수비 행 처리 오류: {}", e.getMessage());
                }
            }

            if (!batch.isEmpty()) {
                teamStatsService.saveBatch(batch);
            }

            log.info("[KBO 팀수비] {}시즌 완료: {}팀 처리", season, teamCount);
        });
    }

    // ==================== 팀 주루 ====================

    public void crawlTeamRunnerStats(int season) {
        log.info("[KBO 팀주루] {}시즌 크롤링 시작", season);

        withBrowser("KBO 팀주루", (page, browser) -> {
            navigateAndWait(page, TEAM_RUNNER_URL);

            if (!selectSeasonDropdown(page, season)) {
                log.warn("[KBO 팀주루] {}시즌 선택 불가 — 스킵", season);
                return;
            }

            Document doc = getJsoupDocument(page);
            Elements rows = findFirstTableRows(doc);

            List<TeamStatsDTO> batch = new ArrayList<>();
            int teamCount = 0;
            for (Element row : rows) {
                try {
                    Elements cells = row.select("td");
                    if (cells.size() < 9) continue;

                    String firstCell = cells.get(0).text().trim();
                    if (firstCell.equals("순위") || firstCell.isEmpty()) continue;

                    int ranking = parseInt(cells.get(0));
                    String teamName = cells.get(1).text().trim();
                    Integer teamId = resolveTeamId(teamName);
                    if (teamId == null) {
                        log.warn("팀주루 - 팀 매핑 실패: {}", teamName);
                        continue;
                    }

                    addTeamStat(batch, teamId, season, "SBA", parseDouble(cells.get(3)), null);
                    addTeamStat(batch, teamId, season, "SB", parseDouble(cells.get(4)), ranking);
                    addTeamStat(batch, teamId, season, "주루CS", parseDouble(cells.get(5)), null);
                    addTeamStat(batch, teamId, season, "SB%", parseDouble(cells.get(6)), null);
                    addTeamStat(batch, teamId, season, "OOB", parseDouble(cells.get(7)), null);

                    teamCount++;
                } catch (Exception e) {
                    log.warn("팀주루 행 처리 오류: {}", e.getMessage());
                }
            }

            if (!batch.isEmpty()) {
                teamStatsService.saveBatch(batch);
            }

            log.info("[KBO 팀주루] {}시즌 완료: {}팀 처리", season, teamCount);
        });
    }

    // ==================== 상대전적 ====================

    public void crawlTeamHeadToHead(int season) {
        log.info("[KBO 상대전적] {}시즌 크롤링 시작", season);

        withBrowser("KBO 상대전적", (page, browser) -> {
            navigateAndWait(page, TEAM_RANK_URL);

            if (!selectSeasonDropdown(page, season)) {
                log.warn("[KBO 상대전적] {}시즌 선택 불가 — 스킵", season);
                return;
            }

            Document doc = getJsoupDocument(page);

            Elements tables = doc.select("table.tData");
            if (tables.size() < 2) {
                log.warn("[KBO 상대전적] 상대전적 테이블을 찾을 수 없습니다 (테이블 수: {})", tables.size());
                return;
            }

            Element h2hTable = tables.get(1);
            Elements rows = h2hTable.select("tbody tr");

            Elements headerCells = h2hTable.select("thead tr th");
            if (headerCells.isEmpty()) {
                headerCells = h2hTable.select("tr:first-child th");
            }

            List<Integer> opponentTeamIds = new java.util.ArrayList<>();
            for (int i = 1; i < headerCells.size(); i++) {
                String rawText = headerCells.get(i).text().trim();
                String opponentName = rawText.replaceAll("\\s*\\(.*\\)", "").trim();
                if ("합계".equals(opponentName) || opponentName.isEmpty()) {
                    opponentTeamIds.add(null);
                    continue;
                }
                Integer opponentId = resolveTeamId(opponentName);
                opponentTeamIds.add(opponentId);
            }

            log.info("[KBO 상대전적] 상대팀 {}개 감지: {}",
                    opponentTeamIds.size(), opponentTeamIds);

            int recordCount = 0;

            for (Element row : rows) {
                try {
                    Elements cells = row.select("td");
                    if (cells.isEmpty()) continue;

                    String teamName = cells.get(0).text().trim();
                    if (teamName.isEmpty()) continue;
                    Integer teamId = resolveTeamId(teamName);
                    if (teamId == null) {
                        log.warn("[상대전적] 기준팀 매핑 실패: {}", teamName);
                        continue;
                    }

                    for (int i = 1; i < cells.size() && i - 1 < opponentTeamIds.size(); i++) {
                        Integer opponentId = opponentTeamIds.get(i - 1);
                        if (opponentId == null) continue;
                        if (opponentId.equals(teamId)) continue;

                        String cellText = cells.get(i).text().trim();
                        if (cellText.isEmpty() || "-".equals(cellText)) continue;

                        int[] wld = parseWinLossDraw(cellText);
                        if (wld == null) continue;

                        teamHeadToHeadService.saveOrUpdate(TeamHeadToHeadDTO.builder()
                                .season(season)
                                .teamId(teamId)
                                .opponentTeamId(opponentId)
                                .wins(wld[0])
                                .losses(wld[1])
                                .draws(wld[2])
                                .build());

                        recordCount++;
                    }
                } catch (Exception e) {
                    log.warn("[상대전적] 행 처리 오류: {}", e.getMessage());
                }
            }

            log.info("[KBO 상대전적] {}시즌 완료: {}건 저장", season, recordCount);
        });
    }

    // ==================== 헬퍼 ====================

    private void addTeamStat(List<TeamStatsDTO> batch, int teamId, int season,
                              String category, double value, Integer ranking) {
        TeamStatsDTO dto = new TeamStatsDTO();
        dto.setTeamId(teamId);
        dto.setSeason(season);
        dto.setCategory(category);
        dto.setValue(value);
        dto.setRank(ranking != null ? String.valueOf(ranking) : null);
        batch.add(dto);
    }

    private int[] parseWinLossDraw(String text) {
        try {
            String[] parts = text.split("-");
            if (parts.length < 2) return null;
            int wins = Integer.parseInt(parts[0].trim());
            int losses = Integer.parseInt(parts[1].trim());
            int draws = parts.length >= 3 ? Integer.parseInt(parts[2].trim()) : 0;
            return new int[]{wins, losses, draws};
        } catch (NumberFormatException e) {
            log.debug("승-패-무 파싱 실패: '{}'", text);
            return null;
        }
    }

    /**
     * IP(이닝) 파싱 — KBO 사이트에서 "1279 1/3", "1279⅓", "1279 2/3" 등 분수 형식 처리
     */
    private double parseInningsPitched(Element cell) {
        try {
            String text = cell.text().trim().replace(",", "");
            if (text.isEmpty() || "-".equals(text)) return 0.0;

            text = text.replace("⅓", " 1/3").replace("⅔", " 2/3");

            if (text.contains("/")) {
                String[] parts = text.split("\\s+");
                double whole = parts.length > 1 ? Double.parseDouble(parts[0]) : 0;
                String fracPart = parts.length > 1 ? parts[1] : parts[0];
                String[] frac = fracPart.split("/");
                double fraction = Double.parseDouble(frac[0]) / Double.parseDouble(frac[1]);
                return whole + fraction;
            }

            return Double.parseDouble(text);
        } catch (Exception e) {
            log.warn("IP 파싱 실패: '{}' → 0.0", cell.text());
            return 0.0;
        }
    }
}
