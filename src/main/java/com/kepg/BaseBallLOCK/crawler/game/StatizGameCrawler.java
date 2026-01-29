package com.kepg.BaseBallLOCK.crawler.game;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    /**
     * 매일 새벽 4시에 자동으로 오늘 경기를 크롤링합니다.
     */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void crawlToday() {
        LocalDate today = LocalDate.now();
        crawlGameRange(today, today);
        log.info("[자동 크롤링 완료] 오늘 날짜: " + today);
    }

    /**
     * 지정된 날짜 범위의 경기를 크롤링합니다 (수동 실행용).
     */
    // statizId 추출할 날짜 범위 지정
    public void crawlGameRange(LocalDate startDate, LocalDate endDate) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage");
        WebDriver driver = new ChromeDriver(options);

        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            List<Integer> statizIds = getStatizIdsFromDailyPage(driver, currentDate);

            log.info("[일자] " + currentDate + " → 추출된 경기 수: " + statizIds.size());
            for (int i = 0; i < statizIds.size(); i++) {
                int statizId = statizIds.get(i);
                log.info("▶ [" + (i + 1) + "/" + statizIds.size() + "] statizId = " + statizId);
                try {
                    processGame(driver, statizId, currentDate);
                } catch (Exception e) {
                    log.info("[오류 발생] statizId: " + statizId);
                    e.printStackTrace();
                }
            }

            currentDate = currentDate.plusDays(1);
        }

        driver.quit();
    }
    
    // 날짜별 statizId 추출
    public List<Integer> getStatizIdsFromDailyPage(WebDriver driver, LocalDate date) {
        List<Integer> statizIds = new ArrayList<>();

        try {
            String dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String url = "https://statiz.sporki.com/schedule/?m=daily&date=" + dateStr;
            log.info("크롤링 URL: " + url);
            driver.get(url);

            // 페이지 로딩 대기 (기본 3초)
            Thread.sleep(3000);

            String html = driver.getPageSource();
            Document doc = Jsoup.parse(html);
            
            // 페이지 전체 구조 확인
            Elements gameBoxes = doc.select(".box_type_boared .item_box");
            log.info("경기 박스 개수: " + gameBoxes.size());
            
            if (gameBoxes.isEmpty()) {
                log.info("경기 박스가 없습니다. 다른 셀렉터를 시도합니다.");
                Elements allBoxes = doc.select(".item_box");
                log.info("전체 item_box 개수: " + allBoxes.size());
                
                Elements boaredBoxes = doc.select(".box_type_boared");
                log.info("box_type_boared 개수: " + boaredBoxes.size());
            }

            // 개선된 statizId 추출 방식 - btn_box 링크를 우선으로 사용
            Elements links = null;
            
            // 첫 번째 시도: btn_box 안의 링크에서 s_no 추출 (가장 정확한 방법)
            links = doc.select("div.btn_box a[href*='s_no=']");
            log.info("첫 번째 시도 (btn_box s_no) 결과: " + links.size() + "개");
            
            // 두 번째 시도: game_result 근처의 링크
            if (links.isEmpty()) {
                links = doc.select("div.game_result ~ div.btn_box a[href*='s_no=']");
                log.info("두 번째 시도 (game_result + btn_box) 결과: " + links.size() + "개");
            }
            
            // 세 번째 시도: summary 링크 (기존 방식 개선)
            if (links.isEmpty()) {
                links = doc.select("a[href*='summary'][href*='s_no=']");
                log.info("세 번째 시도 (summary s_no) 결과: " + links.size() + "개");
            }
            
            // 네 번째 시도: boxscore 링크
            if (links.isEmpty()) {
                links = doc.select("a[href*='boxscore'][href*='s_no=']");
                log.info("네 번째 시도 (boxscore s_no) 결과: " + links.size() + "개");
            }
            
            // 다섯 번째 시도: 모든 s_no 링크
            if (links.isEmpty()) {
                links = doc.select("a[href*='s_no=']");
                log.info("다섯 번째 시도 (모든 s_no) 결과: " + links.size() + "개");
            }
            
            // 여섯 번째 시도: 전체 링크 검사
            if (links.isEmpty()) {
                links = doc.select("a[href]");
                log.info("여섯 번째 시도: 전체 링크 " + links.size() + "개 검사");
                
                // 첫 10개 링크의 href 출력해서 디버깅
                for (int i = 0; i < Math.min(10, links.size()); i++) {
                    Element link = links.get(i);
                    log.info("  링크 " + (i+1) + ": " + link.attr("href") + " - " + link.text().trim());
                }
            }
            
           

            for (Element link : links) {
                String href = link.attr("href");
                if (href.contains("s_no=")) {
                    try {
                        String[] parts = href.split("s_no=");
                        if (parts.length >= 2) {
                            // URL 파라미터 분리 
                            String statizIdStr = parts[1].split("&")[0].trim();
                            int statizId = Integer.parseInt(statizIdStr);
                            if (!statizIds.contains(statizId)) {
                                statizIds.add(statizId);
                                log.info("statizId 추출 성공: " + statizId + " from " + href);
                            }
                        }
                    } catch (NumberFormatException e) {
                        log.info("statizId 파싱 실패: " + href);
                    }
                }
            }

            log.info("최종 statizId 추출 완료: " + statizIds);

        } catch (Exception e) {
            log.info("statizId 추출 중 오류 발생");
            e.printStackTrace();
        }

        return statizIds;
    }
    
    public void processGame(WebDriver driver, int statizId, LocalDate expectedDate) {

        try {
            log.info("\n[시작] statizId = " + statizId);

            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage");
            driver = new ChromeDriver(options);

            // ----------------------- summary 크롤링 ----------------------
            String summaryUrl = "https://statiz.sporki.com/schedule/?m=summary&s_no=" + statizId;
            driver.get(summaryUrl);
            Thread.sleep(3000);

            String html = driver.getPageSource();
            Document doc = Jsoup.parse(html);

            Element scoreTable = doc.selectFirst("div.box_type_boared > div.item_box.w100 .table_type03 table");
            if (scoreTable == null) {
                // 다른 선택자로 시도
                scoreTable = doc.selectFirst("div.box_type_boared .table_type03 table");
                if (scoreTable == null) {
                    scoreTable = doc.selectFirst(".table_type03 table");
                    if (scoreTable == null) {
                        // 더 포괄적인 선택자 시도
                        scoreTable = doc.selectFirst("table.table_type03");
                        if (scoreTable == null) {
                            scoreTable = doc.selectFirst("div.score_table table");
                            if (scoreTable == null) {
                                // 모든 테이블을 확인하여 점수가 있는 테이블 찾기
                                Elements allTables = doc.select("table");
                                for (Element table : allTables) {
                                    if (table.text().contains("이닝") || table.text().contains("팀")) {
                                        scoreTable = table;
                                        log.info("대체 테이블 사용: " + table.select("tr").size() + "행");
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                if (scoreTable == null) {
                    log.info("[중단] 점수 테이블(scoreTable) 없음 - statizId: " + statizId);
                    log.info("페이지 HTML 구조 확인:");
                    Elements allTables = doc.select("table");
                    log.info("전체 테이블 수: " + allTables.size());
                    for (int i = 0; i < Math.min(3, allTables.size()); i++) {
                        log.info("테이블 " + i + ": " + allTables.get(i).select("tr").size() + "행");
                        log.info("  내용: " + allTables.get(i).text().substring(0, Math.min(100, allTables.get(i).text().length())));
                    }
                    return;
                }
            }

            Elements rows = scoreTable.select("tbody > tr");
            if (rows.size() < 2) {
                log.info("[중단] 경기 정보 행(row) 부족 - statizId: " + statizId + ", 행 수: " + rows.size());
                // 경기가 아직 시작하지 않았거나 취소된 경우일 수 있음
                Elements allRows = scoreTable.select("tr");
                log.info("전체 행 수: " + allRows.size());
                if (allRows.size() > 0) {
                    log.info("첫 번째 행: " + allRows.get(0).text());
                }
            	return;
            }

            Elements awayTds = rows.get(0).select("td");
            Elements homeTds = rows.get(1).select("td");

            // 팀 이름 추출 - 링크에서 팀 이름 가져오기
            String awayTeam = "";
            String homeTeam = "";
            
            try {
                // away 팀 이름 추출
                Element awayTeamLink = awayTds.get(0).selectFirst("a");
                if (awayTeamLink != null) {
                    awayTeam = awayTeamLink.text().trim();
                } else {
                    awayTeam = awayTds.get(0).text().trim();
                }
                
                // home 팀 이름 추출
                Element homeTeamLink = homeTds.get(0).selectFirst("a");
                if (homeTeamLink != null) {
                    homeTeam = homeTeamLink.text().trim();
                } else {
                    homeTeam = homeTds.get(0).text().trim();
                }
                
                log.info("[팀 이름 추출] Away: '" + awayTeam + "', Home: '" + homeTeam + "'");
                
                // 디버깅을 위해 첫 번째 컬럼의 HTML 구조 출력
                log.info("[디버깅] Away TD HTML: " + awayTds.get(0).html());
                log.info("[디버깅] Home TD HTML: " + homeTds.get(0).html());
                
            } catch (Exception e) {
                log.info("팀 이름 추출 실패: " + e.getMessage());
                e.printStackTrace();
                return;
            }

            int awayTeamId = teamNameToId.getOrDefault(awayTeam, 0);
            int homeTeamId = teamNameToId.getOrDefault(homeTeam, 0);
            if (awayTeamId == 0 || homeTeamId == 0) {
            	log.info(String.format("[중단] 팀 ID 매핑 실패: away='%s' → %d, home='%s' → %d\n",
                        awayTeam, awayTeamId, homeTeam, homeTeamId));
            	
            	// 사용 가능한 팀 이름 목록 출력
            	log.info("매핑 가능한 팀 이름 목록:");
            	for (String teamName : teamNameToId.keySet()) {
            	    log.info("  - '" + teamName + "' → " + teamNameToId.get(teamName));
            	}
            	
            	// 전체 행의 구조 확인
            	log.info("\n전체 테이블 구조 확인:");
            	for (int i = 0; i < Math.min(rows.size(), 3); i++) {
            	    Elements rowTds = rows.get(i).select("td");
            	    log.info("행 " + i + " (" + rowTds.size() + "개 컬럼):");
            	    for (int j = 0; j < Math.min(rowTds.size(), 5); j++) {
            	        log.info("  컬럼 " + j + ": " + rowTds.get(j).text().trim());
            	    }
            	}
            	return;
            }
            
            // ----------------------- schedule 크롤링 ----------------------
            // 기존 Schedule 데이터에서 정보 가져오기
            Optional<Schedule> existingSchedule = scheduleService.findByStatizId(statizId);
            
            String status = "종료";
            String stadium = "미정";
            LocalDateTime matchDateTime1 = null;
            
            // 중복 방지: 상세 정보가 완전한 경기는 건너뛰기 (더 정확한 검사)
            if (existingSchedule.isPresent()) {
                Schedule existing = existingSchedule.get();
                
                // 1. 기본 점수 정보가 있는지 확인
                boolean hasBasicScore = existing.getHomeTeamScore() != null && existing.getAwayTeamScore() != null && 
                                      existing.getStatizId() != null && existing.getStatizId().equals(statizId);
                
                // 2. 상세 데이터가 있는지 확인 (scheduleId 기준으로)
                boolean hasDetailedData = false;
                if (hasBasicScore) {
                    Integer scheduleIdForCheck = scheduleService.findIdByStatizId(statizId);
                    if (scheduleIdForCheck != null) {
                        // ScoreBoard 데이터 확인
                        boolean hasScoreBoard = scoreBoardService.findByScheduleId(scheduleIdForCheck) != null;
                        // GameHighlight 데이터 확인  
                        boolean hasGameHighlight = !gameHighlightService.findByScheduleId(scheduleIdForCheck).isEmpty();
                        // BatterRecord와 PitcherRecord는 recordService를 통해 확인
                        boolean hasBatterRecord = !recordService.getBatterRecords(scheduleIdForCheck, existing.getHomeTeamId()).isEmpty() ||
                                                !recordService.getBatterRecords(scheduleIdForCheck, existing.getAwayTeamId()).isEmpty();
                        boolean hasPitcherRecord = !recordService.getPitcherRecords(scheduleIdForCheck, existing.getHomeTeamId()).isEmpty() ||
                                                 !recordService.getPitcherRecords(scheduleIdForCheck, existing.getAwayTeamId()).isEmpty();
                        
                        hasDetailedData = hasScoreBoard && hasGameHighlight && hasBatterRecord && hasPitcherRecord;
                        
                        log.info(String.format("[상세 정보 확인] statizId: %s (scheduleId: %s) - ScoreBoard: %s, GameHighlight: %s, BatterRecord: %s, PitcherRecord: %s\n",
                            statizId, scheduleIdForCheck, hasScoreBoard ? "✓" : "✗", hasGameHighlight ? "✓" : "✗",
                            hasBatterRecord ? "✓" : "✗", hasPitcherRecord ? "✓" : "✗"));
                    }
                }
                
                if (hasBasicScore && hasDetailedData) {
                    log.info("[건너뛰기] 이미 완전한 상세 정보가 있는 경기 - statizId: " + statizId);
                    return;
                } else if (hasBasicScore) {
                    log.info("[부분 크롤링] 기본 점수는 있지만 상세 데이터가 부족한 경기 - statizId: " + statizId);
                }
                
                status = existing.getStatus() != null ? existing.getStatus() : "종료";
                stadium = existing.getStadium() != null ? existing.getStadium() : "미정";
                matchDateTime1 = existing.getMatchDate() != null ? existing.getMatchDate().toLocalDateTime() : null;
            }
            
            // 실제 경기 시간 정보 크롤링 시도
            try {
                log.info("[시간 크롤링] statizId: " + statizId + " 시작");
                
                // 방법 1: 스코어 박스의 "잠실, 07-03" 형태 날짜 정보 찾기
                Element scoreBox = doc.selectFirst("div.callout_box .game_schedule .txt");
                if (scoreBox != null) {
                    String scoreBoxText = scoreBox.text().trim();
                    log.info("[시간 크롤링] 스코어박스 텍스트: " + scoreBoxText);
                    
                    // "잠실, 07-03" 패턴에서 날짜 추출
                    if (scoreBoxText.matches(".*\\d{2}-\\d{2}.*")) {
                        try {
                            String datePattern = scoreBoxText.replaceAll(".*?(\\d{2})-(\\d{2}).*", "$1-$2");
                            log.info("[시간 크롤링] 추출된 날짜 패턴: " + datePattern);
                            
                            String[] dateParts = datePattern.split("-");
                            int month = Integer.parseInt(dateParts[0]);
                            int day = Integer.parseInt(dateParts[1]);
                            
                            // 년도는 expectedDate에서 가져오기
                            int year = expectedDate.getYear();
                            
                            // 기본 시간은 18:30으로 설정 (대부분의 경기 시간)
                            matchDateTime1 = LocalDate.of(year, month, day).atTime(18, 30);
                            log.info("스코어박스에서 경기 날짜 크롤링 성공: " + matchDateTime1);
                            
                            // 경기장 정보도 추출
                            if (scoreBoxText.contains(",")) {
                                String stadiumKey = scoreBoxText.split(",")[0].trim();
                                if (stadiumNameMap.containsKey(stadiumKey)) {
                                    stadium = stadiumNameMap.get(stadiumKey);
                                    log.info("경기장 정보 추출: " + stadiumKey + " -> " + stadium);
                                }
                            }
                        } catch (Exception parseEx) {
                            log.info("[시간 크롤링] 스코어박스 파싱 실패: " + parseEx.getMessage());
                        }
                    }
                }
                
                // 방법 2: box_head에서 직접 날짜/시간 텍스트 찾기 (fallback)
                if (matchDateTime1 == null) {
                    Elements boxHeads = doc.select("div.box_head");
                    for (Element boxHead : boxHeads) {
                        String headText = boxHead.text();
                        log.info("[시간 크롤링] box_head 텍스트: " + headText);
                        
                        // "07-17 18:30 (잠실)" 패턴 찾기
                        if (headText.matches(".*\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}.*")) {
                            try {
                                // 월-일 시:분 패턴 추출
                                String dateTimePattern = headText.replaceAll(".*?(\\d{2})-(\\d{2})\\s+(\\d{2}):(\\d{2}).*", "$1-$2 $3:$4");
                                log.info("[시간 크롤링] 추출된 패턴: " + dateTimePattern);
                                
                                String[] parts = dateTimePattern.split(" ");
                                String[] dateParts = parts[0].split("-");
                                String[] timeParts = parts[1].split(":");
                                
                                int month = Integer.parseInt(dateParts[0]);
                                int day = Integer.parseInt(dateParts[1]);
                                int hour = Integer.parseInt(timeParts[0]);
                                int minute = Integer.parseInt(timeParts[1]);
                                
                                // 년도는 expectedDate에서 가져오기
                                int year = expectedDate.getYear();
                                
                                matchDateTime1 = LocalDate.of(year, month, day).atTime(hour, minute);
                                log.info("box_head에서 경기 시간 크롤링 성공: " + matchDateTime1);
                                
                                // 경기장 정보도 추출
                                if (headText.contains("(") && headText.contains(")")) {
                                    String stadiumKey = headText.replaceAll(".*\\(([^)]+)\\).*", "$1");
                                    if (stadiumNameMap.containsKey(stadiumKey)) {
                                        stadium = stadiumNameMap.get(stadiumKey);
                                        log.info("경기장 정보 추출: " + stadiumKey + " -> " + stadium);
                                    }
                                }
                                break; // 찾으면 종료
                            } catch (Exception parseEx) {
                                log.info("[시간 크롤링] 파싱 실패: " + parseEx.getMessage());
                            }
                        }
                    }
                }
                
                // 방법 2: 기존 방법 (fallback)
                if (matchDateTime1 == null) {
                    Element gameInfoBox = doc.selectFirst("div.box_type_boared .item_box.w100");
                    if (gameInfoBox != null) {
                        log.info("[시간 크롤링] gameInfoBox 찾음: " + gameInfoBox.text());
                        
                        Element dateTimeElement = gameInfoBox.selectFirst("strong");
                        if (dateTimeElement != null) {
                            String dateTimeText = dateTimeElement.text().trim();
                            log.info("[시간 크롤링] dateTimeText: " + dateTimeText);
                            
                            // 기존 파싱 로직...
                        }
                    }
                }
            } catch (Exception e) {
                log.info("경기 시간/경기장 크롤링 실패: " + e.getMessage());
                e.printStackTrace();
            }
            
            if (existingSchedule.isPresent()) {
                Schedule existing = existingSchedule.get();
                status = existing.getStatus() != null ? existing.getStatus() : "종료";
                if (stadium.equals("미정") && existing.getStadium() != null) {
                    stadium = existing.getStadium();
                }
                if (matchDateTime1 == null && existing.getMatchDate() != null) {
                    matchDateTime1 = existing.getMatchDate().toLocalDateTime();
                }
            } else {
                log.info("기존 Schedule 정보를 찾을 수 없음 - statizId: " + statizId);
                // 기본값으로 크롤링 대상 날짜 사용 (실제 크롤링이 실패한 경우에만)
                if (matchDateTime1 == null) {
                    matchDateTime1 = expectedDate.atTime(18, 30);
                    log.info("기본 날짜 사용: " + matchDateTime1);
                }
            }

            // 점수 파싱 - HTML 구조에 맞게 수정
            Integer awayScore = null;
            Integer homeScore = null;
            try {
                // HTML 구조를 보면 R, H, E, B는 각각 13, 14, 15, 16번째 컬럼입니다
                // 하지만 실제로는 연장전이 없으면 10, 11, 12번째 컬럼이 비어있고
                // R은 13번째가 아니라 더 뒤에 있을 수 있습니다
                
                // 헤더에서 R 컬럼의 위치를 동적으로 찾기
                int rColumnIndex = -1;
                Elements headerCols = scoreTable.select("thead > tr > th");
                for (int i = 0; i < headerCols.size(); i++) {
                    String headerText = headerCols.get(i).text().trim();
                    if (headerText.equals("R")) {
                        rColumnIndex = i;
                        log.info("R 컬럼 위치 찾음: " + i);
                        break;
                    }
                }
                
                if (rColumnIndex != -1 && rColumnIndex < awayTds.size() && rColumnIndex < homeTds.size()) {
                    Element awayScoreElement = awayTds.get(rColumnIndex).selectFirst("div.score");
                    Element homeScoreElement = homeTds.get(rColumnIndex).selectFirst("div.score");
                    
                    if (awayScoreElement != null && homeScoreElement != null) {
                        String awayScoreText = awayScoreElement.ownText().trim();
                        String homeScoreText = homeScoreElement.ownText().trim();
                        
                        if (!awayScoreText.equals("-") && !awayScoreText.isEmpty()) {
                            awayScore = Integer.parseInt(awayScoreText);
                        }
                        if (!homeScoreText.equals("-") && !homeScoreText.isEmpty()) {
                            homeScore = Integer.parseInt(homeScoreText);
                        }
                        
                        log.info("점수 파싱 성공: " + awayTeam + " " + awayScore + " vs " + homeScore + " " + homeTeam);
                    }
                } else {
                    log.info("R 컬럼을 찾을 수 없음. 헤더 개수: " + headerCols.size() + ", Away TD 개수: " + awayTds.size());
                    // 디버깅을 위해 헤더 출력
                    for (int i = 0; i < headerCols.size(); i++) {
                        log.info("헤더 " + i + ": " + headerCols.get(i).text());
                    }
                }
            } catch (Exception e) {
                log.info("점수 파싱 실패: " + e.getMessage());
                log.info("awayTds 크기: " + awayTds.size() + ", homeTds 크기: " + homeTds.size());
                e.printStackTrace();
            }

            // Schedule 저장
            Schedule schedule = new Schedule();
            schedule.setMatchDate(matchDateTime1 != null ? Timestamp.valueOf(matchDateTime1) : null);
            schedule.setHomeTeamId(homeTeamId);
            schedule.setAwayTeamId(awayTeamId);
            schedule.setHomeTeamScore(homeScore);
            schedule.setAwayTeamScore(awayScore);
            schedule.setStadium(stadium);
            schedule.setStatus(status);
            schedule.setStatizId(statizId);

            try {
                scheduleService.saveOrUpdate(schedule);
                log.info(String.format(
                    "[Schedule 업데이트 완료] statizId=%d, matchDateTime=%s, %d vs %d (%s)\n",
                    statizId, matchDateTime1, homeTeamId, awayTeamId, status
                ));
            } catch (Exception e) {
                log.info("[Schedule 저장 실패] statizId: " + statizId + ", 오류: " + e.getMessage());
                // 중복 데이터 문제일 가능성이 높으므로 계속 진행
            }         

            // ----------------------- scoreBox 크롤링 ----------------------
            Integer scheduleId = null;
            try {
                scheduleId = scheduleService.findIdByStatizId(statizId);
            } catch (Exception e) {
                log.info("Schedule ID 조회 실패 - statizId: " + statizId + ", 오류: " + e.getMessage());
                // 중복 데이터가 있을 수 있으므로 다른 방법으로 시도
                try {
                    // 팀 정보와 날짜로 다시 시도
                    if (matchDateTime1 != null) {
                        scheduleId = scheduleService.findScheduleIdByMatchInfo(
                            Timestamp.valueOf(matchDateTime1), homeTeamId, awayTeamId);
                    }
                } catch (Exception e2) {
                    log.info("대체 방법으로도 Schedule ID 조회 실패: " + e2.getMessage());
                }
            }
            
            if (scheduleId == null) {
                log.info("Schedule ID를 찾을 수 없어 상세 정보 크롤링을 건너뜁니다 - statizId: " + statizId);
                return;
            }

            // 이닝별 점수와 R, H, E, B 통계 파싱
            List<Integer> awayScores = new ArrayList<>();
            List<Integer> homeScores = new ArrayList<>();
            int awayR = 0, awayH = 0, awayE = 0, awayB = 0;
            int homeR = 0, homeH = 0, homeE = 0, homeB = 0;
            
            try {
                // 헤더에서 각 컬럼의 위치를 동적으로 찾기
                Elements headerCols = scoreTable.select("thead > tr > th");
                int rIndex = -1, hIndex = -1, eIndex = -1, bIndex = -1;
                
                for (int i = 0; i < headerCols.size(); i++) {
                    String headerText = headerCols.get(i).text().trim();
                    if (headerText.equals("R")) rIndex = i;
                    else if (headerText.equals("H")) hIndex = i;
                    else if (headerText.equals("E")) eIndex = i;
                    else if (headerText.equals("B")) bIndex = i;
                }
                
                log.info("컬럼 인덱스 - R: " + rIndex + ", H: " + hIndex + ", E: " + eIndex + ", B: " + bIndex);
                
                // 1-9이닝 점수 파싱 (1번째부터 9번째 컬럼까지)
                for (int i = 1; i <= 9 && i < awayTds.size() && i < homeTds.size(); i++) {
                    Element awayScoreTd = awayTds.get(i).selectFirst("div.score");
                    Element homeScoreTd = homeTds.get(i).selectFirst("div.score");

                    String awayText = awayScoreTd != null ? awayScoreTd.ownText().trim() : "0";
                    String homeText = homeScoreTd != null ? homeScoreTd.ownText().trim() : "0";

                    awayScores.add(awayText.equals("-") || awayText.isEmpty() ? 0 : Integer.parseInt(awayText));
                    homeScores.add(homeText.equals("-") || homeText.isEmpty() ? 0 : Integer.parseInt(homeText));
                }
                
                // R, H, E, B 통계 파싱
                if (rIndex != -1 && rIndex < awayTds.size() && rIndex < homeTds.size()) {
                    Element awayRElement = awayTds.get(rIndex).selectFirst("div.score");
                    Element homeRElement = homeTds.get(rIndex).selectFirst("div.score");
                    if (awayRElement != null) awayR = Integer.parseInt(awayRElement.ownText().trim());
                    if (homeRElement != null) homeR = Integer.parseInt(homeRElement.ownText().trim());
                }
                
                if (hIndex != -1 && hIndex < awayTds.size() && hIndex < homeTds.size()) {
                    Element awayHElement = awayTds.get(hIndex).selectFirst("div.score");
                    Element homeHElement = homeTds.get(hIndex).selectFirst("div.score");
                    if (awayHElement != null) awayH = Integer.parseInt(awayHElement.ownText().trim());
                    if (homeHElement != null) homeH = Integer.parseInt(homeHElement.ownText().trim());
                }
                
                if (eIndex != -1 && eIndex < awayTds.size() && eIndex < homeTds.size()) {
                    Element awayEElement = awayTds.get(eIndex).selectFirst("div.score");
                    Element homeEElement = homeTds.get(eIndex).selectFirst("div.score");
                    if (awayEElement != null) awayE = Integer.parseInt(awayEElement.ownText().trim());
                    if (homeEElement != null) homeE = Integer.parseInt(homeEElement.ownText().trim());
                }
                
                if (bIndex != -1 && bIndex < awayTds.size() && bIndex < homeTds.size()) {
                    Element awayBElement = awayTds.get(bIndex).selectFirst("div.score");
                    Element homeBElement = homeTds.get(bIndex).selectFirst("div.score");
                    if (awayBElement != null) awayB = Integer.parseInt(awayBElement.ownText().trim());
                    if (homeBElement != null) homeB = Integer.parseInt(homeBElement.ownText().trim());
                }
                
                log.info("이닝별 점수 파싱 완료 - Away: " + awayScores + ", Home: " + homeScores);
                log.info("통계 파싱 완료 - Away R:" + awayR + " H:" + awayH + " E:" + awayE + " B:" + awayB);
                log.info("통계 파싱 완료 - Home R:" + homeR + " H:" + homeH + " E:" + homeE + " B:" + homeB);
                
            } catch (Exception e) {
                log.info("스코어보드 통계 파싱 실패: " + e.getMessage());
                e.printStackTrace();
                
                // 에러 발생 시 기본값으로 설정
                for (int i = awayScores.size(); i < 9; i++) {
                    awayScores.add(0);
                    homeScores.add(0);
                }
            }

            // 승리/패배 투수 정보 추출 - 여러 방법으로 시도
            String winPitcher = null;
            String losePitcher = null;
            String savePitcher = null;
            
            try {
                log.info("투수 정보 추출 시작");
                
                // 방법 1: 경기 결과 영역에서 승부투수 찾기
                Element gameResultSection = doc.selectFirst("div.game_result");
                if (gameResultSection != null) {
                    log.info("game_result 영역 발견");
                    
                    // 승리 투수
                    Element winElement = gameResultSection.selectFirst(".win");
                    if (winElement != null) {
                        Element winLink = winElement.selectFirst("a");
                        if (winLink != null) {
                            winPitcher = winLink.text().trim();
                            log.info("승리 투수 (방법1): " + winPitcher);
                        }
                    }
                    
                    // 패배 투수
                    Element loseElement = gameResultSection.selectFirst(".lose");
                    if (loseElement != null) {
                        Element loseLink = loseElement.selectFirst("a");
                        if (loseLink != null) {
                            losePitcher = loseLink.text().trim();
                            log.info("패배 투수 (방법1): " + losePitcher);
                        }
                    }
                    
                    // 세이브 투수
                    Element saveElement = gameResultSection.selectFirst(".save");
                    if (saveElement != null) {
                        Element saveLink = saveElement.selectFirst("a");
                        if (saveLink != null) {
                            savePitcher = saveLink.text().trim();
                            log.info("세이브 투수 (방법1): " + savePitcher);
                        }
                    }
                }
                
                // 방법 2: 투구기록 테이블에서 승부 정보 찾기 (방법1이 실패한 경우)
                if (winPitcher == null || losePitcher == null) {
                    log.info("방법2 시도: 투구기록 테이블에서 검색");
                    Elements pitcherSections = doc.select("div.box_type_boared:has(.box_head:contains(투구기록))");
                    for (Element section : pitcherSections) {
                        Element table = section.selectFirst("table");
                        if (table != null) {
                            Elements pitcherRows = table.select("tbody > tr:not(.total)");
                            for (Element row : pitcherRows) {
                                Elements cols = row.select("td");
                                if (cols.size() > 0) {
                                    String pitcherInfo = cols.get(0).text().trim();
                                    
                                    if (pitcherInfo.contains("(승") && winPitcher == null) {
                                        // 승리 투수 추출: "김태훈 (승, 2-2)" -> "김태훈"
                                        winPitcher = pitcherInfo.replaceAll("\\s*\\(승.*", "").trim();
                                        log.info("승리 투수 (방법2): " + winPitcher);
                                    } else if (pitcherInfo.contains("(패") && losePitcher == null) {
                                        // 패배 투수 추출: "최지강 (패, 2-5)" -> "최지강"
                                        losePitcher = pitcherInfo.replaceAll("\\s*\\(패.*", "").trim();
                                        log.info("패배 투수 (방법2): " + losePitcher);
                                    } else if (pitcherInfo.contains("(세") && savePitcher == null) {
                                        // 세이브 투수 추출: "이호성 (세, 7)" -> "이호성"
                                        savePitcher = pitcherInfo.replaceAll("\\s*\\(세.*", "").trim();
                                        log.info("세이브 투수 (방법2): " + savePitcher);
                                    }
                                }
                            }
                        }
                    }
                }
                
                // 방법 3: 전체 페이지에서 승부투수 패턴 검색
                if (winPitcher == null || losePitcher == null) {
                    log.info("방법3 시도: 전체 페이지 패턴 검색");
                    String htmlText = doc.text();
                    
                    // 승리 투수 패턴 찾기
                    if (winPitcher == null && htmlText.contains("승")) {
                        Elements allLinks = doc.select("a");
                        for (Element link : allLinks) {
                            String linkText = link.text().trim();
                            Element parent = link.parent();
                            String parentText = parent != null ? parent.text() : "";
                            if (parentText.contains("승") && !linkText.isEmpty() && linkText.length() < 10) {
                                winPitcher = linkText;
                                log.info("승리 투수 (방법3): " + winPitcher);
                                break;
                            }
                        }
                    }
                    
                    // 패배 투수 패턴 찾기
                    if (losePitcher == null && htmlText.contains("패")) {
                        Elements allLinks = doc.select("a");
                        for (Element link : allLinks) {
                            String linkText = link.text().trim();
                            Element parent = link.parent();
                            String parentText = parent != null ? parent.text() : "";
                            if (parentText.contains("패") && !linkText.isEmpty() && linkText.length() < 10 && !linkText.equals(winPitcher)) {
                                losePitcher = linkText;
                                log.info("패배 투수 (방법3): " + losePitcher);
                                break;
                            }
                        }
                    }
                }
                
                log.info("최종 투수 정보 - 승: " + winPitcher + ", 패: " + losePitcher + ", 세: " + savePitcher);
                
            } catch (Exception e) {
                log.info("투수 정보 파싱 실패: " + e.getMessage());
                e.printStackTrace();
            }

            ScoreBoard sb = ScoreBoard.builder()
                    .scheduleId(scheduleId)
                    .homeScore(homeR).awayScore(awayR)
                    .homeInningScores(toInningString(homeScores))
                    .awayInningScores(toInningString(awayScores))
                    .homeR(homeR).homeH(homeH).homeE(homeE).homeB(homeB)
                    .awayR(awayR).awayH(awayH).awayE(awayE).awayB(awayB)
                    .winPitcher(winPitcher)
                    .losePitcher(losePitcher)
                    .build();
            
            try {
                scoreBoardService.saveOrUpdate(sb);
                log.info("ScoreBoard 저장 완료 - scheduleId: " + scheduleId);
            } catch (Exception e) {
                log.info("ScoreBoard 저장 실패 - scheduleId: " + scheduleId + ", 오류: " + e.getMessage());
            }
            
            // ----------------------- gameHighlight 크롤링 ----------------------
            Element highlightBox = doc.selectFirst("div.sh_box:has(.box_head:contains(결정적 장면 Best 5)) table");
            if (highlightBox == null) {
                // 다른 선택자들로 시도
                highlightBox = doc.selectFirst("div:contains(결정적 장면 Best 5) table");
                if (highlightBox == null) {
                    highlightBox = doc.selectFirst("h5:contains(결정적 장면) ~ table");
                    if (highlightBox == null) {
                        // 모든 테이블에서 "결정적 장면" 관련 테이블 찾기
                        Elements allTables = doc.select("table");
                        for (Element table : allTables) {
                            if (table.text().contains("결정적") || table.text().contains("장면") || 
                                table.text().contains("이닝") && table.text().contains("투수") && table.text().contains("타자")) {
                                highlightBox = table;
                                log.info("결정적 장면 테이블 대체 방법으로 발견");
                                break;
                            }
                        }
                    }
                }
            }
            
            if (highlightBox != null) {
                log.info("결정적 장면 테이블 발견");
                Elements highlightRows = highlightBox.select("tbody > tr");
                if (highlightRows.isEmpty()) {
                    // tbody가 없는 경우 직접 tr 찾기
                    highlightRows = highlightBox.select("tr");
                    if (highlightRows.size() > 1) {
                        // 첫 번째 행이 헤더인 경우 제거
                        highlightRows.remove(0);
                    }
                }
                
                log.info("결정적 장면 행 수: " + highlightRows.size());
                int ranking = 1;
                for (int i = 0; i < highlightRows.size(); i++) {
                    Elements tds = highlightRows.get(i).select("td");
                    log.info("행 " + i + " 컬럼 수: " + tds.size());
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
                        try {
                            gameHighlightService.saveOrUpdate(dto);
                            log.info("GameHighlight 저장 성공 - ranking: " + (ranking-1));
                        } catch (Exception e) {
                            log.info("GameHighlight 저장 실패 - ranking: " + ranking + ", 오류: " + e.getMessage());
                        }
                    }
                }
            } else {
                log.info("결정적 장면 테이블을 찾을 수 없습니다.");
            }

            // ------------------------ boxscore 크롤링 ---------------------
            String boxscoreUrl = "https://statiz.sporki.com/schedule/?m=boxscore&s_no=" + statizId;
            log.info("Boxscore URL 접속: " + boxscoreUrl);
            driver.get(boxscoreUrl);
            Thread.sleep(3000);

            String boxHtml = driver.getPageSource();
            Document boxDoc = Jsoup.parse(boxHtml);

            try {
                log.info("Boxscore 크롤링 시작 - scheduleId: " + scheduleId);
                Map<String, Integer> sbMap = extractStolenBases(boxDoc);
                log.info("도루 데이터 추출 완료: " + sbMap.size() + "개");
                
                saveBatterRecords(boxDoc, scheduleId, awayTeamId, sbMap);
                log.info("원정팀 타자 기록 저장 완료");
                
                saveBatterRecords(boxDoc, scheduleId, homeTeamId, sbMap);
                log.info("홈팀 타자 기록 저장 완료");
                
                savePitcherRecords(boxDoc, scheduleId);
                log.info("투수 기록 저장 완료");
                
                log.info("Boxscore 크롤링 완료 - scheduleId: " + scheduleId);
            } catch (Exception e) {
                log.info("Boxscore 크롤링 실패 - scheduleId: " + scheduleId + ", 오류: " + e.getMessage());
                e.printStackTrace();
            }

        } catch (Exception e) {
            log.info("오류 발생: " + statizId);
            e.printStackTrace();
        } finally {
            if (driver != null) driver.quit();
        }
    }
    
    // 이닝 숫자 -> 문자열로 변경
    private String toInningString(List<Integer> scores) {
        String result = "";
        for (int i = 0; i < scores.size(); i++) {
            result += scores.get(i);
            if (i < scores.size() - 1) result += " ";
        }
        return result;
    }

    
    // 도루 정보 추출
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
            Integer extractedTeamId = teamNameToId.get(teamName);
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
            Integer teamId = teamNameToId.get(teamName);
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
}
