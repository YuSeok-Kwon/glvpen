package com.kepg.BaseBallLOCK.modules.gameMode.realMatch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Service;

import com.kepg.BaseBallLOCK.modules.game.schedule.dto.ScheduleCardView;
import com.kepg.BaseBallLOCK.modules.game.schedule.service.ScheduleService;
import com.kepg.BaseBallLOCK.modules.gameMode.realMatch.dto.RealMatchGameDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.realMatch.dto.RealMatchLineupDTO;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealMatchCrawlerService {

    private final ScheduleService scheduleService;

    private static final Map<String, Integer> teamNameToId = new HashMap<>();
    private static final Map<String, String> teamIdToName = new HashMap<>();

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

        teamIdToName.put("1", "KIA");
        teamIdToName.put("2", "두산");
        teamIdToName.put("3", "삼성");
        teamIdToName.put("4", "SSG");
        teamIdToName.put("5", "LG");
        teamIdToName.put("6", "한화");
        teamIdToName.put("7", "NC");
        teamIdToName.put("8", "KT");
        teamIdToName.put("9", "롯데");
        teamIdToName.put("10", "키움");
    }

    /**
     * 전날 경기 목록을 가져옵니다.
     * 화요일인 경우 일요일 경기를 가져옵니다.
     */
    public List<RealMatchGameDTO> getPreviousDayGames() {
        LocalDate targetDate = calculateTargetDate();
        log.info("전날 경기 조회 시작 - 대상 날짜: {}", targetDate);

        List<ScheduleCardView> schedules = scheduleService.getSchedulesByDate(targetDate);
        List<RealMatchGameDTO> games = new ArrayList<>();

        for (ScheduleCardView schedule : schedules) {
            RealMatchGameDTO game = new RealMatchGameDTO();
            game.setScheduleId(schedule.getId().longValue());
            game.setHomeTeamName(schedule.getHomeTeamName());
            game.setAwayTeamName(schedule.getAwayTeamName());
            game.setStadium(schedule.getStadium());
            game.setGameTime(schedule.getMatchDate().toLocalDateTime());
            game.setGameDate(schedule.getMatchDate().toLocalDateTime().toLocalDate());
            game.setHomeScore(schedule.getHomeTeamScore());
            game.setAwayScore(schedule.getAwayTeamScore());
            game.setStatus(schedule.getHomeTeamScore() != null ? "completed" : "scheduled");
            
            // 라인업 데이터 존재 여부 확인 (실제로는 lineup 테이블을 확인해야 함)
            game.setHasLineup(true); // 임시로 true 설정
            
            games.add(game);
        }

        log.info("전날 경기 {} 건 조회 완료", games.size());
        return games;
    }

    /**
     * 특정 경기의 라인업을 크롤링합니다.
     */
    public RealMatchLineupDTO crawlGameLineup(Long scheduleId) {
        log.info("경기 라인업 크롤링 시작 - scheduleId: {}", scheduleId);
        
        // 실제 크롤링 구현은 추후 진행
        // 현재는 더미 데이터 반환
        RealMatchLineupDTO lineup = new RealMatchLineupDTO();
        lineup.setScheduleId(scheduleId);
        lineup.setHomeTeamName("KIA");
        lineup.setAwayTeamName("두산");
        lineup.setHomeStarterPitcher("양현종");
        lineup.setAwayStarterPitcher("곽빈");
        
        // 더미 라인업 데이터
        lineup.setHomeLineup(new ArrayList<>());
        lineup.setAwayLineup(new ArrayList<>());
        
        log.info("경기 라인업 크롤링 완료");
        return lineup;
    }

    /**
     * 대상 날짜를 계산합니다.
     * 화요일인 경우 일요일 경기를, 그 외에는 전날 경기를 대상으로 합니다.
     */
    private LocalDate calculateTargetDate() {
        LocalDate today = LocalDate.now();
        
        // 화요일(2)인 경우 일요일(7) 경기를 대상으로 함
        if (today.getDayOfWeek().getValue() == 2) {
            return today.minusDays(2); // 일요일
        } else {
            return today.minusDays(1); // 전날
        }
    }

    /**
     * Statiz에서 라인업 데이터를 크롤링합니다.
     */
    private void crawlLineupFromStatiz(int scheduleId) {
        WebDriver driver = null;
        try {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            driver = new ChromeDriver(options);
            
            // Statiz 경기 상세 페이지 접근
            String url = "https://www.statiz.co.kr/game.php?opt=5&game_id=" + scheduleId;
            driver.get(url);
            
            Thread.sleep(2000); // 페이지 로딩 대기
            
            String pageSource = driver.getPageSource();
            Document doc = Jsoup.parse(pageSource);
            
            // 라인업 정보 파싱
            parseLineupData(doc, scheduleId);
            
        } catch (Exception e) {
            log.error("라인업 크롤링 중 오류 발생 - scheduleId: {}", scheduleId, e);
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    /**
     * HTML에서 라인업 데이터를 파싱합니다.
     */
    private void parseLineupData(Document doc, int scheduleId) {
        try {
            // 라인업 테이블 파싱 로직
            Elements lineupTables = doc.select("table.lineup");
            
            for (Element table : lineupTables) {
                // 홈팀/원정팀 라인업 파싱
                Elements rows = table.select("tr");
                for (Element row : rows) {
                    // 선수 정보 파싱
                    Elements cells = row.select("td");
                    if (cells.size() >= 3) {
                        String battingOrder = cells.get(0).text();
                        String playerName = cells.get(1).text();
                        String position = cells.get(2).text();
                        
                        // DB에 저장하는 로직 추가 필요
                        log.debug("라인업 파싱 - 타순: {}, 선수: {}, 포지션: {}", 
                                 battingOrder, playerName, position);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("라인업 데이터 파싱 중 오류 발생", e);
        }
    }
}
