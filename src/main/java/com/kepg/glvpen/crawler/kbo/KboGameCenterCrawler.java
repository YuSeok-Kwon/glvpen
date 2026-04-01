package com.kepg.glvpen.crawler.kbo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.springframework.stereotype.Service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kepg.glvpen.crawler.kbo.util.KboConstants;
import com.kepg.glvpen.modules.game.keyPlayer.domain.GameKeyPlayer;
import com.kepg.glvpen.modules.game.keyPlayer.repository.GameKeyPlayerRepository;
import com.kepg.glvpen.modules.game.keyPlayer.service.GameKeyPlayerService;
import com.kepg.glvpen.modules.game.lineUp.repository.BatterLineupRepository;
import com.kepg.glvpen.modules.game.lineUp.service.LineupService;
import com.kepg.glvpen.modules.game.record.service.RecordService;
import com.kepg.glvpen.modules.game.schedule.domain.Schedule;
import com.kepg.glvpen.modules.game.schedule.repository.ScheduleRepository;
import com.kepg.glvpen.modules.game.scoreBoard.domain.ScoreBoard;
import com.kepg.glvpen.modules.game.scoreBoard.service.ScoreBoardService;
import com.kepg.glvpen.modules.game.summaryRecord.domain.GameSummaryRecord;
import com.kepg.glvpen.modules.game.summaryRecord.service.GameSummaryRecordService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * KBO 게임센터 크롤러
 *
 * 스코어보드, 박스스코어(타자/투수 기록), 상세기록, 키플레이어 데이터를 수집한다.
 * Playwright 불필요 — HTTP POST로 JSON 응답 직접 수신 후 파싱
 *
 * API 응답 특이사항:
 * - table 필드들(table1, table2, table3, tableEtc)은 JSON 문자열(double-encoded)
 * - 이닝 점수는 table2.rows (rows[0]=원정, rows[1]=홈)
 * - R/H/E/B는 table3.rows (rows[0]=원정, rows[1]=홈)
 * - 타자 기록: arrHitter[].table1(선수정보) + table3(스탯) — 모두 JSON 문자열
 * - 투수 기록: arrPitcher[].table — JSON 문자열
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KboGameCenterCrawler {

    private final ScheduleRepository scheduleRepository;
    private final ScoreBoardService scoreBoardService;
    private final RecordService recordService;
    private final LineupService lineupService;
    private final BatterLineupRepository batterLineupRepository;
    private final GameKeyPlayerService keyPlayerService;
    private final GameKeyPlayerRepository keyPlayerRepository;
    private final GameSummaryRecordService summaryRecordService;

    private final HttpClient httpClient = createHttpClient();

    // 키플레이어 투수 지표 목록
    private static final String[] PITCHER_METRICS = {
        "GAME_WPA_RT", "WPA_RT", "KK_CN", "RELIEF_KK_CN", "WHIP_RT", "INN2_CN", "ERA_RT"
    };

    // 키플레이어 타자 지표 목록
    private static final String[] HITTER_METRICS = {
        "GAME_WPA_RT", "WPA_RT", "HR_CN", "HIT_CN", "RBI_CN", "SB_CN"
    };

    private static HttpClient createHttpClient() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }}, new SecureRandom());

            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        } catch (Exception e) {
            return HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
    }

    // ==================== 공개 메서드 ====================

    /**
     * 단일 경기 게임센터 크롤링
     */
    public void crawlGameCenter(String kboGameId, int season) {
        Schedule schedule = scheduleRepository.findByKboGameId(kboGameId).orElse(null);
        if (schedule == null) {
            log.warn("[게임센터] Schedule 없음: {}", kboGameId);
            return;
        }

        int scheduleId = schedule.getId();
        String srId = resolveSrId(schedule);

        log.info("[게임센터] 크롤링 시작: {} (scheduleId={}, srId={})", kboGameId, scheduleId, srId);

        // 1. 스코어보드 + 메타데이터
        crawlScoreBoard(kboGameId, season, scheduleId, srId);

        // 2. 박스스코어 (타자/투수 기록 + 상세기록)
        crawlBoxScore(kboGameId, season, scheduleId, srId);

        // 3. 키플레이어 - 투수
        crawlKeyPlayers(kboGameId, season, scheduleId, srId, "PITCHER",
                KboConstants.GAME_CENTER_KEY_PITCHER_URL, PITCHER_METRICS);

        // 4. 키플레이어 - 타자
        crawlKeyPlayers(kboGameId, season, scheduleId, srId, "HITTER",
                KboConstants.GAME_CENTER_KEY_HITTER_URL, HITTER_METRICS);

        log.info("[게임센터] 크롤링 완료: {}", kboGameId);
    }

    /**
     * 시즌 전체 게임센터 크롤링
     */
    public void crawlAllGameCenters(int season) {
        crawlAllGameCenters(season, false);
    }

    /**
     * 시즌 전체 게임센터 크롤링 (강제 재크롤링 옵션)
     */
    public void crawlAllGameCenters(int season, boolean forceRecrawl) {
        log.info("[게임센터] {}시즌 전체 크롤링 시작 (force={})", season, forceRecrawl);

        List<Schedule> games = scheduleRepository.findFinishedGamesBySeason(season);
        log.info("[게임센터] {}시즌 종료된 경기 {}건 발견", season, games.size());

        int crawled = 0;
        int skipped = 0;

        for (Schedule game : games) {
            String kboGameId = game.getKboGameId();
            int scheduleId = game.getId();

            // 이미 크롤링된 경기는 스킵 (강제 재크롤링이 아닌 경우)
            if (!forceRecrawl && isAlreadyCrawled(scheduleId)) {
                skipped++;
                continue;
            }

            try {
                crawlGameCenter(kboGameId, season);
                crawled++;

                // API 부하 방지
                Thread.sleep(300);
            } catch (Exception e) {
                log.error("[게임센터] 크롤링 실패: {} - {}", kboGameId, e.getMessage());
            }
        }

        log.info("[게임센터] {}시즌 완료: {}건 크롤링, {}건 스킵", season, crawled, skipped);
    }

    /**
     * 시범경기 전용 게임센터 크롤링
     * seriesType="1"인 경기만 타겟팅하여 크롤링
     */
    public void crawlExhibitionGameCenters(int season, boolean forceRecrawl) {
        log.info("[게임센터] {}시즌 시범경기 크롤링 시작 (force={})", season, forceRecrawl);

        List<Schedule> games = scheduleRepository.findFinishedExhibitionGamesBySeason(season);
        log.info("[게임센터] {}시즌 종료된 시범경기 {}건 발견", season, games.size());

        int crawled = 0;
        int skipped = 0;

        for (Schedule game : games) {
            String kboGameId = game.getKboGameId();
            int scheduleId = game.getId();

            if (!forceRecrawl && isAlreadyCrawled(scheduleId)) {
                skipped++;
                continue;
            }

            try {
                crawlGameCenter(kboGameId, season);
                crawled++;
                Thread.sleep(300);
            } catch (Exception e) {
                log.error("[게임센터] 시범경기 크롤링 실패: {} - {}", kboGameId, e.getMessage());
            }
        }

        log.info("[게임센터] {}시즌 시범경기 완료: {}건 크롤링, {}건 스킵", season, crawled, skipped);
    }

    /**
     * 당일 + 전날 경기만 크롤링 (일일 자동 크롤링용)
     */
    public void crawlRecentGameCenters(int season) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        String startDate = yesterday.toString();
        String endDate = today.toString();

        log.info("[게임센터] 최근 경기 크롤링: {} ~ {}", startDate, endDate);

        List<Schedule> games = scheduleRepository.findFinishedGamesByDateRange(startDate, endDate);
        log.info("[게임센터] {} ~ {} 종료된 경기 {}건 발견", startDate, endDate, games.size());

        int crawled = 0;
        int skipped = 0;

        for (Schedule game : games) {
            String kboGameId = game.getKboGameId();
            int scheduleId = game.getId();

            if (isAlreadyCrawled(scheduleId)) {
                skipped++;
                continue;
            }

            try {
                crawlGameCenter(kboGameId, season);
                crawled++;
                Thread.sleep(300);
            } catch (Exception e) {
                log.error("[게임센터] 크롤링 실패: {} - {}", kboGameId, e.getMessage());
            }
        }

        log.info("[게임센터] 최근 경기 완료: {}건 크롤링, {}건 스킵", crawled, skipped);
    }

    /**
     * 다시즌 배치 크롤링
     */
    public void crawlMultiSeasons(int startYear, int endYear) {
        crawlMultiSeasons(startYear, endYear, false);
    }

    /**
     * 다시즌 배치 크롤링 (강제 재크롤링 옵션)
     */
    public void crawlMultiSeasons(int startYear, int endYear, boolean forceRecrawl) {
        for (int year = endYear; year >= startYear; year--) {
            crawlAllGameCenters(year, forceRecrawl);
        }
    }

    /**
     * 키플레이어 데이터가 누락된 경기만 재크롤링 (키플레이어만)
     */
    public void recrawlMissingKeyPlayers(int startYear, int endYear) {
        log.info("[게임센터] 키플레이어 누락 재크롤링: {}~{}", startYear, endYear);
        int total = 0;
        int success = 0;

        for (int year = endYear; year >= startYear; year--) {
            List<Schedule> games = scheduleRepository.findFinishedGamesBySeason(year);
            for (Schedule game : games) {
                int scheduleId = game.getId();
                if (keyPlayerRepository.existsByScheduleId(scheduleId)) continue;

                String kboGameId = game.getKboGameId();
                String srId = resolveSrId(game);
                total++;

                try {
                    log.info("[키플레이어 보강] {} (scheduleId={})", kboGameId, scheduleId);

                    crawlKeyPlayers(kboGameId, year, scheduleId, srId, "PITCHER",
                            KboConstants.GAME_CENTER_KEY_PITCHER_URL, PITCHER_METRICS);
                    crawlKeyPlayers(kboGameId, year, scheduleId, srId, "HITTER",
                            KboConstants.GAME_CENTER_KEY_HITTER_URL, HITTER_METRICS);
                    success++;
                    Thread.sleep(300);
                } catch (Exception e) {
                    log.error("[키플레이어 보강] 실패: {} - {}", kboGameId, e.getMessage());
                }
            }
        }

        log.info("[게임센터] 키플레이어 보강 완료: {}건 시도, {}건 성공", total, success);
    }

    /**
     * 라인업 누락 경기만 재크롤링 (박스스코어만 호출 → 라인업 저장)
     * 스코어보드/키플레이어는 호출하지 않아 API 부하 최소화
     */
    public void recrawlMissingLineups(int startYear, int endYear) {
        log.info("[게임센터] 라인업 누락 재크롤링: {}~{}", startYear, endYear);
        int total = 0;
        int success = 0;
        int skipped = 0;

        for (int year = endYear; year >= startYear; year--) {
            List<Schedule> games = scheduleRepository.findFinishedGamesBySeason(year);
            log.info("[라인업 보강] {}시즌 종료 경기 {}건 확인", year, games.size());

            for (Schedule game : games) {
                int scheduleId = game.getId();

                // 이미 라인업이 존재하면 스킵
                if (batterLineupRepository.existsByScheduleId(scheduleId)) {
                    skipped++;
                    continue;
                }

                String kboGameId = game.getKboGameId();
                String srId = resolveSrId(game);
                total++;

                try {
                    log.debug("[라인업 보강] {} (scheduleId={})", kboGameId, scheduleId);

                    // 박스스코어만 호출 (타자 파싱 시 라인업 자동 저장)
                    crawlBoxScore(kboGameId, year, scheduleId, srId);
                    success++;
                    Thread.sleep(300);
                } catch (Exception e) {
                    log.error("[라인업 보강] 실패: {} - {}", kboGameId, e.getMessage());
                }
            }
        }

        log.info("[게임센터] 라인업 보강 완료: {}건 시도, {}건 성공, {}건 스킵(기존 존재)", total, success, skipped);
    }

    // ==================== 스코어보드 ====================

    /**
     * 스코어보드 크롤링 (GetScoreBoardScroll)
     */
    private void crawlScoreBoard(String kboGameId, int season, int scheduleId, String srId) {
        try {
            String body = "leId=1&srId=" + srId + "&seasonId=" + season + "&gameId=" + kboGameId;
            String response = postApi(KboConstants.GAME_CENTER_SCOREBOARD_URL, body);
            if (response == null) return;

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            // 메타데이터 추출
            String crowd = getJsonStr(json, "CROWD_CN");
            String startTime = getJsonStr(json, "START_TM");
            String endTime = getJsonStr(json, "END_TM");
            String gameTime = getJsonStr(json, "USE_TM");

            // table2: 이닝별 점수 (JSON 문자열 → double-parse)
            // rows[0] = 원정팀, rows[1] = 홈팀
            String awayInnings = null;
            String homeInnings = null;
            String table2Str = getJsonStr(json, "table2");
            if (table2Str != null) {
                JsonObject table2 = JsonParser.parseString(table2Str).getAsJsonObject();
                JsonArray rows = table2.getAsJsonArray("rows");
                if (rows != null && rows.size() >= 2) {
                    awayInnings = extractInningsFromRow(rows.get(0).getAsJsonObject());
                    homeInnings = extractInningsFromRow(rows.get(1).getAsJsonObject());
                }
            }

            // table3: R/H/E/B (JSON 문자열 → double-parse)
            // rows[0] = 원정팀, rows[1] = 홈팀
            Integer awayR = null, awayH = null, awayE = null, awayB = null;
            Integer homeR = null, homeH = null, homeE = null, homeB = null;
            String table3Str = getJsonStr(json, "table3");
            if (table3Str != null) {
                JsonObject table3 = JsonParser.parseString(table3Str).getAsJsonObject();
                JsonArray rows = table3.getAsJsonArray("rows");
                if (rows != null && rows.size() >= 2) {
                    // 원정팀 R/H/E/B
                    JsonArray awayRow = rows.get(0).getAsJsonObject().getAsJsonArray("row");
                    if (awayRow != null && awayRow.size() >= 4) {
                        awayR = parseIntSafe(getCellText(awayRow, 0));
                        awayH = parseIntSafe(getCellText(awayRow, 1));
                        awayE = parseIntSafe(getCellText(awayRow, 2));
                        awayB = parseIntSafe(getCellText(awayRow, 3));
                    }
                    // 홈팀 R/H/E/B
                    JsonArray homeRow = rows.get(1).getAsJsonObject().getAsJsonArray("row");
                    if (homeRow != null && homeRow.size() >= 4) {
                        homeR = parseIntSafe(getCellText(homeRow, 0));
                        homeH = parseIntSafe(getCellText(homeRow, 1));
                        homeE = parseIntSafe(getCellText(homeRow, 2));
                        homeB = parseIntSafe(getCellText(homeRow, 3));
                    }
                }
            }

            // 유효한 데이터가 없으면 빈 스코어보드 저장 방지
            if (crowd == null && awayR == null && homeR == null && awayInnings == null && homeInnings == null) {
                log.warn("[게임센터] 스코어보드 데이터 없음 (빈 응답): {}", kboGameId);
                return;
            }

            ScoreBoard scoreBoard = ScoreBoard.builder()
                    .scheduleId(scheduleId)
                    .awayInningScores(awayInnings)
                    .homeInningScores(homeInnings)
                    .awayR(awayR).awayH(awayH).awayE(awayE).awayB(awayB)
                    .homeR(homeR).homeH(homeH).homeE(homeE).homeB(homeB)
                    .homeScore(homeR).awayScore(awayR)
                    .crowd(crowd)
                    .startTime(startTime)
                    .endTime(endTime)
                    .gameTime(gameTime)
                    .build();

            scoreBoardService.saveOrUpdate(scoreBoard);
            log.debug("[게임센터] 스코어보드 저장 완료: {}", kboGameId);
        } catch (Exception e) {
            log.error("[게임센터] 스코어보드 파싱 오류: {} - {}", kboGameId, e.getMessage());
        }
    }

    /**
     * 이닝별 점수를 공백 구분 문자열로 추출
     * row 구조: { "row": [ {Text: "0"}, {Text: "2"}, ... ] }
     */
    private String extractInningsFromRow(JsonObject rowObj) {
        JsonArray row = rowObj.getAsJsonArray("row");
        if (row == null) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < row.size(); i++) {
            String text = getCellText(row, i);
            if (text == null || text.isBlank() || "-".equals(text.trim())) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(text.trim());
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    // ==================== 박스스코어 ====================

    /**
     * 박스스코어 크롤링 (GetBoxScoreScroll)
     */
    private void crawlBoxScore(String kboGameId, int season, int scheduleId, String srId) {
        try {
            String body = "leId=1&srId=" + srId + "&seasonId=" + season + "&gameId=" + kboGameId;
            String response = postApi(KboConstants.GAME_CENTER_BOXSCORE_URL, body);
            if (response == null) return;

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();

            // kboGameId에서 원정/홈팀 ID 추출
            int awayTeamId = extractAwayTeamId(kboGameId);
            int homeTeamId = extractHomeTeamId(kboGameId);

            // 상세기록에서 선수별 홈런/도루 맵 추출 (타자 기록 보강용)
            Map<String, Integer> hrMap = new HashMap<>();
            Map<String, Integer> sbMap = new HashMap<>();
            extractHrSbFromSummary(json, hrMap, sbMap);

            // 타자 기록 파싱 (arrHitter: [원정팀, 홈팀]) — HR/SB 맵으로 보강
            parseHitterRecords(json, scheduleId, awayTeamId, homeTeamId, hrMap, sbMap);

            // 투수 기록 파싱 (arrPitcher: [원정팀, 홈팀])
            parsePitcherRecords(json, scheduleId, awayTeamId, homeTeamId);

            // 상세기록 파싱 (tableEtc)
            parseSummaryRecords(json, scheduleId);

            log.debug("[게임센터] 박스스코어 저장 완료: {}", kboGameId);
        } catch (Exception e) {
            log.error("[게임센터] 박스스코어 파싱 오류: {} - {}", kboGameId, e.getMessage());
        }
    }

    /**
     * 타자 기록 파싱
     * arrHitter: JSON 배열 [원정팀, 홈팀]
     * 각 원소: { table1: "JSON문자열(선수정보)", table2: "JSON문자열(이닝별결과)", table3: "JSON문자열(스탯)" }
     *
     * table1 row: [타순, 포지션, 선수명]
     * table3 row: [타수, 안타, 타점, 득점, 타율]
     *
     * @param hrMap 상세기록에서 추출한 선수별 홈런 수
     * @param sbMap 상세기록에서 추출한 선수별 도루 수
     */
    private void parseHitterRecords(JsonObject json, int scheduleId, int awayTeamId, int homeTeamId,
                                    Map<String, Integer> hrMap, Map<String, Integer> sbMap) {
        JsonArray arrHitter = json.getAsJsonArray("arrHitter");
        if (arrHitter == null || arrHitter.size() < 2) return;

        parseTeamHitterTable(arrHitter.get(0).getAsJsonObject(), scheduleId, awayTeamId, hrMap, sbMap);
        parseTeamHitterTable(arrHitter.get(1).getAsJsonObject(), scheduleId, homeTeamId, hrMap, sbMap);
    }

    /**
     * 한 팀의 타자 기록 파싱
     *
     * @param hrMap 상세기록에서 추출한 선수별 홈런 수
     * @param sbMap 상세기록에서 추출한 선수별 도루 수
     */
    private void parseTeamHitterTable(JsonObject hitterData, int scheduleId, int teamId,
                                      Map<String, Integer> hrMap, Map<String, Integer> sbMap) {
        // table1 (선수 정보): JSON 문자열 → parse
        String table1Str = getJsonStr(hitterData, "table1");
        if (table1Str == null) return;
        JsonObject table1 = JsonParser.parseString(table1Str).getAsJsonObject();
        JsonArray playerRows = table1.getAsJsonArray("rows");
        if (playerRows == null) return;

        // table3 (스탯): JSON 문자열 → parse
        String table3Str = getJsonStr(hitterData, "table3");
        if (table3Str == null) return;
        JsonObject table3 = JsonParser.parseString(table3Str).getAsJsonObject();
        JsonArray statsRows = table3.getAsJsonArray("rows");
        if (statsRows == null) return;

        // table1과 table3의 행 수가 동일 (1:1 매핑)
        int rowCount = Math.min(playerRows.size(), statsRows.size());

        for (int i = 0; i < rowCount; i++) {
            try {
                JsonArray playerRow = playerRows.get(i).getAsJsonObject().getAsJsonArray("row");
                JsonArray statsRow = statsRows.get(i).getAsJsonObject().getAsJsonArray("row");
                if (playerRow == null || statsRow == null) continue;

                // 선수명 (table1 row의 인덱스 2)
                String playerName = getCellText(playerRow, 2);
                if (playerName == null || playerName.isBlank()) continue;
                playerName = playerName.replaceAll("<[^>]*>", "").trim();
                if (playerName.contains("합계") || playerName.isEmpty()) continue;

                // 타순 1~9번 선수만 라인업 저장 (대타/대주자 제외)
                String orderText = getCellText(playerRow, 0);
                if (orderText != null) {
                    orderText = orderText.replaceAll("<[^>]*>", "").trim();
                    Integer battingOrder = parseIntSafe(orderText);
                    if (battingOrder != null && battingOrder >= 1 && battingOrder <= 9) {
                        String position = getCellText(playerRow, 1);
                        if (position != null) {
                            position = position.replaceAll("<[^>]*>", "").trim();
                        }
                        lineupService.saveBatterLineup(scheduleId, teamId, battingOrder, position, playerName);
                    }
                }

                // table3 열: [타수(0), 안타(1), 타점(2), 득점(3), 타율(4)]
                int ab = parseIntOrZero(getCellText(statsRow, 0));
                int hits = parseIntOrZero(getCellText(statsRow, 1));
                int rbi = parseIntOrZero(getCellText(statsRow, 2));
                int runs = parseIntOrZero(getCellText(statsRow, 3));

                // PA ≈ AB (이 API에서는 PA 별도 제공 없음)
                int pa = ab;

                // 상세기록(홈런/도루)에서 추출한 데이터로 보강
                int hr = hrMap.getOrDefault(playerName, 0);
                int sb = sbMap.getOrDefault(playerName, 0);

                recordService.saveBatterRecord(scheduleId, teamId, pa, ab, hits, hr, rbi, 0, 0, sb, runs, playerName);
            } catch (Exception e) {
                log.debug("[게임센터] 타자 행 파싱 오류: row {} - {}", i, e.getMessage());
            }
        }
    }

    /**
     * 투수 기록 파싱
     * arrPitcher: JSON 배열 [원정팀, 홈팀]
     * 각 원소: { table: "JSON문자열" }
     *
     * headers: [선수명, 등판, 결과, 승, 패, 세, 이닝, 타자, 투구수, 타수, 피안타, 홈런, 4사구, 삼진, 실점, 자책, 평균자책점]
     * row:     [선수명, 등판, 결과, 승, 패, 세, 이닝, 타자, 투구수, 타수, 피안타, 홈런, 4사구, 삼진, 실점, 자책]
     */
    private void parsePitcherRecords(JsonObject json, int scheduleId, int awayTeamId, int homeTeamId) {
        JsonArray arrPitcher = json.getAsJsonArray("arrPitcher");
        if (arrPitcher == null || arrPitcher.size() < 2) return;

        parseTeamPitcherTable(arrPitcher.get(0).getAsJsonObject(), scheduleId, awayTeamId);
        parseTeamPitcherTable(arrPitcher.get(1).getAsJsonObject(), scheduleId, homeTeamId);
    }

    /**
     * 한 팀의 투수 기록 파싱
     */
    private void parseTeamPitcherTable(JsonObject pitcherData, int scheduleId, int teamId) {
        // table (JSON 문자열) → parse
        String tableStr = getJsonStr(pitcherData, "table");
        if (tableStr == null) return;
        JsonObject table = JsonParser.parseString(tableStr).getAsJsonObject();
        JsonArray rows = table.getAsJsonArray("rows");
        if (rows == null) return;

        for (JsonElement rowElement : rows) {
            try {
                JsonArray row = rowElement.getAsJsonObject().getAsJsonArray("row");
                if (row == null || row.size() < 14) continue;

                // 인덱스: 선수명(0), 등판(1), 결과(2), 승(3), 패(4), 세(5), 이닝(6),
                //         타자(7), 투구수(8), 타수(9), 피안타(10), 홈런(11), 4사구(12), 삼진(13), 실점(14), 자책(15)
                String playerName = getCellText(row, 0);
                if (playerName == null || playerName.isBlank()) continue;
                playerName = playerName.replaceAll("<[^>]*>", "").trim();
                if (playerName.contains("합계") || playerName.contains("TOTAL")) continue;

                String entryType = stripHtml(getCellText(row, 1));
                String result = stripHtml(getCellText(row, 2));
                String decision = convertDecision(result);

                double innings = parseInningsSafe(getCellText(row, 6));
                int battersFaced = parseIntOrZero(getCellText(row, 7));
                int pitchCount = parseIntOrZero(getCellText(row, 8));
                int pHits = parseIntOrZero(getCellText(row, 10));
                int pHr = parseIntOrZero(getCellText(row, 11));
                int pBb = parseIntOrZero(getCellText(row, 12));   // 4사구
                int pSo = parseIntOrZero(getCellText(row, 13));
                int pRuns = parseIntOrZero(getCellText(row, 14));
                int pEr = parseIntOrZero(getCellText(row, 15));

                recordService.savePitcherRecord(scheduleId, teamId, playerName,
                        innings, pSo, pBb, 0, pRuns, pEr, pHits, pHr, decision,
                        entryType, battersFaced, pitchCount);
            } catch (Exception e) {
                log.debug("[게임센터] 투수 행 파싱 오류: {}", e.getMessage());
            }
        }
    }

    /**
     * 상세기록(tableEtc)에서 선수별 홈런/도루 횟수 추출
     *
     * 홈런 형식: "강민호8호(2회 내야 솔로 정성곤), 박승규2호(5회 내야 솔로 정성곤)"
     * 도루 형식: "노시환(3회), 이정후(7회)"
     */
    private void extractHrSbFromSummary(JsonObject json, Map<String, Integer> hrMap, Map<String, Integer> sbMap) {
        String tableEtcStr = getJsonStr(json, "tableEtc");
        if (tableEtcStr == null) return;

        JsonObject tableEtc = JsonParser.parseString(tableEtcStr).getAsJsonObject();
        JsonArray rows = tableEtc.getAsJsonArray("rows");
        if (rows == null) return;

        // 홈런: "이름N호" 패턴에서 이름 추출
        Pattern hrPattern = Pattern.compile("([가-힣]+)\\d+호");
        // 도루: "이름(N회)" 패턴에서 이름 추출
        Pattern sbPattern = Pattern.compile("([가-힣]+)\\(\\d+회");

        for (JsonElement rowElement : rows) {
            try {
                JsonArray row = rowElement.getAsJsonObject().getAsJsonArray("row");
                if (row == null || row.size() < 2) continue;

                String category = getCellText(row, 0);
                String content = getCellText(row, 1);
                if (category == null || content == null) continue;
                category = stripHtml(category).trim();
                content = stripHtml(content);

                if ("홈런".equals(category)) {
                    Matcher m = hrPattern.matcher(content);
                    while (m.find()) {
                        hrMap.merge(m.group(1), 1, Integer::sum);
                    }
                } else if ("도루".equals(category)) {
                    Matcher m = sbPattern.matcher(content);
                    while (m.find()) {
                        sbMap.merge(m.group(1), 1, Integer::sum);
                    }
                }
            } catch (Exception e) {
                log.debug("[게임센터] HR/SB 추출 오류: {}", e.getMessage());
            }
        }
    }

    /**
     * 상세기록 파싱 (tableEtc — JSON 문자열)
     * rows: [ { row: [{Text:"결승타"}, {Text:"내용..."}] }, ... ]
     */
    private void parseSummaryRecords(JsonObject json, int scheduleId) {
        String tableEtcStr = getJsonStr(json, "tableEtc");
        if (tableEtcStr == null) return;

        JsonObject tableEtc = JsonParser.parseString(tableEtcStr).getAsJsonObject();
        JsonArray rows = tableEtc.getAsJsonArray("rows");
        if (rows == null) return;

        for (JsonElement rowElement : rows) {
            try {
                JsonArray row = rowElement.getAsJsonObject().getAsJsonArray("row");
                if (row == null || row.size() < 2) continue;

                String category = getCellText(row, 0);
                String content = getCellText(row, 1);

                if (category == null || category.isBlank()) continue;
                category = stripHtml(category);
                if (content != null) {
                    content = stripHtml(content).replaceAll("\\s+", " ").trim();
                }

                GameSummaryRecord record = GameSummaryRecord.builder()
                        .scheduleId(scheduleId)
                        .category(category)
                        .content(content)
                        .build();

                summaryRecordService.saveOrUpdate(record);
            } catch (Exception e) {
                log.debug("[게임센터] 상세기록 행 파싱 오류: {}", e.getMessage());
            }
        }
    }

    // ==================== 키플레이어 ====================

    /**
     * 키플레이어 크롤링
     */
    private void crawlKeyPlayers(String kboGameId, int season, int scheduleId,
                                 String srId, String playerType, String apiUrl, String[] metrics) {
        for (String metric : metrics) {
            try {
                String body = "leId=1&srId=" + srId + "&gameId=" + kboGameId
                        + "&groupSc=" + metric + "&sort=";
                String response = postApi(apiUrl, body);
                if (response == null) continue;

                JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                JsonArray records = json.getAsJsonArray("record");
                if (records == null || records.isEmpty()) continue;

                for (JsonElement elem : records) {
                    JsonObject rec = elem.getAsJsonObject();

                    int rankNo = parseIntOrZero(getJsonStr(rec, "RANK_NO"));
                    String pNm = getJsonStr(rec, "P_NM");
                    String tId = getJsonStr(rec, "T_ID");
                    String recordIf = getJsonStr(rec, "RECORD_IF");

                    if (rankNo <= 0 || pNm == null) continue;

                    Integer teamId = null;
                    if (tId != null && !tId.isBlank()) {
                        teamId = KboConstants.getTeamIdByKboAbbr(tId);
                    }

                    GameKeyPlayer keyPlayer = GameKeyPlayer.builder()
                            .scheduleId(scheduleId)
                            .kboGameId(kboGameId)
                            .season(season)
                            .playerType(playerType)
                            .metric(metric)
                            .ranking(rankNo)
                            .playerName(pNm)
                            .teamId(teamId)
                            .recordInfo(recordIf)
                            .build();

                    keyPlayerService.saveOrUpdate(keyPlayer);
                }
            } catch (Exception e) {
                log.debug("[게임센터] 키플레이어 파싱 오류: {} {} {} - {}",
                        kboGameId, playerType, metric, e.getMessage());
            }
        }
    }

    // ==================== 유틸리티 메서드 ====================

    /**
     * HTTP POST API 호출
     */
    private String postApi(String url, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", KboConstants.GAME_CENTER_REFERER)
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("[게임센터] API 응답 오류: HTTP {} - URL: {}", response.statusCode(), url);
                return null;
            }

            String responseBody = response.body();
            if (responseBody == null || responseBody.isBlank()) {
                return null;
            }

            return responseBody;
        } catch (IOException | InterruptedException e) {
            log.error("[게임센터] API 호출 실패: {} - {}", url, e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    /**
     * Schedule의 kboSeriesCode를 srId로 변환
     * kboSeriesCode가 있으면 그대로 사용, 없으면 seriesType으로 유추
     */
    private String resolveSrId(Schedule schedule) {
        // kboSeriesCode가 저장되어 있으면 그대로 사용
        if (schedule.getKboSeriesCode() != null && !schedule.getKboSeriesCode().isBlank()) {
            return schedule.getKboSeriesCode();
        }
        // 정규시즌/시범경기는 seriesType과 동일
        String seriesType = schedule.getSeriesType();
        if ("0".equals(seriesType) || "1".equals(seriesType)) {
            return seriesType;
        }
        // 포스트시즌(seriesType="9")은 원본 코드를 알 수 없으므로 API 시도
        return findCorrectSrIdByTrial(schedule);
    }

    /**
     * 포스트시즌 경기의 올바른 srId를 API 시도로 찾기
     */
    private String findCorrectSrIdByTrial(Schedule schedule) {
        String kboGameId = schedule.getKboGameId();
        int season = schedule.getMatchDate().toLocalDateTime().getYear();
        for (String candidateSrId : new String[]{"7", "5", "4", "3", "6"}) {
            String body = "leId=1&srId=" + candidateSrId + "&seasonId=" + season + "&gameId=" + kboGameId;
            String response = postApi(KboConstants.GAME_CENTER_BOXSCORE_URL, body);
            if (response != null && !response.contains("\"code\"")) {
                log.info("[게임센터] {} → srId={} 확인", kboGameId, candidateSrId);
                // 찾은 코드를 DB에 저장
                schedule.setKboSeriesCode(candidateSrId);
                scheduleRepository.save(schedule);
                return candidateSrId;
            }
        }
        log.warn("[게임센터] {} 올바른 srId를 찾지 못함, 기본값 0 사용", kboGameId);
        return "0";
    }

    /**
     * kboGameId에서 원정팀 ID 추출
     * 예: 20250624SKOB0 → 8~9번째 문자 "SK"
     */
    private int extractAwayTeamId(String kboGameId) {
        if (kboGameId == null || kboGameId.length() < 12) return 0;
        String awayAbbr = kboGameId.substring(8, 10);
        Integer id = KboConstants.getTeamIdByKboAbbr(awayAbbr);
        return id != null ? id : 0;
    }

    /**
     * kboGameId에서 홈팀 ID 추출
     * 예: 20250624SKOB0 → 10~11번째 문자 "OB"
     */
    private int extractHomeTeamId(String kboGameId) {
        if (kboGameId == null || kboGameId.length() < 12) return 0;
        String homeAbbr = kboGameId.substring(10, 12);
        Integer id = KboConstants.getTeamIdByKboAbbr(homeAbbr);
        return id != null ? id : 0;
    }

    /**
     * 이미 크롤링된 경기인지 확인
     */
    private boolean isAlreadyCrawled(int scheduleId) {
        return summaryRecordService.existsByScheduleId(scheduleId);
    }

    /**
     * 결과 텍스트를 decision 코드로 변환
     */
    private String convertDecision(String result) {
        if (result == null) return null;
        result = result.trim();
        return switch (result) {
            case "승" -> "W";
            case "패" -> "L";
            case "세" -> "SV";
            case "홀", "홀드" -> "HLD";
            default -> null;
        };
    }

    /**
     * JSON 배열에서 특정 인덱스의 셀 Text 값 추출
     */
    private String getCellText(JsonArray row, int index) {
        if (row == null || index >= row.size()) return null;
        JsonObject cell = row.get(index).getAsJsonObject();
        JsonElement text = cell.get("Text");
        if (text == null || text.isJsonNull()) return null;
        return text.getAsString();
    }

    private String getJsonStr(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return null;
        return el.getAsString();
    }

    private String stripHtml(String text) {
        if (text == null) return null;
        return text.replaceAll("<[^>]*>", "").replaceAll("&nbsp;", " ").trim();
    }

    private Integer parseIntSafe(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return Integer.parseInt(text.replaceAll("[^\\d-]", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int parseIntOrZero(String text) {
        Integer val = parseIntSafe(text);
        return val != null ? val : 0;
    }

    /**
     * 이닝 파싱 (예: "6" → 6.0, "1/3" → 0.333, "5 1/3" → 5.333, "1 2/3" → 1.666)
     */
    private double parseInningsSafe(String text) {
        if (text == null || text.isBlank()) return 0.0;
        text = text.trim();
        try {
            if (text.contains("/")) {
                // "5 1/3" → whole=5, fraction="1/3"
                // "2/3" → whole=0, fraction="2/3"
                double whole = 0.0;
                String fractionPart = text;
                if (text.contains(" ")) {
                    String[] spaceParts = text.split(" ");
                    whole = Double.parseDouble(spaceParts[0]);
                    fractionPart = spaceParts[1];
                }
                String[] fracParts = fractionPart.split("/");
                return whole + Double.parseDouble(fracParts[0]) / Double.parseDouble(fracParts[1]);
            }
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
