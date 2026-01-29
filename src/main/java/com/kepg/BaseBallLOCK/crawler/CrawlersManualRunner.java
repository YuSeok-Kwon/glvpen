package com.kepg.BaseBallLOCK.crawler;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.kepg.BaseBallLOCK.BaseBallLockApplication;
import com.kepg.BaseBallLOCK.crawler.game.StatizGameCrawler;
import com.kepg.BaseBallLOCK.crawler.player.StatizPlayerUnifiedCrawler;
import com.kepg.BaseBallLOCK.crawler.schedule.StatizScheduleCrawler;
import com.kepg.BaseBallLOCK.crawler.team.StatizTeamUnifiedCrawler;
import com.kepg.BaseBallLOCK.modules.game.schedule.domain.Schedule;
import com.kepg.BaseBallLOCK.modules.game.schedule.service.ScheduleService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CrawlersManualRunner {

public static void main(String[] args) {
ConfigurableApplicationContext context = SpringApplication.run(BaseBallLockApplication.class, args);

try {
    // ===== 크롤링 설정 =====
    // 날짜 범위 설정
    
    // 아래 날짜를 원하는 범위로 수정하세요
//    LocalDate startDate = today.minusDays(1); // 어제부터
//    LocalDate endDate = today;                // 오늘까지
    
    // 다른 방법
     LocalDate startDate = LocalDate.of(2025, 6, 1); // 특정 날짜
     LocalDate endDate = LocalDate.of(2025, 6, 30);   // 특정 날짜까지
    
    // 크롤링 옵션
    boolean crawlTeam = false;    // 팀 데이터 크롤링 여부
    boolean crawlPlayer = false;  // 선수 데이터 크롤링 여부
    boolean onlyMissingData = false; // 없는 데이터만 크롤링할지 여부
    
    log.info("=== 경기 데이터 수집 시작 ===");
    log.info("수집 범위: " + startDate + " ~ " + endDate);
    log.info("팀 데이터 크롤링: " + (crawlTeam ? "예" : "아니오"));
    log.info("선수 데이터 크롤링: " + (crawlPlayer ? "예" : "아니오"));
    log.info("누락 데이터만 크롤링: " + (onlyMissingData ? "예" : "아니오"));
    
    // 크롤러 인스턴스 생성
    StatizScheduleCrawler scheduleCrawler = context.getBean(StatizScheduleCrawler.class);
    StatizGameCrawler gameCrawler = context.getBean(StatizGameCrawler.class);
    StatizTeamUnifiedCrawler teamCrawler = context.getBean(StatizTeamUnifiedCrawler.class);
    StatizPlayerUnifiedCrawler playerCrawler = context.getBean(StatizPlayerUnifiedCrawler.class);
    
    // 데이터베이스 서비스
    ScheduleService scheduleService = context.getBean(ScheduleService.class);
    
    // 1. 지정된 날짜 범위의 스케줄 크롤링
    log.info("\n[1단계] 스케줄 데이터 확인 및 크롤링");
    if (onlyMissingData) {
        crawlMissingScheduleData(scheduleCrawler, scheduleService, startDate, endDate);
    } else {
        log.info("전체 스케줄 크롤링: " + startDate + " ~ " + endDate);
        scheduleCrawler.crawlGameRange(startDate, endDate);
    }
    
    // 2. 지정된 날짜 범위의 경기 상세 정보 크롤링
    log.info("\n[2단계] 경기 상세 데이터 확인 및 크롤링");
    if (onlyMissingData) {
        crawlMissingGameData(gameCrawler, scheduleService, startDate, endDate);
    } else {
        log.info("전체 경기 데이터 크롤링: " + startDate + " ~ " + endDate);
        gameCrawler.crawlGameRange(startDate, endDate);
    }
    
    // 3. 팀 통계 데이터 수집 (선택사항)
    log.info("\n[3단계] 팀 데이터 크롤링");
    if (crawlTeam) {
        log.info("팀 데이터 크롤링 시작");
        teamCrawler.crawlAllTeamData();
    } else {
        log.info("팀 데이터 크롤링 생략");
    }
    
    // 4. 플레이어 통계 데이터 수집 (선택사항)
    log.info("\n[4단계] 선수 데이터 크롤링");
    if (crawlPlayer) {
        log.info("선수 데이터 크롤링 시작");
        playerCrawler.crawlAllPlayerData();
    } else {
        log.info("선수 데이터 크롤링 생략");
    }
    
    log.info("\n=== 데이터 수집 완료 ===");
    
} catch (Exception e) {
    e.printStackTrace();	
} finally {
    context.close();
    System.exit(0);
}
}

/**
 * 스케줄 데이터가 없는 날짜만 크롤링
 */
private static void crawlMissingScheduleData(StatizScheduleCrawler scheduleCrawler, ScheduleService scheduleService, LocalDate startDate, LocalDate endDate) {
    log.info("스케줄 크롤링 (누락 데이터 확인): " + startDate + " ~ " + endDate);
    
    LocalDate currentDate = startDate;
    int totalDays = 0;
    int missingDays = 0;
    
    while (!currentDate.isAfter(endDate)) {
        totalDays++;
        
        // 해당 날짜의 경기 데이터가 있는지 확인
        Timestamp dayStart = Timestamp.valueOf(currentDate.atStartOfDay());
        Timestamp dayEnd = Timestamp.valueOf(currentDate.atTime(23, 59, 59));
        List<Schedule> schedules = scheduleService.getSchedulesByDateRange(dayStart, dayEnd);
        
        if (schedules.isEmpty()) {
            missingDays++;
            log.info("  ▶ " + currentDate + ": 스케줄 데이터 없음 - 크롤링 실행");
            scheduleCrawler.crawlGameRange(currentDate, currentDate);
        } else {
            log.info("  ✓ " + currentDate + ": 스케줄 데이터 존재 (" + schedules.size() + "경기) - 생략");
        }
        
        currentDate = currentDate.plusDays(1);
    }
    
    log.info("스케줄 크롤링 완료: 전체 " + totalDays + "일 중 " + missingDays + "일 크롤링");
}

/**
 * 경기 상세 데이터가 없는 경기만 크롤링
 */
private static void crawlMissingGameData(StatizGameCrawler gameCrawler, ScheduleService scheduleService, LocalDate startDate, LocalDate endDate) {
    log.info("경기 상세 데이터 크롤링 (누락 데이터 확인): " + startDate + " ~ " + endDate);
    
    LocalDate currentDate = startDate;
    int totalDays = 0;
    int crawledDays = 0;
    
    while (!currentDate.isAfter(endDate)) {
        totalDays++;
        
        // 해당 날짜의 경기 데이터 확인
        Timestamp dayStart = Timestamp.valueOf(currentDate.atStartOfDay());
        Timestamp dayEnd = Timestamp.valueOf(currentDate.atTime(23, 59, 59));
        List<Schedule> schedules = scheduleService.getSchedulesByDateRange(dayStart, dayEnd);
        
        boolean needsCrawling = false;
        int gamesWithoutDetails = 0;
        
        for (Schedule schedule : schedules) {
            // 경기 상세 정보가 없는지 확인
            boolean missingDetails = false;
            
            // 1. 점수가 null이거나 둘 다 0인 경우 (단, 취소/연기 경기는 제외)
            if (schedule.getHomeTeamScore() == null || schedule.getAwayTeamScore() == null) {
                missingDetails = true;
            } else if (schedule.getHomeTeamScore() == 0 && schedule.getAwayTeamScore() == 0) {
                // 둘 다 0점이면서 상태가 "종료"인 경우는 의심스러움 (실제 0-0 게임일 수도 있지만)
                String status = schedule.getStatus();
                if (status != null && (status.equals("종료") || status.equals("경기종료"))) {
                    // 추가 확인: statizId가 있는지 체크
                    if (schedule.getStatizId() == null || schedule.getStatizId() == 0) {
                        missingDetails = true;
                    }
                }
            }
            
            // 2. 상태가 null이거나 "예정"인데 과거 경기인 경우
            String status = schedule.getStatus();
            if (status == null || status.equals("예정")) {
                if (schedule.getMatchDate() != null && 
                    schedule.getMatchDate().toLocalDateTime().toLocalDate().isBefore(LocalDate.now())) {
                    missingDetails = true;
                }
            }
            
            if (missingDetails) {
                needsCrawling = true;
                gamesWithoutDetails++;
            }
        }
        
        if (needsCrawling && !schedules.isEmpty()) {
            crawledDays++;
            log.info("  ▶ " + currentDate + ": 상세 데이터 부족 (" + gamesWithoutDetails + "/" + schedules.size() + "경기) - 크롤링 실행");
            gameCrawler.crawlGameRange(currentDate, currentDate);
        } else if (schedules.isEmpty()) {
            log.info("  ! " + currentDate + ": 스케줄 데이터 없음 - 먼저 스케줄 크롤링 필요");
        } else {
            log.info("  ✓ " + currentDate + ": 상세 데이터 완료 (" + schedules.size() + "경기) - 생략");
        }
        
    }
    
    log.info("경기 상세 크롤링 완료: 전체 " + totalDays + "일 중 " + crawledDays + "일 크롤링");
}
}
