package com.kepg.glvpen.crawler;

import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Basic2.aspx Jsoup PostBack 진단
 */
public class Basic2DiagnosticTest {

    static final String BASIC2_URL = "https://www.koreabaseball.com/Record/Player/HitterBasic/Basic2.aspx";

    static {
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }}, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Basic2.aspx Jsoup 진단 ===\n");

        // 1단계: 초기 페이지 로드
        System.out.println("[1] 초기 페이지 로드...");
        Connection.Response initResp = Jsoup.connect(BASIC2_URL)
                .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                .timeout(30000)
                .execute();

        System.out.println("    Status: " + initResp.statusCode());
        System.out.println("    Cookies: " + initResp.cookies());

        Document doc = initResp.parse();

        // 드롭다운 확인
        for (Element sel : doc.select("select[name]")) {
            String name = sel.attr("name");
            Element selected = sel.selectFirst("option[selected]");
            String val = selected != null ? selected.val() + " (" + selected.text() + ")" : "없음";
            System.out.println("    SELECT: " + name.substring(name.lastIndexOf('$') + 1) + " = " + val);

            // 팀 드롭다운 옵션 출력
            if (name.contains("ddlTeam")) {
                Elements opts = sel.select("option");
                System.out.println("    팀 옵션 (" + opts.size() + "개):");
                for (Element opt : opts) {
                    System.out.println("      value='" + opt.val() + "' text='" + opt.text() + "'");
                }
            }
        }

        // 테이블 확인
        Elements headers = doc.select("table thead th");
        System.out.println("    헤더: " + headers.size() + "개");
        for (Element h : headers) System.out.print("  " + h.text());
        System.out.println();

        Elements rows = doc.select("table tbody tr");
        System.out.println("    데이터 행: " + rows.size() + "개");

        // 모든 hidden input 확인
        System.out.println("\n    === 히든 필드 ===");
        for (Element hidden : doc.select("input[type=hidden]")) {
            String name = hidden.attr("name");
            String val = hidden.val();
            System.out.println("    " + name + " = " + (val.length() > 50 ? val.substring(0, 50) + "...(" + val.length() + ")" : val));
        }

        // 시즌 드롭다운 옵션 출력
        for (Element sel : doc.select("select[name]")) {
            if (sel.attr("name").contains("ddlSeason")) {
                Elements opts = sel.select("option");
                System.out.println("\n    시즌 옵션 (" + opts.size() + "개):");
                for (Element opt : opts) {
                    String mark = opt.hasAttr("selected") ? " [선택됨]" : "";
                    System.out.println("      value='" + opt.val() + "' text='" + opt.text() + "'" + mark);
                }
            }
            if (sel.attr("name").contains("ddlSeries")) {
                Elements opts = sel.select("option");
                System.out.println("    시리즈 옵션 (" + opts.size() + "개):");
                for (Element opt : opts) {
                    String mark = opt.hasAttr("selected") ? " [선택됨]" : "";
                    System.out.println("      value='" + opt.val() + "' text='" + opt.text() + "'" + mark);
                }
            }
        }

        String vs = doc.select("input[name=__VIEWSTATE]").val();
        String ev = doc.select("input[name=__EVENTVALIDATION]").val();
        String vsg = doc.select("input[name=__VIEWSTATEGENERATOR]").val();

        // 2단계: 시즌 PostBack (2024)
        System.out.println("\n[2] 시즌 PostBack (2024)...");
        String seasonName = null;
        for (Element sel : doc.select("select[name]")) {
            if (sel.attr("name").contains("ddlSeason")) {
                seasonName = sel.attr("name");
                break;
            }
        }
        System.out.println("    시즌 필드명: " + seasonName);

        if (seasonName != null && vs != null) {
            Map<String, String> data = new HashMap<>();

            // 모든 hidden 필드 포함
            for (Element hidden : doc.select("input[type=hidden]")) {
                data.put(hidden.attr("name"), hidden.val());
            }
            data.put("__EVENTTARGET", seasonName);
            data.put("__EVENTARGUMENT", "");

            // 모든 드롭다운 현재값 유지
            for (Element sel : doc.select("select[name]")) {
                Element selected = sel.selectFirst("option[selected]");
                data.put(sel.attr("name"), selected != null ? selected.val() : "");
            }
            // 시즌만 변경
            data.put(seasonName, "2024");

            System.out.println("    POST data 키 수: " + data.size());
            for (Map.Entry<String, String> e : data.entrySet()) {
                String v = e.getValue();
                if (v.length() > 30) v = v.substring(0, 30) + "...";
                System.out.println("    " + e.getKey() + " = " + v);
            }

            Connection.Response postResp = Jsoup.connect(BASIC2_URL)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", BASIC2_URL)
                    .header("Origin", "https://www.koreabaseball.com")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
                    .header("Cache-Control", "no-cache")
                    .timeout(30000)
                    .cookies(initResp.cookies())
                    .data(data)
                    .method(Connection.Method.POST)
                    .execute();

            System.out.println("    Status: " + postResp.statusCode());
            System.out.println("    Content-Type: " + postResp.contentType());

            String postBody = postResp.body();
            System.out.println("    응답 길이: " + postBody.length());
            // 에러 메시지 추출
            Document errDoc = Jsoup.parse(postBody);
            Element errMsg = errDoc.selectFirst(".error_txt, .error-message, .err_msg, p, .txt");
            if (errMsg != null) System.out.println("    에러 메시지: " + errMsg.text());
            // title
            System.out.println("    응답 타이틀: " + errDoc.title());
            // 본문 텍스트
            String bodyText = errDoc.body() != null ? errDoc.body().text() : "";
            System.out.println("    응답 본문: " + bodyText.substring(0, Math.min(300, bodyText.length())));

            Document postDoc = postResp.parse();

            // 변경 후 시즌 확인
            for (Element sel : postDoc.select("select[name]")) {
                if (sel.attr("name").contains("ddlSeason")) {
                    Element selected = sel.selectFirst("option[selected]");
                    System.out.println("    변경 후 시즌: " + (selected != null ? selected.text() : "없음"));
                }
                if (sel.attr("name").contains("ddlTeam")) {
                    Elements opts = sel.select("option");
                    System.out.println("    팀 옵션 (" + opts.size() + "개):");
                    for (Element opt : opts) {
                        System.out.println("      value='" + opt.val() + "' text='" + opt.text() + "'");
                    }
                }
            }

            // 데이터 확인
            headers = postDoc.select("table thead th");
            System.out.println("    헤더: " + headers.size() + "개");
            rows = postDoc.select("table tbody tr");
            System.out.println("    데이터 행: " + rows.size() + "개");

            // 첫 3행 출력
            int count = 0;
            for (Element row : rows) {
                Elements cells = row.select("td");
                if (cells.size() < 8) continue;
                String first = cells.get(0).text().trim();
                if (first.isEmpty() || first.equals("순위")) continue;

                System.out.println("    " + cells.get(1).text() + " "
                        + "AVG=" + cells.get(3).text() + " "
                        + "BB=" + cells.get(4).text() + " "
                        + "HBP=" + cells.get(6).text() + " "
                        + "SO=" + cells.get(7).text() + " "
                        + "OPS=" + cells.get(11).text());
                if (++count >= 5) break;
            }

            // 3단계: 팀 선택 PostBack (LG)
            System.out.println("\n[3] 팀 PostBack (LG)...");
            vs = postDoc.select("input[name=__VIEWSTATE]").val();
            ev = postDoc.select("input[name=__EVENTVALIDATION]").val();
            vsg = postDoc.select("input[name=__VIEWSTATEGENERATOR]").val();

            String teamFieldName = null;
            for (Element sel : postDoc.select("select[name]")) {
                if (sel.attr("name").contains("ddlTeam")) {
                    teamFieldName = sel.attr("name");
                    break;
                }
            }

            if (teamFieldName != null && vs != null) {
                data = new HashMap<>();
                data.put("__VIEWSTATE", vs);
                data.put("__VIEWSTATEGENERATOR", vsg);
                data.put("__EVENTVALIDATION", ev);
                data.put("__EVENTTARGET", teamFieldName);
                data.put("__EVENTARGUMENT", "");

                for (Element sel : postDoc.select("select[name]")) {
                    Element selected = sel.selectFirst("option[selected]");
                    data.put(sel.attr("name"), selected != null ? selected.val() : "");
                }
                data.put(teamFieldName, "LG");

                Connection.Response teamResp = Jsoup.connect(BASIC2_URL)
                        .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                        .timeout(30000)
                        .cookies(initResp.cookies())
                        .data(data)
                        .method(Connection.Method.POST)
                        .execute();

                Document teamDoc = teamResp.parse();
                rows = teamDoc.select("table tbody tr");
                System.out.println("    LG 데이터 행: " + rows.size() + "개");

                count = 0;
                for (Element row : rows) {
                    Elements cells = row.select("td");
                    if (cells.size() < 12) continue;
                    String first = cells.get(0).text().trim();
                    if (first.isEmpty() || first.equals("순위")) continue;

                    System.out.println("    " + cells.get(1).text() + " "
                            + "AVG=" + cells.get(3).text() + " "
                            + "BB=" + cells.get(4).text() + " "
                            + "HBP=" + cells.get(6).text() + " "
                            + "SO=" + cells.get(7).text() + " "
                            + "SLG=" + cells.get(9).text() + " "
                            + "OBP=" + cells.get(10).text() + " "
                            + "OPS=" + cells.get(11).text());
                    if (++count >= 5) break;
                }
            }
        }

        // 4단계: form action + Basic1/Basic2 비교
        System.out.println("\n[4] form action 비교...");
        Element form2 = doc.selectFirst("form");
        System.out.println("    Basic2 form action: " + (form2 != null ? form2.attr("action") : "없음"));
        System.out.println("    Basic2 form method: " + (form2 != null ? form2.attr("method") : "없음"));
        System.out.println("    Basic2 form id: " + (form2 != null ? form2.attr("id") : "없음"));

        // Basic1 form action
        try {
            Connection.Response b1r = Jsoup.connect("https://www.koreabaseball.com/Record/Player/HitterBasic/Basic1.aspx")
                    .userAgent("Mozilla/5.0").timeout(15000).execute();
            Document b1d = b1r.parse();
            Element form1 = b1d.selectFirst("form");
            System.out.println("    Basic1 form action: " + (form1 != null ? form1.attr("action") : "없음"));

            // Basic2의 script 태그 확인
            System.out.println("\n    Basic2 script 태그:");
            for (Element script : doc.select("script")) {
                String src = script.attr("src");
                String inline = script.html().trim();
                if (!src.isEmpty()) {
                    System.out.println("      src=" + src);
                } else if (!inline.isEmpty() && inline.length() < 200) {
                    System.out.println("      inline: " + inline);
                }
            }
        } catch (Exception e) {
            System.out.println("    비교 실패: " + e.getMessage());
        }

        // 5단계: Basic2에 form action URL로 POST 시도
        System.out.println("\n[5] form action URL로 POST 시도...");
        if (form2 != null && !form2.attr("action").isEmpty()) {
            String actionUrl = form2.attr("action");
            if (!actionUrl.startsWith("http")) {
                actionUrl = "https://www.koreabaseball.com" + actionUrl;
            }
            System.out.println("    POST 대상: " + actionUrl);

            Map<String, String> data2 = new HashMap<>();
            for (Element hidden : doc.select("input[type=hidden]")) {
                data2.put(hidden.attr("name"), hidden.val());
            }
            data2.put("__EVENTTARGET", seasonName);
            data2.put("__EVENTARGUMENT", "");
            for (Element sel : doc.select("select[name]")) {
                Element selected = sel.selectFirst("option[selected]");
                data2.put(sel.attr("name"), selected != null ? selected.val() : "");
            }
            data2.put(seasonName, "2024");

            try {
                Connection.Response actionResp = Jsoup.connect(actionUrl)
                        .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .header("Referer", BASIC2_URL)
                        .header("Origin", "https://www.koreabaseball.com")
                        .timeout(15000)
                        .cookies(initResp.cookies())
                        .data(data2)
                        .method(Connection.Method.POST)
                        .ignoreHttpErrors(true)
                        .execute();

                Document actionDoc = actionResp.parse();
                System.out.println("    Status: " + actionResp.statusCode() + " 타이틀: " + actionDoc.title());
                Elements aHeaders = actionDoc.select("table thead th");
                Elements aRows = actionDoc.select("table tbody tr");
                System.out.println("    헤더=" + aHeaders.size() + " 행=" + aRows.size());
            } catch (Exception ex) {
                System.out.println("    에러: " + ex.getMessage());
            }
        }

        // 5단계: Basic1.aspx PostBack 테스트
        System.out.println("\n[5] Basic1.aspx PostBack 테스트...");
        String basic1Url = "https://www.koreabaseball.com/Record/Player/HitterBasic/Basic1.aspx";
        Connection.Response b1Init = Jsoup.connect(basic1Url)
                .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(15000)
                .execute();
        Document b1Doc = b1Init.parse();
        String b1Season = null;
        for (Element sel : b1Doc.select("select[name]")) {
            if (sel.attr("name").contains("ddlSeason")) {
                b1Season = sel.attr("name");
                break;
            }
        }

        if (b1Season != null) {
            Map<String, String> b1Data = new HashMap<>();
            for (Element hidden : b1Doc.select("input[type=hidden]")) {
                b1Data.put(hidden.attr("name"), hidden.val());
            }
            b1Data.put("__EVENTTARGET", b1Season);
            b1Data.put("__EVENTARGUMENT", "");
            for (Element sel : b1Doc.select("select[name]")) {
                Element selected = sel.selectFirst("option[selected]");
                b1Data.put(sel.attr("name"), selected != null ? selected.val() : "");
            }
            b1Data.put(b1Season, "2024");

            Connection.Response b1Post = Jsoup.connect(basic1Url)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", basic1Url)
                    .header("Origin", "https://www.koreabaseball.com")
                    .timeout(15000)
                    .cookies(b1Init.cookies())
                    .data(b1Data)
                    .method(Connection.Method.POST)
                    .execute();

            Document b1PostDoc = b1Post.parse();
            Elements b1Headers = b1PostDoc.select("table thead th");
            Elements b1Rows = b1PostDoc.select("table tbody tr");
            System.out.println("    Basic1 POST → 타이틀: " + b1PostDoc.title()
                    + " 헤더=" + b1Headers.size() + " 행=" + b1Rows.size());

            if (b1Rows.size() > 0) {
                Element firstRow = b1Rows.first();
                Elements cells = firstRow.select("td");
                System.out.println("    첫 행: ");
                for (int i = 0; i < Math.min(cells.size(), 10); i++) {
                    System.out.print(cells.get(i).text() + " | ");
                }
                System.out.println();
            }
        }

        // 7단계: Basic2에 동일 값으로 POST (변경 없이)
        System.out.println("\n[7] Basic2 무변경 PostBack...");
        {
            Map<String, String> sameData = new HashMap<>();
            for (Element hidden : doc.select("input[type=hidden]")) {
                sameData.put(hidden.attr("name"), hidden.val());
            }
            // __EVENTTARGET를 시즌으로 설정하되, 값은 이미 선택된 2026 그대로
            sameData.put("__EVENTTARGET", seasonName);
            sameData.put("__EVENTARGUMENT", "");
            for (Element sel : doc.select("select[name]")) {
                Element selected = sel.selectFirst("option[selected]");
                sameData.put(sel.attr("name"), selected != null ? selected.val() : "");
            }
            // 시즌 값 변경하지 않음 (2026 그대로)

            Connection.Response sameResp = Jsoup.connect(BASIC2_URL)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", BASIC2_URL)
                    .header("Origin", "https://www.koreabaseball.com")
                    .timeout(15000)
                    .cookies(initResp.cookies())
                    .data(sameData)
                    .method(Connection.Method.POST)
                    .ignoreHttpErrors(true)
                    .maxBodySize(0)
                    .execute();

            Document sameDoc = sameResp.parse();
            System.out.println("    Status: " + sameResp.statusCode() + " 타이틀: " + sameDoc.title());
            System.out.println("    헤더=" + sameDoc.select("table thead th").size()
                    + " 행=" + sameDoc.select("table tbody tr").size());
        }

        // 8단계: Basic2에 __EVENTTARGET 비워서 POST (일반 submit 시뮬레이션)
        System.out.println("\n[8] Basic2 빈 EVENTTARGET PostBack...");
        {
            Map<String, String> emptyTarget = new HashMap<>();
            for (Element hidden : doc.select("input[type=hidden]")) {
                emptyTarget.put(hidden.attr("name"), hidden.val());
            }
            emptyTarget.put("__EVENTTARGET", "");
            emptyTarget.put("__EVENTARGUMENT", "");
            for (Element sel : doc.select("select[name]")) {
                Element selected = sel.selectFirst("option[selected]");
                emptyTarget.put(sel.attr("name"), selected != null ? selected.val() : "");
            }

            Connection.Response emptyResp = Jsoup.connect(BASIC2_URL)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", BASIC2_URL)
                    .header("Origin", "https://www.koreabaseball.com")
                    .timeout(15000)
                    .cookies(initResp.cookies())
                    .data(emptyTarget)
                    .method(Connection.Method.POST)
                    .ignoreHttpErrors(true)
                    .maxBodySize(0)
                    .execute();

            Document emptyDoc = emptyResp.parse();
            System.out.println("    Status: " + emptyResp.statusCode() + " 타이틀: " + emptyDoc.title());
            System.out.println("    헤더=" + emptyDoc.select("table thead th").size()
                    + " 행=" + emptyDoc.select("table tbody tr").size());
        }

        // 9단계: 다양한 값 변경 테스트
        System.out.println("\n[9] 다양한 값 변경 PostBack...");
        String seriesName = null;
        for (Element sel : doc.select("select[name]")) {
            if (sel.attr("name").contains("ddlSeries")) {
                seriesName = sel.attr("name");
                break;
            }
        }

        // 9a: 시리즈만 변경 (1→0)
        System.out.println("  [9a] 시리즈 1→0 (시즌 2026 유지)...");
        {
            Map<String, String> d = buildPostData(doc, seriesName, "0");
            Connection.Response r = doPost(BASIC2_URL, d, initResp.cookies());
            Document rd = r.parse();
            System.out.println("    Status=" + r.statusCode() + " 타이틀=" + rd.title()
                    + " 헤더=" + rd.select("table thead th").size()
                    + " 행=" + rd.select("table tbody tr").size());
        }

        // 9b: 시즌 2025 (시리즈 1 유지)
        System.out.println("  [9b] 시즌 2026→2025...");
        {
            Map<String, String> d = buildPostData(doc, seasonName, "2025");
            Connection.Response r = doPost(BASIC2_URL, d, initResp.cookies());
            Document rd = r.parse();
            System.out.println("    Status=" + r.statusCode() + " 타이틀=" + rd.title()
                    + " 헤더=" + rd.select("table thead th").size()
                    + " 행=" + rd.select("table tbody tr").size());
        }

        // 9c: 시즌을 한단계만 변경 2026→2025→2024 (2단계)
        System.out.println("  [9c] 2단계: 시즌 2026→2025...");
        {
            Map<String, String> d = buildPostData(doc, seasonName, "2025");
            Connection.Response r = doPost(BASIC2_URL, d, initResp.cookies());
            Document rd = r.parse();
            System.out.println("    1단계: Status=" + r.statusCode() + " 타이틀=" + rd.title()
                    + " 헤더=" + rd.select("table thead th").size()
                    + " 행=" + rd.select("table tbody tr").size());

            if (!rd.title().contains("에러")) {
                System.out.println("    2단계: 시즌 2025→2024...");
                Map<String, String> d2 = buildPostData(rd, seasonName, "2024");
                // 쿠키 업데이트
                Map<String, String> cookies2 = new HashMap<>(initResp.cookies());
                cookies2.putAll(r.cookies());
                Connection.Response r2 = doPost(BASIC2_URL, d2, cookies2);
                Document rd2 = r2.parse();
                System.out.println("    2단계: Status=" + r2.statusCode() + " 타이틀=" + rd2.title()
                        + " 헤더=" + rd2.select("table thead th").size()
                        + " 행=" + rd2.select("table tbody tr").size());
                if (rd2.select("table tbody tr").size() > 0) {
                    Element firstRow = rd2.select("table tbody tr").first();
                    Elements cells = firstRow.select("td");
                    StringBuilder sb = new StringBuilder("    첫 행: ");
                    for (int i = 0; i < Math.min(cells.size(), 15); i++) {
                        sb.append(cells.get(i).text()).append(" | ");
                    }
                    System.out.println(sb.toString());
                }
            }
        }

        // 10단계: 다양한 시즌 직접 변경 테스트
        System.out.println("\n[10] 시즌 직접 변경 테스트 (초기 페이지에서)...");
        int[] testSeasons = {2025, 2024, 2023, 2020};
        for (int ts : testSeasons) {
            Map<String, String> d = buildPostData(doc, seasonName, String.valueOf(ts));
            Connection.Response r = doPost(BASIC2_URL, d, initResp.cookies());
            Document rd = r.parse();
            String title = rd.title();
            boolean isError = title.contains("에러") || title.isEmpty();
            System.out.println("    2026→" + ts + ": " + (isError ? "실패" : "성공")
                    + " 행=" + rd.select("table tbody tr").size());
        }

        // 11단계: 시리즈 먼저 변경 후 시즌 변경
        System.out.println("\n[11] 시리즈→0 먼저 후 시즌→2024...");
        {
            // 1단계: 시리즈 1→0
            Map<String, String> d1 = buildPostData(doc, seriesName, "0");
            Connection.Response r1 = doPost(BASIC2_URL, d1, initResp.cookies());
            Document rd1 = r1.parse();
            Map<String, String> cookies1 = new HashMap<>(initResp.cookies());
            cookies1.putAll(r1.cookies());
            System.out.println("    시리즈→0: 타이틀=" + rd1.title()
                    + " 행=" + rd1.select("table tbody tr").size());

            // 2단계: 시즌 2026→2024
            Map<String, String> d2 = buildPostData(rd1, seasonName, "2024");
            Connection.Response r2 = doPost(BASIC2_URL, d2, cookies1);
            Document rd2 = r2.parse();
            System.out.println("    시즌→2024: 타이틀=" + rd2.title()
                    + " 행=" + rd2.select("table tbody tr").size());
            if (rd2.select("table tbody tr").size() > 0) {
                Element firstRow = rd2.select("table tbody tr").first();
                Elements cells = firstRow.select("td");
                StringBuilder sb = new StringBuilder("    첫 행: ");
                for (int i = 0; i < Math.min(cells.size(), 15); i++) {
                    sb.append(cells.get(i).text()).append(" | ");
                }
                System.out.println(sb.toString());
            }
        }

        System.out.println("\n=== 진단 완료 ===");
    }

    // 헬퍼 메서드
    static Map<String, String> buildPostData(Document doc, String eventTarget, String value) {
        Map<String, String> data = new HashMap<>();
        for (Element hidden : doc.select("input[type=hidden]")) {
            data.put(hidden.attr("name"), hidden.val());
        }
        data.put("__EVENTTARGET", eventTarget);
        data.put("__EVENTARGUMENT", "");
        for (Element sel : doc.select("select[name]")) {
            Element selected = sel.selectFirst("option[selected]");
            data.put(sel.attr("name"), selected != null ? selected.val() : "");
        }
        data.put(eventTarget, value);
        return data;
    }

    static Connection.Response doPost(String url, Map<String, String> data, Map<String, String> cookies) throws Exception {
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
}
