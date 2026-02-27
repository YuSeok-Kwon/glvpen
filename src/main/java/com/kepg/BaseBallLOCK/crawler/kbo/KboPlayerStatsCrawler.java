package com.kepg.BaseBallLOCK.crawler.kbo;

import static com.kepg.BaseBallLOCK.crawler.kbo.util.KboConstants.*;
import static com.kepg.BaseBallLOCK.crawler.kbo.util.PlaywrightFactory.*;

import java.time.LocalDate;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import com.kepg.BaseBallLOCK.crawler.util.TeamMappingConstants;
import com.kepg.BaseBallLOCK.modules.player.domain.Player;
import com.kepg.BaseBallLOCK.modules.player.dto.PlayerDTO;
import com.kepg.BaseBallLOCK.modules.player.service.PlayerService;
import com.kepg.BaseBallLOCK.modules.player.stats.service.BatterStatsService;
import com.kepg.BaseBallLOCK.modules.player.stats.service.DefenseStatsService;
import com.kepg.BaseBallLOCK.modules.player.stats.service.PitcherStatsService;
import com.kepg.BaseBallLOCK.modules.player.stats.service.RunnerStatsService;
import com.kepg.BaseBallLOCK.modules.player.stats.statsDto.BatterStatsDTO;
import com.kepg.BaseBallLOCK.modules.player.stats.statsDto.DefenseStatsDTO;
import com.kepg.BaseBallLOCK.modules.player.stats.statsDto.PitcherStatsDTO;
import com.kepg.BaseBallLOCK.modules.player.stats.statsDto.RunnerStatsDTO;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * KBO 공식 사이트 선수 기록 크롤러 (Phase 1 확장판)
 *
 * 타자: Basic1 + Basic2 + Detail1 (3페이지 × 팀×시리즈×상황)
 * 투수: Basic1 + Basic2 + Detail1 + Detail2 (4페이지 × 팀×시리즈×상황)
 * 수비: Basic (1페이지 × 팀×시리즈)
 * 주루: Basic (1페이지 × 팀×시리즈)
 *
 * 드롭다운 조합:
 * - 타자/투수: 10팀 × 6시리즈 × (정규시즌→14상황, 비정규→상황없음)
 * - 수비/주루: 10팀 × 6시리즈 (상황 없음)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KboPlayerStatsCrawler {

    private final PlayerService playerService;
    private final BatterStatsService batterStatsService;
    private final PitcherStatsService pitcherStatsService;
    private final DefenseStatsService defenseStatsService;
    private final RunnerStatsService runnerStatsService;

    private static final int CURRENT_YEAR = LocalDate.now().getYear();

    // ==================== 공개 메서드 ====================

    /**
     * 현재 시즌 모든 선수 데이터 크롤링
     */
    public void crawlAllPlayerData() {
        crawlAllPlayerData(CURRENT_YEAR);
    }

    /**
     * 지정 시즌 모든 선수 데이터 크롤링 (타자+투수+수비+주루)
     */
    public void crawlAllPlayerData(int season) {
        log.info("=== KBO 선수 데이터 크롤링 시작 ({}시즌) ===", season);
        crawlBatterStats(season);
        crawlPitcherStats(season);
        crawlDefenseStats(season);
        crawlRunnerStats(season);
        log.info("=== KBO 선수 데이터 크롤링 완료 ({}시즌) ===", season);
    }

    /**
     * 다시즌 배치 크롤링 (역순: 최신→과거)
     */
    public void crawlMultiSeasons(int startYear, int endYear) {
        log.info("=== KBO 선수 다시즌 크롤링 시작: {}→{} (역순) ===", endYear, startYear);
        for (int year = endYear; year >= startYear; year--) {
            log.info("--- {}시즌 선수 데이터 크롤링 ---", year);
            crawlAllPlayerData(year);
        }
        log.info("=== KBO 선수 다시즌 크롤링 완료 ===");
    }

    // ==================== 타자 기록 크롤링 ====================

    /**
     * 타자 통계 크롤링 (Basic1 + Basic2 + Detail1)
     * 각 페이지마다 팀×시리즈×상황 조합을 순회
     */
    public void crawlBatterStats(int season) {
        log.info("[KBO 타자] {}시즌 크롤링 시작 (3페이지)", season);

        for (int pageIdx = 0; pageIdx < BATTER_PAGE_URLS.length; pageIdx++) {
            String url = BATTER_PAGE_URLS[pageIdx];
            String[] categories = BATTER_PAGE_CATS[pageIdx];
            String pageLabel = "타자페이지" + (pageIdx + 1);

            log.info("[{}] URL: {}, 카테고리 {}개", pageLabel, url, categories.length);
            crawlPlayerStatsPage(season, url, categories, pageLabel, true);
        }

        log.info("[KBO 타자] {}시즌 크롤링 완료", season);
    }

    // ==================== 투수 기록 크롤링 ====================

    /**
     * 투수 통계 크롤링 (Basic1 + Basic2 + Detail1 + Detail2)
     */
    public void crawlPitcherStats(int season) {
        log.info("[KBO 투수] {}시즌 크롤링 시작 (4페이지)", season);

        for (int pageIdx = 0; pageIdx < PITCHER_PAGE_URLS.length; pageIdx++) {
            String url = PITCHER_PAGE_URLS[pageIdx];
            String[] categories = PITCHER_PAGE_CATS[pageIdx];
            String pageLabel = "투수페이지" + (pageIdx + 1);

            log.info("[{}] URL: {}, 카테고리 {}개", pageLabel, url, categories.length);
            crawlPlayerStatsPage(season, url, categories, pageLabel, false);
        }

        log.info("[KBO 투수] {}시즌 크롤링 완료", season);
    }

    // ==================== 수비 기록 크롤링 ====================

    /**
     * 수비 통계 크롤링 (1페이지 × 팀×시리즈, 상황 없음)
     */
    public void crawlDefenseStats(int season) {
        log.info("[KBO 수비] {}시즌 크롤링 시작", season);

        Playwright pw = null;
        Browser browser = null;
        Page page = null;

        try {
            pw = createPlaywright();
            browser = createBrowser(pw);
            page = createPage(browser);

            navigateAndWait(page, PLAYER_DEFENSE_URL);
            selectSeasonDropdown(page, season);

            int totalRows = 0;

            for (String[] teamEntry : TEAM_DROPDOWN_VALUES) {
                String teamValue = teamEntry[0];
                String teamLabel = teamEntry[1];

                if (!selectDropdown(page, SEL_TEAM_DROPDOWN, teamValue)) {
                    log.warn("[수비] 팀 선택 실패: {}", teamLabel);
                    continue;
                }

                for (String[] seriesEntry : SERIES_LIST) {
                    String seriesLabel = seriesEntry[0];
                    String seriesValue = seriesEntry[1];
                    String seriesCode = seriesValueToCode(seriesValue);

                    if (!selectDropdown(page, SEL_SERIES_DROPDOWN_RECORD, seriesValue)) {
                        continue;
                    }

                    log.debug("[수비] {}팀 / {} 크롤링", teamLabel, seriesLabel);
                    totalRows += paginateAndCollectDefense(page, season, teamLabel, seriesCode);
                }
            }

            log.info("[KBO 수비] {}시즌 완료: {}건 처리", season, totalRows);

        } catch (Exception e) {
            log.error("[KBO 수비] {}시즌 크롤링 실패", season, e);
        } finally {
            cleanup(page, browser, pw);
        }
    }

    // ==================== 주루 기록 크롤링 ====================

    /**
     * 주루 통계 크롤링 (1페이지 × 팀×시리즈, 상황 없음)
     */
    public void crawlRunnerStats(int season) {
        log.info("[KBO 주루] {}시즌 크롤링 시작", season);

        Playwright pw = null;
        Browser browser = null;
        Page page = null;

        try {
            pw = createPlaywright();
            browser = createBrowser(pw);
            page = createPage(browser);

            navigateAndWait(page, PLAYER_RUNNER_URL);
            selectSeasonDropdown(page, season);

            int totalRows = 0;

            for (String[] teamEntry : TEAM_DROPDOWN_VALUES) {
                String teamValue = teamEntry[0];
                String teamLabel = teamEntry[1];

                if (!selectDropdown(page, SEL_TEAM_DROPDOWN, teamValue)) {
                    log.warn("[주루] 팀 선택 실패: {}", teamLabel);
                    continue;
                }

                for (String[] seriesEntry : SERIES_LIST) {
                    String seriesLabel = seriesEntry[0];
                    String seriesValue = seriesEntry[1];
                    String seriesCode = seriesValueToCode(seriesValue);

                    if (!selectDropdown(page, SEL_SERIES_DROPDOWN_RECORD, seriesValue)) {
                        continue;
                    }

                    log.debug("[주루] {}팀 / {} 크롤링", teamLabel, seriesLabel);
                    totalRows += paginateAndCollectRunner(page, season, teamLabel, seriesCode);
                }
            }

            log.info("[KBO 주루] {}시즌 완료: {}건 처리", season, totalRows);

        } catch (Exception e) {
            log.error("[KBO 주루] {}시즌 크롤링 실패", season, e);
        } finally {
            cleanup(page, browser, pw);
        }
    }

    // ==================== 타자/투수 공통 크롤링 로직 ====================

    /**
     * 타자/투수 공통: 하나의 URL 페이지에 대해 팀×시리즈×상황 전체 조합 순회
     *
     * @param isBatter true=타자, false=투수
     */
    private void crawlPlayerStatsPage(int season, String url, String[] categories,
                                       String pageLabel, boolean isBatter) {
        Playwright pw = null;
        Browser browser = null;
        Page page = null;

        try {
            pw = createPlaywright();
            browser = createBrowser(pw);
            page = createPage(browser);

            navigateAndWait(page, url);
            selectSeasonDropdown(page, season);

            int totalRows = 0;

            for (String[] teamEntry : TEAM_DROPDOWN_VALUES) {
                String teamValue = teamEntry[0];
                String teamLabel = teamEntry[1];

                if (!selectDropdown(page, SEL_TEAM_DROPDOWN, teamValue)) {
                    log.warn("[{}] 팀 선택 실패: {}", pageLabel, teamLabel);
                    continue;
                }

                for (String[] seriesEntry : SERIES_LIST) {
                    String seriesLabel = seriesEntry[0];
                    String seriesValue = seriesEntry[1];
                    String seriesCode = seriesValueToCode(seriesValue);
                    boolean isRegularSeason = "0".equals(seriesCode);

                    if (!selectDropdown(page, SEL_SERIES_DROPDOWN_RECORD, seriesValue)) {
                        continue;
                    }

                    if (isRegularSeason) {
                        // 정규시즌: 상황별 드롭다운 순회
                        for (String[] sitEntry : SITUATION_CODES) {
                            String sitLabel = sitEntry[0];
                            String sitCode = sitEntry[1];

                            if (!sitCode.isEmpty()) {
                                // 상황별 필터 선택
                                if (!selectDropdown(page, SEL_SITUATION_DROPDOWN, sitCode)) {
                                    continue;
                                }
                            } else {
                                // "전체" → 상황 드롭다운을 빈값으로 리셋
                                selectDropdown(page, SEL_SITUATION_DROPDOWN, "");
                            }

                            boolean hasSitCol = !sitCode.isEmpty();
                            log.debug("[{}] {}팀 / {} / {} 크롤링", pageLabel, teamLabel, seriesLabel, sitLabel);

                            totalRows += paginateAndCollectStats(
                                    page, season, categories, hasSitCol,
                                    seriesCode, sitCode, isBatter);
                        }
                    } else {
                        // 비정규시즌: 상황 드롭다운 없음
                        log.debug("[{}] {}팀 / {} 크롤링", pageLabel, teamLabel, seriesLabel);

                        totalRows += paginateAndCollectStats(
                                page, season, categories, false,
                                seriesCode, "", isBatter);
                    }
                }
            }

            log.info("[{}] {}시즌 완료: {}건 처리", pageLabel, season, totalRows);

        } catch (Exception e) {
            log.error("[{}] {}시즌 크롤링 실패", pageLabel, season, e);
        } finally {
            cleanup(page, browser, pw);
        }
    }

    // ==================== 페이지네이션 + 데이터 수집 ====================

    /**
     * 타자/투수 공통: 현재 드롭다운 상태에서 페이지네이션을 순회하며 데이터 수집
     *
     * @param hasSitCol 상황 컬럼 존재 여부 (true면 dataOffset=4, false면 3)
     * @param seriesCode DB 저장용 시리즈 코드
     * @param sitCode 상황 코드 (빈 문자열이면 전체)
     * @param isBatter true=타자, false=투수
     * @return 처리된 행 수
     */
    private int paginateAndCollectStats(Page page, int season, String[] categories,
                                         boolean hasSitCol, String seriesCode,
                                         String sitCode, boolean isBatter) {
        int dataOffset = hasSitCol ? 4 : 3;
        int totalRows = 0;
        int pageNum = 1;

        while (true) {
            Document doc = getJsoupDocument(page);
            Elements rows = findDataRows(doc);

            if (rows.isEmpty()) break;

            for (Element row : rows) {
                try {
                    Elements cells = row.select("td");
                    if (cells.size() < dataOffset + 1) continue;

                    // 헤더 행 스킵
                    if (row.select("th").size() > 0) continue;
                    String firstCell = cells.get(0).text().trim();
                    if (firstCell.equals("순위") || firstCell.isEmpty()) continue;

                    int ranking = parseInt(cells.get(0));
                    String playerName = cells.get(1).text().trim();
                    String teamName = cells.get(2).text().trim();
                    String sitValue = hasSitCol ? cells.get(3).text().trim() : "";

                    if (playerName.isEmpty() || teamName.isEmpty()) continue;

                    Integer teamId = resolveTeamId(teamName);
                    if (teamId == null) continue;

                    Player player = playerService.findOrCreatePlayer(
                            PlayerDTO.builder().name(playerName).teamId(teamId).build());
                    int playerId = player.getId();

                    // 카테고리별 저장
                    for (int i = 0; i < categories.length; i++) {
                        int cellIdx = dataOffset + i;
                        if (cellIdx >= cells.size()) break;

                        String cat = categories[i];
                        double value = parseDouble(cells.get(cellIdx));

                        if (isBatter) {
                            batterStatsService.saveBatterStats(BatterStatsDTO.builder()
                                    .playerId(playerId)
                                    .season(season)
                                    .category(cat)
                                    .value(value)
                                    .ranking(i == 0 ? ranking : null)
                                    .series(seriesCode)
                                    .situationType(sitCode)
                                    .situationValue(sitValue)
                                    .build());
                        } else {
                            pitcherStatsService.savePitcherStats(PitcherStatsDTO.builder()
                                    .playerId(playerId)
                                    .season(season)
                                    .category(cat)
                                    .value(value)
                                    .ranking(i == 0 ? ranking : null)
                                    .series(seriesCode)
                                    .situationType(sitCode)
                                    .situationValue(sitValue)
                                    .build());
                        }
                    }

                    totalRows++;
                } catch (Exception e) {
                    log.debug("행 처리 스킵: {}", e.getMessage());
                }
            }

            if (!goToNextPage(page, ++pageNum)) break;
        }

        return totalRows;
    }

    /**
     * 수비 데이터 수집: 순위|선수명|팀명|POS|G|GS|IP|E|PKO|PO|A|DP|FPCT|PB|SB|CS|CS%
     * POS는 카테고리가 아니라 position 필드로 저장
     */
    private int paginateAndCollectDefense(Page page, int season,
                                           String teamLabel, String seriesCode) {
        int totalRows = 0;
        int pageNum = 1;

        while (true) {
            Document doc = getJsoupDocument(page);
            Elements rows = findDataRows(doc);

            if (rows.isEmpty()) break;

            for (Element row : rows) {
                try {
                    Elements cells = row.select("td");
                    if (cells.size() < 17) continue;

                    if (row.select("th").size() > 0) continue;
                    String firstCell = cells.get(0).text().trim();
                    if (firstCell.equals("순위") || firstCell.isEmpty()) continue;

                    int ranking = parseInt(cells.get(0));
                    String playerName = cells.get(1).text().trim();
                    String teamName = cells.get(2).text().trim();

                    if (playerName.isEmpty() || teamName.isEmpty()) continue;

                    Integer teamId = resolveTeamId(teamName);
                    if (teamId == null) continue;

                    Player player = playerService.findOrCreatePlayer(
                            PlayerDTO.builder().name(playerName).teamId(teamId).build());
                    int playerId = player.getId();

                    // cells[3] = POS (수비 포지션)
                    String defPosition = cells.get(3).text().trim();

                    // DEFENSE_CATS 순서: POS, G, GS, IP, E, PKO, PO, A, DP, FPCT, PB, SB, CS, CS%
                    // POS는 position 필드로 저장, 나머지는 category+value로 저장
                    for (int i = 0; i < DEFENSE_CATS.length; i++) {
                        String cat = DEFENSE_CATS[i];
                        int cellIdx = 3 + i; // POS부터 시작

                        if (cellIdx >= cells.size()) break;

                        if ("POS".equals(cat)) {
                            // POS는 별도 저장하지 않고 position으로 사용
                            continue;
                        }

                        double value = parseDouble(cells.get(cellIdx));
                        defenseStatsService.saveDefenseStats(DefenseStatsDTO.builder()
                                .playerId(playerId)
                                .season(season)
                                .series(seriesCode)
                                .position(defPosition)
                                .category(cat)
                                .value(value)
                                .ranking(i == 1 ? ranking : null) // G에 순위 부여
                                .build());
                    }

                    totalRows++;
                } catch (Exception e) {
                    log.debug("수비 행 처리 스킵: {}", e.getMessage());
                }
            }

            if (!goToNextPage(page, ++pageNum)) break;
        }

        return totalRows;
    }

    /**
     * 주루 데이터 수집: 순위|선수명|팀명|G|SBA|SB|CS|SB%|OOB|PKO
     */
    private int paginateAndCollectRunner(Page page, int season,
                                          String teamLabel, String seriesCode) {
        int totalRows = 0;
        int pageNum = 1;

        while (true) {
            Document doc = getJsoupDocument(page);
            Elements rows = findDataRows(doc);

            if (rows.isEmpty()) break;

            for (Element row : rows) {
                try {
                    Elements cells = row.select("td");
                    if (cells.size() < 10) continue;

                    if (row.select("th").size() > 0) continue;
                    String firstCell = cells.get(0).text().trim();
                    if (firstCell.equals("순위") || firstCell.isEmpty()) continue;

                    int ranking = parseInt(cells.get(0));
                    String playerName = cells.get(1).text().trim();
                    String teamName = cells.get(2).text().trim();

                    if (playerName.isEmpty() || teamName.isEmpty()) continue;

                    Integer teamId = resolveTeamId(teamName);
                    if (teamId == null) continue;

                    Player player = playerService.findOrCreatePlayer(
                            PlayerDTO.builder().name(playerName).teamId(teamId).build());
                    int playerId = player.getId();

                    // RUNNER_CATS: G, SBA, SB, CS, SB%, OOB, PKO (dataOffset=3)
                    for (int i = 0; i < RUNNER_CATS.length; i++) {
                        String cat = RUNNER_CATS[i];
                        int cellIdx = 3 + i;

                        if (cellIdx >= cells.size()) break;

                        double value = parseDouble(cells.get(cellIdx));
                        runnerStatsService.saveRunnerStats(RunnerStatsDTO.builder()
                                .playerId(playerId)
                                .season(season)
                                .series(seriesCode)
                                .category(cat)
                                .value(value)
                                .ranking(i == 0 ? ranking : null) // G에 순위 부여
                                .build());
                    }

                    totalRows++;
                } catch (Exception e) {
                    log.debug("주루 행 처리 스킵: {}", e.getMessage());
                }
            }

            if (!goToNextPage(page, ++pageNum)) break;
        }

        return totalRows;
    }

    // ==================== 드롭다운 선택 헬퍼 ====================

    /**
     * 시즌 드롭다운을 동적으로 찾아서 선택
     */
    private void selectSeasonDropdown(Page page, int season) {
        String selector = findSeasonDropdownSelector(page);
        if (selector == null) {
            log.error("시즌 드롭다운을 찾을 수 없습니다. 기본 페이지 데이터를 사용합니다.");
            return;
        }

        String currentValue = (String) page.evaluate(
                "(sel) => document.querySelector(sel)?.value", selector);

        if (String.valueOf(season).equals(currentValue)) {
            log.debug("이미 {}시즌이 선택되어 있음", season);
            return;
        }

        selectAndWaitForPostBack(page, selector, String.valueOf(season));
    }

    /**
     * 범용 드롭다운 선택 (팀/시리즈/상황)
     * @return 선택 성공 여부
     */
    private boolean selectDropdown(Page page, String selector, String value) {
        try {
            // 드롭다운 존재 여부 확인
            if (page.locator(selector).count() == 0) {
                log.debug("드롭다운 없음: {}", selector);
                return false;
            }
            return selectAndWaitForPostBack(page, selector, value);
        } catch (Exception e) {
            log.debug("드롭다운 선택 실패: {} → {} ({})", selector, value, e.getMessage());
            return false;
        }
    }

    // ==================== 데이터 행 탐색 ====================

    private Elements findDataRows(Document doc) {
        Elements rows = doc.select("table.tData tbody tr");
        if (!rows.isEmpty()) return rows;

        rows = doc.select("#cphContents_cphContents_cphContents_udpContent table tbody tr");
        if (!rows.isEmpty()) return rows;

        rows = doc.select("table tbody tr");
        return rows;
    }

    // ==================== 페이지네이션 ====================

    private boolean goToNextPage(Page page, int pageNum) {
        try {
            String pageLinkSelector = String.format("a:text-is('%d')", pageNum);

            if (page.locator(".paging " + pageLinkSelector).count() > 0) {
                page.locator(".paging " + pageLinkSelector).click();
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
                return true;
            }

            if (page.locator(pageLinkSelector).count() > 0) {
                page.locator(pageLinkSelector).first().click();
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
                return true;
            }

            return false;
        } catch (Exception e) {
            log.debug("다음 페이지({}) 이동 실패: {}", pageNum, e.getMessage());
            return false;
        }
    }

    // ==================== 팀 ID 매핑 ====================

    private Integer resolveTeamId(String teamName) {
        Integer id = TeamMappingConstants.getTeamId(teamName);
        if (id == null) {
            id = getTeamIdByKboName(teamName);
        }
        return id;
    }

    // ==================== 파싱 유틸 ====================

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
