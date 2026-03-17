package com.kepg.glvpen.crawler;

import java.sql.*;
import java.util.*;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Basic2.aspx 독립 실행형 크롤러
 *
 * 전략: HTTP 크롤링과 DB 저장을 완전히 분리
 *   Phase 1: 모든 시즌/팀 데이터를 메모리에 수집 (MySQL 연결 없음)
 *   Phase 2: MySQL 연결 후 일괄 저장
 *
 * 수집 카테고리: AVG, BB, IBB, HBP, SO, GDP, SLG, OBP, OPS, MH, RISP, PH-BA
 *
 * 사용법: ./gradlew crawlBasic2 -Dcrawl.seasonStart=2020 -Dcrawl.seasonEnd=2025
 */
public class Basic2StandaloneCrawler {

    static final String BASIC2_URL = "https://www.koreabaseball.com/Record/Player/HitterBasic/Basic2.aspx";
    static final String[] CATS = {"AVG", "BB", "IBB", "HBP", "SO", "GDP", "SLG", "OBP", "OPS", "MH", "RISP", "PH-BA"};

    // KBO 팀 코드 → 팀ID (DB)
    static final Map<String, Integer> KBO_TEAM_MAP = new HashMap<>();
    static {
        KBO_TEAM_MAP.put("KIA", 1); KBO_TEAM_MAP.put("두산", 2); KBO_TEAM_MAP.put("삼성", 3);
        KBO_TEAM_MAP.put("SSG", 4); KBO_TEAM_MAP.put("LG", 5);  KBO_TEAM_MAP.put("한화", 6);
        KBO_TEAM_MAP.put("NC", 7);  KBO_TEAM_MAP.put("KT", 8);  KBO_TEAM_MAP.put("롯데", 9);
        KBO_TEAM_MAP.put("키움", 10); KBO_TEAM_MAP.put("SK", 4);
    }

    // SSL 인증서 검증 비활성화
    static {
        resetSSL();
    }

