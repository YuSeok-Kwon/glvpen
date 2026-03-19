package com.kepg.glvpen.crawler.kbo.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;

import lombok.extern.slf4j.Slf4j;

/**
 * KBO 크롤링용 Playwright 브라우저/페이지 팩토리
 * headless Chromium + 불필요 리소스 차단으로 속도 최적화
 */
@Slf4j
public final class PlaywrightFactory {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private static final int DEFAULT_TIMEOUT_MS = 30_000;

    private PlaywrightFactory() {
        throw new AssertionError("유틸리티 클래스는 인스턴스화할 수 없습니다");
    }

    public static Playwright createPlaywright() {
        return Playwright.create();
    }

    public static Browser createBrowser(Playwright pw) {
        return pw.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(Arrays.asList(
                        "--no-sandbox",
                        "--disable-dev-shm-usage",
                        "--disable-gpu"
                )));
    }

    public static Page createPage(Browser browser) {
        Page page = browser.newPage();
        page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
        page.setExtraHTTPHeaders(java.util.Map.of("User-Agent", USER_AGENT));

        // 이미지, 폰트 차단으로 속도 향상 (CSS는 ASP.NET 렌더링에 필요할 수 있어 유지)
        page.route("**/*.{png,jpg,jpeg,gif,svg,ico,woff,woff2,ttf,eot}", Route::abort);

        return page;
    }

    /**
     * 페이지 이동 후 NETWORKIDLE 대기 (ERR_ABORTED 시 재시도)
     * PostBack 진행 중 navigation 충돌 방지를 위해 최대 3회 재시도
     */
    public static void navigateAndWait(Page page, String url) {
        for (int retry = 0; retry < 3; retry++) {
            try {
                if (retry > 0) {
                    // 이전 네비게이션 실패 후 안정화 대기
                    page.waitForTimeout(1_000);
                }
                page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
                return;
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("ERR_ABORTED") && retry < 2) {
                    log.warn("navigateAndWait 재시도 {}/3: {}", retry + 1, url);
                    continue;
                }
                throw e;
            }
        }
    }

    /**
     * ASP.NET 드롭다운 선택 후 PostBack 완료 대기
     *
     * KBO 사이트는 AJAX UpdatePanel (Sys.WebForms.PageRequestManager) 사용:
     * - onchange="setTimeout('__doPostBack(...)', 0)" → 비동기 XHR PostBack
     * - 전체 페이지 네비게이션이 아닌 부분 DOM 업데이트
     *
     * 해결: PageRequestManager.endRequest 콜백으로 AJAX 완료를 정확히 감지
     *
     * @param page Playwright Page
     * @param selectSelector CSS 셀렉터 (예: "select[name*='ddlSeason']")
     * @param value 선택할 값
     */
    public static boolean selectAndWaitForPostBack(Page page, String selectSelector, String value) {
        log.debug("ASP.NET 드롭다운 선택: {} → {}", selectSelector, value);

        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                if (attempt > 0) {
                    log.debug("드롭다운 선택 재시도 {}/3: {} → {}", attempt + 1, selectSelector, value);
                    page.waitForTimeout(1_000);
                }

                // DOM에서 select 요소 확인
                Locator locator = page.locator(selectSelector).first();
                locator.waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.ATTACHED)
                        .setTimeout(5_000));

                // 현재 선택된 값이 이미 원하는 값이면 불필요한 PostBack 스킵
                String currentValue = (String) locator.evaluate("el => el.value");
                if (value.equals(currentValue)) {
                    log.debug("이미 선택된 값이므로 PostBack 스킵: {}", value);
                    return true;
                }

                // 1단계: AJAX PostBack 완료 감지용 콜백 등록
                // PageRequestManager.endRequest: AJAX 응답 수신 + DOM 업데이트 완료 시 발생
                Boolean isAjax = (Boolean) page.evaluate("() => {" +
                        "window.__pbDone = false;" +
                        "if (typeof Sys !== 'undefined' && Sys.WebForms && Sys.WebForms.PageRequestManager) {" +
                        "  var prm = Sys.WebForms.PageRequestManager.getInstance();" +
                        "  var handler = function() { window.__pbDone = true; prm.remove_endRequest(handler); };" +
                        "  prm.add_endRequest(handler);" +
                        "  return true;" +
                        "}" +
                        "return false;" +
                        "}");

                // 2단계: 드롭다운 값 변경 + onchange 트리거 (PostBack 실행)
                page.evaluate("(args) => {" +
                        "var sel = document.querySelector(args[0]);" +
                        "if (!sel) return;" +
                        "sel.value = args[1];" +
                        "var oc = sel.getAttribute('onchange');" +
                        "if (oc) eval(oc);" +
                        "}", new Object[]{selectSelector, value});

                // 3단계: PostBack 완료 대기
                if (Boolean.TRUE.equals(isAjax)) {
                    // AJAX UpdatePanel: endRequest 콜백 대기
                    log.debug("AJAX PostBack 대기 (PageRequestManager.endRequest)");
                    page.waitForFunction("() => window.__pbDone === true",
                            new Page.WaitForFunctionOptions().setTimeout(15_000));
                } else {
                    // 일반 PostBack: 네비게이션 대기
                    log.debug("전체 PostBack 대기");
                    page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
                }

                // 4단계: DOM 안정화 대기
                page.waitForTimeout(500);

                log.debug("PostBack 완료");
                return true;
            } catch (Exception e) {
                if (attempt < 2) continue;
                log.warn("드롭다운 선택 실패 (3회 시도): {} → {} ({})", selectSelector, value, e.getMessage());
                return false;
            }
        }
        return false;
    }

    /**
     * 페이지에서 동적으로 시즌 드롭다운 셀렉터를 찾음
     * ASP.NET에서 렌더링된 ID는 컨테이너에 따라 달라질 수 있으므로,
     * name 속성에 'ddlSeason'이 포함된 select를 탐색
     *
     * @return 찾은 select의 CSS 셀렉터, 없으면 null
     */
    public static String findSeasonDropdownSelector(Page page) {
        // 여러 가능한 패턴 시도
        String[] patterns = {
                "select[name*='ddlSeason']",
                "select[id*='ddlSeason']",
                "select[name*='ddlYear']",
                "select[id*='ddlYear']"
        };

        for (String pattern : patterns) {
            if (page.locator(pattern).count() > 0) {
                log.debug("시즌 드롭다운 발견: {}", pattern);
                return pattern;
            }
        }

        log.warn("시즌 드롭다운을 찾을 수 없습니다. 페이지의 모든 select 태그를 덤프합니다.");
        dumpSelectElements(page);
        return null;
    }

    /**
     * 페이지의 모든 select 태그 정보를 로그에 출력 (디버깅용)
     */
    public static void dumpSelectElements(Page page) {
        Object result = page.evaluate("""
                () => {
                    const selects = document.querySelectorAll('select');
                    return Array.from(selects).map(s => ({
                        id: s.id,
                        name: s.name,
                        className: s.className,
                        optionCount: s.options.length,
                        firstOptions: Array.from(s.options).slice(0, 3).map(o => o.value + ':' + o.text)
                    }));
                }
                """);
        log.info("페이지 내 select 태그 목록: {}", result);
    }

    /**
     * 페이지의 테이블 구조를 로그에 출력 (디버깅용)
     */
    public static void dumpTableStructure(Page page) {
        Object result = page.evaluate("""
                () => {
                    const tables = document.querySelectorAll('table');
                    return Array.from(tables).map(t => ({
                        id: t.id,
                        className: t.className,
                        rowCount: t.rows ? t.rows.length : 0,
                        firstRowCells: t.rows && t.rows[0] ?
                            Array.from(t.rows[0].cells).map(c => c.textContent.trim().substring(0, 20)) : []
                    }));
                }
                """);
        log.info("페이지 내 table 태그 목록: {}", result);
    }

    /**
     * 드롭다운의 모든 option을 동적으로 읽어옴
     * @return [value, text] 쌍의 리스트 (첫 번째 "전체" 옵션 포함)
     */
    @SuppressWarnings("unchecked")
    public static List<String[]> getDropdownOptions(Page page, String selectSelector) {
        List<String[]> result = new ArrayList<>();

        try {
            // locator.count() 대신 evaluate로 직접 확인 (navigation 중 안전)
            Object options = page.evaluate(
                    "(sel) => { const s = document.querySelector(sel); " +
                            "if (!s) return []; " +
                            "return Array.from(s.options).map(o => [o.value, o.text.trim()]); }",
                    selectSelector);

            if (options instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof List<?> pair && pair.size() >= 2) {
                        result.add(new String[]{pair.get(0).toString(), pair.get(1).toString()});
                    }
                }
            }
        } catch (Exception e) {
            log.debug("드롭다운 옵션 읽기 실패: {} ({})", selectSelector, e.getMessage());
        }

        return result;
    }

    /**
     * page.content()를 Jsoup Document로 변환 (네비게이션 중 재시도)
     */
    public static Document getJsoupDocument(Page page) {
        for (int retry = 0; retry < 3; retry++) {
            try {
                return Jsoup.parse(page.content());
            } catch (Exception e) {
                log.debug("page.content() 재시도 {}/3: {}", retry + 1, e.getMessage());
                if (retry < 2) {
                    page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
                } else {
                    throw e;
                }
            }
        }
        throw new RuntimeException("getJsoupDocument 도달 불가");
    }

    /**
     * Playwright 리소스 정리
     */
    public static void cleanup(Page page, Browser browser, Playwright playwright) {
        try {
            if (page != null) page.close();
        } catch (Exception e) {
            log.warn("Page 닫기 실패", e);
        }
        try {
            if (browser != null) browser.close();
        } catch (Exception e) {
            log.warn("Browser 닫기 실패", e);
        }
        try {
            if (playwright != null) playwright.close();
        } catch (Exception e) {
            log.warn("Playwright 닫기 실패", e);
        }
    }
}
