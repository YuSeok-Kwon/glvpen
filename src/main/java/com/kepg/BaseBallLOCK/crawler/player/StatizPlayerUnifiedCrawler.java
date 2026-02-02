package com.kepg.BaseBallLOCK.crawler.player;

import static com.kepg.BaseBallLOCK.crawler.util.CrawlerUtils.*;
import static com.kepg.BaseBallLOCK.crawler.util.TeamMappingConstants.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.kepg.BaseBallLOCK.crawler.util.WebDriverFactory;
import com.kepg.BaseBallLOCK.modules.player.domain.Player;
import com.kepg.BaseBallLOCK.modules.player.dto.PlayerDTO;
import com.kepg.BaseBallLOCK.modules.player.service.PlayerService;
import com.kepg.BaseBallLOCK.modules.player.stats.service.BatterStatsService;
import com.kepg.BaseBallLOCK.modules.player.stats.service.PitcherStatsService;
import com.kepg.BaseBallLOCK.modules.player.stats.statsDto.BatterStatsDTO;
import com.kepg.BaseBallLOCK.modules.player.stats.statsDto.PitcherStatsDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatizPlayerUnifiedCrawler {

    private final PlayerService playerService;
    private final BatterStatsService batterStatsService;
    private final PitcherStatsService pitcherStatsService;

    // ==================== 상수 정의 ====================
    private static final int PAGE_LOAD_WAIT_MS = 3000;
    private static final int CURRENT_YEAR = LocalDate.now().getYear();

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

    // ==================== 공개 메서드 ====================

    /**
     * 매일 자정 이후에 플레이어 데이터 크롤링을 자동 실행합니다.
     */
    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
    public void runScheduledCrawling() {
        log.info("=== 플레이어 데이터 자동 크롤링 시작: {} ===", CURRENT_YEAR);
        crawlAllPlayerData();
        log.info("=== 플레이어 데이터 자동 크롤링 완료 ===");
    }

    /**
     * 모든 플레이어 데이터를 크롤링합니다 (수동 실행용).
     */
    public void crawlAllPlayerData() {
        crawlBatterStats();
        crawlPitcherStats();
    }

    /**
     * 타자 통계를 크롤링합니다.
     */
    public void crawlBatterStats() {
        log.info("=== 타자 데이터 크롤링 시작: {} ===", CURRENT_YEAR);

        WebDriver driver = null;
        int totalTeams = teamTeIds.size();
        int successTeams = 0;
        int totalPlayers = 0;

        try {
            driver = createWebDriver();

            for (Map.Entry<Integer, String> entry : teamTeIds.entrySet()) {
                int teamId = entry.getKey();
                String teId = entry.getValue();

                log.info("팀 {} 타자 데이터 크롤링 시작", teamId);

                try {
                    String url = buildPlayerUrl(teId, CURRENT_YEAR, 1);
                    Document doc = loadPage(driver, url);
                    Elements rows = doc.select("table tbody tr");

                    int playerCount = 0;
                    for (Element row : rows) {
                        try {
                            processBatterRow(row, teamId, CURRENT_YEAR);
                            playerCount++;
                        } catch (Exception e) {
                            log.error("타자 데이터 처리 중 오류: {}", e.getMessage());
                        }
                    }

                    totalPlayers += playerCount;
                    successTeams++;
                    log.info("팀 {} 타자 데이터 크롤링 완료: {} 명", teamId, playerCount);

                } catch (Exception e) {
                    log.error("팀 {} 타자 크롤링 실패: {}", teamId, e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("타자 크롤링 중 오류 발생: {}", e.getMessage(), e);
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }

        // 크롤링 통계
        log.info("=== 타자 크롤링 완료 ===");
        log.info("총 {}팀 중 {}팀 성공", totalTeams, successTeams);
        log.info("총 {}명의 타자 데이터 처리", totalPlayers);
    }

    /**
     * 투수 통계를 크롤링합니다.
     */
    public void crawlPitcherStats() {
        log.info("=== 투수 데이터 크롤링 시작: {} ===", CURRENT_YEAR);

        WebDriver driver = null;
        int totalTeams = teamTeIds.size();
        int successTeams = 0;
        int totalPlayers = 0;

        try {
            driver = createWebDriver();

            for (Map.Entry<Integer, String> entry : teamTeIds.entrySet()) {
                int teamId = entry.getKey();
                String teId = entry.getValue();

                log.info("팀 {} 투수 데이터 크롤링 시작", teamId);

                try {
                    String url = buildPlayerUrl(teId, CURRENT_YEAR, 2);
                    Document doc = loadPage(driver, url);
                    Elements rows = doc.select("table tbody tr");

                    int playerCount = 0;
                    for (Element row : rows) {
                        try {
                            processPitcherRow(row, teamId, CURRENT_YEAR);
                            playerCount++;
                        } catch (Exception e) {
                            log.error("투수 데이터 처리 중 오류: {}", e.getMessage());
                        }
                    }

                    totalPlayers += playerCount;
                    successTeams++;
                    log.info("팀 {} 투수 데이터 크롤링 완료: {} 명", teamId, playerCount);

                } catch (Exception e) {
                    log.error("팀 {} 투수 크롤링 실패: {}", teamId, e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("투수 크롤링 중 오류 발생: {}", e.getMessage(), e);
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }

        // 크롤링 통계
        log.info("=== 투수 크롤링 완료 ===");
        log.info("총 {}팀 중 {}팀 성공", totalTeams, successTeams);
        log.info("총 {}명의 투수 데이터 처리", totalPlayers);
    }

    // ==================== 헬퍼 메서드 ====================

    /**
     * WebDriver 인스턴스를 생성합니다.
     */
    private WebDriver createWebDriver() {
        return WebDriverFactory.createChromeDriverWithExtendedOptions();
    }

    /**
     * 플레이어 통계 페이지 URL을 생성합니다.
     *
     * @param teId 팀 ID
     * @param year 시즌 년도
     * @param stt 통계 타입 (1: 타자, 2: 투수)
     */
    private String buildPlayerUrl(String teId, int year, int stt) {
        return String.format("https://statiz.sporki.com/team/?m=playerrecord&t_code=%s&year=%d&stt=%d",
                           teId, year, stt);
    }

    /**
     * 페이지를 로드하고 파싱합니다.
     */
    private Document loadPage(WebDriver driver, String url) throws InterruptedException {
        return CrawlerUtils.loadPage(driver, url, PAGE_LOAD_WAIT_MS);
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

            log.debug("타자 데이터 저장: {} ({})", name, position);

        } catch (Exception e) {
            log.error("타자 행 처리 중 오류: {}", e.getMessage());
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

            log.debug("투수 데이터 저장: {} ({})", name, position);

        } catch (Exception e) {
            log.error("투수 행 처리 중 오류: {}", e.getMessage());
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
}
