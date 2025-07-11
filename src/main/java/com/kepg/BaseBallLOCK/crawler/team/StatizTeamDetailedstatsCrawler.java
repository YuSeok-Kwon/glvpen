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
public class StatizTeamDetailedstatsCrawler {

    private final TeamStatsService teamStatsService;
    
    // 팀 투수, 타자 세부지표 (teamStats)
    public void crawl(String... args) {
        String baseUrl = "https://statiz.sporki.com/stats/?m=team&m2=%s&m3=default&so=WAR&ob=DESC&year=%d";
        String[] types = {"batting", "pitching"};
        int[] years = {2025};

        for (String type : types) {
            for (int year : years) {
            	WebDriver driver = null;
                try {
                	String url = String.format(baseUrl, type, year);
                	System.out.println("크롤링: " + year + ", " + type);
                	
                    ChromeOptions options = new ChromeOptions();
                    options.addArguments("--headless");
                    options.addArguments("--no-sandbox");
                    options.addArguments("--disable-dev-shm-usage");

                    driver = new ChromeDriver(options);
                    driver.get(url);

                	Thread.sleep(5000);

                    String html = driver.getPageSource();
                    Document doc = Jsoup.parse(html);

                    Elements tables = doc.select("div.table_type01 > table");
                    if (tables.size() < 2) continue;

                    Element table = tables.get(1);
                    for (Element row : table.select("tbody > tr")) {
                        Elements cols = row.select("td");
                        if (type.equals("batting") && cols.size() >= 33) {
                            processBattingRow(cols, year);
                        } else if (type.equals("pitching") && cols.size() >= 36) {
                            processPitchingRow(cols, year);
                        }
                    }

                } catch (Exception e) {
                    System.out.printf("크롤링 실패 (year: %d, type: %s): %s\n", year, type, e.getMessage());
                    e.printStackTrace();
                } finally {
                    if (driver != null) driver.quit();
                }
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

    private void processBattingRow(Elements cols, int year) {
        int teamId = extractTeamId(cols.get(1));
        if (teamId == 0) return;

        save(teamId, year, "HR", parse(cols.get(14)));
        save(teamId, year, "SB", parse(cols.get(17)));
        save(teamId, year, "AVG", parse(cols.get(26)));
        save(teamId, year, "OBP", parse(cols.get(27)));
        save(teamId, year, "SLG", parse(cols.get(28)));
        save(teamId, year, "OPS", parse(cols.get(29)));
        save(teamId, year, "wRC+", parse(cols.get(31)));
        save(teamId, year, "BetterWAR", parse(cols.get(32)));
    }

    private void processPitchingRow(Elements cols, int year) {
        int teamId = extractTeamId(cols.get(1));
        if (teamId == 0) return;

        save(teamId, year, "BB", parse(cols.get(23)));
        save(teamId, year, "SO", parse(cols.get(26)));
        save(teamId, year, "ERA", parse(cols.get(30)));
        save(teamId, year, "WHIP", parse(cols.get(34)));
        save(teamId, year, "PitcherWAR", parse(cols.get(35)));
    }

    private int extractTeamId(Element teamCell) {
        String teamName = teamCell.text().trim();
        int teamId = teamNameToId.getOrDefault(teamName, 0);
        System.out.println("▶ 팀명: " + teamName + ", teamId: " + teamId);
        return teamId;
    }

    private void save(int teamId, int year, String category, double value) {
        teamStatsService.saveOrUpdate(teamId, year, category, value, null);
        System.out.printf("팀 투수, 타자 세부지표 (teamStats) 저장 완료 - 시즌 %d, 팀 %d\n", year, teamId);
        }

    private double parse(Element col) {
        try {
            return Double.parseDouble(col.text().trim());
        } catch (Exception e) {
            return 0.0;
        }
    }
}