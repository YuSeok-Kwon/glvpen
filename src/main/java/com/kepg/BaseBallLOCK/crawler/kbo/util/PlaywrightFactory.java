package com.kepg.BaseBallLOCK.crawler.kbo.util;

import java.util.Arrays;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
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
     * 페이지 이동 후 NETWORKIDLE 대기
     */
    public static void navigateAndWait(Page page, String url) {
        page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
    }

    /**
     * ASP.NET 드롭다운 선택 후 PostBack 완료 대기
     *
     * ASP.NET 페이지에서는 select.change → __doPostBack → 페이지 리로드 순서로 동작.
     * selectOption() 호출 시 change 이벤트 발생 → PostBack 트리거 → 페이지 갱신
     *
     * @param page Playwright Page
     * @param selectSelector CSS 셀렉터 (예: "select[name*='ddlSeason']")
     * @param value 선택할 값
     */
    public static boolean selectAndWaitForPostBack(Page page, String selectSelector, String value) {
        log.debug("ASP.NET 드롭다운 선택: {} → {}", selectSelector, value);

        // 옵션 존재 여부 확인
        Boolean optionExists = (Boolean) page.evaluate(
                "(args) => { const sel = document.querySelector(args[0]); " +
                        "return sel && Array.from(sel.options).some(o => o.value === args[1]); }",
                new Object[]{selectSelector, value});

        if (!Boolean.TRUE.equals(optionExists)) {
            log.warn("드롭다운에 '{}' 옵션이 없습니다 (selector={})", value, selectSelector);
            return false;
        }

        // selectOption + PostBack에 의한 페이지 리로드 대기
        page.selectOption(selectSelector, value);
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);

        log.debug("PostBack 완료");
        return true;
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
     * page.content()를 Jsoup Document로 변환
     */
    public static Document getJsoupDocument(Page page) {
        return Jsoup.parse(page.content());
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
