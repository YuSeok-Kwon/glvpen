package com.kepg.BaseBallLOCK.crawler.schedule;

import static com.kepg.BaseBallLOCK.crawler.util.CrawlerUtils.*;
import static com.kepg.BaseBallLOCK.crawler.util.TeamMappingConstants.*;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;

import com.kepg.BaseBallLOCK.crawler.util.WebDriverFactory;
import com.kepg.BaseBallLOCK.modules.game.schedule.domain.Schedule;
import com.kepg.BaseBallLOCK.modules.game.schedule.service.ScheduleService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Statiz.com에서 KBO 경기 일정을 크롤링하는 컴포넌트
 *
 * 주요 기능:
 * - 일별 경기 일정 크롤링
 * - 경기 정보 추출 (팀, 점수, 시간, 경기장 등)
 * - 더블헤더 자동 감지
 * - 데이터 검증 및 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatizScheduleCrawler {

    private final ScheduleService scheduleService;

    /**
     * 날짜 범위의 경기 일정 크롤링
     *
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     */
    public void crawlGameRange(LocalDate startDate, LocalDate endDate) {
        log.info("=== 크롤링 시작: {} ~ {} ===", startDate, endDate);

        int totalDays = 0;
        int successDays = 0;
        int totalGames = 0;

        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            totalDays++;

            CrawlResult result = processDailySchedule(currentDate);
            if (result.isSuccess()) {
                successDays++;
                totalGames += result.getGameCount();
            }

            currentDate = currentDate.plusDays(1);
        }

        log.info("=== 크롤링 완료 ===");
        log.info("총 {}일 중 {}일 성공 (성공률: {:.1f}%)",
                totalDays, successDays, (successDays * 100.0 / totalDays));
        log.info("총 {}경기 크롤링", totalGames);
    }

    /**
     * 특정 날짜의 일정 크롤링
     *
     * @param date 크롤링할 날짜
     * @return 크롤링 결과
     */
    private CrawlResult processDailySchedule(LocalDate date) {
        log.info(">>> 크롤링 시작: {}", date);

        WebDriver driver = null;
        int successCount = 0;
        int failCount = 0;

        try {
            // 1. WebDriver 초기화
            driver = createWebDriver();

            // 2. 페이지 로드
            String url = buildStatizUrl(date);
            Document doc = loadPage(driver, url);

            // 3. 경기 박스 추출
            Elements gameBoxes = extractGameBoxes(doc);

            if (gameBoxes.isEmpty()) {
                log.warn("<<< 크롤링 완료: {} - 경기 없음", date);
                return CrawlResult.success(0);
            }

            log.info("총 {}개 경기 발견", gameBoxes.size());

            // 4. 각 경기 처리
            for (int i = 0; i < gameBoxes.size(); i++) {
                Element gameBox = gameBoxes.get(i);

                try {
                    GameData gameData = extractGameData(gameBox, date);

                    if (gameData == null) {
                        failCount++;
                        continue;
                    }

                    // 5. 데이터 검증
                    if (!validateGameData(gameData)) {
                        log.warn("경기 데이터 검증 실패 ({}번째)", i + 1);
                        failCount++;
                        continue;
                    }

                    // 6. Schedule 객체 생성 및 저장
                    Schedule schedule = createSchedule(gameData);
                    scheduleService.saveOrUpdate(schedule);

                    successCount++;

                    log.debug("경기 저장 완료: {} vs {} (statizId: {})",
                             gameData.awayTeamName, gameData.homeTeamName, gameData.statizId);

                } catch (Exception e) {
                    log.error("경기 처리 실패 ({}번째): {}", i + 1, e.getMessage());
                    failCount++;
                }
            }

            log.info("<<< 크롤링 완료: {} - 성공: {}, 실패: {}", date, successCount, failCount);
            return CrawlResult.success(successCount);

        } catch (Exception e) {
            log.error("<<< 크롤링 실패: {} - 에러: {}", date, e.getMessage(), e);
            return CrawlResult.failure();

        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    log.warn("WebDriver 종료 실패: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * WebDriver 생성
     */
    private WebDriver createWebDriver() {
        return WebDriverFactory.createChromeDriverWithExtendedOptions();
    }

    /**
     * Statiz URL 생성
     */
    private String buildStatizUrl(LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return "https://statiz.sporki.com/schedule/?m=daily&date=" + dateStr;
    }

    /**
     * 페이지 로드
     */
    private Document loadPage(WebDriver driver, String url) throws Exception {
        log.debug("페이지 로드: {}", url);
        driver.get(url);
        Thread.sleep(3000); // 페이지 로딩 대기
        return Jsoup.parse(driver.getPageSource());
    }

    /**
     * 경기 박스 추출
     */
    private Elements extractGameBoxes(Document doc) {
        // 메인 셀렉터 시도
        Elements boxes = doc.select(".box_type_boared .item_box");

        if (boxes.isEmpty()) {
            log.debug("메인 셀렉터 실패, fallback 셀렉터 시도");
            boxes = doc.select(".item_box");
        }

        return boxes;
    }

    /**
     * 경기 데이터 추출
     */
    private GameData extractGameData(Element gameBox, LocalDate baseDate) {
        try {
            GameData data = new GameData();

            // 1. box_head 정보 추출
            Element boxHeadElement = gameBox.selectFirst(".box_head");
            if (boxHeadElement == null) {
                log.warn("box_head 요소 없음");
                return null;
            }
            String boxHead = boxHeadElement.text();

            // 2. 날짜/시간 추출
            Timestamp matchDateTime = extractDateTime(boxHead, baseDate);
            if (matchDateTime == null) {
                log.warn("날짜/시간 추출 실패: {}", boxHead);
                return null;
            }
            data.matchDateTime = matchDateTime;

            // 3. 팀 정보 추출
            Elements rows = gameBox.select(".table_type03 tbody tr");
            if (rows.size() < 2) {
                log.warn("팀 정보 행 부족: {} 행", rows.size());
                return null;
            }

            TeamInfo awayTeam = extractTeamInfo(rows.get(0));
            TeamInfo homeTeam = extractTeamInfo(rows.get(1));

            if (awayTeam == null || homeTeam == null) {
                log.warn("팀 정보 추출 실패");
                return null;
            }

            data.awayTeamName = awayTeam.name;
            data.awayTeamId = awayTeam.id;
            data.awayTeamScore = awayTeam.score;
            data.homeTeamName = homeTeam.name;
            data.homeTeamId = homeTeam.id;
            data.homeTeamScore = homeTeam.score;

            // 4. 경기장 추출
            data.stadium = extractStadium(boxHead);

            // 5. 상태 추출
            data.status = extractStatus(boxHead);

            // 6. StatizId 추출
            Integer statizId = extractStatizId(gameBox);
            if (statizId == null) {
                log.warn("StatizId 추출 실패: {} vs {}", data.awayTeamName, data.homeTeamName);
                return null;
            }
            data.statizId = statizId;

            return data;

        } catch (Exception e) {
            log.error("경기 데이터 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 날짜/시간 추출
     */
    private Timestamp extractDateTime(String boxHead, LocalDate baseDate) {
        try {
            Matcher matcher = DATE_TIME_PATTERN.matcher(boxHead);
            if (!matcher.find()) {
                return null;
            }

            String dateStr = matcher.group(1); // "MM-DD"
            String timeStr = matcher.group(2); // "HH:mm"

            int year = baseDate.getYear();
            String fullDateStr = year + "-" + dateStr;

            LocalDate matchDate = LocalDate.parse(fullDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            LocalTime matchTime = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));

            return Timestamp.valueOf(matchDate.atTime(matchTime));

        } catch (Exception e) {
            log.warn("날짜/시간 파싱 실패: {} - {}", boxHead, e.getMessage());
            return null;
        }
    }

    /**
     * 팀 정보 추출
     */
    private TeamInfo extractTeamInfo(Element row) {
        try {
            TeamInfo info = new TeamInfo();

            // 팀명
            Element teamNameElement = row.selectFirst("td");
            if (teamNameElement == null) {
                return null;
            }
            String teamName = teamNameElement.text().trim();
            info.name = teamName;

            // 팀 ID
            Integer teamId = getTeamId(teamName);
            if (teamId == null) {
                log.warn("알 수 없는 팀명: {}", teamName);
                return null;
            }
            info.id = teamId;

            // 점수
            Elements tds = row.select("td");
            if (tds.size() > 0) {
                Element scoreElement = tds.get(tds.size() - 1).selectFirst(".score");
                if (scoreElement != null) {
                    String scoreText = scoreElement.text().trim();
                    info.score = scoreText.equals("-") ? null : Integer.parseInt(scoreText);
                }
            }

            return info;

        } catch (Exception e) {
            log.warn("팀 정보 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 경기장 추출
     */
    private String extractStadium(String boxHead) {
        try {
            Matcher matcher = STADIUM_PATTERN.matcher(boxHead);
            if (matcher.find()) {
                String stadiumShort = matcher.group(1).trim();
                return getStadiumFullNameOrDefault(stadiumShort, stadiumShort);
            }
            return "미정";
        } catch (Exception e) {
            log.warn("경기장 추출 실패: {}", e.getMessage());
            return "미정";
        }
    }

    /**
     * 경기 상태 추출
     */
    private String extractStatus(String boxHead) {
        if (boxHead.contains("경기종료")) {
            return "종료";
        } else if (boxHead.contains("경기취소")) {
            return "취소";
        } else {
            return "예정";
        }
    }

    /**
     * StatizId 추출 (개선된 로직)
     */
    private Integer extractStatizId(Element gameBox) {
        try {
            // preview 또는 summary 링크 찾기
            Element link = gameBox.selectFirst("a[href*='preview'], a[href*='summary']");

            if (link == null) {
                return null;
            }

            String href = link.attr("href");

            // 정규표현식으로 s_no 추출
            Matcher matcher = STATIZ_ID_PATTERN.matcher(href);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }

            return null;

        } catch (Exception e) {
            log.warn("StatizId 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 경기 데이터 검증
     */
    private boolean validateGameData(GameData data) {
        if (data.matchDateTime == null) {
            log.warn("검증 실패: 날짜/시간 없음");
            return false;
        }

        if (data.statizId == null) {
            log.warn("검증 실패: StatizId 없음");
            return false;
        }

        if (data.homeTeamId == null || data.homeTeamId == 0) {
            log.warn("검증 실패: 홈팀 ID 없음");
            return false;
        }

        if (data.awayTeamId == null || data.awayTeamId == 0) {
            log.warn("검증 실패: 원정팀 ID 없음");
            return false;
        }

        if (data.stadium == null || data.stadium.isEmpty()) {
            log.warn("검증 실패: 경기장 정보 없음");
            return false;
        }

        return true;
    }

    /**
     * Schedule 객체 생성
     */
    private Schedule createSchedule(GameData data) {
        Schedule schedule = new Schedule();
        schedule.setMatchDate(data.matchDateTime);
        schedule.setHomeTeamId(data.homeTeamId);
        schedule.setAwayTeamId(data.awayTeamId);
        schedule.setHomeTeamScore(data.homeTeamScore);
        schedule.setAwayTeamScore(data.awayTeamScore);
        schedule.setStadium(data.stadium);
        schedule.setStatus(data.status);
        schedule.setStatizId(data.statizId);
        return schedule;
    }

    // ===== 내부 클래스 =====

    /**
     * 경기 데이터 저장용 DTO
     */
    private static class GameData {
        Timestamp matchDateTime;
        String homeTeamName;
        Integer homeTeamId;
        Integer homeTeamScore;
        String awayTeamName;
        Integer awayTeamId;
        Integer awayTeamScore;
        String stadium;
        String status;
        Integer statizId;
    }

    /**
     * 팀 정보 저장용 DTO
     */
    private static class TeamInfo {
        String name;
        Integer id;
        Integer score;
    }

    /**
     * 크롤링 결과 저장용 DTO
     */
    private static class CrawlResult {
        private final boolean success;
        private final int gameCount;

        private CrawlResult(boolean success, int gameCount) {
            this.success = success;
            this.gameCount = gameCount;
        }

        static CrawlResult success(int gameCount) {
            return new CrawlResult(true, gameCount);
        }

        static CrawlResult failure() {
            return new CrawlResult(false, 0);
        }

        boolean isSuccess() {
            return success;
        }

        int getGameCount() {
            return gameCount;
        }
    }
}
