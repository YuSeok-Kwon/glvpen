package com.kepg.BaseBallLOCK.crawler;

import java.time.LocalDate;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.kepg.BaseBallLOCK.BaseBallLockApplication;
import com.kepg.BaseBallLOCK.crawler.game.StatizGameCrawler;
import com.kepg.BaseBallLOCK.crawler.player.StatizPlayerUnifiedCrawler;
import com.kepg.BaseBallLOCK.crawler.schedule.StatizScheduleCrawler;
import com.kepg.BaseBallLOCK.crawler.team.StatizTeamUnifiedCrawler;


public class CrawlersManualRunner {

public static void main(String[] args) {
ConfigurableApplicationContext context = SpringApplication.run(BaseBallLockApplication.class, args);

try {
    // 어제와 오늘 경기 데이터 수집
    LocalDate today = LocalDate.now();
    LocalDate yesterday = today.minusDays(1);
    
    System.out.println("=== 경기 데이터 수집 시작 ===");
    System.out.println("오늘 날짜: " + today);
    System.out.println("어제 날짜: " + yesterday);
    
    // 1. 어제 경기 일정 수집
    StatizScheduleCrawler scheduleCrawler = context.getBean(StatizScheduleCrawler.class);
    System.out.println("어제 스케줄 크롤링 시작: " + yesterday);
    scheduleCrawler.crawlGameRange(yesterday, yesterday);
    
    // 2. 어제 경기 상세 정보 수집
    StatizGameCrawler gameCrawler = context.getBean(StatizGameCrawler.class);
    System.out.println("어제 경기 데이터 크롤링 시작: " + yesterday);
    gameCrawler.crawlGameRange(yesterday, yesterday);
    
    // 3. 팀 통계 데이터 수집
    StatizTeamUnifiedCrawler teamCrawler = context.getBean(StatizTeamUnifiedCrawler.class);
    System.out.println("팀 데이터 크롤링 시작");
    teamCrawler.crawlAllTeamData();
    
    // 4. 플레이어 통계 데이터 수집
    StatizPlayerUnifiedCrawler playerCrawler = context.getBean(StatizPlayerUnifiedCrawler.class);
    System.out.println("플레이어 데이터 크롤링 시작");
    playerCrawler.crawlAllPlayerData();
    
    // 5. 최근 3일간 추가 수집 (데이터 보강)
    LocalDate threeDaysAgo = today.minusDays(3);
    System.out.println("최근 3일간 데이터 보강: " + threeDaysAgo + " ~ " + today);
    scheduleCrawler.crawlGameRange(threeDaysAgo, today);
    gameCrawler.crawlGameRange(threeDaysAgo, today);
    
    System.out.println("=== 데이터 수집 완료 ===");
    
} catch (Exception e) {
    e.printStackTrace();	
} finally {
    context.close();
    System.exit(0);
}
}
}
