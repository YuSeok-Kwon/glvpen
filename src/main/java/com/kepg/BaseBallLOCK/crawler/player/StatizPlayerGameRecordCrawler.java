package com.kepg.BaseBallLOCK.crawler.player;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

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

    private static final Map<String, Integer> teamNameToId = new HashMap<>();
    static {
        teamNameToId.put("KIA", 1);
        teamNameToId.put("두산", 2);
        teamNameToId.put("삼성", 3);
        teamNameToId.put("SSG", 4);
        teamNameToId.put("LG", 5);
        teamNameToId.put("한화", 6);
        teamNameToId.put("NC", 7);
        teamNameToId.put("KT", 8);
        teamNameToId.put("롯데", 9);
        teamNameToId.put("키움", 10);
    }

    // 1경기의 타자 및 투수 기록(batterLineUp, BatterRecord, PitcherRecord)
    public void crawl() {
        int startId = 20250171;
        int endId = 20250175;
        String baseUrl = "https://statiz.sporki.com/schedule/?m=boxscore&s_no=%d";

        for (int statizId = startId; statizId <= endId; statizId++) {
        	WebDriver driver = null;
        	try {
        	    String url = String.format(baseUrl, statizId);
        	    log.info("크롤링 시작: " + statizId);

        	    ChromeOptions options = new ChromeOptions();
        	    options.addArguments("--headless");
        	    options.addArguments("--no-sandbox");
        	    options.addArguments("--disable-dev-shm-usage");

        	    driver = new ChromeDriver(options);
        	    driver.get(url);

        	    Thread.sleep(5000);

        	    String html = driver.getPageSource();
        	    Document doc = Jsoup.parse(html);

                Timestamp matchDate = extractMatchDate(doc);
                if (matchDate == null) continue;

                Integer[] teamIds = extractTeamIds(doc);
                Integer awayTeamId = teamIds[0];
                Integer homeTeamId = teamIds[1];
                if (awayTeamId == null || homeTeamId == null) continue;

                Integer scheduleId = scheduleService.findScheduleIdByMatchInfo(matchDate, homeTeamId, awayTeamId);
                if (scheduleId == null) continue;

                Map<String, Integer> sbMap = extractStolenBases(doc);

                saveBatterRecords(doc, scheduleId, awayTeamId, sbMap);
                saveBatterRecords(doc, scheduleId, homeTeamId, sbMap);
                savePitcherRecords(doc, scheduleId);

                log.info("저장 완료 batterLineUp, BatterRecord, PitcherRecord " + statizId + " (" + scheduleId + ") " + matchDate);
                Thread.sleep(3000);

            } catch (Exception e) {
                log.info(String.format("오류 발생: %d, %s\n", statizId, e.getMessage()));
            } finally {
                if (driver != null) driver.quit();
            }
        }
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
        return new Integer[]{teamNameToId.get(away), teamNameToId.get(home)};
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
            Integer extractedTeamId = teamNameToId.get(teamName);
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
                    log.info("행 부족");
                	continue;
                }

                while (true) {
                    String name = cols.get(1).text().trim();
                    Optional<Player> player = playerService.findByNameAndTeamId(name, teamId);
                    if (player.isEmpty()) {
                        playerService.savePlayer(name, teamId);
                        continue;
                    }

                    try {
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
                        log.info("타자 저장 : " + name);
                        break;
                    } catch (Exception e) {
                        log.info("타자 저장 중 에러: " + name);
                        break;
                    }
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
            Integer teamId = teamNameToId.get(teamName);
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
                	log.info("행 부족");
                	continue;
                }

                while (true) {
                    Element nameAnchor = cols.get(0).selectFirst("a");
                    if (nameAnchor == null) break;
                    String fullText = cols.get(0).text();
                    String decision = fullText.contains("(승") ? "W" : fullText.contains("(패") ? "L" : fullText.contains("(세") ? "SV" : fullText.contains("(홀") ? "HLD" : "";
                    String name = fullText.replaceAll("\\s*\\([^)]*\\)", "").trim();

                    Optional<Player> player = playerService.findByNameAndTeamId(name, teamId);
                    if (player.isEmpty()) {
                        playerService.savePlayer(name, teamId);
                        continue;
                    }
                    try {
                        double innings = Double.parseDouble(cols.get(1).text().trim());
                        int strikeouts = Integer.parseInt(cols.get(8).text().trim());
                        int bb = Integer.parseInt(cols.get(6).text().trim());
                        int hbp = Integer.parseInt(cols.get(7).text().trim());
                        int runs = Integer.parseInt(cols.get(4).text().trim());
                        int er = Integer.parseInt(cols.get(5).text().trim());
                        int hits = Integer.parseInt(cols.get(3).text().trim());
                        int hr = Integer.parseInt(cols.get(9).text().trim());

                        recordService.savePitcherRecord(scheduleId, teamId, name, innings, strikeouts, bb, hbp, runs, er, hits, hr, decision);
                        log.info("투수 저장 : " + name);
                        break;
                    } catch (Exception e) {
                        log.info("투수 저장 중 에러: " + name);
                        break;
                    }
                }
            }
        }
    }
}
