package com.kepg.glvpen.crawler.kbo;

import static com.kepg.glvpen.crawler.kbo.util.KboConstants.*;
import static com.kepg.glvpen.crawler.kbo.util.PlaywrightFactory.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import com.kepg.glvpen.crawler.kbo.util.AbstractPlaywrightCrawler;
import com.kepg.glvpen.modules.crowd.dto.CrowdStatsDTO;
import com.kepg.glvpen.modules.crowd.service.CrowdStatsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * KBO 공식 사이트 관중현황 크롤러
 * GraphDaily.aspx: 경기별 관중 수 데이터
 * 드롭다운: 시즌(ddlSeason) + 월(ddlMonth)
 * 월별로 순회 (3월~11월)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KboCrowdCrawler extends AbstractPlaywrightCrawler {

    private final CrowdStatsService crowdStatsService;

    /**
     * 단일 시즌 관중현황 전체 크롤링 (3월~11월)
     */
    public void crawlCrowdData(int season) {
        log.info("=== KBO 관중현황 크롤링 시작 ({}시즌) ===", season);
        int totalRecords = 0;

        for (int month = 3; month <= 11; month++) {
            totalRecords += crawlMonthlyData(season, month);
        }

        log.info("=== KBO 관중현황 크롤링 완료 ({}시즌): 총 {}건 ===", season, totalRecords);
    }

    /**
     * 다시즌 배치 크롤링 (역순: 최신→과거)
     */
    public void crawlMultiSeasons(int startYear, int endYear) {
        log.info("=== KBO 관중현황 다시즌 크롤링 시작: {}→{} (역순) ===", endYear, startYear);
        for (int year = endYear; year >= startYear; year--) {
            crawlCrowdData(year);
        }
        log.info("=== KBO 관중현황 다시즌 크롤링 완료 ===");
    }

    /**
     * 단일 월 관중현황 크롤링 (외부 호출용)
     */
    public int crawlSingleMonth(int season, int month) {
        log.info("=== KBO 관중현황 단일 월 크롤링 ({}시즌 {}월) ===", season, month);
        return crawlMonthlyData(season, month);
    }

    /**
     * 특정 시즌, 특정 월 관중현황 크롤링
     */
    private int crawlMonthlyData(int season, int month) {
        log.info("[관중현황] {}시즌 {}월 크롤링 시작", season, month);

        AtomicInteger totalRows = new AtomicInteger(0);

        withBrowser("관중현황 " + season + "." + month, (page, browser) -> {
            navigateAndWait(page, CROWD_DAILY_URL);

            if (!selectSeasonDropdown(page, season)) {
                log.warn("[관중현황] {}시즌 선택 불가 — 스킵", season);
                return;
            }

            List<String[]> monthOptions = getDropdownOptions(page, SEL_MONTH_DROPDOWN_RECORD);
            boolean monthFound = false;
            for (String[] opt : monthOptions) {
                if (opt[0].equals(String.valueOf(month))) {
                    monthFound = true;
                    break;
                }
            }

            if (!monthFound) {
                log.debug("[관중현황] {}월 옵션이 없음 — 스킵", month);
                return;
            }

            if (!selectAndWaitForPostBack(page, SEL_MONTH_DROPDOWN_RECORD, String.valueOf(month))) {
                log.warn("[관중현황] {}월 선택 실패", month);
                return;
            }

            int pageNum = 1;
            while (true) {
                Document doc = getJsoupDocument(page);
                Elements rows = findDataRows(doc);
                if (rows.isEmpty()) break;

                for (Element row : rows) {
                    try {
                        Elements cells = row.select("td");
                        if (cells.size() < 6) continue;
                        if (row.select("th").size() > 0) continue;

                        String firstCell = cells.get(0).text().trim();
                        if (firstCell.isEmpty() || firstCell.equals("날짜") || firstCell.equals("일자")) continue;

                        String dateStr = cells.get(0).text().trim();
                        String dayOfWeek = cells.get(1).text().trim();
                        String homeTeamName = cells.get(2).text().trim();
                        String awayTeamName = cells.get(3).text().trim();
                        String stadium = cells.get(4).text().trim();
                        String crowdStr = cells.get(5).text().trim().replace(",", "");

                        if (homeTeamName.isEmpty() || awayTeamName.isEmpty()) continue;

                        Integer homeTeamId = resolveTeamId(homeTeamName);
                        Integer awayTeamId = resolveTeamId(awayTeamName);
                        if (homeTeamId == null || awayTeamId == null) {
                            log.debug("[관중현황] 팀 매핑 실패: {} vs {}", homeTeamName, awayTeamName);
                            continue;
                        }

                        LocalDate gameDate = parseGameDate(dateStr, season);
                        if (gameDate == null) continue;

                        int crowd = parseCrowdNumber(crowdStr);

                        crowdStatsService.saveOrUpdate(CrowdStatsDTO.builder()
                                .season(season)
                                .gameDate(gameDate)
                                .dayOfWeek(dayOfWeek)
                                .homeTeamId(homeTeamId)
                                .awayTeamId(awayTeamId)
                                .stadium(stadium)
                                .crowd(crowd)
                                .build());

                        totalRows.incrementAndGet();
                    } catch (Exception e) {
                        log.debug("관중현황 행 처리 스킵: {}", e.getMessage());
                    }
                }

                if (!goToNextPage(page, ++pageNum)) break;
            }

            log.info("[관중현황] {}시즌 {}월 완료: {}건", season, month, totalRows.get());
        });

        return totalRows.get();
    }

    // ==================== 헬퍼 ====================

    /**
     * 날짜 문자열 파싱 — "2025-03-22", "03.22", "03/22" 등 다양한 형식 처리
     */
    private LocalDate parseGameDate(String dateStr, int season) {
        try {
            dateStr = dateStr.trim();

            if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(dateStr);
            }

            if (dateStr.matches("\\d{4}\\.\\d{2}\\.\\d{2}")) {
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy.MM.dd"));
            }

            if (dateStr.matches("\\d{4}/\\d{2}/\\d{2}")) {
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            }

            if (dateStr.matches("\\d{1,2}\\.\\d{1,2}")) {
                String[] parts = dateStr.split("\\.");
                int month = Integer.parseInt(parts[0]);
                int day = Integer.parseInt(parts[1]);
                return LocalDate.of(season, month, day);
            }

            if (dateStr.matches("\\d{1,2}/\\d{1,2}")) {
                String[] parts = dateStr.split("/");
                int month = Integer.parseInt(parts[0]);
                int day = Integer.parseInt(parts[1]);
                return LocalDate.of(season, month, day);
            }

            log.debug("날짜 파싱 실패: '{}'", dateStr);
            return null;
        } catch (Exception e) {
            log.debug("날짜 파싱 오류: '{}' ({})", dateStr, e.getMessage());
            return null;
        }
    }

    private int parseCrowdNumber(String text) {
        try {
            if (text == null || text.isEmpty() || "-".equals(text)) return 0;
            return (int) Double.parseDouble(text.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
