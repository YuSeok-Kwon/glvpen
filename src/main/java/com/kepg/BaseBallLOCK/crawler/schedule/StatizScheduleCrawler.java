package com.kepg.BaseBallLOCK.crawler.schedule;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import com.kepg.BaseBallLOCK.modules.game.schedule.domain.Schedule;
import com.kepg.BaseBallLOCK.modules.game.schedule.service.ScheduleService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class StatizScheduleCrawler {

    private final ScheduleService scheduleService;

    private static final Map<String, Integer> teamNameToId = new HashMap<>();
    private static final Map<String, String> stadiumNameMap = new HashMap<>();

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

        stadiumNameMap.put("고척", "서울 고척스카이돔");
        stadiumNameMap.put("잠실", "서울 잠실종합운동장");
        stadiumNameMap.put("대구", "대구 삼성라이온즈파크");
        stadiumNameMap.put("문학", "인천 SSG랜더스필드");
        stadiumNameMap.put("수원", "수원 KT위즈파크");
        stadiumNameMap.put("창원", "창원 NC파크");
        stadiumNameMap.put("광주", "광주 기아챔피언스필드");
        stadiumNameMap.put("대전", "대전 한화생명이글스파크");
        stadiumNameMap.put("사직", "부산 사직야구장");
        stadiumNameMap.put("포항", "포항야구장");
        stadiumNameMap.put("울산", "울산 문수야구장");
        stadiumNameMap.put("청주", "청주야구장");
    }
    
    public void crawlGameRange(LocalDate startDate, LocalDate endDate) {
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            processDailySchedule(currentDate);
            currentDate = currentDate.plusDays(1);
        }
    }

    private void processDailySchedule(LocalDate date) {
        WebDriver driver = null;
        try {
            String dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String url = "https://statiz.sporki.com/schedule/?m=daily&date=" + dateStr;

            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage");
            driver = new ChromeDriver(options);
            driver.get(url);
            Thread.sleep(3000);

            Document doc = Jsoup.parse(driver.getPageSource());
            Elements games = doc.select(".box_type_boared .item_box");

            for (Element game : games) {
                try {
                	// 경기 일시
                	Element boxHeadElement = game.selectFirst(".box_head");
                	if (boxHeadElement == null) {
                	    System.out.println("box_head 요소를 찾을 수 없습니다.");
                	    continue;
                	}
                	String boxHead = boxHeadElement.text(); // 예: "정규 05-07 18:30 (고척) 경기전"
                	String dateTimePart = boxHead.replaceAll(".*?(\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2}).*", "$1 $2"); // "05-07 18:30"
                	String[] dateTimeTokens = dateTimePart.split(" "); // ["05-07", "18:30"]

                	int year = 2025;
                	String fullDateStr = year + "-" + dateTimeTokens[0]; // "2025-05-07"
                	String timeStr = dateTimeTokens[1]; // "18:30"

                	LocalDate matchDate = LocalDate.parse(fullDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                	LocalTime matchTime = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));

                	Timestamp matchDateTime = Timestamp.valueOf(matchDate.atTime(matchTime));
                    
                    // 팀 이름
                    Elements rows = game.select(".table_type03 tbody tr");
                    if (rows.size() < 2) {
                        System.out.println("팀 정보 행이 부족합니다.");
                        continue;
                    }
                    
                    Element awayTeamElement = rows.get(0).selectFirst("td");
                    Element homeTeamElement = rows.get(1).selectFirst("td");
                    if (awayTeamElement == null || homeTeamElement == null) {
                        System.out.println("팀 이름 요소를 찾을 수 없습니다.");
                        continue;
                    }
                    
                    String awayTeam = awayTeamElement.text().trim();
                    String homeTeam = homeTeamElement.text().trim();
                    
                 // 점수 
                    Integer awayScore = null;
                    Integer homeScore = null;

                    try {
                        Elements awayTds = rows.get(0).select("td");
                        Elements homeTds = rows.get(1).select("td");

                        Element awayScoreEl = awayTds.get(awayTds.size() - 1).selectFirst(".score");
                        Element homeScoreEl = homeTds.get(homeTds.size() - 1).selectFirst(".score");

                        if (awayScoreEl != null) {
                            String scoreText = awayScoreEl.text().trim();
                            awayScore = scoreText.equals("-") ? null : Integer.parseInt(scoreText);
                        }

                        if (homeScoreEl != null) {
                            String scoreText = homeScoreEl.text().trim();
                            homeScore = scoreText.equals("-") ? null : Integer.parseInt(scoreText);
                        }
                    } catch (Exception e) {
                        System.out.println("점수 파싱 실패 (무시됨): " + e.getMessage());
                    }

                    int awayTeamId = teamNameToId.getOrDefault(awayTeam, 0);
                    int homeTeamId = teamNameToId.getOrDefault(homeTeam, 0);
                    if (homeTeamId == 0 || awayTeamId == 0) continue;

                    // 경기장
                    String stadiumShortName = null;
                    int open = boxHead.indexOf("(");
                    int close = boxHead.indexOf(")");
                    if (open != -1 && close != -1 && close > open) {
                        stadiumShortName = boxHead.substring(open + 1, close).trim(); // 예: "문학"
                    }
                    
                    String stadium = stadiumNameMap.getOrDefault(stadiumShortName, "미정");

                    // 상태
                    String status = boxHead.contains("경기전") ? "예정" : (boxHead.contains("경기종료") ? "종료" : "경기취소");

                    // statizId
                    Element previewLink = game.selectFirst("a[href*='preview']");
                    Element summaryLink = game.selectFirst("a[href*='summary']");
                    String statizUrl = previewLink != null ? previewLink.attr("href") : (summaryLink != null ? summaryLink.attr("href") : null);
                    if (statizUrl == null || !statizUrl.contains("s_no=")) continue;
                    int statizId = Integer.parseInt(statizUrl.split("s_no=")[1]);

                    Schedule schedule = new Schedule();
                    schedule.setMatchDate(matchDateTime);
                    schedule.setHomeTeamId(homeTeamId);
                    schedule.setAwayTeamId(awayTeamId);
                    schedule.setHomeTeamScore(homeScore);
                    schedule.setAwayTeamScore(awayScore);
                    schedule.setStadium(stadium);
                    schedule.setStatus(status);
                    schedule.setStatizId(statizId);

                    scheduleService.saveOrUpdate(schedule);

                    System.out.printf("[Schedule 업데이트 완료] statizId=%d, matchDateTime=%s, %d vs %d (%s)\n",
                            statizId, matchDateTime, homeTeamId, awayTeamId, status);

                } catch (Exception e) {
                    System.out.println("[개별 경기 처리 실패] " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("[일일 일정 처리 실패] " + e.getMessage());
        } finally {
            if (driver != null) driver.quit();
        }
    }
}