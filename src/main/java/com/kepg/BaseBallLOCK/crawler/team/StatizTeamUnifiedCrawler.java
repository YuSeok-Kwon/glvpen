


package com.kepg.BaseBallLOCK.crawler.team;

import java.util.HashMap;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.kepg.BaseBallLOCK.modules.team.teamRanking.domain.TeamRanking;
import com.kepg.BaseBallLOCK.modules.team.teamRanking.repository.TeamRankingRepository;
import com.kepg.BaseBallLOCK.modules.team.teamStats.service.TeamStatsService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class StatizTeamUnifiedCrawler {

    private static final Logger logger = LoggerFactory.getLogger(StatizTeamUnifiedCrawler.class);
    
    private final TeamRankingRepository teamRankingRepository;
    private final TeamStatsService teamStatsService;
    
    private static final Map<String, Integer> teamNameToId = new HashMap<>();
    static {
        teamNameToId.put("KIA", 1);
        teamNameToId.put("두산", 2);
        teamNameToId.put("삼성", 3);
        teamNameToId.put("LG", 4);
        teamNameToId.put("KT", 5);
        teamNameToId.put("SSG", 6);
        teamNameToId.put("롯데", 7);
        teamNameToId.put("한화", 8);
        teamNameToId.put("NC", 9);
        teamNameToId.put("키움", 10);
    }

    // 매일 자정 전에 팀 데이터 크롤링 실행
    @Scheduled(cron = "0 59 23 * * *")
    public void runScheduledCrawling() {
        logger.info("=== 팀 데이터 크롤링 시작 ===");
        crawlTeamRankings();
        crawlTeamWaaStats();
        logger.info("=== 팀 데이터 크롤링 완료 ===");
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
        
        logger.info("팀 순위 크롤링 시작: {}", currentYear);

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--remote-allow-origins=*");

        WebDriver driver = new ChromeDriver(options);

        try {
            driver.get(baseUrl);
            Thread.sleep(3000);

            String html = driver.getPageSource();
            Document doc = Jsoup.parse(html);

            Elements rows = doc.select("table.table_type01 tbody tr");

            for (Element row : rows) {
                try {
                    Elements cells = row.select("td");
                    if (cells.size() >= 12) {
                        String teamName = cells.get(1).text().trim();
                        Integer teamId = teamNameToId.get(teamName);

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
                            logger.info("팀 순위 저장: {} - {}위", teamName, ranking);
                        }
                    }
                } catch (Exception e) {
                    logger.error("팀 순위 데이터 처리 중 오류: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("팀 순위 크롤링 중 오류 발생: {}", e.getMessage(), e);
        } finally {
            driver.quit();
        }
    }

    // 팀 WAA 통계 크롤링
    public void crawlTeamWaaStats() {
        int currentYear = 2025;
        String baseUrl = "https://statiz.sporki.com/season/?m=teamoverall&year=" + currentYear;
        
        logger.info("팀 WAA 통계 크롤링 시작: {}", currentYear);

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--remote-allow-origins=*");

        WebDriver driver = new ChromeDriver(options);

        try {
            driver.get(baseUrl);
            Thread.sleep(3000);

            String html = driver.getPageSource();
            Document doc = Jsoup.parse(html);

            Elements rows = doc.select("table.table_type01 tbody tr");

            for (Element row : rows) {
                try {
                    Elements cells = row.select("td");
                    if (cells.size() >= 12) {
                        String teamName = cells.get(1).text().trim();
                        Integer teamId = teamNameToId.get(teamName);

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
                            
                            logger.info("팀 기본 통계 저장: {}", teamName);
                        }
                    }
                } catch (Exception e) {
                    logger.error("팀 WAA 통계 데이터 처리 중 오류: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("팀 WAA 통계 크롤링 중 오류 발생: {}", e.getMessage(), e);
        } finally {
            driver.quit();
        }
    }
}
