package com.kepg.glvpen.crawler.kbo.util;

import static com.kepg.glvpen.crawler.kbo.util.KboConstants.*;
import static com.kepg.glvpen.crawler.kbo.util.PlaywrightFactory.*;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.kepg.glvpen.crawler.util.TeamMappingConstants;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import lombok.extern.slf4j.Slf4j;

/**
 * Playwright 기반 KBO 크롤러 공통 베이스 클래스
 *
 * 하위 클래스에서 공통으로 사용하는 유틸리티 메서드를 제공:
 * - 브라우저 라이프사이클 관리 (withBrowser)
 * - 시즌 드롭다운 선택 + PostBack 검증
 * - 데이터 테이블 행 탐색 (3단계 fallback)
 * - 페이지네이션
 * - 팀 ID 해석, 파싱 유틸
 */
@Slf4j
public abstract class AbstractPlaywrightCrawler {

    // ==================== 브라우저 라이프사이클 ====================

    @FunctionalInterface
    protected interface BrowserAction {
        void execute(Page page, Browser browser) throws Exception;
    }

    /**
     * Playwright 브라우저 생성 → 액션 실행 → 정리를 자동으로 관리
     */
    protected void withBrowser(String label, BrowserAction action) {
        Playwright pw = null;
        Browser browser = null;
        Page page = null;
        try {
            pw = createPlaywright();
            browser = createBrowser(pw);
            page = createPage(browser);
            action.execute(page, browser);
        } catch (Exception e) {
            log.error("[{}] 크롤링 실패", label, e);
        } finally {
            cleanup(page, browser, pw);
        }
    }

    // ==================== 시즌 드롭다운 ====================

    /**
     * 시즌 드롭다운 선택 + PostBack 검증 + 재시도
     * KboPlayerStatsCrawler의 검증 로직을 포함한 가장 robust한 버전
     *
     * @return true: 시즌 변경 성공 또는 이미 해당 시즌, false: 실패
     */
    protected boolean selectSeasonDropdown(Page page, int season) {
        String selector = findSeasonDropdownSelector(page);
        if (selector == null) {
            log.warn("시즌 드롭다운을 찾을 수 없습니다.");
            return true;
        }

        String target = String.valueOf(season);
        String currentValue = (String) page.evaluate(
                "(sel) => document.querySelector(sel)?.value", selector);
        if (target.equals(currentValue)) return true;

        selectAndWaitForPostBack(page, selector, target);

        // PostBack 후 검증 — 드롭다운이 실제로 변경되었는지 확인
        String verify = (String) page.evaluate(
                "(sel) => document.querySelector(sel)?.value", selector);
        if (!target.equals(verify)) {
            log.warn("시즌 PostBack 검증 실패 ({}→{}), 재시도", verify, target);
            selectAndWaitForPostBack(page, selector, target);
            verify = (String) page.evaluate(
                    "(sel) => document.querySelector(sel)?.value", selector);
            if (!target.equals(verify)) {
                log.error("시즌 변경 최종 실패: {} (기대: {})", verify, target);
                return false;
            }
        }
        return true;
    }

    // ==================== 드롭다운 선택 ====================

    /**
     * selectAndWaitForPostBack 래퍼
     */
    protected boolean selectDropdown(Page page, String selector, String value) {
        return selectAndWaitForPostBack(page, selector, value);
    }

    // ==================== 데이터 테이블 행 탐색 ====================

    /**
     * 3단계 fallback으로 데이터 테이블 행 탐색
     * 1. table.tData tbody tr
     * 2. #cphContents_cphContents_cphContents_udpContent table tbody tr
     * 3. table tbody tr (마지막 fallback)
     */
    protected Elements findDataRows(Document doc) {
        Elements rows = doc.select("table.tData tbody tr");
        if (!rows.isEmpty()) return rows;
        rows = doc.select("#cphContents_cphContents_cphContents_udpContent table tbody tr");
        if (!rows.isEmpty()) return rows;
        return doc.select("table tbody tr");
    }

    /**
     * 첫 번째 데이터 테이블의 tbody 행만 반환 (두 번째 테이블 제외)
     */
    protected Elements findFirstTableRows(Document doc) {
        Element table = doc.selectFirst("table.tData");
        if (table != null) return table.select("tbody tr");

        table = doc.selectFirst("#cphContents_cphContents_cphContents_udpContent table");
        if (table != null) return table.select("tbody tr");

        table = doc.selectFirst("table");
        if (table != null) return table.select("tbody tr");

        return new Elements();
    }

    // ==================== 페이지네이션 ====================

    /**
     * 페이지네이션 클릭 + NETWORKIDLE 대기
     * @return 다음 페이지가 존재하여 이동했으면 true
     */
    protected boolean goToNextPage(Page page, int pageNum) {
        try {
            String pageLinkSelector = String.format("a:text-is('%d')", pageNum);
            if (page.locator(".paging " + pageLinkSelector).count() > 0) {
                page.locator(".paging " + pageLinkSelector).click();
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
                return true;
            }
            if (page.locator(pageLinkSelector).count() > 0) {
                page.locator(pageLinkSelector).first().click();
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 팀 ID 해석 ====================

    /**
     * 팀 이름 → 팀 ID 변환 (TeamMappingConstants → KboConstants fallback)
     */
    protected Integer resolveTeamId(String teamName) {
        Integer id = TeamMappingConstants.getTeamId(teamName);
        if (id == null) id = getTeamIdByKboName(teamName);
        return id;
    }

    // ==================== 파싱 유틸 ====================

    protected double parseDouble(Element cell) {
        try {
            String text = cell.text().trim().replace(",", "");
            if (text.isEmpty() || "-".equals(text)) return 0.0;
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    protected int parseInt(Element cell) {
        try {
            String text = cell.text().trim().replace(",", "");
            if (text.isEmpty() || "-".equals(text)) return 0;
            return (int) Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ==================== 드롭다운 필터 ====================

    /**
     * 드롭다운 옵션에서 "전체" 옵션 제거 (개별 팀별 수집을 위해)
     */
    protected List<String[]> filterOutAllOption(List<String[]> options) {
        List<String[]> filtered = new ArrayList<>();
        for (String[] opt : options) {
            String value = opt[0];
            String text = opt[1];
            if (value.isEmpty() || "전체".equals(text)) continue;
            filtered.add(opt);
        }
        return filtered;
    }

    // ==================== 페이지 재생성 ====================

    /**
     * 오류 발생 시 기존 페이지를 닫고 새 페이지 생성
     */
    protected Page recreatePage(Page page, Browser browser) {
        try {
            page.close();
        } catch (Exception ignored) {}
        return createPage(browser);
    }
}
