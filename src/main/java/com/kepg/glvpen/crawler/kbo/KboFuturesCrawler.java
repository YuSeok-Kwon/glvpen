package com.kepg.glvpen.crawler.kbo;

import static com.kepg.glvpen.crawler.kbo.util.KboConstants.*;
import static com.kepg.glvpen.crawler.kbo.util.PlaywrightFactory.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import com.kepg.glvpen.crawler.kbo.util.AbstractPlaywrightCrawler;
import com.kepg.glvpen.modules.futures.schedule.dto.FuturesScheduleDTO;
import com.kepg.glvpen.modules.futures.schedule.service.FuturesScheduleService;
import com.kepg.glvpen.modules.futures.stats.dto.FuturesBatterStatsDTO;
import com.kepg.glvpen.modules.futures.stats.dto.FuturesPitcherStatsDTO;
import com.kepg.glvpen.modules.futures.stats.dto.FuturesTeamStatsDTO;
import com.kepg.glvpen.modules.futures.stats.service.FuturesBatterStatsService;
import com.kepg.glvpen.modules.futures.stats.service.FuturesPitcherStatsService;
import com.kepg.glvpen.modules.futures.stats.service.FuturesTeamStatsService;
import com.kepg.glvpen.modules.player.domain.Player;
import com.kepg.glvpen.modules.player.dto.PlayerDTO;
import com.kepg.glvpen.modules.player.service.PlayerService;
import com.microsoft.playwright.options.LoadState;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * KBO 퓨처스 리그 크롤러 (AbstractPlaywrightCrawler 기반)
 *
 * 크롤링 대상:
 * - 선수 타자/투수 기록 (리그x팀 순회, 페이지네이션)
 * - 팀 타자/투수 기록 (리그 순회)
 * - 일정 (월 순회)
 *
 * 1군 크롤러(KboPlayerStatsCrawler)와 동일한 패턴:
 * - withBrowser: 브라우저 라이프사이클 자동 관리
 * - 팀마다 URL 재네비게이션 (ASP.NET PostBack 누적 손상 방지)
 * - 배치 저장: 메모리 수집 후 일괄 DB 저장
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KboFuturesCrawler extends AbstractPlaywrightCrawler {

    private final PlayerService playerService;
    private final FuturesBatterStatsService futuresBatterStatsService;
    private final FuturesPitcherStatsService futuresPitcherStatsService;
    private final FuturesTeamStatsService futuresTeamStatsService;
    private final FuturesScheduleService futuresScheduleService;

    // ==================== Override: 퓨처스 테이블 탐색 ====================

    /**
     * 퓨처스 페이지는 table.tbl 클래스 사용 (1군: table.tData)
     */
    @Override
    protected Elements findDataRows(Document doc) {
        Elements rows = doc.select("table.tbl tbody tr");
        if (!rows.isEmpty()) return rows;
        rows = doc.select("table.tData tbody tr");
        if (!rows.isEmpty()) return rows;
        rows = doc.select("#cphContents_cphContents_cphContents_udpContent table tbody tr");
        if (!rows.isEmpty()) return rows;
        return doc.select("table tbody tr");
    }

    @Override
    protected Elements findFirstTableRows(Document doc) {
        Element table = doc.selectFirst("table.tbl");
        if (table != null) return table.select("tbody tr");
        table = doc.selectFirst("table.tData");
        if (table != null) return table.select("tbody tr");
        table = doc.selectFirst("#cphContents_cphContents_cphContents_udpContent table");
        if (table != null) return table.select("tbody tr");
        table = doc.selectFirst("table");
        if (table != null) return table.select("tbody tr");
        return new Elements();
    }

    /**
     * 퓨처스 드롭다운은 "선택" 텍스트도 필터링 필요
     */
    @Override
    protected List<String[]> filterOutAllOption(List<String[]> options) {
        List<String[]> filtered = new ArrayList<>();
        for (String[] opt : options) {
            String value = opt[0];
            String text = opt[1];
            if (value.isEmpty() || "전체".equals(text) || text.contains("선택")) continue;
            filtered.add(opt);
        }
        return filtered;
    }

    // ==================== 공개 메서드 ====================

    /**
     * 특정 시즌의 모든 퓨처스 데이터 크롤링
     */
    public void crawlAllFuturesData(int season) {
        log.info("=== 퓨처스 리그 전체 크롤링 시작 ({}시즌) ===", season);
        crawlFuturesTeamBatterStats(season);
        crawlFuturesTeamPitcherStats(season);
        crawlFuturesBatterStats(season);
        crawlFuturesPitcherStats(season);
        crawlFuturesSchedule(season);
        log.info("=== 퓨처스 리그 전체 크롤링 완료 ({}시즌) ===", season);
    }

    /**
     * 다시즌 배치 크롤링 (역순: 최신->과거)
     */
    public void crawlMultiSeasons(int startYear, int endYear) {
        log.info("=== 퓨처스 다시즌 크롤링 시작: {}→{} (역순) ===", endYear, startYear);
        for (int year = endYear; year >= startYear; year--) {
            log.info("--- {}시즌 퓨처스 데이터 크롤링 ---", year);
            crawlAllFuturesData(year);
        }
        log.info("=== 퓨처스 다시즌 크롤링 완료 ===");
    }

    // ==================== 팀 타자 기록 ====================

    /**
     * 퓨처스 팀 타자 통계 크롤링
     * 테이블: (순위)|팀명|AVG|G|PA|AB|R|H|2B|3B|HR|RBI|SB|BB|HBP|SO|SLG|OBP
     */
    public void crawlFuturesTeamBatterStats(int season) {
        log.info("[퓨처스 팀타자] {}시즌 크롤링 시작", season);

        withBrowser("퓨처스 팀타자", (page, browser) -> {
            navigateAndWait(page, FUTURES_TEAM_BATTER_URL);
            selectSeasonDropdown(page, season);

            List<String[]> leagueOptions = filterOutAllOption(
                    getDropdownOptions(page, SEL_FUTURES_LEAGUE_DROPDOWN));
            log.info("[퓨처스 팀타자] 리그 {}개 감지: {}", leagueOptions.size(), formatOptions(leagueOptions));

            List<FuturesTeamStatsDTO> batch = new ArrayList<>();
            int totalTeams = 0;

            for (String[] leagueOpt : leagueOptions) {
                String leagueValue = leagueOpt[0];
                String leagueLabel = leagueOpt[1];

                try {
                    navigateAndWait(page, FUTURES_TEAM_BATTER_URL);
                    selectSeasonDropdown(page, season);
                    if (!selectDropdown(page, SEL_FUTURES_LEAGUE_DROPDOWN, leagueValue)) continue;

                    Document doc = getJsoupDocument(page);
                    Elements rows = findFirstTableRows(doc);

                    for (Element row : rows) {
                        totalTeams += collectTeamStats(row, batch, season, leagueLabel,
                                "BATTER", FUTURES_BATTER_CATS, "퓨처스 팀타자");
                    }
                    log.info("[퓨처스 팀타자] {} 완료", leagueLabel);
                } catch (Exception e) {
                    log.warn("[퓨처스 팀타자] {} 크롤링 오류: {}", leagueLabel, e.getMessage());
                    page = recreatePage(page, browser);
                }
            }

            if (!batch.isEmpty()) {
                futuresTeamStatsService.saveBatch(batch);
            }
            log.info("[퓨처스 팀타자] {}시즌 완료: {}팀 처리", season, totalTeams);
        });
    }

    // ==================== 팀 투수 기록 ====================

    /**
     * 퓨처스 팀 투수 통계 크롤링
     * 테이블: (순위)|팀명|ERA|G|W|L|SV|HLD|WPCT|IP|H|HR|BB|HBP|SO|R|ER
     */
    public void crawlFuturesTeamPitcherStats(int season) {
        log.info("[퓨처스 팀투수] {}시즌 크롤링 시작", season);

        withBrowser("퓨처스 팀투수", (page, browser) -> {
            navigateAndWait(page, FUTURES_TEAM_PITCHER_URL);
            selectSeasonDropdown(page, season);

            List<String[]> leagueOptions = filterOutAllOption(
                    getDropdownOptions(page, SEL_FUTURES_LEAGUE_DROPDOWN));
            log.info("[퓨처스 팀투수] 리그 {}개 감지: {}", leagueOptions.size(), formatOptions(leagueOptions));

            List<FuturesTeamStatsDTO> batch = new ArrayList<>();
            int totalTeams = 0;

            for (String[] leagueOpt : leagueOptions) {
                String leagueValue = leagueOpt[0];
                String leagueLabel = leagueOpt[1];

                try {
                    navigateAndWait(page, FUTURES_TEAM_PITCHER_URL);
                    selectSeasonDropdown(page, season);
                    if (!selectDropdown(page, SEL_FUTURES_LEAGUE_DROPDOWN, leagueValue)) continue;

                    Document doc = getJsoupDocument(page);
                    Elements rows = findFirstTableRows(doc);

                    for (Element row : rows) {
                        totalTeams += collectTeamStats(row, batch, season, leagueLabel,
                                "PITCHER", FUTURES_PITCHER_CATS, "퓨처스 팀투수");
                    }
                    log.info("[퓨처스 팀투수] {} 완료", leagueLabel);
                } catch (Exception e) {
                    log.warn("[퓨처스 팀투수] {} 크롤링 오류: {}", leagueLabel, e.getMessage());
                    page = recreatePage(page, browser);
                }
            }

            if (!batch.isEmpty()) {
                futuresTeamStatsService.saveBatch(batch);
            }
            log.info("[퓨처스 팀투수] {}시즌 완료: {}팀 처리", season, totalTeams);
        });
    }

    // ==================== 선수 타자 기록 ====================

    /**
     * 퓨처스 선수 타자 통계 크롤링
     * 리그x팀 조합 순회, 페이지네이션
     */
    public void crawlFuturesBatterStats(int season) {
        log.info("[퓨처스 타자] {}시즌 크롤링 시작", season);

        withBrowser("퓨처스 타자", (page, browser) -> {
            navigateAndWait(page, FUTURES_BATTER_URL);
            selectSeasonDropdown(page, season);

            List<String[]> leagueOptions = filterOutAllOption(
                    getDropdownOptions(page, SEL_FUTURES_LEAGUE_DROPDOWN));
            List<String[]> teamOptions = filterOutAllOption(
                    getDropdownOptions(page, SEL_TEAM_DROPDOWN));

            log.info("[퓨처스 타자] 리그 {}개, 팀 {}개 감지", leagueOptions.size(), teamOptions.size());

            List<FuturesBatterStatsDTO> batch = new ArrayList<>();
            int totalRows = 0;

            for (String[] leagueOpt : leagueOptions) {
                String leagueValue = leagueOpt[0];
                String leagueLabel = leagueOpt[1];

                for (int teamIdx = 0; teamIdx < teamOptions.size(); teamIdx++) {
                    String teamValue = teamOptions.get(teamIdx)[0];
                    String teamLabel = teamOptions.get(teamIdx)[1];

                    try {
                        navigateAndWait(page, FUTURES_BATTER_URL);
                        selectSeasonDropdown(page, season);
                        if (!selectDropdown(page, SEL_FUTURES_LEAGUE_DROPDOWN, leagueValue)) continue;
                        if (!selectDropdown(page, SEL_TEAM_DROPDOWN, teamValue)) continue;

                        log.debug("[퓨처스 타자] {} / {} ({}/{})",
                                leagueLabel, teamLabel, teamIdx + 1, teamOptions.size());

                        totalRows += paginateAndCollectBatterStats(
                                page, season, leagueLabel, FUTURES_BATTER_CATS, batch);
                    } catch (Exception e) {
                        log.warn("[퓨처스 타자] {}/{} 오류, 페이지 재생성: {}",
                                leagueLabel, teamLabel, e.getMessage());
                        page = recreatePage(page, browser);
                    }
                }
                log.info("[퓨처스 타자] {} 완료", leagueLabel);
            }

            if (!batch.isEmpty()) {
                futuresBatterStatsService.saveBatch(batch);
            }
            log.info("[퓨처스 타자] {}시즌 완료: {}건 처리", season, totalRows);
        });
    }

    // ==================== 선수 투수 기록 ====================

    /**
     * 퓨처스 선수 투수 통계 크롤링
     * 리그x팀 조합 순회, 페이지네이션
     */
    public void crawlFuturesPitcherStats(int season) {
        log.info("[퓨처스 투수] {}시즌 크롤링 시작", season);

        withBrowser("퓨처스 투수", (page, browser) -> {
            navigateAndWait(page, FUTURES_PITCHER_URL);
            selectSeasonDropdown(page, season);

            List<String[]> leagueOptions = filterOutAllOption(
                    getDropdownOptions(page, SEL_FUTURES_LEAGUE_DROPDOWN));
            List<String[]> teamOptions = filterOutAllOption(
                    getDropdownOptions(page, SEL_TEAM_DROPDOWN));

            log.info("[퓨처스 투수] 리그 {}개, 팀 {}개 감지", leagueOptions.size(), teamOptions.size());

            List<FuturesPitcherStatsDTO> batch = new ArrayList<>();
            int totalRows = 0;

            for (String[] leagueOpt : leagueOptions) {
                String leagueValue = leagueOpt[0];
                String leagueLabel = leagueOpt[1];

                for (int teamIdx = 0; teamIdx < teamOptions.size(); teamIdx++) {
                    String teamValue = teamOptions.get(teamIdx)[0];
                    String teamLabel = teamOptions.get(teamIdx)[1];

                    try {
                        navigateAndWait(page, FUTURES_PITCHER_URL);
                        selectSeasonDropdown(page, season);
                        if (!selectDropdown(page, SEL_FUTURES_LEAGUE_DROPDOWN, leagueValue)) continue;
                        if (!selectDropdown(page, SEL_TEAM_DROPDOWN, teamValue)) continue;

                        log.debug("[퓨처스 투수] {} / {} ({}/{})",
                                leagueLabel, teamLabel, teamIdx + 1, teamOptions.size());

                        totalRows += paginateAndCollectPitcherStats(
                                page, season, leagueLabel, FUTURES_PITCHER_CATS, batch);
                    } catch (Exception e) {
                        log.warn("[퓨처스 투수] {}/{} 오류, 페이지 재생성: {}",
                                leagueLabel, teamLabel, e.getMessage());
                        page = recreatePage(page, browser);
                    }
                }
                log.info("[퓨처스 투수] {} 완료", leagueLabel);
            }

            if (!batch.isEmpty()) {
                futuresPitcherStatsService.saveBatch(batch);
            }
            log.info("[퓨처스 투수] {}시즌 완료: {}건 처리", season, totalRows);
        });
    }

    // ==================== 일정 ====================

    /**
     * 퓨처스 리그 일정 크롤링 (월 3~11 순회)
     */
    public void crawlFuturesSchedule(int season) {
        log.info("[퓨처스 일정] {}시즌 크롤링 시작", season);

        withBrowser("퓨처스 일정", (page, browser) -> {
            int totalGames = 0;

            for (int month = 3; month <= 11; month++) {
                try {
                    navigateAndWait(page, FUTURES_SCHEDULE_URL);

                    // 일정 페이지는 selectOption 방식 사용
                    // (ASP.NET setTimeout PostBack 타이밍 이슈 대응)
                    String yearSel = findSeasonDropdownSelector(page);
                    String currentYear = (String) page.evaluate(
                            "(s) => document.querySelector(s)?.value", yearSel);
                    if (!String.valueOf(season).equals(currentYear)) {
                        page.selectOption(yearSel, String.valueOf(season));
                        page.waitForTimeout(500);
                        page.waitForLoadState(LoadState.NETWORKIDLE);
                    }

                    String monthValue = String.format("%02d", month);
                    page.selectOption(SEL_MONTH_DROPDOWN_RECORD, monthValue);
                    page.waitForTimeout(500);
                    page.waitForLoadState(LoadState.NETWORKIDLE);

                    Document doc = getJsoupDocument(page);
                    List<FuturesScheduleDTO> monthBatch = parseScheduleTable(doc, season, month);
                    totalGames += monthBatch.size();

                    if (!monthBatch.isEmpty()) {
                        futuresScheduleService.saveBatch(monthBatch);
                    }

                    log.info("[퓨처스 일정] {}년 {}월: {}경기", season, month, monthBatch.size());
                } catch (Exception e) {
                    log.warn("[퓨처스 일정] {}년 {}월 크롤링 오류: {}", season, month, e.getMessage());
                    page = recreatePage(page, browser);
                }
            }

            log.info("[퓨처스 일정] {}시즌 완료: {}경기 처리", season, totalGames);
        });
    }

    // ==================== 셀렉터 디버그 ====================

    /**
     * 퓨처스 5개 페이지의 select 태그를 덤프하여 셀렉터 확정
     */
    public void debugDumpAllSelectors() {
        String[] urls = {
            FUTURES_SCHEDULE_URL, FUTURES_BATTER_URL, FUTURES_PITCHER_URL,
            FUTURES_TEAM_BATTER_URL, FUTURES_TEAM_PITCHER_URL
        };
        String[] labels = {"일정", "선수타자", "선수투수", "팀타자", "팀투수"};

        withBrowser("퓨처스 디버그", (page, browser) -> {
            for (int i = 0; i < urls.length; i++) {
                log.info("===== [퓨처스 디버그] {} ({}) =====", labels[i], urls[i]);
                navigateAndWait(page, urls[i]);
                dumpSelectElements(page);
                dumpTableStructure(page);
            }
        });
    }

    // ==================== 팀 기록 수집 (공통) ====================

    /**
     * 팀 테이블 행에서 카테고리별 스탯 추출하여 배치에 추가
     * @return 처리된 팀 수 (0 또는 1)
     */
    private int collectTeamStats(Element row, List<FuturesTeamStatsDTO> batch,
                                  int season, String league, String statType,
                                  String[] categories, String logPrefix) {
        try {
            Elements cells = row.select("td");
            if (cells.size() < 3) return 0;
            if (row.select("th").size() > 0) return 0;

            String firstCell = cells.get(0).text().trim();
            if (firstCell.equals("순위") || firstCell.isEmpty()) return 0;

            boolean hasRanking = isNumeric(firstCell);
            int dataOffset = hasRanking ? 2 : 1;
            int ranking = hasRanking ? parseInt(cells.get(0)) : 0;
            String teamName = cells.get(hasRanking ? 1 : 0).text().trim();

            Integer teamId = resolveTeamId(teamName);
            if (teamId == null) {
                log.warn("[{}] 팀 매핑 실패: {}", logPrefix, teamName);
                return 0;
            }

            for (int i = 0; i < categories.length; i++) {
                int cellIdx = dataOffset + i;
                if (cellIdx >= cells.size()) break;

                batch.add(FuturesTeamStatsDTO.builder()
                        .teamId(teamId).season(season)
                        .league(league)
                        .statType(statType)
                        .category(categories[i])
                        .value(parseDouble(cells.get(cellIdx)))
                        .ranking(i == 0 ? ranking : null)
                        .build());
            }
            return 1;
        } catch (Exception e) {
            log.debug("[{}] 행 처리 스킵: {}", logPrefix, e.getMessage());
            return 0;
        }
    }

    // ==================== 선수 기록 페이지네이션 ====================

    /**
     * 퓨처스 선수 타자 기록 페이지네이션 + 배치 수집
     */
    private int paginateAndCollectBatterStats(com.microsoft.playwright.Page page, int season,
                                               String league, String[] categories,
                                               List<FuturesBatterStatsDTO> batch) {
        int totalRows = 0;
        int pageNum = 1;

        while (true) {
            Document doc = getJsoupDocument(page);
            Elements rows = findDataRows(doc);
            if (rows.isEmpty()) break;

            for (Element row : rows) {
                try {
                    Elements cells = row.select("td");
                    if (cells.size() < 4 || row.select("th").size() > 0) continue;

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

                    for (int i = 0; i < categories.length; i++) {
                        int cellIdx = 3 + i;
                        if (cellIdx >= cells.size()) break;

                        batch.add(FuturesBatterStatsDTO.builder()
                                .playerId(playerId).season(season)
                                .league(league)
                                .category(categories[i])
                                .value(parseDouble(cells.get(cellIdx)))
                                .ranking(i == 0 ? ranking : null)
                                .build());
                    }
                    totalRows++;
                } catch (Exception e) {
                    log.debug("퓨처스 타자 행 처리 스킵: {}", e.getMessage());
                }
            }
            if (!goToNextPage(page, ++pageNum)) break;
        }
        return totalRows;
    }

    /**
     * 퓨처스 선수 투수 기록 페이지네이션 + 배치 수집
     */
    private int paginateAndCollectPitcherStats(com.microsoft.playwright.Page page, int season,
                                                String league, String[] categories,
                                                List<FuturesPitcherStatsDTO> batch) {
        int totalRows = 0;
        int pageNum = 1;

        while (true) {
            Document doc = getJsoupDocument(page);
            Elements rows = findDataRows(doc);
            if (rows.isEmpty()) break;

            for (Element row : rows) {
                try {
                    Elements cells = row.select("td");
                    if (cells.size() < 4 || row.select("th").size() > 0) continue;

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

                    for (int i = 0; i < categories.length; i++) {
                        int cellIdx = 3 + i;
                        if (cellIdx >= cells.size()) break;

                        batch.add(FuturesPitcherStatsDTO.builder()
                                .playerId(playerId).season(season)
                                .league(league)
                                .category(categories[i])
                                .value(parseDouble(cells.get(cellIdx)))
                                .ranking(i == 0 ? ranking : null)
                                .build());
                    }
                    totalRows++;
                } catch (Exception e) {
                    log.debug("퓨처스 투수 행 처리 스킵: {}", e.getMessage());
                }
            }
            if (!goToNextPage(page, ++pageNum)) break;
        }
        return totalRows;
    }

    // ==================== 일정 파싱 ====================

    /**
     * 퓨처스 일정 테이블 파싱 -> DTO 배치 반환
     */
    private List<FuturesScheduleDTO> parseScheduleTable(Document doc, int season, int month) {
        List<FuturesScheduleDTO> batch = new ArrayList<>();
        String currentDate = null;

        Elements rows = doc.select("table.scheduleBoard tbody tr");
        if (rows.isEmpty()) {
            rows = doc.select("table tbody tr");
        }

        for (Element row : rows) {
            try {
                // 날짜 행 감지
                Element dayCell = row.selectFirst("td.day, td[class*=day]");
                if (dayCell != null) {
                    String dayText = dayCell.text().trim();
                    Matcher dateMatcher = Pattern.compile("(\\d{2}\\.\\d{2})").matcher(dayText);
                    if (dateMatcher.find()) {
                        currentDate = dateMatcher.group(1);
                    }
                    continue;
                }

                if (currentDate == null) continue;

                Elements cells = row.select("td");
                if (cells.size() < 3) continue;

                String rowText = row.text().trim();
                if (rowText.contains("이동일") || rowText.contains("경기없음") ||
                    rowText.contains("데이터가 없습니다")) continue;

                // play 셀에서 경기 정보 추출
                Element playCell = row.selectFirst("td.play, td[class*=play]");
                if (playCell != null) {
                    FuturesScheduleDTO dto = parseGameFromPlayCell(
                            playCell.text().trim(), cells, currentDate, season);
                    if (dto != null) batch.add(dto);
                    continue;
                }

                // 전체 행에서 "팀명 점수 vs 점수 팀명" 패턴 찾기
                Matcher gameMatcher = Pattern.compile(
                        "(\\S+)\\s+(\\d+)\\s+vs\\s+(\\d+)\\s+(\\S+)").matcher(rowText);
                if (gameMatcher.find()) {
                    FuturesScheduleDTO dto = buildScheduleDTO(season, currentDate, cells,
                            gameMatcher.group(1), gameMatcher.group(4),
                            Integer.parseInt(gameMatcher.group(2)),
                            Integer.parseInt(gameMatcher.group(3)),
                            "경기종료", rowText);
                    if (dto != null) batch.add(dto);
                } else {
                    // "팀명 vs 팀명" (점수 없음 - 예정 경기)
                    Matcher pendingMatcher = Pattern.compile(
                            "(\\S+)\\s+vs\\s+(\\S+)").matcher(rowText);
                    if (pendingMatcher.find()) {
                        FuturesScheduleDTO dto = buildScheduleDTO(season, currentDate, cells,
                                pendingMatcher.group(1), pendingMatcher.group(2),
                                null, null, "예정", rowText);
                        if (dto != null) batch.add(dto);
                    }
                }
            } catch (Exception e) {
                log.debug("[퓨처스 일정] 행 처리 스킵: {}", e.getMessage());
            }
        }
        return batch;
    }

    /**
     * play 셀 텍스트에서 경기 DTO 생성
     */
    private FuturesScheduleDTO parseGameFromPlayCell(String playText, Elements cells,
                                                      String currentDate, int season) {
        String time = extractTime(cells);
        String status = "경기종료";

        if (playText.contains("우천취소") || playText.contains("취소")) {
            status = "취소";
        }

        // "팀명 점수 vs 점수 팀명" 파싱
        Matcher gameMatcher = Pattern.compile("(\\S+)\\s+(\\d+)\\s+vs\\s+(\\d+)\\s+(\\S+)").matcher(playText);
        if (gameMatcher.find()) {
            return buildScheduleDTO(season, currentDate, cells,
                    gameMatcher.group(1), gameMatcher.group(4),
                    Integer.parseInt(gameMatcher.group(2)),
                    Integer.parseInt(gameMatcher.group(3)),
                    status, playText);
        }

        // "팀명 vs 팀명" (예정)
        Matcher pendingMatcher = Pattern.compile("(\\S+)\\s+vs\\s+(\\S+)").matcher(playText);
        if (pendingMatcher.find()) {
            return buildScheduleDTO(season, currentDate, cells,
                    pendingMatcher.group(1), pendingMatcher.group(2),
                    null, null, "예정", playText);
        }

        return null;
    }

    /**
     * 일정 DTO 생성 공통 메서드
     */
    private FuturesScheduleDTO buildScheduleDTO(int season, String currentDate, Elements cells,
                                                 String awayTeam, String homeTeam,
                                                 Integer awayScore, Integer homeScore,
                                                 String status, String rowText) {
        Integer awayTeamId = resolveTeamId(awayTeam);
        Integer homeTeamId = resolveTeamId(homeTeam);
        if (awayTeamId == null || homeTeamId == null) return null;

        String time = extractTime(cells);
        Timestamp matchDate = parseMatchDate(season, currentDate, time);
        if (matchDate == null) return null;

        // 구장: 마지막 비빈 셀에서 추출
        String stadium = "";
        for (int i = cells.size() - 1; i >= 0; i--) {
            String cellText = cells.get(i).text().trim();
            if (!cellText.isEmpty() && !cellText.contains("vs") && !cellText.matches("\\d{2}:\\d{2}")) {
                stadium = cellText;
                break;
            }
        }
        if (stadium.matches(".*\\d+.*vs.*")) stadium = "";

        return FuturesScheduleDTO.builder()
                .season(season)
                .matchDate(matchDate)
                .homeTeamId(homeTeamId)
                .awayTeamId(awayTeamId)
                .homeTeamScore(homeScore)
                .awayTeamScore(awayScore)
                .stadium(stadium)
                .status(status)
                .leagueType("퓨처스리그")
                .build();
    }

    // ==================== 헬퍼 메서드 ====================

    private String extractTime(Elements cells) {
        for (Element cell : cells) {
            Matcher tm = Pattern.compile("(\\d{2}:\\d{2})").matcher(cell.text().trim());
            if (tm.find()) return tm.group(1);
        }
        return "";
    }

    private Timestamp parseMatchDate(int season, String dateStr, String timeStr) {
        try {
            String[] dateParts = dateStr.split("\\.");
            int monthVal = Integer.parseInt(dateParts[0]);
            int day = Integer.parseInt(dateParts[1]);
            int hour = 0, minute = 0;

            if (timeStr != null && !timeStr.isEmpty()) {
                String[] timeParts = timeStr.split(":");
                hour = Integer.parseInt(timeParts[0]);
                minute = Integer.parseInt(timeParts[1]);
            }

            return Timestamp.valueOf(LocalDateTime.of(season, monthVal, day, hour, minute));
        } catch (Exception e) {
            log.debug("날짜 파싱 실패: {}-{} {}", season, dateStr, timeStr);
            return null;
        }
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) return false;
        try {
            Double.parseDouble(str.replace(",", ""));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String formatOptions(List<String[]> options) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < options.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(options.get(i)[1]).append("(").append(options.get(i)[0]).append(")");
        }
        sb.append("]");
        return sb.toString();
    }
}
