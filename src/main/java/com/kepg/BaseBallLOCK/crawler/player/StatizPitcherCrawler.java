package com.kepg.BaseBallLOCK.crawler.player;

import java.util.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.kepg.BaseBallLOCK.modules.player.domain.Player;
import com.kepg.BaseBallLOCK.modules.player.dto.PlayerDTO;
import com.kepg.BaseBallLOCK.modules.player.service.PlayerService;
import com.kepg.BaseBallLOCK.modules.player.stats.service.PitcherStatsService;
import com.kepg.BaseBallLOCK.modules.player.stats.statsDto.PitcherStatsDTO;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class StatizPitcherCrawler {

    private final PlayerService playerService;
    private final PitcherStatsService statsService;

    private static final Map<Integer, String> teamTeIds = new HashMap<>();

    static {
        teamTeIds.put(1, "2002");   // KIA
        teamTeIds.put(2, "6002");   // 두산
        teamTeIds.put(3, "1001");   // 삼성
        teamTeIds.put(4, "9002");   // SSG
        teamTeIds.put(5, "5002");   // LG
        teamTeIds.put(6, "7002");   // 한화	
        teamTeIds.put(7, "11001");  // NC
        teamTeIds.put(8, "12001");  // KT
        teamTeIds.put(9, "3001");   // 롯데
        teamTeIds.put(10, "10001"); // 키움
    }

    /**
     * 매일 밤 11시 50분에 자동으로 투수 기록을 크롤링합니다.
     */
    @Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")
    public void crawl() {
        crawlStats(2025); // 현재 시즌 크롤링
    }
    
    /**
     * 특정 년도의 투수 기록을 크롤링합니다 (수동 실행용).
     */
    public void crawlStats(int year) {
        for (Map.Entry<Integer, String> entry : teamTeIds.entrySet()) {
            int teamId = entry.getKey();
            String teCode = entry.getValue();

            String url = String.format(
                "https://statiz.sporki.com/stats/?m=main&m2=pitching&m3=default&so=WAR&ob=DESC&year=%d&te=%s&po=1&lt=10100&reg=A",
                year, teCode
            );

            // 크롤링 처리 로직 호출
            crawlTeamPitcherStats(teamId, year, url);
        }
    }

    public void crawlTeamPitcherStats(int teamId, int year, String url) {
    	
        System.out.printf("크롤링 중: teamId=%d, year=%d, url=%s%n", teamId, year, url);

        WebDriver driver = null;
        try {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");

            driver = new ChromeDriver(options);
            driver.get(url);

            Thread.sleep(10000);
            
            String pageSource = driver.getPageSource();
        	Document doc = Jsoup.parse(pageSource);
            
        	Element table = doc.selectFirst("table");
            if (table == null) {
                System.out.println("테이블 없음: " + teamId);
                return;
            }
            
            Elements rows = table.select("tr");

            for (Element row : rows) {
                Elements cols = row.select("td");
                if (cols.size() <= 36) continue;

                processRow(cols, year, teamId);
            }

        } catch (Exception e) {
            System.out.println("에러 발생: teamId=" + teamId + ", year=" + year);
            e.printStackTrace();
        } finally {
            if (driver != null) driver.quit();
        }
            
        
    }

    private void processRow(Elements cols, int year, int teamId) {
        String name = cols.get(1).text().trim();
        System.out.println("processRow 진입 - 선수명: " + cols.get(1).text());
        int season = year;
        String position = "P";

        PlayerDTO dto = PlayerDTO.builder().name(name).teamId(teamId).build();
        Player player = playerService.findOrCreatePlayer(dto);
        int playerId = player.getId();

        save(playerId, season, "G", parseInt(cols.get(4).text()), null, position);
        save(playerId, season, "GS", parseInt(cols.get(5).text()), null, position);
        save(playerId, season, "GR", parseInt(cols.get(6).text()), null, position);
        save(playerId, season, "W", parseInt(cols.get(10).text()), null, position);
        save(playerId, season, "L", parseInt(cols.get(11).text()), null, position);
        save(playerId, season, "SV", parseInt(cols.get(12).text()), null, position);
        save(playerId, season, "HLD", parseInt(cols.get(13).text()), null, position);
        save(playerId, season, "IP", parseDouble(cols.get(14).text()), null, position);
        save(playerId, season, "H", parseInt(cols.get(19).text()), null, position);
        save(playerId, season, "HR", parseInt(cols.get(22).text()), null, position);
        save(playerId, season, "BB", parseInt(cols.get(23).text()), null, position);
        save(playerId, season, "SO", parseInt(cols.get(26).text()), null, position);
        save(playerId, season, "ERA", parseDouble(cols.get(30).text()), null, position);
        save(playerId, season, "WHIP", parseDouble(cols.get(35).text()), null, position);
        save(playerId, season, "WAR", parseDouble(cols.get(36).text()), null, position);
    }

    private void save(int playerId, int season, String category, double value, Integer ranking, String position) {
    	System.out.println("[DEBUG] save 호출 - category=ERA");
        PitcherStatsDTO dto = PitcherStatsDTO.builder()
                .playerId(playerId)
                .season(season)
                .category(category)
                .value(value)
                .ranking(ranking)
                .position(position)
                .build();
        statsService.savePitcherStats(dto);
    }

    private int parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private double parseDouble(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }
}
