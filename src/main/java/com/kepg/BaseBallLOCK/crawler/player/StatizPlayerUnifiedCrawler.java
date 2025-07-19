package com.kepg.BaseBallLOCK.crawler.player;

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

import com.kepg.BaseBallLOCK.modules.player.domain.Player;
import com.kepg.BaseBallLOCK.modules.player.dto.PlayerDTO;
import com.kepg.BaseBallLOCK.modules.player.service.PlayerService;
import com.kepg.BaseBallLOCK.modules.player.stats.service.BatterStatsService;
import com.kepg.BaseBallLOCK.modules.player.stats.service.PitcherStatsService;
import com.kepg.BaseBallLOCK.modules.player.stats.statsDto.BatterStatsDTO;
import com.kepg.BaseBallLOCK.modules.player.stats.statsDto.PitcherStatsDTO;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class StatizPlayerUnifiedCrawler {

    private static final Logger logger = LoggerFactory.getLogger(StatizPlayerUnifiedCrawler.class);
    
    private final PlayerService playerService;
    private final BatterStatsService batterStatsService;
    private final PitcherStatsService pitcherStatsService;

    private static final Map<Integer, String> teamTeIds = new HashMap<>();
    static {
        teamTeIds.put(1, "2002");   // KIA
        teamTeIds.put(2, "6002");   // 두산
        teamTeIds.put(3, "1001");   // 삼성
        teamTeIds.put(4, "5002");   // LG
        teamTeIds.put(5, "12001");  // KT
        teamTeIds.put(6, "9002");   // SSG
        teamTeIds.put(7, "3001");   // 롯데
        teamTeIds.put(8, "7002");   // 한화
        teamTeIds.put(9, "11001");  // NC
        teamTeIds.put(10, "10001"); // 키움
    }

    // 매일 자정 이후에 플레이어 데이터 크롤링 실행
    @Scheduled(cron = "0 0 1 * * *")
    public void runScheduledCrawling() {
        logger.info("=== 플레이어 데이터 크롤링 시작 ===");
        crawlAllPlayerData();
        logger.info("=== 플레이어 데이터 크롤링 완료 ===");
    }

    // 수동 실행용 메서드
    public void crawlAllPlayerData() {
        crawlBatterStats();
        crawlPitcherStats();
    }

    // 타자 통계 크롤링
    public void crawlBatterStats() {
        int currentYear = 2025;
        logger.info("타자 데이터 크롤링 시작: {}", currentYear);

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--remote-allow-origins=*");

        WebDriver driver = new ChromeDriver(options);

        try {
            for (Map.Entry<Integer, String> entry : teamTeIds.entrySet()) {
                int teamId = entry.getKey();
                String teId = entry.getValue();
                
                logger.info("팀 {} 타자 데이터 크롤링 시작", teamId);
                
                String url = String.format("https://statiz.sporki.com/team/?m=playerrecord&t_code=%s&year=%d&stt=1", 
                                         teId, currentYear);
                driver.get(url);
                Thread.sleep(3000);

                String html = driver.getPageSource();
                Document doc = Jsoup.parse(html);

                Elements rows = doc.select("table tbody tr");

                for (Element row : rows) {
                    try {
                        processBatterRow(row, teamId, currentYear);
                    } catch (Exception e) {
                        logger.error("타자 데이터 처리 중 오류: {}", e.getMessage());
                    }
                }
                
                logger.info("팀 {} 타자 데이터 크롤링 완료", teamId);
            }

        } catch (Exception e) {
            logger.error("타자 크롤링 중 오류 발생: {}", e.getMessage(), e);
        } finally {
            driver.quit();
        }
    }

    // 투수 통계 크롤링
    public void crawlPitcherStats() {
        int currentYear = 2025;
        logger.info("투수 데이터 크롤링 시작: {}", currentYear);

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--remote-allow-origins=*");

        WebDriver driver = new ChromeDriver(options);

        try {
            for (Map.Entry<Integer, String> entry : teamTeIds.entrySet()) {
                int teamId = entry.getKey();
                String teId = entry.getValue();
                
                logger.info("팀 {} 투수 데이터 크롤링 시작", teamId);
                
                String url = String.format("https://statiz.sporki.com/team/?m=playerrecord&t_code=%s&year=%d&stt=2", 
                                         teId, currentYear);
                driver.get(url);
                Thread.sleep(3000);

                String html = driver.getPageSource();
                Document doc = Jsoup.parse(html);

                Elements rows = doc.select("table tbody tr");

                for (Element row : rows) {
                    try {
                        processPitcherRow(row, teamId, currentYear);
                    } catch (Exception e) {
                        logger.error("투수 데이터 처리 중 오류: {}", e.getMessage());
                    }
                }
                
                logger.info("팀 {} 투수 데이터 크롤링 완료", teamId);
            }

        } catch (Exception e) {
            logger.error("투수 크롤링 중 오류 발생: {}", e.getMessage(), e);
        } finally {
            driver.quit();
        }
    }

    // 타자 데이터 처리
    private void processBatterRow(Element row, int teamId, int year) {
        Elements cols = row.select("td");
        if (cols.size() < 15) return;

        try {
            String name = cols.get(0).text().trim();
            String position = cols.get(1).text().trim();
            
            // 플레이어 정보 저장/업데이트
            PlayerDTO playerDTO = PlayerDTO.builder()
                    .name(name)
                    .teamId(teamId)
                    .build();
            
            Player player = playerService.findOrCreatePlayer(playerDTO);
            int playerId = player.getId();

            // 타자 통계 데이터 저장
            saveBatterStat(playerId, year, position, "G", parseDouble(cols.get(2)));
            saveBatterStat(playerId, year, position, "PA", parseDouble(cols.get(3)));
            saveBatterStat(playerId, year, position, "AB", parseDouble(cols.get(4)));
            saveBatterStat(playerId, year, position, "R", parseDouble(cols.get(5)));
            saveBatterStat(playerId, year, position, "H", parseDouble(cols.get(6)));
            saveBatterStat(playerId, year, position, "2B", parseDouble(cols.get(7)));
            saveBatterStat(playerId, year, position, "3B", parseDouble(cols.get(8)));
            saveBatterStat(playerId, year, position, "HR", parseDouble(cols.get(9)));
            saveBatterStat(playerId, year, position, "RBI", parseDouble(cols.get(10)));
            saveBatterStat(playerId, year, position, "SB", parseDouble(cols.get(11)));
            saveBatterStat(playerId, year, position, "BB", parseDouble(cols.get(12)));
            saveBatterStat(playerId, year, position, "SO", parseDouble(cols.get(13)));
            saveBatterStat(playerId, year, position, "AVG", parseDouble(cols.get(14)));
            
            if (cols.size() > 15) {
                saveBatterStat(playerId, year, position, "OBP", parseDouble(cols.get(15)));
            }
            if (cols.size() > 16) {
                saveBatterStat(playerId, year, position, "SLG", parseDouble(cols.get(16)));
            }
            if (cols.size() > 17) {
                saveBatterStat(playerId, year, position, "OPS", parseDouble(cols.get(17)));
            }
            if (cols.size() > 18) {
                saveBatterStat(playerId, year, position, "WAR", parseDouble(cols.get(18)));
            }

            logger.debug("타자 데이터 저장: {} ({})", name, position);

        } catch (Exception e) {
            logger.error("타자 행 처리 중 오류: {}", e.getMessage());
        }
    }

    // 투수 데이터 처리
    private void processPitcherRow(Element row, int teamId, int year) {
        Elements cols = row.select("td");
        if (cols.size() < 15) return;

        try {
            String name = cols.get(0).text().trim();
            String position = cols.get(1).text().trim();
            
            // 플레이어 정보 저장/업데이트
            PlayerDTO playerDTO = PlayerDTO.builder()
                    .name(name)
                    .teamId(teamId)
                    .build();
            
            Player player = playerService.findOrCreatePlayer(playerDTO);
            int playerId = player.getId();

            // 투수 통계 데이터 저장
            savePitcherStat(playerId, year, position, "G", parseDouble(cols.get(2)));
            savePitcherStat(playerId, year, position, "GS", parseDouble(cols.get(3)));
            savePitcherStat(playerId, year, position, "W", parseDouble(cols.get(4)));
            savePitcherStat(playerId, year, position, "L", parseDouble(cols.get(5)));
            savePitcherStat(playerId, year, position, "SV", parseDouble(cols.get(6)));
            savePitcherStat(playerId, year, position, "HLD", parseDouble(cols.get(7)));
            savePitcherStat(playerId, year, position, "IP", parseDouble(cols.get(8)));
            savePitcherStat(playerId, year, position, "H", parseDouble(cols.get(9)));
            savePitcherStat(playerId, year, position, "HR", parseDouble(cols.get(10)));
            savePitcherStat(playerId, year, position, "BB", parseDouble(cols.get(11)));
            savePitcherStat(playerId, year, position, "SO", parseDouble(cols.get(12)));
            savePitcherStat(playerId, year, position, "R", parseDouble(cols.get(13)));
            savePitcherStat(playerId, year, position, "ER", parseDouble(cols.get(14)));
            
            if (cols.size() > 15) {
                savePitcherStat(playerId, year, position, "ERA", parseDouble(cols.get(15)));
            }
            if (cols.size() > 16) {
                savePitcherStat(playerId, year, position, "WHIP", parseDouble(cols.get(16)));
            }
            if (cols.size() > 17) {
                savePitcherStat(playerId, year, position, "WAR", parseDouble(cols.get(17)));
            }

            logger.debug("투수 데이터 저장: {} ({})", name, position);

        } catch (Exception e) {
            logger.error("투수 행 처리 중 오류: {}", e.getMessage());
        }
    }

    // 타자 통계 저장
    private void saveBatterStat(int playerId, int season, String position, String category, double value) {
        BatterStatsDTO dto = BatterStatsDTO.builder()
                .playerId(playerId)
                .season(season)
                .position(position)
                .category(category)
                .value(value)
                .ranking(null)
                .build();
        
        batterStatsService.saveBatterStats(dto);
    }

    // 투수 통계 저장
    private void savePitcherStat(int playerId, int season, String position, String category, double value) {
        PitcherStatsDTO dto = PitcherStatsDTO.builder()
                .playerId(playerId)
                .season(season)
                .position(position)
                .category(category)
                .value(value)
                .ranking(null)
                .build();
        
        pitcherStatsService.savePitcherStats(dto);
    }

    // 숫자 파싱 헬퍼 메서드
    private double parseDouble(Element element) {
        try {
            String text = element.text().trim();
            if (text.isEmpty() || text.equals("-")) {
                return 0.0;
            }
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
