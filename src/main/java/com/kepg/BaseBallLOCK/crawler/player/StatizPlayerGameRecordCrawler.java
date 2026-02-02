package com.kepg.BaseBallLOCK.crawler.player;

import static com.kepg.BaseBallLOCK.crawler.util.CrawlerUtils.*;
import static com.kepg.BaseBallLOCK.crawler.util.TeamMappingConstants.*;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;

import com.kepg.BaseBallLOCK.crawler.util.WebDriverFactory;
import com.kepg.BaseBallLOCK.modules.game.lineUp.service.LineupService;
import com.kepg.BaseBallLOCK.modules.game.record.service.RecordService;
import com.kepg.BaseBallLOCK.modules.game.schedule.service.ScheduleService;
import com.kepg.BaseBallLOCK.modules.player.domain.Player;
import com.kepg.BaseBallLOCK.modules.player.service.PlayerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatizPlayerGameRecordCrawler {

    private final ScheduleService scheduleService;
    private final PlayerService playerService;
    private final LineupService lineupService;
    private final RecordService recordService;

    // ==================== 상수 정의 ====================
    private static final int PAGE_LOAD_WAIT_MS = 5000;
    private static final String BASE_URL = "https://statiz.sporki.com/schedule/?m=boxscore&s_no=%d";

    // ==================== 공개 메서드 ====================

    /**
     * 지정된 범위의 경기 기록을 크롤링합니다.
     */
    public void crawl(int startId, int endId) {
        log.info("=== 경기 기록 크롤링 시작: {} ~ {} ===", startId, endId);

        int totalGames = endId - startId + 1;
        int successGames = 0;

        for (int statizId = startId; statizId <= endId; statizId++) {
            WebDriver driver = null;
            try {
                log.info("경기 크롤링 시작: {}", statizId);

                driver = createWebDriver();
                String url = String.format(BASE_URL, statizId);
                Document doc = loadPage(driver, url);

                Timestamp matchDate = extractMatchDate(doc);
                if (matchDate == null) {
                    log.warn("경기 날짜 추출 실패: {}", statizId);
                    continue;
                }

                Integer[] teamIds = extractTeamIds(doc);
                Integer awayTeamId = teamIds[0];
                Integer homeTeamId = teamIds[1];
                if (awayTeamId == null || homeTeamId == null) {
                    log.warn("팀 ID 추출 실패: {}", statizId);
                    continue;
                }

                Integer scheduleId = scheduleService.findScheduleIdByMatchInfo(matchDate, homeTeamId, awayTeamId);
                if (scheduleId == null) {
                    log.warn("Schedule ID 조회 실패: {}", statizId);
                    continue;
                }

                Map<String, Integer> sbMap = extractStolenBases(doc);

                saveBatterRecords(doc, scheduleId, awayTeamId, sbMap);
                saveBatterRecords(doc, scheduleId, homeTeamId, sbMap);
                savePitcherRecords(doc, scheduleId);

                successGames++;
                log.info("경기 기록 저장 완료: statizId={}, scheduleId={}, date={}", statizId, scheduleId, matchDate);

            } catch (Exception e) {
                log.error("경기 크롤링 실패: {}, 오류: {}", statizId, e.getMessage(), e);
            } finally {
                if (driver != null) {
                    driver.quit();
                }
            }
        }

        // 크롤링 통계
        log.info("=== 경기 기록 크롤링 완료 ===");
        log.info("총 {}경기 중 {}경기 성공", totalGames, successGames);
    }

    /**
     * 기본 범위로 크롤링을 실행합니다.
     */
    public void crawl() {
        crawl(20250171, 20250175);
    }

    // ==================== 헬퍼 메서드 ====================

    /**
     * WebDriver 인스턴스를 생성합니다.
     */
    private WebDriver createWebDriver() {
        return WebDriverFactory.createChromeDriver();
    }

    /**
     * 페이지를 로드하고 파싱합니다.
     */
    private Document loadPage(WebDriver driver, String url) throws InterruptedException {
        return CrawlerUtils.loadPage(driver, url, PAGE_LOAD_WAIT_MS);
    }
    
    // 경기 날짜 추출
    private Timestamp extractMatchDate(Document doc) {
        try {
            Element calloutBox = doc.selectFirst(".callout_box .txt");
            if (calloutBox == null) return null;
            String dateText = calloutBox.text().split(",")[1].trim();
            LocalDate date = LocalDate.parse("2025-" + dateText, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            return Timestamp.valueOf(date.atStartOfDay());
        } catch (Exception e) {
            log.info("경기 날짜 추출 실패");
            return null;
        }
    }
    
    // teamId추출
    private Integer[] extractTeamIds(Document doc) {
        Elements headers = doc.select("div.box_type_boared .box_head");
        if (headers.size() < 2) return new Integer[]{null, null};
        String away = headers.get(0).text().replace("타격기록", "").replaceAll("[()]", "").trim();
        String home = headers.get(1).text().replace("타격기록", "").replaceAll("[()]", "").trim();
        return new Integer[]{getTeamId(away), getTeamId(home)};
    }
    
    // 도루기록 추출
    private Map<String, Integer> extractStolenBases(Document doc) {
        Map<String, Integer> sbMap = new HashMap<>();
        Elements logBoxes = doc.select("div.box_type_boared .log_box");
        for (Element box : logBoxes) {
            for (Element div : box.select("div.log_div")) {
                Element strongElement = div.selectFirst("strong");
                if (strongElement != null && strongElement.text().contains("도루성공")) {
                    for (Element span : div.select("span")) {
                        Element aElement = span.selectFirst("a");
                        if (aElement != null) {
                            String name = aElement.text().trim();
                            sbMap.put(name, sbMap.getOrDefault(name, 0) + 1);
                        }
                    }
                }
            }
        }
        return sbMap;
    }
    
    // 타자기록 저장
    private void saveBatterRecords(Document doc, int scheduleId, int teamId, Map<String, Integer> sbMap) {
        Elements sections = doc.select("div.box_type_boared");

        for (Element section : sections) {
            Element head = section.selectFirst(".box_head");
            if (head == null || !head.text().contains("타격기록")) {
                log.info("타격기록 섹션 없음");
                continue;
            }

            String teamName = head.text().replaceAll(".*\\((.*?)\\).*", "$1").trim();
            Integer extractedTeamId = getTeamId(teamName);
            if (extractedTeamId == null || !extractedTeamId.equals(teamId)) {
                log.info("TeamId 추출 실패");
                continue;
            }

            Element table = section.selectFirst("table");
            if (table == null) {
                log.info("테이블 없음");
            	continue;
            }

            for (Element row : table.select("tbody > tr:not(.total)")) {
                Elements cols = row.select("td");
                if (cols.size() < 22) {
                    log.debug("컬럼 수 부족: {}", cols.size());
                    continue;
                }

                try {
                    String name = cols.get(1).text().trim();

                    // 플레이어가 없으면 생성
                    Optional<Player> player = playerService.findByNameAndTeamId(name, teamId);
                    if (player.isEmpty()) {
                        playerService.savePlayer(name, teamId);
                        player = playerService.findByNameAndTeamId(name, teamId);
                        if (player.isEmpty()) {
                            log.warn("플레이어 생성 실패: {}", name);
                            continue;
                        }
                    }

                    int pa = Integer.parseInt(cols.get(3).text().trim());
                    int ab = Integer.parseInt(cols.get(4).text().trim());
                    int hits = Integer.parseInt(cols.get(6).text().trim());
                    int hr = Integer.parseInt(cols.get(7).text().trim());
                    int rbi = Integer.parseInt(cols.get(8).text().trim());
                    int bb = Integer.parseInt(cols.get(9).text().trim());
                    int so = Integer.parseInt(cols.get(11).text().trim());
                    int sb = sbMap.getOrDefault(name, 0);
                    int order = cols.get(0).text().isEmpty() ? 0 : Integer.parseInt(cols.get(0).text().trim());
                    String pos = cols.get(2).text().trim();

                    lineupService.saveBatterLineup(scheduleId, teamId, order, pos, name);
                    recordService.saveBatterRecord(scheduleId, teamId, pa, ab, hits, hr, rbi, bb, so, sb, name);
                    log.debug("타자 데이터 저장: {}", name);

                } catch (Exception e) {
                    log.error("타자 데이터 저장 실패: {}", e.getMessage());
                }
            }
        }
    }
    
    // 투수기록 저장
    private void savePitcherRecords(Document doc, int scheduleId) {
        for (Element section : doc.select("div.box_type_boared")) {
            Element head = section.selectFirst(".box_head");
            if (head == null || !head.text().contains("투구기록")) {
            	log.info("투구기록 head 없음");
            	continue;
            }

            String teamName = head.text().replaceAll(".*\\((.*?)\\).*", "$1").trim();
            Integer teamId = getTeamId(teamName);
            if (teamId == null) {
            	log.info("TeamId 추출 실패");
            	continue;
            }

            Element table = section.selectFirst(".table_type03 table");
            if (table == null) {
            	log.info("테이블 없음");
            	continue;
            }

            for (Element row : table.select("tbody > tr:not(.total)")) {
                Elements cols = row.select("td");
                if (cols.size() < 18) {
                    log.debug("컬럼 수 부족: {}", cols.size());
                    continue;
                }

                try {
                    Element nameAnchor = cols.get(0).selectFirst("a");
                    if (nameAnchor == null) {
                        log.debug("투수 이름 링크 없음");
                        continue;
                    }

                    String fullText = cols.get(0).text();
                    String decision = fullText.contains("(승") ? "W" :
                                    fullText.contains("(패") ? "L" :
                                    fullText.contains("(세") ? "SV" :
                                    fullText.contains("(홀") ? "HLD" : "";
                    String name = fullText.replaceAll("\\s*\\([^)]*\\)", "").trim();

                    // 플레이어가 없으면 생성
                    Optional<Player> player = playerService.findByNameAndTeamId(name, teamId);
                    if (player.isEmpty()) {
                        playerService.savePlayer(name, teamId);
                        player = playerService.findByNameAndTeamId(name, teamId);
                        if (player.isEmpty()) {
                            log.warn("플레이어 생성 실패: {}", name);
                            continue;
                        }
                    }

                    double innings = Double.parseDouble(cols.get(1).text().trim());
                    int strikeouts = Integer.parseInt(cols.get(8).text().trim());
                    int bb = Integer.parseInt(cols.get(6).text().trim());
                    int hbp = Integer.parseInt(cols.get(7).text().trim());
                    int runs = Integer.parseInt(cols.get(4).text().trim());
                    int er = Integer.parseInt(cols.get(5).text().trim());
                    int hits = Integer.parseInt(cols.get(3).text().trim());
                    int hr = Integer.parseInt(cols.get(9).text().trim());

                    recordService.savePitcherRecord(scheduleId, teamId, name, innings, strikeouts, bb, hbp, runs, er, hits, hr, decision);
                    log.debug("투수 데이터 저장: {}", name);

                } catch (Exception e) {
                    log.error("투수 데이터 저장 실패: {}", e.getMessage());
                }
            }
        }
    }
}