    static void resetSSL() {
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            }}, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((h, s) -> true);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // 수집된 선수 데이터 (메모리 저장용)
    static class PlayerRow {
        String playerName;
        String teamName;
        int ranking;
        int season;
        Map<String, Double> stats = new LinkedHashMap<>(); // category → value

        @Override
        public String toString() {
            return playerName + " (" + teamName + ") " + stats;
        }
    }

    public static void main(String[] args) throws Exception {
        int seasonStart = Integer.parseInt(System.getProperty("crawl.seasonStart", "2020"));
        int seasonEnd = Integer.parseInt(System.getProperty("crawl.seasonEnd", "2025"));

        System.out.println("=== Basic2 독립 크롤러 (Phase 분리 방식) ===");
        System.out.println("시즌 범위: " + seasonStart + " → " + seasonEnd);

        // ===== Phase 1: HTTP 크롤링 (DB 연결 없음) =====
        System.out.println("\n===== Phase 1: HTTP 크롤링 시작 =====");

        List<PlayerRow> allRows = new ArrayList<>();

        for (int season = seasonEnd; season >= seasonStart; season--) {
            List<PlayerRow> seasonRows = crawlSeason(season);
            allRows.addAll(seasonRows);
            System.out.println("[Phase 1] " + season + "시즌: " + seasonRows.size() + "명 수집");
        }

        System.out.println("\n[Phase 1 완료] 총 " + allRows.size() + "명 수집");

        if (allRows.isEmpty()) {
            System.out.println("수집된 데이터 없음. 종료.");
            return;
        }

        // ===== Phase 2: DB 저장 =====
        System.out.println("\n===== Phase 2: DB 저장 시작 =====");

        // MySQL 연결 (useSSL=false로 SSL 간섭 방지)
        String dbUrl = "jdbc:mysql://localhost:3306/glvpen?useSSL=false&allowPublicKeyRetrieval=true";
        String dbUser = "root";
        String dbPass = "rnjs7944";

        try (java.sql.Connection dbConn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
            dbConn.setAutoCommit(false);

            int savedCount = saveAllRows(dbConn, allRows);
            dbConn.commit();

            System.out.println("[Phase 2 완료] " + savedCount + "건 저장");
        }

        System.out.println("\n=== Basic2 크롤링 완료 ===");
    }

    // ==================== Phase 1: HTTP 크롤링 ====================

    static List<PlayerRow> crawlSeason(int season) {
        System.out.println("\n[크롤링] " + season + "시즌 시작...");
        List<PlayerRow> rows = new ArrayList<>();

        try {
            // 초기 페이지 로드
            Connection.Response initResp = Jsoup.connect(BASIC2_URL)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .timeout(30000)
                    .execute();
            Document initDoc = initResp.parse();
            Map<String, String> cookies = new HashMap<>(initResp.cookies());

            String seasonName = getFieldName(initDoc, "ddlSeason");
            String seriesName = getFieldName(initDoc, "ddlSeries");
            if (seasonName == null) {
                System.out.println("  시즌 드롭다운 못 찾음!");
                return rows;
            }

            // 워밍업: 무변경 PostBack (ASP.NET 세션 초기화)
            Element currentSeason = initDoc.selectFirst("select[name*='ddlSeason'] option[selected]");
            String currentSeasonVal = currentSeason != null ? currentSeason.val() : "2026";
            Map<String, String> warmupData = buildPostData(initDoc, seasonName, currentSeasonVal);
            Connection.Response warmupResp = doPost(BASIC2_URL, warmupData, cookies);
            cookies.putAll(warmupResp.cookies());
            Document warmupDoc = warmupResp.parse();
            System.out.println("  워밍업: 타이틀=" + warmupDoc.title().substring(0, Math.min(20, warmupDoc.title().length())));

            // 시리즈→0 (정규시즌) 변경 (초기값이 1=시범경기일 수 있음)
            if (seriesName != null) {
                Map<String, String> seriesData = buildPostData(warmupDoc, seriesName, "0");
                Connection.Response seriesResp = doPost(BASIC2_URL, seriesData, cookies);
                cookies.putAll(seriesResp.cookies());
                warmupDoc = seriesResp.parse();
                System.out.println("  시리즈→정규시즌: 타이틀=" + warmupDoc.title().substring(0, Math.min(20, warmupDoc.title().length())));
            }

            // 시즌 변경 PostBack
            Map<String, String> postData = buildPostData(warmupDoc, seasonName, String.valueOf(season));
            Connection.Response seasonResp = doPost(BASIC2_URL, postData, cookies);
            cookies.putAll(seasonResp.cookies());
            Document seasonDoc = seasonResp.parse();
            System.out.println("  시즌→" + season + ": 타이틀=" + seasonDoc.title().substring(0, Math.min(20, seasonDoc.title().length()))
                    + " 행=" + seasonDoc.select("table tbody tr").size());

            if (seasonDoc.title().contains("에러") || seasonDoc.title().isEmpty()) {
                System.out.println("  시즌 PostBack 실패. 2단계 방식 시도...");
                Thread.sleep(1000);
                // 새 세션 + 2단계: 먼저 2025로, 그 다음 목표 시즌으로
                initResp = Jsoup.connect(BASIC2_URL)
                        .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                        .timeout(30000).execute();
                initDoc = initResp.parse();
                cookies = new HashMap<>(initResp.cookies());

                // 워밍업
                warmupData = buildPostData(initDoc, seasonName, currentSeasonVal);
                warmupResp = doPost(BASIC2_URL, warmupData, cookies);
                cookies.putAll(warmupResp.cookies());
                warmupDoc = warmupResp.parse();

                // 시리즈→0
                if (seriesName != null) {
                    Map<String, String> sd = buildPostData(warmupDoc, seriesName, "0");
                    Connection.Response sr = doPost(BASIC2_URL, sd, cookies);
                    cookies.putAll(sr.cookies());
                    warmupDoc = sr.parse();
                }

                // 2025로 먼저
                postData = buildPostData(warmupDoc, seasonName, "2025");
                seasonResp = doPost(BASIC2_URL, postData, cookies);
                cookies.putAll(seasonResp.cookies());
                seasonDoc = seasonResp.parse();

                if (season != 2025) {
                    // 목표 시즌으로
                    postData = buildPostData(seasonDoc, seasonName, String.valueOf(season));
                    seasonResp = doPost(BASIC2_URL, postData, cookies);
                    cookies.putAll(seasonResp.cookies());
                    seasonDoc = seasonResp.parse();
                }

                System.out.println("  2단계 결과: 타이틀=" + seasonDoc.title().substring(0, Math.min(20, seasonDoc.title().length()))
                        + " 행=" + seasonDoc.select("table tbody tr").size());

                if (seasonDoc.title().contains("에러") || seasonDoc.title().isEmpty()) {
                    System.out.println("  " + season + "시즌 크롤링 불가. 건너뜀.");
                    return rows;
                }
            }

            // 팀 목록 추출
            String teamFieldName = getFieldName(seasonDoc, "ddlTeam");
            List<String[]> teams = getSelectOptions(seasonDoc, teamFieldName);
            teams.removeIf(t -> t[0].isEmpty() || "전체".equals(t[1]));
            System.out.println("  팀 " + teams.size() + "개 감지");

            // 팀별 크롤링
            for (int ti = 0; ti < teams.size(); ti++) {
                String teamValue = teams.get(ti)[0];
                String teamLabel = teams.get(ti)[1];

                try {
                    // 매 팀마다 새 세션 시작 (PostBack 상태 꼬임 방지)
                    initResp = Jsoup.connect(BASIC2_URL)
                            .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                            .timeout(30000).execute();
                    initDoc = initResp.parse();
                    cookies = new HashMap<>(initResp.cookies());

                    // 워밍업 PostBack
                    Element curSeason = initDoc.selectFirst("select[name*='ddlSeason'] option[selected]");
                    String curSeasonVal = curSeason != null ? curSeason.val() : "2026";
                    Map<String, String> wu = buildPostData(initDoc, seasonName, curSeasonVal);
                    Connection.Response wuResp = doPost(BASIC2_URL, wu, cookies);
                    cookies.putAll(wuResp.cookies());
                    Document wuDoc = wuResp.parse();

                    // 시리즈→0 (정규시즌)
                    if (seriesName != null) {
                        Map<String, String> sd = buildPostData(wuDoc, seriesName, "0");
                        Connection.Response sr = doPost(BASIC2_URL, sd, cookies);
                        cookies.putAll(sr.cookies());
                        wuDoc = sr.parse();
                    }

                    // 시즌 설정
                    postData = buildPostData(wuDoc, seasonName, String.valueOf(season));
                    seasonResp = doPost(BASIC2_URL, postData, cookies);
                    cookies.putAll(seasonResp.cookies());
                    seasonDoc = seasonResp.parse();

                    if (seasonDoc.title().contains("에러") || seasonDoc.title().isEmpty()) {
                        System.out.println("  " + teamLabel + " 시즌 PostBack 실패");
                        continue;
                    }

                    // 팀 설정
                    postData = buildPostData(seasonDoc, teamFieldName, teamValue);
                    Connection.Response teamResp = doPost(BASIC2_URL, postData, cookies);
                    cookies.putAll(teamResp.cookies());
                    Document teamDoc = teamResp.parse();

                    // 데이터 수집 (현재 페이지)
                    List<PlayerRow> teamRows = parseRows(teamDoc, season);
                    rows.addAll(teamRows);

                    // 다음 페이지 처리
                    int page = 2;
                    while (true) {
                        Element nextLink = findNextPageLink(teamDoc, page);
                        if (nextLink == null) break;
                        String href = nextLink.attr("href");
                        String eventTarget = extractEventTarget(href);
                        if (eventTarget == null) break;

                        postData = buildPostData(teamDoc, eventTarget, "");
                        Connection.Response pageResp = doPost(BASIC2_URL, postData, cookies);
                        cookies.putAll(pageResp.cookies());
                        teamDoc = pageResp.parse();

                        List<PlayerRow> pageRows = parseRows(teamDoc, season);
                        if (pageRows.isEmpty()) break;
                        rows.addAll(pageRows);
                        page++;
                    }

                    System.out.println("  " + (ti + 1) + "/" + teams.size() + " " + teamLabel + " - " + teamRows.size() + "명");
                    Thread.sleep(300);
                } catch (Exception e) {
                    System.out.println("  " + teamLabel + " 실패: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("  " + season + "시즌 크롤링 실패: " + e.getMessage());
            e.printStackTrace();
        }

        return rows;
    }

    /**
     * HTML 테이블 행 → PlayerRow 리스트 변환
     */
    static List<PlayerRow> parseRows(Document doc, int season) {
        List<PlayerRow> rows = new ArrayList<>();
        Elements trs = doc.select("table tbody tr");

        for (Element tr : trs) {
            Elements cells = tr.select("td");
            if (cells.size() < 10) continue;

            String firstCell = cells.get(0).text().trim();
            if (firstCell.isEmpty() || "순위".equals(firstCell)) continue;

            String playerName = cells.get(1).text().trim();
            String teamName = cells.get(2).text().trim();
            if (playerName.isEmpty() || teamName.isEmpty()) continue;

            Integer teamId = KBO_TEAM_MAP.get(teamName);
            if (teamId == null) continue;

            PlayerRow row = new PlayerRow();
            row.playerName = playerName;
            row.teamName = teamName;
            row.ranking = parseIntSafe(firstCell);
            row.season = season;

            for (int i = 0; i < CATS.length; i++) {
                int cellIdx = 3 + i;
                if (cellIdx >= cells.size()) break;
                row.stats.put(CATS[i], parseDoubleSafe(cells.get(cellIdx).text().trim()));
            }
            rows.add(row);
        }
        return rows;
    }

    // ==================== Phase 2: DB 저장 ====================

    static int saveAllRows(java.sql.Connection dbConn, List<PlayerRow> rows) throws Exception {
        String upsertSql = """
            INSERT INTO player_batter_stats (playerId, season, category, value, ranking, series, situationType, situationValue)
            VALUES (?, ?, ?, ?, ?, '0', '', '')
            ON DUPLICATE KEY UPDATE value = VALUES(value), ranking = VALUES(ranking)
            """;

        int totalSaved = 0;
        // 시즌별 그룹핑
        Map<Integer, List<PlayerRow>> bySeason = new LinkedHashMap<>();
        for (PlayerRow row : rows) {
            bySeason.computeIfAbsent(row.season, k -> new ArrayList<>()).add(row);
        }

        try (PreparedStatement ps = dbConn.prepareStatement(upsertSql)) {
            for (Map.Entry<Integer, List<PlayerRow>> entry : bySeason.entrySet()) {
                int season = entry.getKey();
                List<PlayerRow> seasonRows = entry.getValue();

                for (PlayerRow row : seasonRows) {
                    Integer teamId = KBO_TEAM_MAP.get(row.teamName);
                    if (teamId == null) continue;

                    int playerId = findOrCreatePlayer(dbConn, row.playerName, teamId);

                    int catIdx = 0;
                    for (Map.Entry<String, Double> stat : row.stats.entrySet()) {
                        ps.setInt(1, playerId);
                        ps.setInt(2, season);
                        ps.setString(3, stat.getKey());
                        ps.setDouble(4, stat.getValue());
                        if (catIdx == 0) ps.setObject(5, row.ranking);
                        else ps.setNull(5, Types.INTEGER);
                        ps.addBatch();
                        catIdx++;
                    }
                    totalSaved++;
                }
                ps.executeBatch();
                System.out.println("  [DB] " + season + "시즌: " + seasonRows.size() + "명 저장");
            }
        }
        return totalSaved;
    }

    static int findOrCreatePlayer(java.sql.Connection dbConn, String name, int teamId) throws Exception {
        // 이름+팀ID로 조회
        try (PreparedStatement ps = dbConn.prepareStatement(
                "SELECT id FROM player WHERE name = ? AND teamId = ? LIMIT 1")) {
            ps.setString(1, name);
            ps.setInt(2, teamId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        }

        // 이름만으로 조회
        try (PreparedStatement ps = dbConn.prepareStatement(
                "SELECT id FROM player WHERE name = ? LIMIT 1")) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        }

        // 새 선수 생성
        try (PreparedStatement ps = dbConn.prepareStatement(
                "INSERT INTO player (name, teamId) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setInt(2, teamId);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            return rs.getInt(1);
        }
    }

    // ==================== HTTP 헬퍼 (검증된 방식) ====================

    /**
     * ASP.NET PostBack 데이터 빌드 (Basic2DiagnosticTest에서 검증된 방식)
     */
    static Map<String, String> buildPostData(Document doc, String eventTarget, String value) {
        Map<String, String> data = new HashMap<>();
        // 모든 hidden 필드 포함 (hfPage, hfOrderByCol, hfOrderBy 필수)
        for (Element hidden : doc.select("input[type=hidden]")) {
            data.put(hidden.attr("name"), hidden.val());
        }
        data.put("__EVENTTARGET", eventTarget);
        data.put("__EVENTARGUMENT", "");
        // 모든 드롭다운 현재값 유지
        for (Element sel : doc.select("select[name]")) {
            Element selected = sel.selectFirst("option[selected]");
            data.put(sel.attr("name"), selected != null ? selected.val() : "");
        }
        // 대상 드롭다운 값 변경
        data.put(eventTarget, value);
        return data;
    }

    /**
     * HTTP POST (Basic2DiagnosticTest에서 검증된 방식)
     */
    static Connection.Response doPost(String url, Map<String, String> data,
                                       Map<String, String> cookies) throws Exception {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", url)
                .header("Origin", "https://www.koreabaseball.com")
                .timeout(15000)
                .cookies(cookies)
                .data(data)
                .method(Connection.Method.POST)
                .ignoreHttpErrors(true)
                .maxBodySize(0)
                .execute();
    }

    // ==================== 공통 헬퍼 ====================

    static String getFieldName(Document doc, String partialName) {
        Element el = doc.selectFirst("select[name*='" + partialName + "']");
        return el != null ? el.attr("name") : null;
    }

    static List<String[]> getSelectOptions(Document doc, String name) {
        List<String[]> options = new ArrayList<>();
        if (name == null) return options;
        Element select = doc.selectFirst("select[name='" + name + "']");
        if (select == null) return options;
        for (Element opt : select.select("option")) {
            options.add(new String[]{opt.val(), opt.text()});
        }
        return options;
    }

    static Element findNextPageLink(Document doc, int pageNum) {
        Elements links = doc.select(".paging a");
        for (Element link : links) {
            if (link.text().trim().equals(String.valueOf(pageNum))) return link;
        }
        return null;
    }

    static String extractEventTarget(String href) {
        if (href == null) return null;
        int start = href.indexOf("'");
        int end = href.indexOf("'", start + 1);
        if (start >= 0 && end > start) return href.substring(start + 1, end);
        return null;
    }

    static double parseDoubleSafe(String text) {
        try {
            text = text.replace(",", "");
            if (text.isEmpty() || "-".equals(text)) return 0.0;
            return Double.parseDouble(text);
        } catch (NumberFormatException e) { return 0.0; }
    }

    static int parseIntSafe(String text) {
        try {
            text = text.replace(",", "");
            if (text.isEmpty() || "-".equals(text)) return 0;
            return (int) Double.parseDouble(text);
        } catch (NumberFormatException e) { return 0; }
    }
}
