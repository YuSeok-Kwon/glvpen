package com.kepg.BaseBallLOCK.crawler;

import java.time.LocalDate;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.kepg.BaseBallLOCK.BaseBallLockApplication;
import com.kepg.BaseBallLOCK.crawler.kbo.KboPlayerStatsCrawler;
import com.kepg.BaseBallLOCK.crawler.kbo.KboScheduleCrawler;
import com.kepg.BaseBallLOCK.crawler.kbo.KboTeamStatsCrawler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CrawlersManualRunner {

public static void main(String[] args) {
ConfigurableApplicationContext context = SpringApplication.run(BaseBallLockApplication.class, args);

try {
    // ===== 크롤링 설정 =====

    // 크롤링 옵션
    boolean crawlSchedule = false;
    boolean crawlTeam = false;
    boolean crawlPlayer = true;

    // 크롤링 대상 시즌
    int targetSeason = 2025;

    // 다시즌 배치 크롤링 (2020~2025, 역순으로 수집)
    boolean crawlMultiSeason = false;
    int multiSeasonStart = 2020;
    int multiSeasonEnd = 2025;

    // 단일 기간 스케줄 크롤링
    LocalDate startDate = LocalDate.of(targetSeason, 3, 1);
    LocalDate endDate = LocalDate.of(targetSeason, 3, 31);

    log.info("=== KBO 데이터 수집 시작 ===");
    log.info("대상 시즌: {}", targetSeason);
    log.info("일정 크롤링: {}", crawlSchedule ? "예" : "아니오");
    log.info("팀 데이터 크롤링: {}", crawlTeam ? "예" : "아니오");
    log.info("선수 데이터 크롤링: {}", crawlPlayer ? "예" : "아니오");
    log.info("다시즌 배치 크롤링: {}", crawlMultiSeason ? multiSeasonEnd + "→" + multiSeasonStart + " (역순)" : "아니오");

    // KBO 크롤러 인스턴스
    KboScheduleCrawler scheduleCrawler = context.getBean(KboScheduleCrawler.class);
    KboTeamStatsCrawler teamCrawler = context.getBean(KboTeamStatsCrawler.class);
    KboPlayerStatsCrawler playerCrawler = context.getBean(KboPlayerStatsCrawler.class);

    if (crawlMultiSeason) {
        // 다시즌 배치 크롤링 (2025 → 2024 → ... → 2020)

        if (crawlSchedule) {
            log.info("\n[1단계] 다시즌 일정 크롤링: {}→{}", multiSeasonEnd, multiSeasonStart);
            for (int year = multiSeasonEnd; year >= multiSeasonStart; year--) {
                LocalDate seasonStart = LocalDate.of(year, 3, 1);
                LocalDate seasonEnd = LocalDate.of(year, 11, 30);
                log.info("--- {}시즌 일정 크롤링: {} ~ {} ---", year, seasonStart, seasonEnd);
                scheduleCrawler.crawlGameRange(seasonStart, seasonEnd);
            }
        }

        if (crawlTeam) {
            log.info("\n[2단계] 다시즌 팀 데이터 크롤링: {}→{}", multiSeasonEnd, multiSeasonStart);
            teamCrawler.crawlMultiSeasons(multiSeasonStart, multiSeasonEnd);
        }

        if (crawlPlayer) {
            log.info("\n[3단계] 다시즌 선수 데이터 크롤링: {}→{}", multiSeasonEnd, multiSeasonStart);
            playerCrawler.crawlMultiSeasons(multiSeasonStart, multiSeasonEnd);
        }

    } else {
        // 단일 시즌 크롤링

        if (crawlSchedule) {
            log.info("\n[1단계] 일정 크롤링: {} ~ {}", startDate, endDate);
            scheduleCrawler.crawlGameRange(startDate, endDate);
        }

        if (crawlTeam) {
            log.info("\n[2단계] 팀 데이터 크롤링 ({}시즌)", targetSeason);
            teamCrawler.crawlAllTeamData(targetSeason);
        }

        if (crawlPlayer) {
            log.info("\n[3단계] 선수 데이터 크롤링 ({}시즌)", targetSeason);
            playerCrawler.crawlAllPlayerData(targetSeason);
        }
    }

    log.info("\n=== KBO 데이터 수집 완료 ===");

} catch (Exception e) {
    log.error("크롤링 중 오류 발생", e);
} finally {
    context.close();
    System.exit(0);
}
}
}
