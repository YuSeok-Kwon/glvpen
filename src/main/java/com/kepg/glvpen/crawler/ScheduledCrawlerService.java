package com.kepg.glvpen.crawler;

import java.time.LocalDate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.kepg.glvpen.crawler.kbo.KboGameCenterCrawler;
import com.kepg.glvpen.crawler.kbo.KboPlayerStatsCrawler;
import com.kepg.glvpen.crawler.kbo.KboScheduleCrawler;
import com.kepg.glvpen.crawler.kbo.KboTeamStatsCrawler;
import com.kepg.glvpen.modules.player.stats.service.SabermetricsBatchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 일일 자동 크롤링 서비스 (AWS 전용)
 * crawl.scheduled.enabled=true 일 때만 Bean 생성
 *
 * 매일 00:00 실행 파이프라인:
 * 1) 일정 크롤링 (오늘~+7일)
 * 2) 게임센터 크롤링 (전날+당일 종료 경기 — 스코어보드/박스스코어/키플레이어 전부 포함)
 * 3) 선수 개인기록 크롤링 (당해 시즌 타자/투수/수비/주루 — 시리즈별 분리 수집)
 * 4) 팀 데이터 크롤링 (순위/타격/투수/수비/주루/상대전적 — 시리즈별 분리 수집)
 * 5) 세이버메트릭스 재계산 (당해 시즌)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "crawl.scheduled.enabled", havingValue = "true")
public class ScheduledCrawlerService {

    private final KboScheduleCrawler kboScheduleCrawler;
    private final KboGameCenterCrawler kboGameCenterCrawler;
    private final KboPlayerStatsCrawler kboPlayerStatsCrawler;
    private final KboTeamStatsCrawler kboTeamStatsCrawler;
    private final SabermetricsBatchService sabermetricsBatchService;

    @Scheduled(cron = "0 0 0 * * *")
    public void crawlDailyPipeline() {
        log.info("[자동 크롤링] 일일 크롤링 파이프라인 시작");
        int currentSeason = LocalDate.now().getYear();

        crawlSchedule();
        crawlGameCenter(currentSeason);
        crawlPlayerStats(currentSeason);
        crawlTeamStats(currentSeason);
        calculateSabermetrics(currentSeason);

        log.info("[자동 크롤링] 일일 크롤링 파이프라인 완료");
    }

    private void crawlSchedule() {
        try {
            LocalDate today = LocalDate.now();
            kboScheduleCrawler.crawlGameRange(today, today.plusDays(7));
            log.info("[자동 크롤링] 일정 크롤링 완료");
        } catch (Exception e) {
            log.error("[자동 크롤링] 일정 크롤링 실패", e);
        }
    }

    private void crawlGameCenter(int season) {
        try {
            kboGameCenterCrawler.crawlRecentGameCenters(season);
            log.info("[자동 크롤링] 게임센터 크롤링 완료 (전날+당일)");
        } catch (Exception e) {
            log.error("[자동 크롤링] 게임센터 크롤링 실패", e);
        }
    }

    private void crawlPlayerStats(int season) {
        try {
            kboPlayerStatsCrawler.crawlAllPlayerData(season);
            log.info("[자동 크롤링] 선수 개인기록 크롤링 완료 (시즌: {})", season);
        } catch (Exception e) {
            log.error("[자동 크롤링] 선수 개인기록 크롤링 실패", e);
        }
    }

    private void crawlTeamStats(int season) {
        try {
            kboTeamStatsCrawler.crawlAllTeamData(season);
            log.info("[자동 크롤링] 팀 데이터 크롤링 완료 (시즌: {})", season);
        } catch (Exception e) {
            log.error("[자동 크롤링] 팀 데이터 크롤링 실패", e);
        }
    }

    private void calculateSabermetrics(int season) {
        try {
            sabermetricsBatchService.calculateAllSabermetrics(season, season);
            log.info("[자동 크롤링] 세이버메트릭스 계산 완료 (시즌: {})", season);
        } catch (Exception e) {
            log.error("[자동 크롤링] 세이버메트릭스 계산 실패", e);
        }
    }
}
