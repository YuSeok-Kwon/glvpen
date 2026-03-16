package com.kepg.glvpen.crawler.kbo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kepg.glvpen.crawler.kbo.util.KboConstants;
import com.kepg.glvpen.crawler.util.TeamMappingConstants;
import com.kepg.glvpen.modules.game.schedule.domain.Schedule;
import com.kepg.glvpen.modules.game.schedule.service.ScheduleService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * KBO 공식 사이트 경기 일정 크롤러
 *
 * AJAX API 직접 호출: POST /ws/Schedule.asmx/GetScheduleList
 * 응답: JSON { rows: [ { row: [ {Text, Class, RowSpan, ...}, ... ] }, ... ] }
 *
 * Playwright 불필요 — HTTP POST로 JSON 응답 직접 수신 후 파싱
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KboScheduleCrawler {

    private final ScheduleService scheduleService;

    private static final String SCHEDULE_API_URL =
            "https://www.koreabaseball.com/ws/Schedule.asmx/GetScheduleList";
    private static final String SERIES_REGULAR = "0,9,6";

    // gameId 추출 패턴: gameId=20250322LTLG0
    private static final Pattern GAME_ID_PATTERN = Pattern.compile("gameId=(\\d{8}[A-Z]{4}\\d)");

    // 시간 추출 패턴: <b>14:00</b>
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d{2}):(\\d{2})");

    // 날짜 추출 패턴: 03.22(토)
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{2})\\.(\\d{2})\\(");

    private final HttpClient httpClient = createHttpClient();

    private static HttpClient createHttpClient() {
        try {
            // KBO 사이트 SSL 인증서 호환을 위한 설정
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
            // fallback
            return HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
    }

    /**
     * 기간 범위 크롤링 (월 단위 순회)
     */
    public void crawlGameRange(LocalDate startDate, LocalDate endDate) {
        log.info("=== KBO 일정 크롤링 시작: {} ~ {} ===", startDate, endDate);

        YearMonth startYm = YearMonth.from(startDate);
        YearMonth endYm = YearMonth.from(endDate);

        YearMonth current = startYm;
        while (!current.isAfter(endYm)) {
            crawlMonth(current.getYear(), current.getMonthValue());
            current = current.plusMonths(1);
        }

        log.info("=== KBO 일정 크롤링 완료 ===");
    }

    /**
     * 특정 월 크롤링 — AJAX API 직접 호출
     */
    public void crawlMonth(int year, int month) {
        log.info("[KBO 일정] {}년 {}월 크롤링 시작", year, month);

        try {
            String monthStr = String.format("%02d", month);

            String requestBody = "leId=1&srIdList="
                    + SERIES_REGULAR.replace(",", "%2C")
                    + "&seasonId=" + year
                    + "&gameMonth=" + monthStr
                    + "&teamId=";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SCHEDULE_API_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", "https://www.koreabaseball.com/Schedule/Schedule.aspx")
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("[KBO 일정] API 응답 오류: HTTP {}", response.statusCode());
                return;
            }

            String body = response.body();
            if (body == null || body.isBlank()) {
                log.info("[KBO 일정] {}년 {}월: 빈 응답", year, month);
                return;
            }

            // JSON 파싱
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonArray rows = json.getAsJsonArray("rows");

            if (rows == null || rows.isEmpty()) {
                log.info("[KBO 일정] {}년 {}월: 데이터 없음", year, month);
                return;
            }

            parseJsonRows(rows, year);

            log.info("[KBO 일정] {}년 {}월 크롤링 완료", year, month);

        } catch (IOException | InterruptedException e) {
            log.error("[KBO 일정] {}년 {}월 크롤링 실패", year, month, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * JSON rows 배열 파싱
     *
     * 구조:
     * rows: [
     *   { row: [
     *     {Text:"03.22(토)", Class:"day", RowSpan:"5"},  // 날짜 (첫 번째 경기에만)
     *     {Text:"<b>14:00</b>", Class:"time"},           // 시간
     *     {Text:"<span>롯데</span>...<span>LG</span>", Class:"play"},  // 경기
     *     {Text:"<a href='...gameId=20250322LTLG0...'>리뷰</a>", Class:"relay"},  // gameId
     *     ...
     *     {Text:"잠실"},  // 경기장
     *   ]},
     *   ...
     * ]
     */
    private void parseJsonRows(JsonArray rows, int year) {
        LocalDate currentDate = null;
        int savedCount = 0;
        int skippedCount = 0;

        for (JsonElement rowElement : rows) {
            JsonObject rowObj = rowElement.getAsJsonObject();
            JsonArray cells = rowObj.getAsJsonArray("row");

            if (cells == null || cells.isEmpty()) continue;

            // 셀 데이터 추출
            String dayText = null;
            String timeText = null;
            String playHtml = null;
            String relayHtml = null;
            String stadium = null;

            for (JsonElement cellElement : cells) {
                JsonObject cell = cellElement.getAsJsonObject();
                String text = getJsonString(cell, "Text");
                String cssClass = getJsonString(cell, "Class");

                if ("day".equals(cssClass)) {
                    dayText = text;
                } else if ("time".equals(cssClass)) {
                    timeText = text;
                } else if ("play".equals(cssClass)) {
                    playHtml = text;
                } else if ("relay".equals(cssClass)) {
                    relayHtml = text;
                }
            }

            // 경기장은 보통 뒤에서 2번째 셀 (Class가 없는 셀 중 경기장 이름)
            for (int i = cells.size() - 1; i >= 0; i--) {
                JsonObject cell = cells.get(i).getAsJsonObject();
                String text = getJsonString(cell, "Text");
                String cssClass = getJsonString(cell, "Class");

                if (cssClass == null && text != null && !text.isEmpty() && !"-".equals(text)
                        && !text.contains("<") && !text.contains("T")) {
                    // 방송 정보가 아닌 순수 텍스트 → 경기장
                    if (TeamMappingConstants.isValidStadiumName(text)) {
                        stadium = text;
                        break;
                    }
                }
            }

            // 날짜 갱신
            if (dayText != null) {
                Matcher dateMatcher = DATE_PATTERN.matcher(dayText);
                if (dateMatcher.find()) {
                    int month = Integer.parseInt(dateMatcher.group(1));
                    int day = Integer.parseInt(dateMatcher.group(2));
                    currentDate = LocalDate.of(year, month, day);
                }
            }

            // 이동일, 데이터 없는 행 스킵
            if (currentDate == null || playHtml == null || playHtml.isEmpty()) {
                continue;
            }

            if (playHtml.contains("이동일") || playHtml.contains("데이터가 없습니다")) {
                continue;
            }

            try {
                // gameId 추출 (relay 셀의 URL에서)
                String kboGameId = extractGameId(relayHtml);

                // play 셀 HTML 파싱
                Document playDoc = Jsoup.parseBodyFragment(playHtml);

                // 팀명 추출: 직접 자식 <span>만 (em 내부 제외)
                Elements topSpans = playDoc.body().select(":root > span");
                if (topSpans.size() < 2) {
                    skippedCount++;
                    continue;
                }

                String awayTeamName = topSpans.get(0).text().trim();
                String homeTeamName = topSpans.get(1).text().trim();

                // 점수 추출: em 내부의 span (win/lose 클래스)
                Integer awayScore = null;
                Integer homeScore = null;
                Element emTag = playDoc.selectFirst("em");
                if (emTag != null) {
                    Elements scoreSpans = emTag.select("span");
                    // 구조: <span class="lose">2</span><span>vs</span><span class="win">12</span>
                    for (Element span : scoreSpans) {
                        String spanText = span.text().trim();
                        if ("vs".equals(spanText)) continue;
                        if (span.hasClass("win") || span.hasClass("lose")) {
                            if (awayScore == null) {
                                awayScore = parseIntSafe(spanText);
                            } else {
                                homeScore = parseIntSafe(spanText);
                            }
                        }
                    }
                }

                // 팀 ID 매핑
                Integer awayTeamId = resolveTeamId(awayTeamName);
                Integer homeTeamId = resolveTeamId(homeTeamName);

                if (awayTeamId == null || homeTeamId == null) {
                    log.warn("팀 ID 매핑 실패: 원정={}, 홈={}", awayTeamName, homeTeamName);
                    skippedCount++;
                    continue;
                }

                // 시간 파싱
                Timestamp matchDate;
                if (timeText != null) {
                    Matcher timeMatcher = TIME_PATTERN.matcher(timeText);
                    if (timeMatcher.find()) {
                        int hour = Integer.parseInt(timeMatcher.group(1));
                        int minute = Integer.parseInt(timeMatcher.group(2));
                        matchDate = Timestamp.valueOf(
                                LocalDateTime.of(currentDate, java.time.LocalTime.of(hour, minute)));
                    } else {
                        matchDate = Timestamp.valueOf(currentDate.atStartOfDay());
                    }
                } else {
                    matchDate = Timestamp.valueOf(currentDate.atStartOfDay());
                }

                // kboGameId가 URL에서 추출 안 되면 직접 생성
                if (kboGameId == null) {
                    String awayAbbr = findKboAbbrByTeamId(awayTeamId);
                    String homeAbbr = findKboAbbrByTeamId(homeTeamId);
                    kboGameId = currentDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                            + awayAbbr + homeAbbr + "0";
                }

                // 경기장 풀네임
                String fullStadium = "";
                if (stadium != null) {
                    String mapped = TeamMappingConstants.getStadiumFullName(stadium);
                    fullStadium = mapped != null ? mapped : stadium;
                }

                // 상태 결정
                String status;
                if (awayScore != null && homeScore != null) {
                    status = "종료";
                } else if (playHtml.contains("취소")) {
                    status = "취소";
                } else {
                    status = "예정";
                }

                // Schedule 저장
                Schedule schedule = Schedule.builder()
                        .matchDate(matchDate)
                        .homeTeamId(homeTeamId)
                        .awayTeamId(awayTeamId)
                        .homeTeamScore(homeScore)
                        .awayTeamScore(awayScore)
                        .stadium(fullStadium)
                        .status(status)
                        .kboGameId(kboGameId)
                        .build();

                scheduleService.saveOrUpdateByKboGameId(schedule);
                savedCount++;

                log.debug("저장: {} {} {}({}) vs {}({}) | {} | {}",
                        currentDate, timeText != null ? timeText.replaceAll("<[^>]*>", "") : "",
                        awayTeamName, awayScore, homeTeamName, homeScore,
                        fullStadium, kboGameId);

            } catch (Exception e) {
                log.warn("경기 행 처리 오류: {}", e.getMessage());
                skippedCount++;
            }
        }

        log.info("일정 파싱 완료: {}건 저장, {}건 스킵", savedCount, skippedCount);
    }

    /**
     * relay 셀 HTML에서 gameId 추출
     * 예: gameId=20250322LTLG0 → "20250322LTLG0"
     */
    private String extractGameId(String relayHtml) {
        if (relayHtml == null || relayHtml.isEmpty()) return null;

        Matcher matcher = GAME_ID_PATTERN.matcher(relayHtml);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private Integer resolveTeamId(String teamName) {
        Integer id = TeamMappingConstants.getTeamId(teamName);
        if (id == null) {
            id = KboConstants.getTeamIdByKboName(teamName);
        }
        return id;
    }

    private String findKboAbbrByTeamId(int teamId) {
        return switch (teamId) {
            case 1 -> "HT";
            case 2 -> "OB";
            case 3 -> "SS";
            case 4 -> "SK";
            case 5 -> "LG";
            case 6 -> "HH";
            case 7 -> "NC";
            case 8 -> "KT";
            case 9 -> "LT";
            case 10 -> "WO";
            default -> "XX";
        };
    }

    private Integer parseIntSafe(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getJsonString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return null;
        return el.getAsString();
    }
}
