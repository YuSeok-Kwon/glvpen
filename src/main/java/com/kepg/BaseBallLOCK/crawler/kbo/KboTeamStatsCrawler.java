package com.kepg.BaseBallLOCK.crawler.kbo;

import static com.kepg.BaseBallLOCK.crawler.kbo.util.KboConstants.*;
import static com.kepg.BaseBallLOCK.crawler.kbo.util.PlaywrightFactory.*;

import java.time.LocalDate;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import com.kepg.BaseBallLOCK.crawler.util.TeamMappingConstants;
import com.kepg.BaseBallLOCK.modules.team.teamRanking.dto.TeamRankingDTO;
import com.kepg.BaseBallLOCK.modules.team.teamRanking.service.TeamRankingService;
import com.kepg.BaseBallLOCK.modules.team.teamStats.service.TeamStatsService;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

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
public class KboTeamStatsCrawler {

    private final TeamRankingService teamRankingService;
    private final TeamStatsService teamStatsService;

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
            crawlTeamBatterStats(year);
            crawlTeamPitcherStats(year);
            crawlTeamDefenseStats(year);
            crawlTeamRunnerStats(year);
        }
        log.info("=== KBO 팀 다시즌 크롤링 완료 ===");
    }

    // ==================== 팀 순위 ====================

    /**
     * 팀 순위 크롤링 (TeamRankDaily.aspx)
     * 컬럼: 순위(0)|팀명(1)|경기(2)|승(3)|패(4)|무(5)|승률(6)|게임차(7)|최근10경기(8)|연속(9)|홈(10)|방문(11)
     *
     * 주의: 이 페이지에는 시즌 드롭다운이 없고 현재 시즌 순위만 표시됨
     */
    public void crawlTeamRankings(int season) {
        log.info("[KBO 팀순위] {}시즌 크롤링 시작", season);

        Playwright pw = null;
        Browser browser = null;
        Page page = null;

        try {
            pw = createPlaywright();
            browser = createBrowser(pw);
            page = createPage(browser);

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

        } catch (Exception e) {
            log.error("[KBO 팀순위] {}시즌 크롤링 실패", season, e);
        } finally {
            cleanup(page, browser, pw);
        }
    }

    // ==================== 팀 타격 ====================

    /**
     * 팀 타격 통계 크롤링 (Basic1 + Basic2)
     *
     * Basic1 컬럼: 순위(0)|팀명(1)|AVG(2)|G(3)|PA(4)|AB(5)|R(6)|H(7)|2B(8)|3B(9)|HR(10)|TB(11)|RBI(12)|SAC(13)|SF(14)
     * Basic2 컬럼: 순위(0)|팀명(1)|AVG(2)|BB(3)|IBB(4)|HBP(5)|SO(6)|GDP(7)|SLG(8)|OBP(9)|OPS(10)|MH(11)|RISP(12)|PH-BA(13)
     */
    public void crawlTeamBatterStats(int season) {
        log.info("[KBO 팀타격] {}시즌 크롤링 시작 (Basic1 + Basic2)", season);

        Playwright pw = null;
        Browser browser = null;
        Page page = null;

        try {
            pw = createPlaywright();
            browser = createBrowser(pw);
            page = createPage(browser);

            // ---- Basic1: AVG, R, H, HR, RBI ----
            navigateAndWait(page, TEAM_BATTER_URL);

            if (!selectSeasonDropdown(page, season)) {
                log.warn("[KBO 팀타격] {}시즌 선택 불가 — 스킵", season);
                return;
            }

            Document doc = getJsoupDocument(page);
            Elements rows = findFirstTableRows(doc);

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

                    // Basic1 지표 저장
                    teamStatsService.saveOrUpdate(teamId, season, "AVG", parseDouble(cells.get(2)), ranking);
                    teamStatsService.saveOrUpdate(teamId, season, "G", parseDouble(cells.get(3)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "PA", parseDouble(cells.get(4)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "AB", parseDouble(cells.get(5)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "R", parseDouble(cells.get(6)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "H", parseDouble(cells.get(7)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "2B", parseDouble(cells.get(8)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "3B", parseDouble(cells.get(9)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "HR", parseDouble(cells.get(10)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "TB", parseDouble(cells.get(11)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "RBI", parseDouble(cells.get(12)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "SAC", parseDouble(cells.get(13)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "SF", parseDouble(cells.get(14)), null);

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

                    // Basic2 지표 저장
                    teamStatsService.saveOrUpdate(teamId, season, "BB", parseDouble(cells.get(3)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "IBB", parseDouble(cells.get(4)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "HBP", parseDouble(cells.get(5)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "SO", parseDouble(cells.get(6)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "GDP", parseDouble(cells.get(7)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "SLG", parseDouble(cells.get(8)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "OBP", parseDouble(cells.get(9)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "OPS", parseDouble(cells.get(10)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "RISP", parseDouble(cells.get(12)), null);

                    teamCount2++;
                } catch (Exception e) {
                    log.warn("팀타격 Basic2 행 처리 오류: {}", e.getMessage());
                }
            }
            log.info("[KBO 팀타격] Basic2 완료: {}팀", teamCount2);
            log.info("[KBO 팀타격] {}시즌 크롤링 완료", season);

        } catch (Exception e) {
            log.error("[KBO 팀타격] {}시즌 크롤링 실패", season, e);
        } finally {
            cleanup(page, browser, pw);
        }
    }

    // ==================== 팀 투수 ====================

    /**
     * 팀 투수 통계 크롤링 (Basic1 + Basic2)
     *
     * Basic1: 순위(0)|팀명(1)|ERA(2)|G(3)|W(4)|L(5)|SV(6)|HLD(7)|WPCT(8)|IP(9)|H(10)|HR(11)|BB(12)|HBP(13)|SO(14)|R(15)|ER(16)|WHIP(17)
     * Basic2: 순위(0)|팀명(1)|ERA(2)|CG(3)|SHO(4)|QS(5)|BSV(6)|TBF(7)|NP(8)|AVG(9)|2B(10)|3B(11)|SAC(12)|SF(13)|IBB(14)|WP(15)|BK(16)
     */
    public void crawlTeamPitcherStats(int season) {
        log.info("[KBO 팀투수] {}시즌 크롤링 시작 (Basic1 + Basic2)", season);

        Playwright pw = null;
        Browser browser = null;
        Page page = null;

        try {
            pw = createPlaywright();
            browser = createBrowser(pw);
            page = createPage(browser);

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

                    teamStatsService.saveOrUpdate(teamId, season, "ERA", parseDouble(cells.get(2)), ranking);
                    teamStatsService.saveOrUpdate(teamId, season, "투G", parseDouble(cells.get(3)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "W", parseDouble(cells.get(4)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "L", parseDouble(cells.get(5)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "SV", parseDouble(cells.get(6)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "HLD", parseDouble(cells.get(7)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "WPCT", parseDouble(cells.get(8)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "IP", parseInningsPitched(cells.get(9)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "피안타", parseDouble(cells.get(10)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "피홈런", parseDouble(cells.get(11)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "투BB", parseDouble(cells.get(12)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "투SO", parseDouble(cells.get(14)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "실점", parseDouble(cells.get(15)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "자책점", parseDouble(cells.get(16)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "WHIP", parseDouble(cells.get(17)), null);

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

                    teamStatsService.saveOrUpdate(teamId, season, "CG", parseDouble(cells.get(3)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "SHO", parseDouble(cells.get(4)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "QS", parseDouble(cells.get(5)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "BSV", parseDouble(cells.get(6)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "TBF", parseDouble(cells.get(7)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "NP", parseDouble(cells.get(8)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "피안타율", parseDouble(cells.get(9)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "투IBB", parseDouble(cells.get(14)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "WP", parseDouble(cells.get(15)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "BK", parseDouble(cells.get(16)), null);

                    teamCount2++;
                } catch (Exception e) {
                    log.warn("팀투수 Basic2 행 처리 오류: {}", e.getMessage());
                }
            }
            log.info("[KBO 팀투수] Basic2 완료: {}팀", teamCount2);
            log.info("[KBO 팀투수] {}시즌 크롤링 완료", season);

        } catch (Exception e) {
            log.error("[KBO 팀투수] {}시즌 크롤링 실패", season, e);
        } finally {
            cleanup(page, browser, pw);
        }
    }

    // ==================== 팀 수비 ====================

    /**
     * 팀 수비 통계 크롤링 (Team/Defense/Basic.aspx)
     * 컬럼: 순위(0)|팀명(1)|G(2)|E(3)|PKO(4)|PO(5)|A(6)|DP(7)|FPCT(8)|PB(9)|SB(10)|CS(11)|CS%(12)
     */
    public void crawlTeamDefenseStats(int season) {
        log.info("[KBO 팀수비] {}시즌 크롤링 시작", season);

        Playwright pw = null;
        Browser browser = null;
        Page page = null;

        try {
            pw = createPlaywright();
            browser = createBrowser(pw);
            page = createPage(browser);

            navigateAndWait(page, TEAM_DEFENSE_URL);

            if (!selectSeasonDropdown(page, season)) {
                log.warn("[KBO 팀수비] {}시즌 선택 불가 — 스킵", season);
                return;
            }

            Document doc = getJsoupDocument(page);
            Elements rows = findFirstTableRows(doc);

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

                    teamStatsService.saveOrUpdate(teamId, season, "E", parseDouble(cells.get(3)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "DP", parseDouble(cells.get(7)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "FPCT", parseDouble(cells.get(8)), ranking);
                    teamStatsService.saveOrUpdate(teamId, season, "PB", parseDouble(cells.get(9)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "도루허용", parseDouble(cells.get(10)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "도루저지", parseDouble(cells.get(11)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "CS%", parseDouble(cells.get(12)), null);

                    teamCount++;
                } catch (Exception e) {
                    log.warn("팀수비 행 처리 오류: {}", e.getMessage());
                }
            }

            log.info("[KBO 팀수비] {}시즌 완료: {}팀 처리", season, teamCount);

        } catch (Exception e) {
            log.error("[KBO 팀수비] {}시즌 크롤링 실패", season, e);
        } finally {
            cleanup(page, browser, pw);
        }
    }

    // ==================== 팀 주루 ====================

    /**
     * 팀 주루 통계 크롤링 (Team/Runner/Basic.aspx)
     * 컬럼: 순위(0)|팀명(1)|G(2)|SBA(3)|SB(4)|CS(5)|SB%(6)|OOB(7)|PKO(8)
     */
    public void crawlTeamRunnerStats(int season) {
        log.info("[KBO 팀주루] {}시즌 크롤링 시작", season);

        Playwright pw = null;
        Browser browser = null;
        Page page = null;

        try {
            pw = createPlaywright();
            browser = createBrowser(pw);
            page = createPage(browser);

            navigateAndWait(page, TEAM_RUNNER_URL);

            if (!selectSeasonDropdown(page, season)) {
                log.warn("[KBO 팀주루] {}시즌 선택 불가 — 스킵", season);
                return;
            }

            Document doc = getJsoupDocument(page);
            Elements rows = findFirstTableRows(doc);

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

                    teamStatsService.saveOrUpdate(teamId, season, "SBA", parseDouble(cells.get(3)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "SB", parseDouble(cells.get(4)), ranking);
                    teamStatsService.saveOrUpdate(teamId, season, "주루CS", parseDouble(cells.get(5)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "SB%", parseDouble(cells.get(6)), null);
                    teamStatsService.saveOrUpdate(teamId, season, "OOB", parseDouble(cells.get(7)), null);

                    teamCount++;
                } catch (Exception e) {
                    log.warn("팀주루 행 처리 오류: {}", e.getMessage());
                }
            }

            log.info("[KBO 팀주루] {}시즌 완료: {}팀 처리", season, teamCount);

        } catch (Exception e) {
            log.error("[KBO 팀주루] {}시즌 크롤링 실패", season, e);
        } finally {
            cleanup(page, browser, pw);
        }
    }

    // ==================== 공통 메서드 ====================

    /**
     * 시즌 드롭다운 동적 탐색 + ASP.NET PostBack 선택
     * @return true: 시즌 변경 성공 또는 이미 해당 시즌, false: 옵션이 없음
     */
    private boolean selectSeasonDropdown(Page page, int season) {
        String selector = findSeasonDropdownSelector(page);
        if (selector == null) {
            log.warn("시즌 드롭다운이 없는 페이지 — 현재 표시된 데이터를 사용합니다.");
            return true;
        }

        String currentValue = (String) page.evaluate(
                "(sel) => document.querySelector(sel)?.value", selector);

        if (String.valueOf(season).equals(currentValue)) {
            log.debug("이미 {}시즌이 선택되어 있음", season);
            return true;
        }

        return selectAndWaitForPostBack(page, selector, String.valueOf(season));
    }

    /**
     * 첫 번째 데이터 테이블의 tbody 행만 반환 (두 번째 테이블 제외)
     */
    private Elements findFirstTableRows(Document doc) {
        Element table = doc.selectFirst("table.tData");
        if (table != null) return table.select("tbody tr");

        table = doc.selectFirst("#cphContents_cphContents_cphContents_udpContent table");
        if (table != null) return table.select("tbody tr");

        table = doc.selectFirst("table");
        if (table != null) return table.select("tbody tr");

        return new Elements();
    }

    private Integer resolveTeamId(String teamName) {
        Integer id = TeamMappingConstants.getTeamId(teamName);
        if (id == null) id = getTeamIdByKboName(teamName);
        return id;
    }

    // ==================== 파싱 유틸 ====================

    /**
     * IP(이닝) 파싱 — KBO 사이트에서 "1279 1/3", "1279⅓", "1279 2/3" 등 분수 형식 처리
     */
    private double parseInningsPitched(Element cell) {
        try {
            String text = cell.text().trim().replace(",", "");
            if (text.isEmpty() || "-".equals(text)) return 0.0;

            // 유니코드 분수 문자 치환
            text = text.replace("⅓", " 1/3").replace("⅔", " 2/3");

            // "1279 1/3" 형태 처리
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

    private double parseDouble(Element cell) {
        try {
            String text = cell.text().trim().replace(",", "");
            if (text.isEmpty() || "-".equals(text)) return 0.0;
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private int parseInt(Element cell) {
        try {
            String text = cell.text().trim().replace(",", "");
            if (text.isEmpty() || "-".equals(text)) return 0;
            return (int) Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
