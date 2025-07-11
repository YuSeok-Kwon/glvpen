package com.kepg.BaseBallLOCK.crawler.util;

import java.time.LocalDate;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.kepg.BaseBallLOCK.BaseBallLockApplication;
import com.kepg.BaseBallLOCK.crawler.player.StatizBatterCrawler;
import com.kepg.BaseBallLOCK.crawler.player.StatizPitcherCrawler;
import com.kepg.BaseBallLOCK.crawler.team.StatizTeamRankingCrawler;
import com.kepg.BaseBallLOCK.crawler.team.StatizTeamWaaCrawler;
import com.kepg.BaseBallLOCK.crawler.team.StatizTeamDetailedstatsCrawler;
import com.kepg.BaseBallLOCK.crawler.schedule.StatizScheduleCrawler;
import com.kepg.BaseBallLOCK.crawler.game.StatizGameCrawler;


public class CrawlersManualRunner {

public static void main(String[] args) {
ConfigurableApplicationContext context = SpringApplication.run(BaseBallLockApplication.class, args);

try {
//    context.getBean(StatizBatterCrawler.class).crawl(); // 타자기록 (batterStats)
//    context.getBean(StatizPitcherCrawler.class).crawl();  // 투수기록 (pitcherStats)
//	context.getBean(StatizTeamRankingCrawler.class).crawl(); // 팀 순위기록 (teamRanking)
//	context.getBean(StatizTeamWaaCrawler.class).crawl(); // 팀 WAA기록 (teamStats)
//	context.getBean(StatizTeamDetailedstatsCrawler.class).crawl(); // 팀 투수, 타자 세부지표 (teamStats)
	
//	StatizScheduleCrawler scheduleCrawler = context.getBean(StatizScheduleCrawler.class); // 스케쥴만 업데이
//	scheduleCrawler.crawlGameRange(LocalDate.of(2025, 3, 22),LocalDate.of(2025, 8, 31));
	
	StatizGameCrawler crawler = context.getBean(StatizGameCrawler.class); // 경기 일정(종료, 예정 등) (schedule) + 1경기의 스코어보드 기록(scoreBoard), 하이라이트(gameHighlight)
	crawler.crawlGameRange(LocalDate.of(2025, 5, 10),LocalDate.of(2025, 5, 10)); // 1경기의 타자 및 투수 기록(batterLineUp, BatterRecord, PitcherRecord) 
    
} catch (Exception e) {
    e.printStackTrace();	
} finally {
    context.close();
}
}
}
