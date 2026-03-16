package com.kepg.glvpen.crawler;

import static com.kepg.glvpen.crawler.kbo.util.KboConstants.*;
import static com.kepg.glvpen.crawler.kbo.util.PlaywrightFactory.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

/**
 * PostBack 수정 최종 검증 테스트 v7
 *
 * selectAndWaitForPostBack에 300ms 안정화 대기 추가 후,
 * 추가 대기 없이도 시즌→팀 연속 PostBack이 동작하는지 확인.
 *
 * 추가로 페이지네이션을 포함한 전체 선수 수를 확인하여
 * 실제 크롤링 시나리오를 시뮬레이션.
 */
public class PostBackFixTest {

    static final String URL = PITCHER_PAGE_URLS[0]; // Basic1.aspx

    public static void main(String[] args) {
        Playwright pw = null;
        Browser browser = null;
        Page page = null;

        try {
            pw = createPlaywright();
            browser = createBrowser(pw);
            page = createPage(browser);

            System.out.println("=== PostBack 수정 최종 검증 테스트 v7 ===\n");

            // 초기 설정
            navigateAndWait(page, URL);
            List<String[]> teams = getDropdownOptions(page, SEL_TEAM_DROPDOWN);
            teams.removeIf(o -> o[0] == null || o[0].isEmpty() || "전체".equals(o[1]));
            String seasonSel = findSeasonDropdownSelector(page);

            System.out.println("팀 수: " + teams.size());
            System.out.println("시즌 셀렉터: " + seasonSel + "\n");

            // ===== [1] 2025(기본) + 각 팀별 선수 수 (3개 팀 샘플) =====
            System.out.println("========== [1] 2025 + 팀별 선수 수 ==========");
            int total2025 = 0;
            for (int i = 0; i < Math.min(3, teams.size()); i++) {
                String tVal = teams.get(i)[0];
                String tLbl = teams.get(i)[1];
                navigateAndWait(page, URL);
                selectAndWaitForPostBack(page, SEL_TEAM_DROPDOWN, tVal);
                int[] r = countWithPagination(page, tLbl);
                System.out.printf("  %s: %d명 (전원 매칭: %s), %d페이지%n",
                        tLbl, r[0], r[1] == r[0] ? "✅" : "❌ " + r[1] + "/" + r[0], r[2]);
                total2025 += r[0];
            }
            System.out.println("  합계 (3팀): " + total2025 + "명\n");

            // ===== [2] 2024 + 팀별 (추가 대기 없이, selectAndWaitForPostBack 내 300ms만) =====
            System.out.println("========== [2] 2024 + 팀별 (300ms 내장 대기만) ==========");
            int total2024 = 0;
            for (int i = 0; i < Math.min(3, teams.size()); i++) {
                String tVal = teams.get(i)[0];
                String tLbl = teams.get(i)[1];
                // 크롤러 실제 패턴: 팀마다 네비게이션 → 시즌 → 팀 (추가 대기 없음)
                navigateAndWait(page, URL);
                selectAndWaitForPostBack(page, seasonSel, "2024");
                selectAndWaitForPostBack(page, SEL_TEAM_DROPDOWN, tVal);
                int[] r = countWithPagination(page, tLbl);
                System.out.printf("  %s: %d명 (전원 매칭: %s), %d페이지%n",
                        tLbl, r[0], r[1] == r[0] ? "✅" : "❌ " + r[1] + "/" + r[0], r[2]);
                total2024 += r[0];
            }
            System.out.println("  합계 (3팀): " + total2024 + "명\n");

            // ===== [3] 2024 + ensureTeamSelected 패턴 (시리즈 PostBack 포함) =====
            System.out.println("========== [3] 2024 + ensureTeamSelected 패턴 ==========");
            String tVal = teams.get(0)[0];
            String tLbl = teams.get(0)[1];
            // 크롤러 실제 패턴: 네비 → 시즌 → 팀 → 시리즈 → ensureTeamSelected
            navigateAndWait(page, URL);
            selectAndWaitForPostBack(page, seasonSel, "2024");
            selectAndWaitForPostBack(page, SEL_TEAM_DROPDOWN, tVal);
            List<String[]> seriesOpts = getDropdownOptions(page, SEL_SERIES_DROPDOWN_RECORD);
            System.out.println("  시리즈 수: " + seriesOpts.size());
            // 정규시즌(0) 선택
            selectAndWaitForPostBack(page, SEL_SERIES_DROPDOWN_RECORD, "0");
            // ensureTeamSelected 패턴: 재네비게이션 → 시즌 → 팀 → 시리즈
            navigateAndWait(page, URL);
            selectAndWaitForPostBack(page, seasonSel, "2024");
            selectAndWaitForPostBack(page, SEL_TEAM_DROPDOWN, tVal);
            selectAndWaitForPostBack(page, SEL_SERIES_DROPDOWN_RECORD, "0");
            int[] r3 = countWithPagination(page, tLbl);
            System.out.printf("  %s 정규시즌: %d명 (전원 매칭: %s), %d페이지%n",
                    tLbl, r3[0], r3[1] == r3[0] ? "✅" : "❌ " + r3[1] + "/" + r3[0], r3[2]);

            // ===== [4] 2022 + 팀별 (더 오래된 시즌) =====
            System.out.println("\n========== [4] 2022 + " + tLbl + " ==========");
            navigateAndWait(page, URL);
            selectAndWaitForPostBack(page, seasonSel, "2022");
            selectAndWaitForPostBack(page, SEL_TEAM_DROPDOWN, tVal);
            int[] r4 = countWithPagination(page, tLbl);
            System.out.printf("  %s: %d명 (전원 매칭: %s), %d페이지%n",
                    tLbl, r4[0], r4[1] == r4[0] ? "✅" : "❌ " + r4[1] + "/" + r4[0], r4[2]);

            // ===== [5] 2020 + 팀별 (최소 지원 시즌) =====
            System.out.println("\n========== [5] 2020 + " + tLbl + " ==========");
            navigateAndWait(page, URL);
            selectAndWaitForPostBack(page, seasonSel, "2020");
            selectAndWaitForPostBack(page, SEL_TEAM_DROPDOWN, tVal);
            int[] r5 = countWithPagination(page, tLbl);
            System.out.printf("  %s: %d명 (전원 매칭: %s), %d페이지%n",
                    tLbl, r5[0], r5[1] == r5[0] ? "✅" : "❌ " + r5[1] + "/" + r5[0], r5[2]);

            // ==================== 결과 요약 ====================
            System.out.println("\n==================== 결과 요약 ====================");
            System.out.println("[1] 2025 3팀 합계:     " + total2025 + "명");
            System.out.println("[2] 2024 3팀 합계:     " + total2024 + "명");
            System.out.println("[3] 2024 ensureTeam:   " + r3[0] + "명");
            System.out.println("[4] 2022 " + tLbl + ":        " + r4[0] + "명");
            System.out.println("[5] 2020 " + tLbl + ":        " + r5[0] + "명");

            boolean allGood = total2024 > 60 && r3[1] == r3[0] && r4[1] == r4[0] && r5[1] == r5[0];
            if (allGood) {
                System.out.println("\n✅ 모든 테스트 통과! 300ms 안정화 대기로 문제 해결 확인.");
                System.out.println("   리크롤 준비 완료.");
            } else {
                System.out.println("\n❌ 일부 테스트 실패. 추가 대기 시간 조정 필요.");
            }

        } catch (Exception e) {
            System.err.println("테스트 실패: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cleanup(page, browser, pw);
        }
    }

    /**
     * 페이지네이션 포함하여 전체 선수 수 / 팀 매칭 수 / 페이지 수 반환
     * @return [총선수수, 팀매칭수, 페이지수]
     */
    private static int[] countWithPagination(Page page, String teamLabel) {
        Set<String> allPlayers = new HashSet<>();
        int matchCount = 0;
        int pageNum = 1;

        while (true) {
            Document doc = getJsoupDocument(page);
            Elements rows = doc.select("table.tData01 tbody tr");
            if (rows.isEmpty()) rows = doc.select("table.tData tbody tr");
            if (rows.isEmpty()) rows = doc.select("table tbody tr");

            boolean foundData = false;
            for (Element row : rows) {
                Elements cells = row.select("td");
                if (cells.size() < 3 || !row.select("th").isEmpty()) continue;
                String first = cells.get(0).text().trim();
                if (first.equals("순위") || first.isEmpty()) continue;
                try {
                    Integer.parseInt(first);
                    String playerName = cells.get(1).text().trim();
                    String teamName = cells.get(2).text().trim();
                    if (!playerName.isEmpty() && !teamName.isEmpty()) {
                        String key = playerName + "|" + teamName;
                        if (allPlayers.add(key)) {
                            if (teamLabel.equals(teamName)) matchCount++;
                        }
                        foundData = true;
                    }
                } catch (NumberFormatException e) { /* skip */ }
            }
            if (!foundData) break;

            int nextPage = pageNum + 1;
            try {
                String sel = String.format("a:text-is('%d')", nextPage);
                if (page.locator(".paging " + sel).count() > 0) {
                    page.locator(".paging " + sel).click();
                    page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
                    pageNum = nextPage;
                } else {
                    break;
                }
            } catch (Exception e) { break; }
        }
        return new int[]{allPlayers.size(), matchCount, pageNum};
    }
}
