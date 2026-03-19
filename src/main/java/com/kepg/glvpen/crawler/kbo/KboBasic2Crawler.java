package com.kepg.glvpen.crawler.kbo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import com.kepg.glvpen.crawler.util.TeamMappingConstants;
import com.kepg.glvpen.modules.player.domain.Player;
import com.kepg.glvpen.modules.player.dto.PlayerDTO;
import com.kepg.glvpen.modules.player.service.PlayerService;
import com.kepg.glvpen.modules.player.stats.service.BatterStatsService;
import com.kepg.glvpen.modules.player.stats.statsDto.BatterStatsDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.kepg.glvpen.crawler.kbo.util.KboConstants.*;

/**
 * KBO Basic2.aspx 크롤러 (Jsoup HTTP POST 방식)
 *
 * Basic2.aspx는 JavaScript 리다이렉트로 Playwright 사용 불가.
 * Jsoup으로 직접 HTTP POST를 보내 ASP.NET PostBack을 처리한다.
 *
 * 수집 카테고리: AVG, BB, IBB, HBP, SO, GDP, SLG, OBP, OPS, MH, RISP, PH-BA
 * → 세이버메트릭스 핵심 입력값 (BB, SO, HBP) + 파생 지표 (SLG, OBP, OPS) + 클러치 (RISP, PH-BA)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KboBasic2Crawler {

    private final PlayerService playerService;
    private final BatterStatsService batterStatsService;

    private static final String BASIC2_URL = "https://www.koreabaseball.com/Record/Player/HitterBasic/Basic2.aspx";

    // SSL 인증서 검증 비활성화 (KBO 사이트 인증서 호환성 문제)
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
            // 무시
        }
    }

    /**
     * 단일 시즌 Basic2 크롤링
     */
    public void crawlBasic2(int season) {
        log.info("[Basic2] {}시즌 크롤링 시작", season);
        int totalRows = 0;

        try {
            // 1단계: 초기 페이지 로드
            Connection.Response initResp = Jsoup.connect(BASIC2_URL)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .timeout(30000)
                    .execute();
            Document initDoc = initResp.parse();
            Map<String, String> cookies = new HashMap<>(initResp.cookies());

            // 2단계: 시즌 변경 PostBack
            String seasonName = getFieldName(initDoc, "ddlSeason");
            if (seasonName == null) {
                log.error("[Basic2] 시즌 드롭다운을 찾을 수 없습니다");
                return;
            }

            Document seasonDoc = doPostBack(initDoc, cookies, seasonName, String.valueOf(season));
            if (seasonDoc.title().contains("에러")) {
                log.error("[Basic2] 시즌 PostBack 에러. 재시도 중...");
                // 재시도: 새 세션으로 다시 시도
                Thread.sleep(1000);
                initResp = Jsoup.connect(BASIC2_URL)
                        .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                        .timeout(30000)
                        .execute();
                initDoc = initResp.parse();
                cookies = new HashMap<>(initResp.cookies());
                seasonDoc = doPostBack(initDoc, cookies, seasonName, String.valueOf(season));
            }

            // 3단계: 팀 목록 추출
            String teamName = getFieldName(seasonDoc, "ddlTeam");
            List<String[]> teams = getSelectOptions(seasonDoc, teamName);
            teams = filterOutAll(teams);
            log.info("[Basic2] 팀 {}개 감지, 시즌 PostBack 행: {}",
                    teams.size(), seasonDoc.select("table tbody tr").size());

            // 4단계: 팀별 크롤링
            for (int ti = 0; ti < teams.size(); ti++) {
                String teamValue = teams.get(ti)[0];
                String teamLabel = teams.get(ti)[1];

                try {
                    // 매 팀마다 초기 페이지에서 재시작 (PostBack 누적 방지)
                    initResp = Jsoup.connect(BASIC2_URL)
                            .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                            .timeout(30000)
                            .execute();
                    initDoc = initResp.parse();
                    cookies = new HashMap<>(initResp.cookies());

                    // 시즌 설정
                    seasonDoc = doPostBack(initDoc, cookies, seasonName, String.valueOf(season));

                    // 팀 설정
                    Document teamDoc = doPostBack(seasonDoc, cookies, teamName, teamValue);

                    // 데이터 수집 (모든 페이지)
                    int teamRows = collectAllPages(teamDoc, cookies, season, "0");
                    totalRows += teamRows;

                    log.info("[Basic2] {}/{} {} - {}건", ti + 1, teams.size(), teamLabel, teamRows);
                } catch (Exception e) {
                    log.warn("[Basic2] {} 크롤링 실패: {}", teamLabel, e.getMessage());
                }
            }

            log.info("[Basic2] {}시즌 완료: {}건 처리", season, totalRows);
        } catch (Exception e) {
            log.error("[Basic2] {}시즌 크롤링 실패", season, e);
        }
    }

    /**
     * PostBack 전송 (진단 테스트에서 검증된 방식)
     */
    private Document doPostBack(Document doc, Map<String, String> cookies,
                                 String eventTarget, String value) throws Exception {
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

        Connection.Response resp = Jsoup.connect(BASIC2_URL)
                .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", BASIC2_URL)
                .header("Origin", "https://www.koreabaseball.com")
                .timeout(15000)
                .cookies(cookies)
                .data(data)
                .method(Connection.Method.POST)
                .ignoreHttpErrors(true)
                .maxBodySize(0)
                .execute();
        cookies.putAll(resp.cookies());
        return resp.parse();
    }

    /**
     * 다시즌 크롤링
     */
    public void crawlMultiSeasons(int startYear, int endYear) {
        log.info("[Basic2] 다시즌 크롤링: {}→{} (역순)", endYear, startYear);
        for (int year = endYear; year >= startYear; year--) {
            crawlBasic2(year);
        }
    }

    // ==================== HTTP 통신 ====================

    private Connection.Response fetchPage(String url, Map<String, String> cookies,
                                           Map<String, String> data) throws Exception {
        Connection conn = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", BASIC2_URL)
                .header("Origin", "https://www.koreabaseball.com")
                .timeout(30000)
                .ignoreHttpErrors(true)
                .maxBodySize(0);

        if (cookies != null) conn.cookies(cookies);
        if (data != null) {
            conn.data(data);
            conn.method(Connection.Method.POST);
        }
        return conn.execute();
    }

    /**
     * ASP.NET PostBack 시뮬레이션
     * ViewState, EventValidation 등을 유지하면서 드롭다운 변경 POST 전송
     */
    private Document postBack(Document currentDoc, Map<String, String> cookies,
                               String fieldName, String value) throws Exception {
        Map<String, String> data = new HashMap<>();

        // 모든 hidden input 필드 포함 (hfPage, hfOrderByCol, hfOrderBy 등 필수)
        for (Element hidden : currentDoc.select("input[type=hidden]")) {
            data.put(hidden.attr("name"), hidden.val());
        }
        data.put("__EVENTTARGET", fieldName);
        data.put("__EVENTARGUMENT", "");

        // 모든 드롭다운의 현재값 유지
        for (Element select : currentDoc.select("select[name]")) {
            String name = select.attr("name");
            Element selected = select.selectFirst("option[selected]");
            String val = selected != null ? selected.attr("value") : "";
            data.put(name, val);
        }

        // 대상 드롭다운 값 변경
        data.put(fieldName, value);

        // 직접 Jsoup 호출 (fetchPage 우회)
        Connection.Response resp = Jsoup.connect(BASIC2_URL)
                .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", BASIC2_URL)
                .header("Origin", "https://www.koreabaseball.com")
                .timeout(30000)
                .cookies(cookies)
                .data(data)
                .method(Connection.Method.POST)
                .ignoreHttpErrors(true)
                .maxBodySize(0)
                .execute();
        cookies.putAll(resp.cookies());
        return resp.parse();
    }

    // ==================== 데이터 수집 ====================

    private int collectAllPages(Document doc, Map<String, String> cookies,
                                 int season, String seriesCode) {
        int totalRows = 0;
        int pageNum = 1;

        while (true) {
            int rows = collectDataFromPage(doc, season, seriesCode);
            totalRows += rows;

            if (rows == 0 && pageNum > 1) break;

            // 다음 페이지 링크 찾기
            Element nextLink = findNextPageLink(doc, pageNum + 1);
            if (nextLink == null) break;

            try {
                // 페이지네이션 PostBack
                String href = nextLink.attr("href");
                String eventTarget = extractEventTarget(href);
                if (eventTarget == null) break;

                doc = postBack(doc, cookies,
                        "ctl00$ctl00$ctl00$cphContents$cphContents$cphContents$ucPager$btnNo" + (pageNum + 1),
                        "");
                // 실제로는 __EVENTTARGET으로 페이지네이션
                pageNum++;
            } catch (Exception e) {
                log.info("[Basic2] 페이지네이션 종료: {}", e.getMessage());
                break;
            }
        }

        return totalRows;
    }

    private int collectDataFromPage(Document doc, int season, String seriesCode) {
        Elements rows = doc.select("table tbody tr");
        int count = 0;

        for (Element row : rows) {
            try {
                Elements cells = row.select("td");
                if (cells.size() < 10) continue;

                String firstCell = cells.get(0).text().trim();
                if (firstCell.isEmpty() || firstCell.equals("순위")) continue;

                int ranking = parseIntSafe(firstCell);
                String playerName = cells.get(1).text().trim();
                String teamName = cells.get(2).text().trim();
                if (playerName.isEmpty() || teamName.isEmpty()) continue;

                Integer teamId = resolveTeamId(teamName);
                if (teamId == null) continue;

                Player player = playerService.findOrCreatePlayer(
                        PlayerDTO.builder().name(playerName).teamId(teamId).build());
                int playerId = player.getId();

                // Basic2 카테고리: AVG, BB, IBB, HBP, SO, GDP, SLG, OBP, OPS, MH, RISP, PH-BA
                for (int i = 0; i < BATTER_BASIC2_CATS.length; i++) {
                    int cellIdx = 3 + i; // 순위(0), 선수명(1), 팀명(2) 이후
                    if (cellIdx >= cells.size()) break;

                    String cat = BATTER_BASIC2_CATS[i];
                    double value = parseDoubleSafe(cells.get(cellIdx).text().trim());

                    batterStatsService.saveBatterStats(BatterStatsDTO.builder()
                            .playerId(playerId)
                            .season(season)
                            .category(cat)
                            .value(value)
                            .ranking(i == 0 ? ranking : null)
                            .series(seriesCode)
                            .situationType("")
                            .situationValue("")
                            .build());
                }
                count++;
            } catch (Exception e) {
                log.info("[Basic2] 행 처리 스킵: {}", e.getMessage());
            }
        }
        return count;
    }

    // ==================== 헬퍼 ====================

    private String getFieldName(Document doc, String partialName) {
        Element el = doc.selectFirst("select[name*='" + partialName + "']");
        return el != null ? el.attr("name") : null;
    }

    private String getHiddenValue(Document doc, String name) {
        Element el = doc.selectFirst("input[name='" + name + "']");
        return el != null ? el.val() : "";
    }

    private List<String[]> getSelectOptions(Document doc, String name) {
        List<String[]> options = new ArrayList<>();
        if (name == null) return options;
        Element select = doc.selectFirst("select[name='" + name + "']");
        if (select == null) return options;
        for (Element opt : select.select("option")) {
            options.add(new String[]{opt.val(), opt.text()});
        }
        return options;
    }

    private List<String[]> filterOutAll(List<String[]> options) {
        List<String[]> filtered = new ArrayList<>();
        for (String[] opt : options) {
            if (!opt[0].isEmpty() && !"전체".equals(opt[1])) {
                filtered.add(opt);
            }
        }
        return filtered;
    }

    private Element findNextPageLink(Document doc, int pageNum) {
        Elements links = doc.select(".paging a");
        for (Element link : links) {
            if (link.text().trim().equals(String.valueOf(pageNum))) {
                return link;
            }
        }
        return null;
    }

    private String extractEventTarget(String href) {
        if (href == null) return null;
        // javascript:__doPostBack('ctl00$...','')
        int start = href.indexOf("'");
        int end = href.indexOf("'", start + 1);
        if (start >= 0 && end > start) {
            return href.substring(start + 1, end);
        }
        return null;
    }

    private Integer resolveTeamId(String teamName) {
        Integer id = TeamMappingConstants.getTeamId(teamName);
        if (id == null) id = getTeamIdByKboName(teamName);
        return id;
    }

    private double parseDoubleSafe(String text) {
        try {
            text = text.replace(",", "");
            if (text.isEmpty() || "-".equals(text)) return 0.0;
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private int parseIntSafe(String text) {
        try {
            text = text.replace(",", "");
            if (text.isEmpty() || "-".equals(text)) return 0;
            return (int) Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
