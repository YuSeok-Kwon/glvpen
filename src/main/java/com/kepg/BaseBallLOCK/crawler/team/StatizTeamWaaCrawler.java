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
import org.springframework.stereotype.Component;

import com.kepg.BaseBallLOCK.modules.team.teamStats.service.TeamStatsService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class StatizTeamWaaCrawler  {

    private final TeamStatsService teamStatsService;

    // 팀 WAA기록 (teamStats)
    public void crawl() {
        int[] years = {2025};
        String baseUrl = "https://statiz.sporki.com/season/?m=teamoverall&year=%d";

        for (int year : years) {
            String url = String.format(baseUrl, year);
            System.out.println("시즌 " + year + " 팀 분석(WAA) 시작");

            WebDriver driver = null;
            try {
            	ChromeOptions options = new ChromeOptions();
            	options.addArguments("--headless");  // UI 없이 백그라운드 실행
            	options.addArguments("--no-sandbox");
            	options.addArguments("--disable-dev-shm-usage");

            	driver = new ChromeDriver(options);
            	driver.get(url);

            	Thread.sleep(5000);
                
            	String pageSource = driver.getPageSource();
            	Document doc = Jsoup.parse(pageSource);

            	Element rankingTable = null;
            	for (Element box : doc.select("div.item_box")) {
            		Element head = box.selectFirst(".box_head");
            		if (head != null && head.text().contains("팀 분석 (WAA)")) {
            			rankingTable = box.selectFirst("table");
            			break;
            		}
            	}
     
     
            	if (rankingTable == null) {
            		System.out.println("팀 분석 (WAA) 테이블을 찾지 못했습니다.");
            		continue;
            	}

                Elements rows = rankingTable.select("tbody > tr");
                System.out.println("WAA rows 수: " + rows.size());


                for (Element row : rows) {
                    processRow(row, year);
                }

            } catch (Exception e) {
                System.out.printf("크롤링 실패 (year: %d): %s\n", year, e.getMessage());
                e.printStackTrace();
            } finally {
                if (driver != null) driver.quit();
            }
        }
    }

    private static final Map<String, Integer> teamNameToId = new HashMap<>();
    static {
        teamNameToId.put("KIA", 1);
        teamNameToId.put("두산", 2);
        teamNameToId.put("삼성", 3);
        teamNameToId.put("SSG", 4);
        teamNameToId.put("LG", 5);
        teamNameToId.put("한화", 6);
        teamNameToId.put("NC", 7);
        teamNameToId.put("KT", 8);
        teamNameToId.put("롯데", 9);
        teamNameToId.put("키움", 10);
    }
    
    private void processRow(Element row, int year) {
        Elements cols = row.select("td");
        if (cols.size() < 6) return;

        String teamName = cols.get(0).text().trim();
        int teamId = teamNameToId.getOrDefault(teamName, 0);
        System.out.println("팀명: " + teamName + ", teamId: " + teamId);
        if (teamId == 0) return;

        double hitting  = parse(cols.get(1));
        double running  = parse(cols.get(2));
        double defense  = parse(cols.get(3));
        double starting = parse(cols.get(4));
        double bullpen  = parse(cols.get(5));

        teamStatsService.saveOrUpdate(teamId, year, "타격", hitting, null);
        teamStatsService.saveOrUpdate(teamId, year, "주루", running, null);
        teamStatsService.saveOrUpdate(teamId, year, "수비", defense, null);
        teamStatsService.saveOrUpdate(teamId, year, "선발", starting, null);
        teamStatsService.saveOrUpdate(teamId, year, "불펜", bullpen, null);

        System.out.printf("teamStats WAA 저장 완료 - [%s] (%d)\n", teamName, year);
    }

    private double parse(Element col) {
        try {
            return Double.parseDouble(col.text().trim());
        } catch (Exception e) {
            return 0.0;
        }
    }
}