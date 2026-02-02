package com.kepg.BaseBallLOCK.crawler.game;
import static com.kepg.BaseBallLOCK.crawler.util.CrawlerUtils.*;
import static com.kepg.BaseBallLOCK.crawler.util.TeamMappingConstants.*;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.kepg.BaseBallLOCK.crawler.util.WebDriverFactory;
import com.kepg.BaseBallLOCK.modules.game.highlight.dto.GameHighlightDTO;
import com.kepg.BaseBallLOCK.modules.game.highlight.service.GameHighlightService;
import com.kepg.BaseBallLOCK.modules.game.lineUp.service.LineupService;
import com.kepg.BaseBallLOCK.modules.game.record.service.RecordService;
import com.kepg.BaseBallLOCK.modules.game.schedule.domain.Schedule;
import com.kepg.BaseBallLOCK.modules.game.schedule.service.ScheduleService;
import com.kepg.BaseBallLOCK.modules.game.scoreBoard.domain.ScoreBoard;
import com.kepg.BaseBallLOCK.modules.game.scoreBoard.service.ScoreBoardService;
import com.kepg.BaseBallLOCK.modules.player.domain.Player;
import com.kepg.BaseBallLOCK.modules.player.service.PlayerService;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatizGameCrawler {

    private final ScheduleService scheduleService;
    private final ScoreBoardService scoreBoardService;
    private final GameHighlightService gameHighlightService;
    private final PlayerService playerService;
    private final LineupService lineupService;
    private final RecordService recordService;

    // ==================== 상수 정의 ====================

    // 셀렉터 상수
    private static final String SELECTOR_GAME_BOXES = ".box_type_boared .item_box";
    private static final String SELECTOR_SCORE_TABLE = "div.box_type_boared > div.item_box.w100 .table_type03 table";
    private static final String SELECTOR_HIGHLIGHT_TABLE = "div.sh_box:has(.box_head:contains(결정적 장면 Best 5)) table";

    // 크롤링 설정
    private static final int PAGE_LOAD_WAIT_MS = 3000;
    private static final int DEFAULT_GAME_HOUR = 18;
    private static final int DEFAULT_GAME_MINUTE = 30;

    // ==================== 공개 메서드 ====================

    /**
     * 매일 새벽 4시에 자동으로 오늘 경기를 크롤링합니다.
     */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void crawlToday() {
        LocalDate today = LocalDate.now();
        log.info("=== 자동 크롤링 시작: {} ===", today);
        crawlGameRange(today, today);
        log.info("=== 자동 크롤링 완료: {} ===", today);
    }

    /**
     * 지정된 날짜 범위의 경기를 크롤링합니다 (수동 실행용).
     */
    public void crawlGameRange(LocalDate startDate, LocalDate endDate) {
        log.info("=== 경기 크롤링 시작 ===");
        log.info("크롤링 기간: {} ~ {}", startDate, endDate);

        WebDriver driver = null;
        int totalDays = 0;
        int successDays = 0;
        int totalGames = 0;
        int successGames = 0;

        try {
            driver = createWebDriver();
            LocalDate currentDate = startDate;

            while (!currentDate.isAfter(endDate)) {
                totalDays++;
                log.info("\n=== 날짜: {} ===", currentDate);

                try {
                    CrawlResult result = crawlDailyGames(driver, currentDate);
                    if (result.isSuccess()) {
                        successDays++;
                        totalGames += result.getGameCount();
                        successGames += result.getGameCount();
                    }
                } catch (Exception e) {
                    log.error("날짜 {} 크롤링 실패: {}", currentDate, e.getMessage(), e);
                }

                currentDate = currentDate.plusDays(1);
            }
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }

        // 크롤링 통계 출력
        log.info("\n=== 크롤링 완료 ===");
        log.info("총 {}일 중 {}일 성공 (성공률: {:.1f}%)", totalDays, successDays,
                 totalDays > 0 ? (successDays * 100.0 / totalDays) : 0);
        log.info("총 {}경기 중 {}경기 성공", totalGames, successGames);
    }

    /**
     * 특정 날짜의 모든 경기를 크롤링합니다.
     */
    private CrawlResult crawlDailyGames(WebDriver driver, LocalDate date) {
        try {
            List<Integer> statizIds = extractStatizIds(driver, date);
            log.info("추출된 경기 수: {}", statizIds.size());

            int successCount = 0;
            for (int i = 0; i < statizIds.size(); i++) {
                int statizId = statizIds.get(i);
                log.info("▶ [{}/{}] statizId = {}", i + 1, statizIds.size(), statizId);

                try {
                    processGame(driver, statizId, date);
                    successCount++;
                } catch (Exception e) {
                    log.error("경기 처리 실패 (statizId: {}): {}", statizId, e.getMessage(), e);
                }
            }

            return CrawlResult.builder()
                    .success(true)
                    .gameCount(successCount)
                    .build();

        } catch (Exception e) {
            log.error("일일 크롤링 실패: {}", e.getMessage(), e);
            return CrawlResult.builder()
                    .success(false)
                    .gameCount(0)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    // ==================== StatizId 추출 ====================

    /**
     * 특정 날짜의 모든 경기 statizId를 추출합니다.
     */
    private List<Integer> extractStatizIds(WebDriver driver, LocalDate date) {
        try {
            String url = buildDailyUrl(date);
            log.info("페이지 접속: {}", url);

            Document doc = loadPage(driver, url);
            Elements links = findStatizIdLinks(doc);

            return parseStatizIds(links);

        } catch (Exception e) {
            log.error("StatizId 추출 실패: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 일일 일정 페이지 URL을 생성합니다.
     */
    private String buildDailyUrl(LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return "https://statiz.sporki.com/schedule/?m=daily&date=" + dateStr;
    }

    /**
     * 페이지를 로드하고 파싱합니다.
     */
    private Document loadPage(WebDriver driver, String url) throws InterruptedException {
        driver.get(url);
        Thread.sleep(PAGE_LOAD_WAIT_MS);

        String html = driver.getPageSource();
        return Jsoup.parse(html);
    }

    /**
     * 여러 전략으로 statizId를 포함한 링크를 찾습니다.
     */
    private Elements findStatizIdLinks(Document doc) {
        // 전략 1: btn_box 안의 s_no 링크 (가장 정확)
        Elements links = doc.select("div.btn_box a[href*='s_no=']");
        if (!links.isEmpty()) {
            log.debug("전략 1 성공 (btn_box): {} 개", links.size());
            return links;
        }

        // 전략 2: game_result 근처의 btn_box 링크
        links = doc.select("div.game_result ~ div.btn_box a[href*='s_no=']");
        if (!links.isEmpty()) {
            log.debug("전략 2 성공 (game_result + btn_box): {} 개", links.size());
            return links;
        }

        // 전략 3: summary 링크
        links = doc.select("a[href*='summary'][href*='s_no=']");
        if (!links.isEmpty()) {
            log.debug("전략 3 성공 (summary): {} 개", links.size());
            return links;
        }

        // 전략 4: boxscore 링크
        links = doc.select("a[href*='boxscore'][href*='s_no=']");
        if (!links.isEmpty()) {
            log.debug("전략 4 성공 (boxscore): {} 개", links.size());
            return links;
        }

        // 전략 5: 모든 s_no 링크
        links = doc.select("a[href*='s_no=']");
        if (!links.isEmpty()) {
            log.debug("전략 5 성공 (모든 s_no): {} 개", links.size());
            return links;
        }

        log.warn("statizId 링크를 찾을 수 없습니다.");
        return new Elements();
    }

    /**
     * 링크에서 statizId를 파싱합니다.
     */
    private List<Integer> parseStatizIds(Elements links) {
        List<Integer> statizIds = new ArrayList<>();

        for (Element link : links) {
            String href = link.attr("href");
            Integer statizId = extractStatizIdFromUrl(href);

            if (statizId != null && !statizIds.contains(statizId)) {
                statizIds.add(statizId);
                log.debug("StatizId 추출: {} from {}", statizId, href);
            }
        }

        log.info("총 {} 개의 StatizId 추출 완료", statizIds.size());
        return statizIds;
    }

    /**
     * URL에서 statizId를 추출합니다.
     */
    private Integer extractStatizIdFromUrl(String url) {
        if (url == null || !url.contains("s_no=")) {
            return null;
        }

        Matcher matcher = STATIZ_ID_PATTERN.matcher(url);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                log.warn("StatizId 파싱 실패: {}", url);
            }
        }

        return null;
    }

    // ==================== 경기 처리 ====================

    /**
     * 단일 경기의 모든 정보를 크롤링하고 저장합니다.
     */
    private void processGame(WebDriver driver, int statizId, LocalDate expectedDate) {
        log.info("\n=== 경기 처리 시작: statizId = {} ===", statizId);

        WebDriver gameDriver = null;
        try {
            gameDriver = createWebDriver();

            // 1. Summary 페이지 크롤링
            Document summaryDoc = loadSummaryPage(gameDriver, statizId);

            // 2. 기본 경기 정보 추출
            GameInfo gameInfo = extractGameInfo(summaryDoc, statizId, expectedDate);
            if (gameInfo == null) {
                log.warn("경기 정보 추출 실패 - statizId: {}", statizId);
                return;
            }

            // 3. 경기 상세 정보 존재 여부 확인 (중복 방지)
            if (shouldSkipGame(statizId, gameInfo)) {
                log.info("이미 완전한 데이터가 있는 경기 - statizId: {}", statizId);
                return;
            }

            // 4. Schedule 저장
            Integer scheduleId = saveSchedule(gameInfo);
            if (scheduleId == null) {
                log.warn("Schedule 저장 실패 - statizId: {}", statizId);
                return;
            }

            // 5. ScoreBoard 정보 추출 및 저장
            ScoreBoardInfo scoreBoardInfo = extractScoreBoardInfo(summaryDoc);
            saveScoreBoard(scheduleId, scoreBoardInfo);

            // 6. GameHighlight 추출 및 저장
            saveGameHighlights(summaryDoc, scheduleId);

            // 7. Boxscore 크롤링 (타자/투수 기록)
            crawlBoxscore(gameDriver, statizId, scheduleId, gameInfo.getHomeTeamId(), gameInfo.getAwayTeamId());

            log.info("=== 경기 처리 완료: statizId = {} ===", statizId);

        } catch (Exception e) {
            log.error("경기 처리 실패 (statizId: {}): {}", statizId, e.getMessage(), e);
        } finally {
            if (gameDriver != null) {
                gameDriver.quit();
            }
        }
    }

    /**
     * WebDriver 인스턴스를 생성합니다.
     */
    private WebDriver createWebDriver() {
        return WebDriverFactory.createChromeDriver();
    }

    /**
     * Summary 페이지를 로드합니다.
     */
    private Document loadSummaryPage(WebDriver driver, int statizId) throws InterruptedException {
        String url = "https://statiz.sporki.com/schedule/?m=summary&s_no=" + statizId;
        log.debug("Summary 페이지 접속: {}", url);

        driver.get(url);
        Thread.sleep(PAGE_LOAD_WAIT_MS);

        String html = driver.getPageSource();
        return Jsoup.parse(html);
    }

    /**
     * Summary 페이지에서 기본 경기 정보를 추출합니다.
     */
    private GameInfo extractGameInfo(Document doc, int statizId, LocalDate expectedDate) {
        try {
            // 스코어 테이블 찾기
            Element scoreTable = findScoreTable(doc);
            if (scoreTable == null) {
                log.warn("스코어 테이블을 찾을 수 없음 - statizId: {}", statizId);
                return null;
            }

            // 팀 정보 추출
            Elements rows = scoreTable.select("tbody > tr");
            if (rows.size() < 2) {
                log.warn("경기 정보 행 부족 - statizId: {}, 행 수: {}", statizId, rows.size());
                return null;
            }

            TeamInfo awayTeam = extractTeamInfo(rows.get(0));
            TeamInfo homeTeam = extractTeamInfo(rows.get(1));

            if (!validateTeamInfo(awayTeam, homeTeam)) {
                return null;
            }

            // 경기 시간 및 경기장 정보
            LocalDateTime matchDateTime = extractMatchDateTime(doc, expectedDate);
            String stadium = extractStadium(doc, expectedDate);

            return GameInfo.builder()
                    .statizId(statizId)
                    .awayTeam(awayTeam.getName())
                    .homeTeam(homeTeam.getName())
                    .awayTeamId(awayTeam.getId())
                    .homeTeamId(homeTeam.getId())
                    .awayScore(awayTeam.getScore())
                    .homeScore(homeTeam.getScore())
                    .matchDateTime(matchDateTime)
                    .stadium(stadium)
                    .status("종료")
                    .build();

        } catch (Exception e) {
            log.error("경기 정보 추출 실패: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 스코어 테이블을 찾습니다 (여러 선택자 시도).
     */
    private Element findScoreTable(Document doc) {
        // 전략 1: 기본 선택자
        Element table = doc.selectFirst(SELECTOR_SCORE_TABLE);
        if (table != null) return table;

        // 전략 2: 대체 선택자들
        String[] selectors = {
            "div.box_type_boared .table_type03 table",
            ".table_type03 table",
            "table.table_type03",
            "div.score_table table"
        };

        for (String selector : selectors) {
            table = doc.selectFirst(selector);
            if (table != null) {
                log.debug("대체 선택자로 테이블 발견: {}", selector);
                return table;
            }
        }

        // 전략 3: 모든 테이블 검사
        Elements allTables = doc.select("table");
        for (Element tbl : allTables) {
            if (tbl.text().contains("이닝") || tbl.text().contains("팀")) {
                log.debug("패턴 매칭으로 테이블 발견");
                return tbl;
            }
        }

        return null;
    }

    /**
     * 행에서 팀 정보를 추출합니다.
     */
    private TeamInfo extractTeamInfo(Element row) {
        Elements cols = row.select("td");
        if (cols.isEmpty()) {
            return null;
        }

        // 팀 이름
        String teamName = "";
        Element teamLink = cols.get(0).selectFirst("a");
        if (teamLink != null) {
            teamName = teamLink.text().trim();
        } else {
            teamName = cols.get(0).text().trim();
        }

        Integer teamId = getTeamId(teamName);

        // 점수 (R 컬럼)
        Integer score = extractScoreFromRow(cols, row.parent().parent());

        return TeamInfo.builder()
                .name(teamName)
                .id(teamId)
                .score(score)
                .build();
    }

    /**
     * 행에서 점수(R 컬럼)를 추출합니다.
     */
    private Integer extractScoreFromRow(Elements cols, Element table) {
        try {
            // 헤더에서 R 컬럼 인덱스 찾기
            Elements headerCols = table.select("thead > tr > th");
            int rIndex = -1;
            for (int i = 0; i < headerCols.size(); i++) {
                if (headerCols.get(i).text().trim().equals("R")) {
                    rIndex = i;
                    break;
                }
            }

            if (rIndex != -1 && rIndex < cols.size()) {
                Element scoreElement = cols.get(rIndex).selectFirst("div.score");
                if (scoreElement != null) {
                    String scoreText = scoreElement.ownText().trim();
                    if (!scoreText.equals("-") && !scoreText.isEmpty()) {
                        return Integer.parseInt(scoreText);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("점수 추출 실패: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 팀 정보의 유효성을 검증합니다.
     */
    private boolean validateTeamInfo(TeamInfo awayTeam, TeamInfo homeTeam) {
        if (awayTeam == null || homeTeam == null) {
            log.warn("팀 정보가 null입니다.");
            return false;
        }

        if (awayTeam.getId() == null || awayTeam.getId() == 0 ||
            homeTeam.getId() == null || homeTeam.getId() == 0) {
            log.warn("팀 ID 매핑 실패 - away: '{}' → {}, home: '{}' → {}",
                    awayTeam.getName(), awayTeam.getId(),
                    homeTeam.getName(), homeTeam.getId());
            log.info("매핑 가능한 팀: {}", teamNameToId.keySet());
            return false;
        }

        return true;
    }

    /**
     * Summary 페이지에서 경기 시간을 추출합니다.
     */
    private LocalDateTime extractMatchDateTime(Document doc, LocalDate expectedDate) {
        try {
            // 전략 1: 스코어박스에서 "잠실, 07-03" 형태 추출
            Element scoreBox = doc.selectFirst("div.callout_box .game_schedule .txt");
            if (scoreBox != null) {
                String scoreBoxText = scoreBox.text().trim();
                Matcher matcher = DATE_PATTERN.matcher(scoreBoxText);
                if (matcher.find()) {
                    int month = Integer.parseInt(matcher.group(1));
                    int day = Integer.parseInt(matcher.group(2));
                    int year = expectedDate.getYear();

                    LocalDateTime dateTime = LocalDate.of(year, month, day)
                            .atTime(DEFAULT_GAME_HOUR, DEFAULT_GAME_MINUTE);
                    log.debug("경기 시간 추출 (전략 1): {}", dateTime);
                    return dateTime;
                }
            }

            // 전략 2: box_head에서 "07-17 18:30 (잠실)" 형태 추출
            Elements boxHeads = doc.select("div.box_head");
            for (Element boxHead : boxHeads) {
                String headText = boxHead.text();
                Matcher matcher = DATE_TIME_PATTERN.matcher(headText);
                if (matcher.find()) {
                    int month = Integer.parseInt(matcher.group(1));
                    int day = Integer.parseInt(matcher.group(2));
                    int hour = Integer.parseInt(matcher.group(3));
                    int minute = Integer.parseInt(matcher.group(4));
                    int year = expectedDate.getYear();

                    LocalDateTime dateTime = LocalDate.of(year, month, day).atTime(hour, minute);
                    log.debug("경기 시간 추출 (전략 2): {}", dateTime);
                    return dateTime;
                }
            }

        } catch (Exception e) {
            log.debug("경기 시간 추출 실패: {}", e.getMessage());
        }

        // 기본값: expectedDate 18:30
        return expectedDate.atTime(DEFAULT_GAME_HOUR, DEFAULT_GAME_MINUTE);
    }

    /**
     * Summary 페이지에서 경기장을 추출합니다.
     */
    private String extractStadium(Document doc, LocalDate expectedDate) {
        try {
            // 전략 1: 스코어박스에서 "잠실, 07-03" 형태 추출
            Element scoreBox = doc.selectFirst("div.callout_box .game_schedule .txt");
            if (scoreBox != null) {
                String scoreBoxText = scoreBox.text().trim();
                if (scoreBoxText.contains(",")) {
                    String stadiumKey = scoreBoxText.split(",")[0].trim();
                    String stadium = getStadiumFullName(stadiumKey);
                    if (stadium != null) {
                        log.debug("경기장 추출 (전략 1): {} -> {}", stadiumKey, stadium);
                        return stadium;
                    }
                }
            }

            // 전략 2: box_head에서 "(잠실)" 형태 추출
            Elements boxHeads = doc.select("div.box_head");
            for (Element boxHead : boxHeads) {
                String headText = boxHead.text();
                Matcher matcher = STADIUM_PATTERN.matcher(headText);
                if (matcher.find()) {
                    String stadiumKey = matcher.group(1);
                    String stadium = getStadiumFullName(stadiumKey);
                    if (stadium != null) {
                        log.debug("경기장 추출 (전략 2): {} -> {}", stadiumKey, stadium);
                        return stadium;
                    }
                }
            }

        } catch (Exception e) {
            log.debug("경기장 추출 실패: {}", e.getMessage());
        }

        return "미정";
    }

    /**
     * 경기를 건너뛸지 여부를 판단합니다 (중복 방지).
     */
    private boolean shouldSkipGame(int statizId, GameInfo gameInfo) {
        try {
            Optional<Schedule> existingSchedule = scheduleService.findByStatizId(statizId);
            if (existingSchedule.isEmpty()) {
                return false;
            }

            Schedule existing = existingSchedule.get();

            // 기본 점수 정보 확인
            boolean hasBasicScore = existing.getHomeTeamScore() != null &&
                                   existing.getAwayTeamScore() != null &&
                                   existing.getStatizId() != null &&
                                   existing.getStatizId().equals(statizId);

            if (!hasBasicScore) {
                return false;
            }

            // 상세 데이터 확인
            Integer scheduleId = scheduleService.findIdByStatizId(statizId);
            if (scheduleId == null) {
                return false;
            }

            boolean hasScoreBoard = scoreBoardService.findByScheduleId(scheduleId) != null;
            boolean hasGameHighlight = !gameHighlightService.findByScheduleId(scheduleId).isEmpty();
            boolean hasBatterRecord = !recordService.getBatterRecords(scheduleId, existing.getHomeTeamId()).isEmpty() ||
                                     !recordService.getBatterRecords(scheduleId, existing.getAwayTeamId()).isEmpty();
            boolean hasPitcherRecord = !recordService.getPitcherRecords(scheduleId, existing.getHomeTeamId()).isEmpty() ||
                                      !recordService.getPitcherRecords(scheduleId, existing.getAwayTeamId()).isEmpty();

            boolean hasDetailedData = hasScoreBoard && hasGameHighlight && hasBatterRecord && hasPitcherRecord;

            log.debug("상세 정보 확인 - ScoreBoard: {}, GameHighlight: {}, BatterRecord: {}, PitcherRecord: {}",
                     hasScoreBoard ? "✓" : "✗", hasGameHighlight ? "✓" : "✗",
                     hasBatterRecord ? "✓" : "✗", hasPitcherRecord ? "✓" : "✗");

            return hasDetailedData;

        } catch (Exception e) {
            log.debug("중복 체크 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Schedule을 저장하고 scheduleId를 반환합니다.
     */
    private Integer saveSchedule(GameInfo gameInfo) {
        try {
            // 기존 데이터에서 추가 정보 가져오기
            Optional<Schedule> existingSchedule = scheduleService.findByStatizId(gameInfo.getStatizId());
            if (existingSchedule.isPresent()) {
                Schedule existing = existingSchedule.get();
                if (gameInfo.getStadium().equals("미정") && existing.getStadium() != null) {
                    gameInfo.setStadium(existing.getStadium());
                }
                if (gameInfo.getMatchDateTime() == null && existing.getMatchDate() != null) {
                    gameInfo.setMatchDateTime(existing.getMatchDate().toLocalDateTime());
                }
                if (gameInfo.getStatus() == null && existing.getStatus() != null) {
                    gameInfo.setStatus(existing.getStatus());
                }
            }

            Schedule schedule = new Schedule();
            schedule.setMatchDate(gameInfo.getMatchDateTime() != null ?
                    Timestamp.valueOf(gameInfo.getMatchDateTime()) : null);
            schedule.setHomeTeamId(gameInfo.getHomeTeamId());
            schedule.setAwayTeamId(gameInfo.getAwayTeamId());
            schedule.setHomeTeamScore(gameInfo.getHomeScore());
            schedule.setAwayTeamScore(gameInfo.getAwayScore());
            schedule.setStadium(gameInfo.getStadium());
            schedule.setStatus(gameInfo.getStatus());
            schedule.setStatizId(gameInfo.getStatizId());

            scheduleService.saveOrUpdate(schedule);
            log.info("Schedule 저장 완료 - statizId: {}, {} vs {} ({})",
                    gameInfo.getStatizId(), gameInfo.getHomeTeamId(),
                    gameInfo.getAwayTeamId(), gameInfo.getStatus());

            return scheduleService.findIdByStatizId(gameInfo.getStatizId());

        } catch (Exception e) {
            log.error("Schedule 저장 실패: {}", e.getMessage(), e);
            return null;
        }
    }
    // ==================== ScoreBoard 처리 ====================

    /**
     * Summary 페이지에서 ScoreBoard 정보를 추출합니다.
     */
    private ScoreBoardInfo extractScoreBoardInfo(Document doc) {
        try {
            Element scoreTable = findScoreTable(doc);
            if (scoreTable == null) {
                log.warn("ScoreBoard 추출 실패: 스코어 테이블 없음");
                return createEmptyScoreBoardInfo();
            }

            Elements rows = scoreTable.select("tbody > tr");
            if (rows.size() < 2) {
                log.warn("ScoreBoard 추출 실패: 행 부족");
                return createEmptyScoreBoardInfo();
            }

            Elements awayTds = rows.get(0).select("td");
            Elements homeTds = rows.get(1).select("td");
            Elements headerCols = scoreTable.select("thead > tr > th");

            // 컬럼 인덱스 찾기
            Map<String, Integer> columnIndex = findColumnIndices(headerCols);

            // 이닝별 점수 파싱
            List<Integer> awayScores = parseInningScores(awayTds);
            List<Integer> homeScores = parseInningScores(homeTds);

            // R, H, E, B 통계 파싱
            int awayR = parseStatValue(awayTds, columnIndex.get("R"));
            int homeR = parseStatValue(homeTds, columnIndex.get("R"));
            int awayH = parseStatValue(awayTds, columnIndex.get("H"));
            int homeH = parseStatValue(homeTds, columnIndex.get("H"));
            int awayE = parseStatValue(awayTds, columnIndex.get("E"));
            int homeE = parseStatValue(homeTds, columnIndex.get("E"));
            int awayB = parseStatValue(awayTds, columnIndex.get("B"));
            int homeB = parseStatValue(homeTds, columnIndex.get("B"));

            // 승부투수 정보 추출
            String[] pitchers = extractPitchers(doc);

            return ScoreBoardInfo.builder()
                    .awayScores(awayScores)
                    .homeScores(homeScores)
                    .awayR(awayR).awayH(awayH).awayE(awayE).awayB(awayB)
                    .homeR(homeR).homeH(homeH).homeE(homeE).homeB(homeB)
                    .winPitcher(pitchers[0])
                    .losePitcher(pitchers[1])
                    .savePitcher(pitchers[2])
                    .build();

        } catch (Exception e) {
            log.error("ScoreBoard 추출 실패: {}", e.getMessage(), e);
            return createEmptyScoreBoardInfo();
        }
    }

    /**
     * 빈 ScoreBoardInfo를 생성합니다.
     */
    private ScoreBoardInfo createEmptyScoreBoardInfo() {
        List<Integer> emptyScores = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            emptyScores.add(0);
        }

        return ScoreBoardInfo.builder()
                .awayScores(emptyScores)
                .homeScores(new ArrayList<>(emptyScores))
                .awayR(0).awayH(0).awayE(0).awayB(0)
                .homeR(0).homeH(0).homeE(0).homeB(0)
                .build();
    }

    /**
     * 헤더에서 컬럼 인덱스를 찾습니다.
     */
    private Map<String, Integer> findColumnIndices(Elements headerCols) {
        Map<String, Integer> indices = new HashMap<>();
        String[] targets = {"R", "H", "E", "B"};

        for (int i = 0; i < headerCols.size(); i++) {
            String header = headerCols.get(i).text().trim();
            for (String target : targets) {
                if (header.equals(target)) {
                    indices.put(target, i);
                }
            }
        }

        log.debug("컬럼 인덱스: {}", indices);
        return indices;
    }

    /**
     * 1-9이닝 점수를 파싱합니다.
     */
    private List<Integer> parseInningScores(Elements cols) {
        List<Integer> scores = new ArrayList<>();

        for (int i = 1; i <= 9 && i < cols.size(); i++) {
            Element scoreElement = cols.get(i).selectFirst("div.score");
            String scoreText = scoreElement != null ? scoreElement.ownText().trim() : "0";

            int score = (scoreText.equals("-") || scoreText.isEmpty()) ? 0 : Integer.parseInt(scoreText);
            scores.add(score);
        }

        // 9이닝 미만이면 0으로 채우기
        while (scores.size() < 9) {
            scores.add(0);
        }

        return scores;
    }

    /**
     * 통계 값을 파싱합니다.
     */
    private int parseStatValue(Elements cols, Integer index) {
        if (index == null || index >= cols.size()) {
            return 0;
        }

        Element statElement = cols.get(index).selectFirst("div.score");
        if (statElement == null) {
            return 0;
        }

        String text = statElement.ownText().trim();
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 승부투수 정보를 추출합니다. [승리, 패배, 세이브]
     */
    private String[] extractPitchers(Document doc) {
        String[] pitchers = new String[3];  // [승리, 패배, 세이브]

        try {
            // 전략 1: game_result 영역에서 추출
            Element gameResultSection = doc.selectFirst("div.game_result");
            if (gameResultSection != null) {
                pitchers[0] = extractPitcherName(gameResultSection, ".win");
                pitchers[1] = extractPitcherName(gameResultSection, ".lose");
                pitchers[2] = extractPitcherName(gameResultSection, ".save");
            }

            // 전략 2: 투구기록 테이블에서 추출 (전략 1 실패 시)
            if (pitchers[0] == null || pitchers[1] == null) {
                extractPitchersFromTable(doc, pitchers);
            }

            log.debug("투수 정보 - 승: {}, 패: {}, 세: {}", pitchers[0], pitchers[1], pitchers[2]);

        } catch (Exception e) {
            log.error("투수 정보 추출 실패: {}", e.getMessage(), e);
        }

        return pitchers;
    }

    /**
     * 특정 셀렉터에서 투수 이름을 추출합니다.
     */
    private String extractPitcherName(Element section, String selector) {
        Element element = section.selectFirst(selector);
        if (element != null) {
            Element link = element.selectFirst("a");
            if (link != null) {
                return link.text().trim();
            }
        }
        return null;
    }

    /**
     * 투구기록 테이블에서 승부투수를 추출합니다.
     */
    private void extractPitchersFromTable(Document doc, String[] pitchers) {
        Elements pitcherSections = doc.select("div.box_type_boared:has(.box_head:contains(투구기록))");
        for (Element section : pitcherSections) {
            Element table = section.selectFirst("table");
            if (table == null) continue;

            Elements rows = table.select("tbody > tr:not(.total)");
            for (Element row : rows) {
                Elements cols = row.select("td");
                if (cols.isEmpty()) continue;

                String pitcherInfo = cols.get(0).text().trim();
                Matcher matcher = PITCHER_RESULT_PATTERN.matcher(pitcherInfo);

                if (matcher.find()) {
                    String name = pitcherInfo.substring(0, matcher.start()).trim();
                    String result = matcher.group(1);

                    if (result.equals("승") && pitchers[0] == null) {
                        pitchers[0] = name;
                    } else if (result.equals("패") && pitchers[1] == null) {
                        pitchers[1] = name;
                    } else if (result.equals("세") && pitchers[2] == null) {
                        pitchers[2] = name;
                    }
                }
            }
        }
    }

    /**
     * ScoreBoard를 저장합니다.
     */
    private void saveScoreBoard(Integer scheduleId, ScoreBoardInfo info) {
        try {
            ScoreBoard sb = ScoreBoard.builder()
                    .scheduleId(scheduleId)
                    .homeScore(info.getHomeR())
                    .awayScore(info.getAwayR())
                    .homeInningScores(toInningString(info.getHomeScores()))
                    .awayInningScores(toInningString(info.getAwayScores()))
                    .homeR(info.getHomeR()).homeH(info.getHomeH())
                    .homeE(info.getHomeE()).homeB(info.getHomeB())
                    .awayR(info.getAwayR()).awayH(info.getAwayH())
                    .awayE(info.getAwayE()).awayB(info.getAwayB())
                    .winPitcher(info.getWinPitcher())
                    .losePitcher(info.getLosePitcher())
                    .build();

            scoreBoardService.saveOrUpdate(sb);
            log.info("ScoreBoard 저장 완료 - scheduleId: {}", scheduleId);

        } catch (Exception e) {
            log.error("ScoreBoard 저장 실패 - scheduleId: {}: {}", scheduleId, e.getMessage(), e);
        }
    }

    // ==================== GameHighlight 처리 ====================

    /**
     * Summary 페이지에서 GameHighlight를 추출하고 저장합니다.
     */
    private void saveGameHighlights(Document doc, Integer scheduleId) {
        try {
            Element highlightTable = findHighlightTable(doc);
            if (highlightTable == null) {
                log.warn("결정적 장면 테이블을 찾을 수 없음");
                return;
            }

            Elements rows = highlightTable.select("tbody > tr");
            if (rows.isEmpty()) {
                rows = highlightTable.select("tr");
                if (!rows.isEmpty()) {
                    rows.remove(0);  // 헤더 행 제거
                }
            }

            log.info("결정적 장면: {} 개", rows.size());

            int ranking = 1;
            for (Element row : rows) {
                Elements tds = row.select("td");
                if (tds.size() >= 7) {
                    GameHighlightDTO dto = GameHighlightDTO.builder()
                            .scheduleId(scheduleId)
                            .ranking(ranking++)
                            .inning(tds.get(0).text().trim())
                            .pitcherName(tds.get(1).text().trim())
                            .batterName(tds.get(2).text().trim())
                            .pitchCount(tds.get(3).text().trim())
                            .result(tds.get(4).text().trim())
                            .beforeSituation(tds.get(5).text().trim())
                            .afterSituation(tds.get(6).text().trim())
                            .build();

                    gameHighlightService.saveOrUpdate(dto);
                    log.debug("GameHighlight 저장: ranking {}", ranking - 1);
                }
            }

        } catch (Exception e) {
            log.error("GameHighlight 저장 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 결정적 장면 테이블을 찾습니다.
     */
    private Element findHighlightTable(Document doc) {
        // 전략 1: 기본 선택자
        Element table = doc.selectFirst(SELECTOR_HIGHLIGHT_TABLE);
        if (table != null) return table;

        // 전략 2: 대체 선택자들
        String[] selectors = {
            "div:contains(결정적 장면 Best 5) table",
            "h5:contains(결정적 장면) ~ table"
        };

        for (String selector : selectors) {
            table = doc.selectFirst(selector);
            if (table != null) {
                log.debug("대체 선택자로 하이라이트 테이블 발견: {}", selector);
                return table;
            }
        }

        // 전략 3: 모든 테이블 검사
        Elements allTables = doc.select("table");
        for (Element tbl : allTables) {
            String text = tbl.text();
            if ((text.contains("결정적") || text.contains("장면")) &&
                text.contains("이닝") && text.contains("투수") && text.contains("타자")) {
                log.debug("패턴 매칭으로 하이라이트 테이블 발견");
                return tbl;
            }
        }

        return null;
    }

    // ==================== Boxscore 처리 ====================

    /**
     * Boxscore 페이지를 크롤링하고 타자/투수 기록을 저장합니다.
     */
    private void crawlBoxscore(WebDriver driver, int statizId, Integer scheduleId,
                                Integer homeTeamId, Integer awayTeamId) {
        try {
            String url = "https://statiz.sporki.com/schedule/?m=boxscore&s_no=" + statizId;
            log.debug("Boxscore 페이지 접속: {}", url);

            driver.get(url);
            Thread.sleep(PAGE_LOAD_WAIT_MS);

            String html = driver.getPageSource();
            Document doc = Jsoup.parse(html);

            // 도루 정보 추출
            Map<String, Integer> sbMap = extractStolenBases(doc);
            log.debug("도루 데이터: {} 개", sbMap.size());

            // 타자 기록 저장
            saveBatterRecords(doc, scheduleId, awayTeamId, sbMap);
            saveBatterRecords(doc, scheduleId, homeTeamId, sbMap);

            // 투수 기록 저장
            savePitcherRecords(doc, scheduleId);

            log.info("Boxscore 크롤링 완료 - scheduleId: {}", scheduleId);

        } catch (Exception e) {
            log.error("Boxscore 크롤링 실패 - scheduleId: {}: {}", scheduleId, e.getMessage(), e);
        }
    }

    // ==================== 유틸리티 메서드 ====================

    /**
     * 이닝 점수 리스트를 문자열로 변환합니다.
     */
    private String toInningString(List<Integer> scores) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < scores.size(); i++) {
            result.append(scores.get(i));
            if (i < scores.size() - 1) {
                result.append(" ");
            }
        }
        return result.toString();
    }

    // ==================== Helper 메서드 (Boxscore 관련) ====================

    /**
     * 도루 정보를 추출합니다.
     */
    private Map<String, Integer> extractStolenBases(Document doc) {
        Map<String, Integer> sbMap = new HashMap<>();

        Elements logBoxes = doc.select("div.box_type_boared .log_box");
        for (Element box : logBoxes) {
            Elements divs = box.select("div.log_div");
            for (int i = 0; i < divs.size(); i++) {
                Element div = divs.get(i);
                Element strong = div.selectFirst("strong");
                if (strong != null && strong.text().contains("도루성공")) {
                    Elements spans = div.select("span");
                    for (int j = 0; j < spans.size(); j++) {
                        Element a = spans.get(j).selectFirst("a");
                        if (a != null) {
                            String name = a.text().trim();
                            if (sbMap.containsKey(name)) {
                                sbMap.put(name, sbMap.get(name) + 1);
                            } else {
                                sbMap.put(name, 1);
                            }
                        }
                    }
                }
            }
        }

        return sbMap;
    }

    // 타자 기록 저장
    private void saveBatterRecords(Document doc, int scheduleId, int teamId, Map<String, Integer> sbMap) {
        Elements sections = doc.select("div.box_type_boared");

        for (int i = 0; i < sections.size(); i++) {
            Element section = sections.get(i);
            Element head = section.selectFirst(".box_head");
            if (head == null || !head.text().contains("타격기록")) continue;

            String teamName = head.text().replaceAll(".*\\((.*?)\\).*", "$1").trim();
            Integer extractedTeamId = getTeamId(teamName);
            if (extractedTeamId == null || !extractedTeamId.equals(teamId)) continue;

            Element table = section.selectFirst("table");
            if (table == null) continue;

            Elements rows = table.select("tbody > tr:not(.total)");
            for (int r = 0; r < rows.size(); r++) {
                Element row = rows.get(r);
                Elements cols = row.select("td");
                if (cols.size() < 22) continue;

                String name = cols.get(1).text().trim();

                Optional<Player> player = playerService.findByNameAndTeamId(name, teamId);
                if (player.isEmpty()) {
                    playerService.savePlayer(name, teamId);
                    player = playerService.findByNameAndTeamId(name, teamId);
                }

                try {
                    int pa = Integer.parseInt(cols.get(3).text().trim());
                    int ab = Integer.parseInt(cols.get(4).text().trim());
                    int hits = Integer.parseInt(cols.get(6).text().trim());
                    int hr = Integer.parseInt(cols.get(7).text().trim());
                    int rbi = Integer.parseInt(cols.get(8).text().trim());
                    int bb = Integer.parseInt(cols.get(9).text().trim());
                    int so = Integer.parseInt(cols.get(11).text().trim());
                    int sb = sbMap.containsKey(name) ? sbMap.get(name) : 0;
                    int order = cols.get(0).text().isEmpty() ? 0 : Integer.parseInt(cols.get(0).text().trim());
                    String pos = cols.get(2).text().trim();

                    lineupService.saveBatterLineup(scheduleId, teamId, order, pos, name);
                    recordService.saveBatterRecord(scheduleId, teamId, pa, ab, hits, hr, rbi, bb, so, sb, name);

                } catch (Exception e) {
                    log.info("타자 저장 중 에러: " + name + " - " + e.getMessage());
                }
            }
        }
    }

    // 투수 기록 저장
    private void savePitcherRecords(Document doc, int scheduleId) {
        Elements sections = doc.select("div.box_type_boared");

        for (int i = 0; i < sections.size(); i++) {
            Element section = sections.get(i);
            Element head = section.selectFirst(".box_head");
            if (head == null || !head.text().contains("투구기록")) continue;

            String teamName = head.text().replaceAll(".*\\((.*?)\\).*", "$1").trim();
            Integer teamId = getTeamId(teamName);
            if (teamId == null) continue;

            Element table = section.selectFirst(".table_type03 table");
            if (table == null) continue;

            Elements rows = table.select("tbody > tr:not(.total)");
            for (int r = 0; r < rows.size(); r++) {
                Element row = rows.get(r);
                Elements cols = row.select("td");
                if (cols.size() < 18) continue;

                Element nameAnchor = cols.get(0).selectFirst("a");
                if (nameAnchor == null) continue;

                String fullText = cols.get(0).text();
                String name = fullText.replaceAll("\\s*\\([^)]*\\)", "").trim();
                String decision = "";
                if (fullText.contains("(승")) decision = "W";
                else if (fullText.contains("(패")) decision = "L";
                else if (fullText.contains("(세")) decision = "SV";
                else if (fullText.contains("(홀")) decision = "HLD";

                Optional<Player> player = playerService.findByNameAndTeamId(name, teamId);
                if (player.isEmpty()) {
                    playerService.savePlayer(name, teamId);
                    player = playerService.findByNameAndTeamId(name, teamId);
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

                } catch (Exception e) {
                    log.info("투수 저장 중 에러: " + name + " - " + e.getMessage());
                }
            }
        }
    }

    // ==================== 내부 DTO ====================

    /**
     * 경기 기본 정보를 담는 DTO
     */
    @Data
    @Builder
    private static class GameInfo {
        private int statizId;
        private String awayTeam;
        private String homeTeam;
        private Integer awayTeamId;
        private Integer homeTeamId;
        private Integer awayScore;
        private Integer homeScore;
        private LocalDateTime matchDateTime;
        private String stadium;
        private String status;
    }

    /**
     * 스코어보드 정보를 담는 DTO
     */
    @Data
    @Builder
    private static class ScoreBoardInfo {
        private List<Integer> awayScores;
        private List<Integer> homeScores;
        private int awayR;
        private int awayH;
        private int awayE;
        private int awayB;
        private int homeR;
        private int homeH;
        private int homeE;
        private int homeB;
        private String winPitcher;
        private String losePitcher;
        private String savePitcher;
    }

    /**
     * 크롤링 결과를 담는 DTO
     */
    @Data
    @Builder
    private static class CrawlResult {
        private boolean success;
        private int gameCount;
        private String errorMessage;
    }

    /**
     * 팀 정보를 담는 DTO
     */
    @Data
    @Builder
    private static class TeamInfo {
        private String name;
        private Integer id;
        private Integer score;
    }
}
