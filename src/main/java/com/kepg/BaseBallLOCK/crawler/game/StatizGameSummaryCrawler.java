package com.kepg.BaseBallLOCK.crawler.game;

import java.sql.Timestamp;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import com.kepg.BaseBallLOCK.modules.game.highlight.dto.GameHighlightDTO;
import com.kepg.BaseBallLOCK.modules.game.highlight.service.GameHighlightService;
import com.kepg.BaseBallLOCK.modules.game.schedule.service.ScheduleService;
import com.kepg.BaseBallLOCK.modules.game.scoreBoard.domain.ScoreBoard;
import com.kepg.BaseBallLOCK.modules.game.scoreBoard.service.ScoreBoardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatizGameSummaryCrawler {

    private final ScheduleService scheduleService;
    private final ScoreBoardService scoreBoardService;
    private final GameHighlightService gameHighlightService;

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
    
    // 1경기의 스코어보드 기록(scoreBoard) 및 하이라이트(gameHighlight)
    public void crawl() {
        String baseUrl = "https://statiz.sporki.com/schedule/?m=summary&s_no=%d";

        for (int statizId = 20250191; statizId <= 20250200; statizId++) {
            log.info("크롤링 시작: " + statizId);
            WebDriver driver = null;
            try {
                ChromeOptions options = new ChromeOptions();
                options.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage");
                driver = new ChromeDriver(options);

                String url = String.format(baseUrl, statizId);
                driver.get(url);
                Thread.sleep(5000);

                String html = driver.getPageSource();
                Document doc = Jsoup.parse(html);

                Element scoreTable = doc.selectFirst("div.box_type_boared > div.item_box.w100 .table_type03 table");

                if (scoreTable == null) {
                    log.info("스코어 테이블 없음: " + statizId);
                    continue;
                }

                Elements rows = scoreTable.select("tbody > tr");
                if (rows.size() < 2) {
                    log.info("팀 정보 행 부족: " + statizId);
                    continue;
                }

                Element awayRow = rows.get(0);
                Element homeRow = rows.get(1);

                Elements awayTds = awayRow.select("td");
                Elements homeTds = homeRow.select("td");

                String awayTeam = awayTds.get(0).text().trim();
                String homeTeam = homeTds.get(0).text().trim();

                int homeTeamId = teamNameToId.getOrDefault(homeTeam, 0);
                int awayTeamId = teamNameToId.getOrDefault(awayTeam, 0);
                if (homeTeamId == 0 || awayTeamId == 0) {
                    log.info("TeamId 추출 실패");
                    continue;
                }

                Element dateElement = doc.selectFirst(".callout_box .txt");
                if (dateElement == null) {
                    log.info("날짜 정보 없음: " + statizId);
                    continue;
                }
                
                String[] dateParts = dateElement.text().split(",");
                if (dateParts.length < 2) {
                    log.info("날짜 파싱 실패: " + statizId);
                    continue;
                }
                
                String datePart = dateParts[1].trim();
                int year = statizId / 10000;
                LocalDate matchDate = LocalDate.parse(year + "-" + datePart, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                Timestamp matchDateTime = Timestamp.valueOf(matchDate.atStartOfDay());

                Integer scheduleId = scheduleService.findIdByDateAndTeams(matchDateTime, homeTeamId, awayTeamId);
                if (scheduleId == null) {
                    log.info("ScheduleId 매칭 실패");
                    continue;
                }

                 List<Integer> awayScores = new ArrayList<>();
                 List<Integer> homeScores = new ArrayList<>();
                 parseScores(awayTds, awayScores);
                 parseScores(homeTds, homeScores);

                 int awayR = getInt(awayTds.get(13));
                 int awayH = getInt(awayTds.get(14));
                 int awayE = getInt(awayTds.get(15));
                 int awayB = getInt(awayTds.get(16));

                 int homeR = getInt(homeTds.get(13));
                 int homeH = getInt(homeTds.get(14));
                 int homeE = getInt(homeTds.get(15));
                 int homeB = getInt(homeTds.get(16));

                 String winPitcher = extractPitcher(doc, ".game_result .win a");
                 String losePitcher = extractPitcher(doc, ".game_result .lose a");

                 saveScoreBoard(scheduleId, homeScores, awayScores, homeR, homeH, homeE, homeB, awayR, awayH, awayE, awayB, winPitcher, losePitcher);
                 log.info("scoreBoard 저장 완료: " + statizId + " ScheduleId: " + scheduleId);

                saveGameHighlights(doc, scheduleId);
                Thread.sleep(3000);

            } catch (Exception e) {
                log.info("오류 발생: " + statizId);
                e.printStackTrace();
            } finally {
                if (driver != null) driver.quit();
            }
        }
    }

    private void parseScores(Elements tds, List<Integer> scoreList) {
        for (int i = 1; i <= 9; i++) {
            String score = Optional.ofNullable(tds.get(i).selectFirst(".score")).map(Element::ownText).orElse("0");
            scoreList.add(Integer.parseInt(score.trim()));
        }
    }

    private int getInt(Element td) {
        Element scoreElement = td.selectFirst(".score");
        if (scoreElement == null) {
            return 0; // 기본값 반환
        }
        try {
            return Integer.parseInt(scoreElement.ownText().trim());
        } catch (NumberFormatException e) {
            return 0; // 파싱 실패 시 기본값 반환
        }
    }

    private void saveScoreBoard(Integer scheduleId, List<Integer> homeScores, List<Integer> awayScores,
                                 int homeR, int homeH, int homeE, int homeB,
                                 int awayR, int awayH, int awayE, int awayB,
                                 String winPitcher, String losePitcher) {

        ScoreBoard scoreBoard = ScoreBoard.builder()
                .scheduleId(scheduleId)
                .homeScore(homeR).awayScore(awayR)
                .homeInningScores(toInningString(homeScores))
                .awayInningScores(toInningString(awayScores))
                .homeR(homeR).homeH(homeH).homeE(homeE).homeB(homeB)
                .awayR(awayR).awayH(awayH).awayE(awayE).awayB(awayB)
                .winPitcher(winPitcher)
                .losePitcher(losePitcher)
                .build();

        scoreBoardService.saveOrUpdate(scoreBoard);
    }
    
    private void saveGameHighlights(Document doc, Integer scheduleId) {
        Element highlightBox = doc.selectFirst("div.sh_box:has(.box_head:contains(결정적 장면 Best 5)) table");
        if (highlightBox == null) {
            log.info("결정적 장면 테이블 없음");
            return;
        }

        Elements rows = highlightBox.select("tbody > tr");
        int ranking = 1;

        for (Element row : rows) {
            Elements tds = row.select("td");

            String inning = tds.get(0).text().trim();
            String pitcher = tds.get(1).text().trim();
            String batter = tds.get(2).text().trim();
            String pitchCount = tds.get(3).text().trim();
            String result = tds.get(4).text().trim();
            String beforeSituation = tds.get(5).text().trim();
            String afterSituation = tds.get(6).text().trim();

            GameHighlightDTO dto = GameHighlightDTO.builder()
                    .scheduleId(scheduleId)
                    .ranking(ranking++)
                    .inning(inning)
                    .pitcherName(pitcher)
                    .batterName(batter)
                    .pitchCount(pitchCount)
                    .result(result)
                    .beforeSituation(beforeSituation)
                    .afterSituation(afterSituation)
                    .build();

            gameHighlightService.saveOrUpdate(dto);
        }

        log.info("gameHighlight 저장 완료: " + scheduleId);
    }

    private String toInningString(List<Integer> scores) {
        return String.join(" ", scores.subList(0, 9).stream()
                .map(String::valueOf)
                .toArray(String[]::new));
    }

    private String extractPitcher(Document doc, String selector) {
        Element element = doc.selectFirst(selector);
        return element != null ? element.text() : null;
    }
}
