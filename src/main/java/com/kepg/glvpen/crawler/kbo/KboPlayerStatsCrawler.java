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
import com.kepg.glvpen.modules.player.domain.Player;
import com.kepg.glvpen.modules.player.dto.PlayerDTO;
import com.kepg.glvpen.modules.player.service.PlayerService;
import com.kepg.glvpen.modules.player.stats.service.BatterStatsService;
import com.kepg.glvpen.modules.player.stats.service.DefenseStatsService;
import com.kepg.glvpen.modules.player.stats.service.PitcherStatsService;
import com.kepg.glvpen.modules.player.stats.service.RunnerStatsService;
import com.kepg.glvpen.modules.player.stats.statsDto.BatterStatsDTO;
import com.kepg.glvpen.modules.player.stats.statsDto.DefenseStatsDTO;
import com.kepg.glvpen.modules.player.stats.statsDto.PitcherStatsDTO;
import com.kepg.glvpen.modules.player.stats.statsDto.RunnerStatsDTO;
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
 * 드롭다운 option 값은 하드코딩하지 않고 페이지에서 동적으로 읽어옴
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KboPlayerStatsCrawler extends AbstractPlaywrightCrawler {

    private final PlayerService playerService;
    private final BatterStatsService batterStatsService;
    private final PitcherStatsService pitcherStatsService;
    private final DefenseStatsService defenseStatsService;
    private final RunnerStatsService runnerStatsService;

    private static final int CURRENT_YEAR = LocalDate.now().getYear();

    // ==================== 공개 메서드 ====================

    public void crawlAllPlayerData() {
        crawlAllPlayerData(CURRENT_YEAR);
    }

    public void crawlAllPlayerData(int season) {
        log.info("=== KBO 선수 데이터 크롤링 시작 ({}시즌) ===", season);
        crawlBatterStats(season);
        crawlPitcherStats(season);
        crawlDefenseStats(season);
        crawlRunnerStats(season);
        log.info("=== KBO 선수 데이터 크롤링 완료 ({}시즌) ===", season);
    }

    public void crawlMultiSeasons(int startYear, int endYear) {
        log.info("=== KBO 선수 다시즌 크롤링 시작: {}→{} (역순) ===", endYear, startYear);
        for (int year = endYear; year >= startYear; year--) {
            log.info("--- {}시즌 선수 데이터 크롤링 ---", year);
            crawlAllPlayerData(year);
        }
        log.info("=== KBO 선수 다시즌 크롤링 완료 ===");
    }

    // ==================== 타자 기록 크롤링 ====================

    public void crawlBatterStats(int season) {
        log.info("[KBO 타자] {}시즌 크롤링 시작 ({}페이지)", season, BATTER_PAGE_URLS.length);
        for (int pageIdx = 0; pageIdx < BATTER_PAGE_URLS.length; pageIdx++) {
            String url = BATTER_PAGE_URLS[pageIdx];
            String[] categories = BATTER_PAGE_CATS[pageIdx];
            String pageLabel = "타자페이지" + (pageIdx + 1);
            log.info("[{}] URL: {}, 카테고리 {}개", pageLabel, url, categories.length);
            crawlPlayerStatsPage(season, url, categories, pageLabel, true);
        }
        log.info("[KBO 타자] {}시즌 크롤링 완료", season);
    }

    /**
     * Basic2 페이지만 크롤링 (BB, SO, HBP, SLG, OBP, OPS, RISP, PH-BA)
     * 세이버메트릭스 핵심 입력값 수집용
     */
    public void crawlBatterBasic2(int season) {
        log.info("[KBO 타자Basic2] {}시즌 크롤링 시작", season);
        crawlPlayerStatsPage(season, BATTER_STATS_URL2, BATTER_BASIC2_CATS, "타자Basic2", true);
        log.info("[KBO 타자Basic2] {}시즌 크롤링 완료", season);
    }

    // ==================== 투수 기록 크롤링 ====================

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

            List<String[]> teamOptions = getDropdownOptions(page, SEL_TEAM_DROPDOWN);
            teamOptions = filterOutAllOption(teamOptions);
            log.info("[수비] 팀 {}개 감지", teamOptions.size());

            int totalRows = 0;

            for (int teamIdx = 0; teamIdx < teamOptions.size(); teamIdx++) {
                String teamValue = teamOptions.get(teamIdx)[0];
                String teamLabel = teamOptions.get(teamIdx)[1];

                try {
                    navigateAndWait(page, PLAYER_DEFENSE_URL);
                    selectSeasonDropdown(page, season);

                    if (!selectDropdown(page, SEL_TEAM_DROPDOWN, teamValue)) continue;

                    List<String[]> seriesOptions = getDropdownOptions(page, SEL_SERIES_DROPDOWN_RECORD);
                    log.info("[수비] {}/{} {} - 시리즈 {}개",
                            teamIdx + 1, teamOptions.size(), teamLabel, seriesOptions.size());

                    for (String[] seriesOpt : seriesOptions) {
                        String seriesValue = seriesOpt[0];
                        String seriesLabel = seriesOpt[1];

                        if (!selectDropdown(page, SEL_SERIES_DROPDOWN_RECORD, seriesValue)) continue;

                        if (!ensureTeamSelected(page, PLAYER_DEFENSE_URL, season, teamValue, seriesValue, null)) continue;

                        log.debug("[수비] {} / {} 크롤링", teamLabel, seriesLabel);
                        totalRows += paginateAndCollectDefense(page, season, teamLabel, seriesValue);
                    }
                } catch (Exception e) {
                    log.warn("[수비] {} 크롤링 중 오류, 페이지 재생성: {}", teamLabel, e.getMessage());
                    page = recreatePage(page, browser);
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

            List<String[]> teamOptions = getDropdownOptions(page, SEL_TEAM_DROPDOWN);
            teamOptions = filterOutAllOption(teamOptions);
            log.info("[주루] 팀 {}개 감지", teamOptions.size());

            int totalRows = 0;

            for (int teamIdx = 0; teamIdx < teamOptions.size(); teamIdx++) {
                String teamValue = teamOptions.get(teamIdx)[0];
                String teamLabel = teamOptions.get(teamIdx)[1];

                try {
                    navigateAndWait(page, PLAYER_RUNNER_URL);
                    selectSeasonDropdown(page, season);

                    if (!selectDropdown(page, SEL_TEAM_DROPDOWN, teamValue)) continue;

                    List<String[]> seriesOptions = getDropdownOptions(page, SEL_SERIES_DROPDOWN_RECORD);
                    log.info("[주루] {}/{} {} - 시리즈 {}개",
                            teamIdx + 1, teamOptions.size(), teamLabel, seriesOptions.size());

                    for (String[] seriesOpt : seriesOptions) {
                        String seriesValue = seriesOpt[0];
                        String seriesLabel = seriesOpt[1];

                        if (!selectDropdown(page, SEL_SERIES_DROPDOWN_RECORD, seriesValue)) continue;

                        if (!ensureTeamSelected(page, PLAYER_RUNNER_URL, season, teamValue, seriesValue, null)) continue;

                        log.debug("[주루] {} / {} 크롤링", teamLabel, seriesLabel);
                        totalRows += paginateAndCollectRunner(page, season, teamLabel, seriesValue);
                    }
                } catch (Exception e) {
                    log.warn("[주루] {} 크롤링 중 오류, 페이지 재생성: {}", teamLabel, e.getMessage());
                    page = recreatePage(page, browser);
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
     * 하나의 URL 페이지에 대해 팀×시리즈×상황 전체 조합 순회
     * 드롭다운 option을 페이지에서 동적으로 읽어옴
     *
     * 핵심: 팀마다 URL을 새로 네비게이션하여 ASP.NET PostBack 누적 손상 방지
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

            List<String[]> teamOptions = getDropdownOptions(page, SEL_TEAM_DROPDOWN);
            teamOptions = filterOutAllOption(teamOptions);
            log.info("[{}] 팀 {}개 감지", pageLabel, teamOptions.size());

            int totalRows = 0;

            for (int teamIdx = 0; teamIdx < teamOptions.size(); teamIdx++) {
                String teamValue = teamOptions.get(teamIdx)[0];
                String teamLabel = teamOptions.get(teamIdx)[1];

                try {
                    navigateAndWait(page, url);
                    selectSeasonDropdown(page, season);

                    if (!selectDropdown(page, SEL_TEAM_DROPDOWN, teamValue)) {
                        log.warn("[{}] 팀 선택 실패: {} (value={})", pageLabel, teamLabel, teamValue);
                        continue;
                    }

                    List<String[]> seriesOptions = getDropdownOptions(page, SEL_SERIES_DROPDOWN_RECORD);
                    log.info("[{}] {}/{} {} - 시리즈 {}개", pageLabel,
                            teamIdx + 1, teamOptions.size(), teamLabel, seriesOptions.size());

                    for (String[] seriesOpt : seriesOptions) {
                        String seriesValue = seriesOpt[0];
                        String seriesLabel = seriesOpt[1];
                        boolean isRegularSeason = "0".equals(seriesValue);

                        if (!selectDropdown(page, SEL_SERIES_DROPDOWN_RECORD, seriesValue)) continue;

                        if (!ensureTeamSelected(page, url, season, teamValue, seriesValue, null)) continue;

                        String[] effectiveCats = categories;
                        if (isBatter && url.contains("BasicOld")) {
                            if (isRegularSeason) {
                                effectiveCats = BATTER_REGULAR_CATS;
                            } else {
                                effectiveCats = BATTER_BASICOLD_CATS;
                            }
                        }

                        if (isRegularSeason) {
                            List<String[]> sitOptions = getDropdownOptions(page, SEL_SITUATION_DROPDOWN);

                            if (sitOptions.isEmpty()) {
                                totalRows += paginateAndCollectStats(
                                        page, season, effectiveCats, false, seriesValue, "", isBatter);
                            } else {
                                for (String[] sitOpt : sitOptions) {
                                    String sitValue = sitOpt[0];
                                    String sitLabel = sitOpt[1];
                                    boolean hasSitCol = !sitValue.isEmpty();

                                    selectDropdown(page, SEL_SITUATION_DROPDOWN, sitValue);

                                    if (!ensureTeamSelected(page, url, season, teamValue, seriesValue, sitValue)) continue;

                                    log.debug("[{}] {} / {} / {} 크롤링",
                                            pageLabel, teamLabel, seriesLabel, sitLabel);
                                    totalRows += paginateAndCollectStats(
                                            page, season, effectiveCats, hasSitCol,
                                            seriesValue, sitValue, isBatter);
                                }
                            }
                        } else {
                            log.debug("[{}] {} / {} 크롤링", pageLabel, teamLabel, seriesLabel);
                            totalRows += paginateAndCollectStats(
                                    page, season, effectiveCats, false,
                                    seriesValue, "", isBatter);
                        }
                    }
                } catch (Exception e) {
                    log.warn("[{}] {} 크롤링 중 오류, 페이지 재생성: {}", pageLabel, teamLabel, e.getMessage());
                    page = recreatePage(page, browser);
                }
            }

            log.info("[{}] {}시즌 완료: {}건 처리", pageLabel, season, totalRows);
        } catch (Exception e) {
            log.error("[{}] {}시즌 크롤링 실패", pageLabel, season, e);
        } finally {
            cleanup(page, browser, pw);
        }
    }

    // ==================== 페이지네이션 + 데이터 수집 (배치) ====================

    private int paginateAndCollectStats(Page page, int season, String[] categories,
                                         boolean hasSitCol, String seriesCode,
                                         String sitCode, boolean isBatter) {
        int dataOffset = hasSitCol ? 4 : 3;
        int totalRows = 0;
        int pageNum = 1;

        List<BatterStatsDTO> batterBatch = isBatter ? new ArrayList<>() : null;
        List<PitcherStatsDTO> pitcherBatch = !isBatter ? new ArrayList<>() : null;

        while (true) {
            Document doc = getJsoupDocument(page);
            Elements rows = findDataRows(doc);
            if (rows.isEmpty()) break;

            for (Element row : rows) {
                try {
                    Elements cells = row.select("td");
                    if (cells.size() < dataOffset + 1) continue;
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

                    for (int i = 0; i < categories.length; i++) {
                        int cellIdx = dataOffset + i;
                        if (cellIdx >= cells.size()) break;

                        String cat = categories[i];
                        double value = parseDouble(cells.get(cellIdx));

                        if (isBatter) {
                            batterBatch.add(BatterStatsDTO.builder()
                                    .playerId(playerId).season(season)
                                    .category(cat).value(value)
                                    .ranking(i == 0 ? ranking : null)
                                    .series(seriesCode)
                                    .situationType(sitCode)
                                    .situationValue(sitValue)
                                    .build());
                        } else {
                            pitcherBatch.add(PitcherStatsDTO.builder()
                                    .playerId(playerId).season(season)
                                    .category(cat).value(value)
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

        // 배치 저장
        if (isBatter && !batterBatch.isEmpty()) {
            batterStatsService.saveBatch(batterBatch);
        } else if (!isBatter && !pitcherBatch.isEmpty()) {
            pitcherStatsService.saveBatch(pitcherBatch);
        }

        return totalRows;
    }

    private int paginateAndCollectDefense(Page page, int season,
                                           String teamLabel, String seriesCode) {
        int totalRows = 0;
        int pageNum = 1;
        List<DefenseStatsDTO> batch = new ArrayList<>();

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

                    String defPosition = cells.get(3).text().trim();

                    for (int i = 0; i < DEFENSE_CATS.length; i++) {
                        String cat = DEFENSE_CATS[i];
                        int cellIdx = 3 + i;
                        if (cellIdx >= cells.size()) break;
                        if ("POS".equals(cat)) continue;

                        double value = parseDouble(cells.get(cellIdx));
                        batch.add(DefenseStatsDTO.builder()
                                .playerId(playerId).season(season)
                                .series(seriesCode).position(defPosition)
                                .category(cat).value(value)
                                .ranking(i == 1 ? ranking : null)
                                .build());
                    }
                    totalRows++;
                } catch (Exception e) {
                    log.debug("수비 행 처리 스킵: {}", e.getMessage());
                }
            }
            if (!goToNextPage(page, ++pageNum)) break;
        }

        if (!batch.isEmpty()) {
            defenseStatsService.saveBatch(batch);
        }

        return totalRows;
    }

    private int paginateAndCollectRunner(Page page, int season,
                                          String teamLabel, String seriesCode) {
        int totalRows = 0;
        int pageNum = 1;
        List<RunnerStatsDTO> batch = new ArrayList<>();

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

                    for (int i = 0; i < RUNNER_CATS.length; i++) {
                        String cat = RUNNER_CATS[i];
                        int cellIdx = 3 + i;
                        if (cellIdx >= cells.size()) break;

                        double value = parseDouble(cells.get(cellIdx));
                        batch.add(RunnerStatsDTO.builder()
                                .playerId(playerId).season(season)
                                .series(seriesCode)
                                .category(cat).value(value)
                                .ranking(i == 0 ? ranking : null)
                                .build());
                    }
                    totalRows++;
                } catch (Exception e) {
                    log.debug("주루 행 처리 스킵: {}", e.getMessage());
                }
            }
            if (!goToNextPage(page, ++pageNum)) break;
        }

        if (!batch.isEmpty()) {
            runnerStatsService.saveBatch(batch);
        }

        return totalRows;
    }

    // ==================== 헬퍼 ====================

    /**
     * PostBack 캐스케이드 방지: 시리즈/상황 PostBack 후 팀 드롭다운 리셋 확인
     */
    private boolean ensureTeamSelected(Page page, String url, int season,
                                        String teamValue, String seriesValue,
                                        String sitValue) {
        log.debug("PostBack 후 전체 재설정: 팀={}, 시리즈={}, 상황={}", teamValue, seriesValue, sitValue);
        navigateAndWait(page, url);
        selectSeasonDropdown(page, season);
        if (!selectDropdown(page, SEL_TEAM_DROPDOWN, teamValue)) return false;
        if (seriesValue != null && !selectDropdown(page, SEL_SERIES_DROPDOWN_RECORD, seriesValue)) return false;
        if (sitValue != null && !selectDropdown(page, SEL_SITUATION_DROPDOWN, sitValue)) return false;
        return true;
    }
}
