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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.kepg.BaseBallLOCK.modules.team.teamRanking.domain.TeamRanking;
import com.kepg.BaseBallLOCK.modules.team.teamRanking.repository.TeamRankingRepository;
import com.kepg.BaseBallLOCK.modules.team.teamRanking.service.TeamRankingService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class StatizTeamRankingCrawler {

    private final TeamRankingRepository teamRankingRepository;
    private final TeamRankingService teamRankingService;

    /**
     * 매일 새벽 2시에 자동으로 팀 순위를 크롤링합니다.
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void crawl() {
        crawlStats(2025); // 현재 시즌 크롤링
    }
    
    /**
     * 특정 년도의 팀 순위를 크롤링합니다 (수동 실행용).
     */
    public void crawlStats(int year) {
        String baseUrl = "https://statiz.sporki.com/season/?m=teamoverall&year=%d";
        String url = String.format(baseUrl, year);
        System.out.println("시즌 " + year + " 팀 Ranking 데이터수집 시작");
        System.out.println("▶ URL: " + url);

        WebDriver driver = null;
        try {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");

            driver = new ChromeDriver(options);
            driver.get(url);

        	Thread.sleep(5000);

            String html = driver.getPageSource();
            Document doc = Jsoup.parse(html);

            Element rankingTable = null;
            for (Element box : doc.select("div.item_box")) {
                Element head = box.selectFirst(".box_head");
                if (head != null && head.text().contains("정규 시즌 중")) {
                    rankingTable = box.selectFirst("table");
                    break;
                }
            }

            if (rankingTable == null) {
                System.out.println("▶ 정규 시즌 중 테이블을 찾지 못했습니다.");
                return;
            }

            Elements rows = rankingTable.select("tbody > tr");
            for (Element row : rows) {
                Elements cols = row.select("td");
                if (cols.size() < 8) continue;

                int teamId = resolveTeamId(cols.get(1));
                if (teamId == 0) continue;

                TeamRanking parsed = parseRankingRow(cols, year, teamId);
                saveOrUpdateRanking(parsed);
                System.out.printf("저장 완료 - 시즌 %d, 팀 %d (%d위)\n", year, teamId, parsed.getRanking());
            }

        } catch (Exception e) {
            System.out.println("▶ 에러 발생: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (driver != null) driver.quit();
        }
    }

    private static final Map<String, Integer> teamNameToId = new HashMap<>();
    static {
        teamNameToId.put("KIA", 1);
        teamNameToId.put("두산", 2);
        teamNameToId.put("삼성", 3);
        teamNameToId.put("SSG", 4);
        teamNameToId.put("SK", 4); // 옛 SSG
        teamNameToId.put("LG", 5);
        teamNameToId.put("한화", 6);
        teamNameToId.put("NC", 7);
        teamNameToId.put("KT", 8);
        teamNameToId.put("롯데", 9);
        teamNameToId.put("키움", 10);
    }
    
    private int resolveTeamId(Element teamCell) {
        Element anchor = teamCell.selectFirst("a");
        String teamName = anchor != null ? anchor.ownText().trim() : teamCell.text().trim();
        int teamId = teamNameToId.getOrDefault(teamName, 0);
        System.out.println("▶ 팀명: " + teamName + ", teamId: " + teamId);
        return teamId;
    }
    
    private TeamRanking parseRankingRow(Elements cols, int year, int teamId) {
        int ranking = Integer.parseInt(cols.get(0).text().trim());
        int games = Integer.parseInt(cols.get(2).text().trim());
        int wins = Integer.parseInt(cols.get(3).text().trim());
        int draws = Integer.parseInt(cols.get(4).text().trim());
        int losses = Integer.parseInt(cols.get(5).text().trim());

        String gbText = cols.get(6).text().trim();
        double gamesBehind = gbText.equals("-") ? 0.0 : Double.parseDouble(gbText);
        double winRate = Double.parseDouble(cols.get(7).text().trim());

        return TeamRanking.builder()
                .season(year)
                .teamId(teamId)
                .ranking(ranking)
                .games(games)
                .wins(wins)
                .draws(draws)
                .losses(losses)
                .gamesBehind(gamesBehind)
                .winRate(winRate)
                .build();
    }
    
    private void saveOrUpdateRanking(TeamRanking newEntity) {
        TeamRanking entity = teamRankingRepository.findBySeasonAndTeamId(newEntity.getSeason(), newEntity.getTeamId())
            .map(existing -> {
                existing.setRanking(newEntity.getRanking());
                existing.setGames(newEntity.getGames());
                existing.setWins(newEntity.getWins());
                existing.setDraws(newEntity.getDraws());
                existing.setLosses(newEntity.getLosses());
                existing.setGamesBehind(newEntity.getGamesBehind());
                existing.setWinRate(newEntity.getWinRate());
                return existing;
            }).orElse(newEntity);

        teamRankingRepository.save(entity);
    }
}