package com.kepg.glvpen.crawler;

import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.kepg.glvpen.crawler.kbo.KboScheduleCrawler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 일일 스케줄 자동 크롤링 서비스
 * 매일 자정에 향후 7일간의 KBO 일정을 크롤링하여 최신 상태로 유지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledCrawlerService {

    private final KboScheduleCrawler kboScheduleCrawler;

    @Scheduled(cron = "0 0 0 * * *")
    public void crawlDailySchedule() {
        log.info("[자동 크롤링] 일일 스케줄 크롤링 시작");
        try {
            LocalDate today = LocalDate.now();
            kboScheduleCrawler.crawlGameRange(today, today.plusDays(7));
            log.info("[자동 크롤링] 일일 스케줄 크롤링 완료");
        } catch (Exception e) {
            log.error("[자동 크롤링] 일일 스케줄 크롤링 실패", e);
        }
    }
}
