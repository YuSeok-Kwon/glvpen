


package com.kepg.BaseBallLOCK.crawler.team;

import static com.kepg.BaseBallLOCK.crawler.util.CrawlerUtils.*;
import static com.kepg.BaseBallLOCK.crawler.util.TeamMappingConstants.*;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.kepg.BaseBallLOCK.crawler.util.WebDriverFactory;

import com.kepg.BaseBallLOCK.modules.team.teamRanking.domain.TeamRanking;
import com.kepg.BaseBallLOCK.modules.team.teamRanking.repository.TeamRankingRepository;
import com.kepg.BaseBallLOCK.modules.team.teamStats.service.TeamStatsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatizTeamUnifiedCrawler {

    // ==================== 상수 정의 ====================
    private static final int PAGE_LOAD_WAIT_MS = 3000;

    private final TeamRankingRepository teamRankingRepository;
    private final TeamStatsService teamStatsService;

    // 매일 자정 전에 팀 데이터 크롤링 실행
    @Scheduled(cron = "0 59 23 * * *")
    public void runScheduledCrawling() {
        log.info("=== 팀 데이터 크롤링 시작 ===");
        crawlTeamRankings();
        crawlTeamWaaStats();
        log.info("=== 팀 데이터 크롤링 완료 ===");
    }

    // 수동 실행용 메서드
    public void crawlAllTeamData() {
        crawlTeamRankings();
        crawlTeamWaaStats();
    }

    // 팀 순위 크롤링
    public void crawlTeamRankings() {
        int currentYear = 2025;
        String baseUrl = "https://statiz.sporki.com/season/?m=teamoverall&year=" + currentYear;
        
        log.info("팀 순위 크롤링 시작: {}", currentYear);

        WebDriver driver = WebDriverFactory.createChromeDriverWithExtendedOptions();

        try {
            Document doc = CrawlerUtils.loadPage(driver, baseUrl, PAGE_LOAD_WAIT_MS);

            Elements rows = doc.select("table.table_type01 tbody tr");

            for (Element row : rows) {
                try {
                    Elements cells = row.select("td");
                    if (cells.size() >= 12) {
                        String teamName = cells.get(1).text().trim();
                        Integer teamId = getTeamId(teamName);

                        if (teamId != null) {
                            int ranking = Integer.parseInt(cells.get(0).text().trim());
                            int games = Integer.parseInt(cells.get(2).text().trim());
                            int wins = Integer.parseInt(cells.get(3).text().trim());
                            int losses = Integer.parseInt(cells.get(4).text().trim());
                            int draws = Integer.parseInt(cells.get(5).text().trim());
                            double winRate = Double.parseDouble(cells.get(6).text().trim());
                            double gameBehind = cells.get(7).text().trim().equals("-") ? 0.0 : 
                                              Double.parseDouble(cells.get(7).text().trim());

                            TeamRanking teamRanking = TeamRanking.builder()
                                    .teamId(teamId)
                                    .season(currentYear)
                                    .ranking(ranking)
                                    .games(games)
                                    .wins(wins)
                                    .losses(losses)
                                    .draws(draws)
                                    .winRate(winRate)
                                    .gamesBehind(gameBehind)
                                    .build();

                            teamRankingRepository.save(teamRanking);
                            log.info("팀 순위 저장: {} - {}위", teamName, ranking);
                        }
                    }
                } catch (Exception e) {
                    log.error("팀 순위 데이터 처리 중 오류: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("팀 순위 크롤링 중 오류 발생: {}", e.getMessage(), e);
        } finally {
            driver.quit();
        }
    }

    // 팀 WAA 통계 크롤링
    public void crawlTeamWaaStats() {
        int currentYear = 2025;
        String baseUrl = "https://statiz.sporki.com/season/?m=teamoverall&year=" + currentYear;
        
        log.info("팀 WAA 통계 크롤링 시작: {}", currentYear);

        WebDriver driver = WebDriverFactory.createChromeDriverWithExtendedOptions();

        try {
            Document doc = CrawlerUtils.loadPage(driver, baseUrl, PAGE_LOAD_WAIT_MS);

            Elements rows = doc.select("table.table_type01 tbody tr");

            for (Element row : rows) {
                try {
                    Elements cells = row.select("td");
                    if (cells.size() >= 12) {
                        String teamName = cells.get(1).text().trim();
                        Integer teamId = getTeamId(teamName);

                        if (teamId != null) {
                            int wins = Integer.parseInt(cells.get(3).text().trim());
                            int losses = Integer.parseInt(cells.get(4).text().trim());
                            int draws = Integer.parseInt(cells.get(5).text().trim());
                            double winRate = Double.parseDouble(cells.get(6).text().trim());

                            // TeamStatsService를 통해 기본 통계 데이터 저장
                            teamStatsService.saveOrUpdate(teamId, currentYear, "승수", wins, null);
                            teamStatsService.saveOrUpdate(teamId, currentYear, "패수", losses, null);
                            teamStatsService.saveOrUpdate(teamId, currentYear, "무승부", draws, null);
                            teamStatsService.saveOrUpdate(teamId, currentYear, "승률", winRate, null);
                            
                            log.info("팀 기본 통계 저장: {}", teamName);
                        }
                    }
                } catch (Exception e) {
                    log.error("팀 WAA 통계 데이터 처리 중 오류: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("팀 WAA 통계 크롤링 중 오류 발생: {}", e.getMessage(), e);
        } finally {
            driver.quit();
        }
    }
}
