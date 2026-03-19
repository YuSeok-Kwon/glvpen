package com.kepg.glvpen.crawler.kbo;

import static com.kepg.glvpen.crawler.kbo.util.KboConstants.*;
import static com.kepg.glvpen.crawler.kbo.util.PlaywrightFactory.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import com.kepg.glvpen.crawler.util.TeamMappingConstants;
import com.kepg.glvpen.modules.player.domain.Player;
import com.kepg.glvpen.modules.player.repository.PlayerRepository;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * KBO 공식 사이트 선수 프로필 크롤러
 *
 * 2단계 수집:
 * 1) 선수 기록 페이지(Basic1)에서 선수명 <a> 태그 href의 playerId를 추출 → DB에 kboPlayerId 저장
 * 2) kboPlayerId가 있는 선수에 대해 상세 페이지 접근 → 프로필 파싱 → Player 업데이트
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KboPlayerProfileCrawler {

    private final PlayerRepository playerRepository;

    /**
     * 선수 프로필 전체 수집 (kboPlayerId 추출 + 프로필 파싱)
     */
    public void crawlPlayerProfiles(int season) {
        log.info("=== KBO 선수 프로필 크롤링 시작 ({}시즌) ===", season);

        // 1단계: 타자/투수 기록 페이지에서 kboPlayerId 추출
        int batterIds = extractKboPlayerIds(season, BATTER_STATS_URL, "타자");
        int pitcherIds = extractKboPlayerIds(season, PITCHER_STATS_URL, "투수");
        log.info("[프로필] kboPlayerId 추출 완료: 타자 {}명, 투수 {}명", batterIds, pitcherIds);

        // 2단계: 프로필 미수집 선수에 대해 상세 페이지 접근
        int profileCount = crawlDetailProfiles();
        log.info("=== KBO 선수 프로필 크롤링 완료: {}명 프로필 수집 ===", profileCount);
    }

    /**
     * 선수 기록 페이지에서 kboPlayerId 추출
     * 선수명 <a> 태그 href: "/Record/Player/HitterDetail/Basic.aspx?playerId=12345"
     */
    private int extractKboPlayerIds(int season, String url, String label) {
        log.info("[프로필] {} 기록 페이지에서 kboPlayerId 추출 시작", label);

        Playwright pw = null;
        Browser browser = null;
        Page page = null;
        int totalExtracted = 0;

        try {
            pw = createPlaywright();
            browser = createBrowser(pw);
            page = createPage(browser);
            navigateAndWait(page, url);
            selectSeasonDropdown(page, season);

            // 팀 드롭다운 옵션 읽기
            List<String[]> teamOptions = getDropdownOptions(page, SEL_TEAM_DROPDOWN);
            teamOptions = filterOutAllOption(teamOptions);
            log.info("[프로필] {} 팀 드롭다운: {}팀", label, teamOptions.size());

            for (int teamIdx = 0; teamIdx < teamOptions.size(); teamIdx++) {
                String teamValue = teamOptions.get(teamIdx)[0];
                String teamLabel = teamOptions.get(teamIdx)[1];

                try {
                    // 팀마다 URL 새로 네비게이션
                    navigateAndWait(page, url);
                    selectSeasonDropdown(page, season);

                    if (!selectAndWaitForPostBack(page, SEL_TEAM_DROPDOWN, teamValue)) continue;

                    log.debug("[프로필] {}/{} {} kboPlayerId 추출", teamIdx + 1, teamOptions.size(), teamLabel);

                    Integer teamId = resolveTeamId(teamLabel);
                    if (teamId == null) continue;

                    // 페이지네이션 순회
                    int pageNum = 1;
                    boolean firstPage = true;
                    while (true) {
                        Document doc = getJsoupDocument(page);
                        // tData 또는 tData01 클래스 모두 지원
                        Elements rows = doc.select("table[class*=tData] tbody tr");
                        if (rows.isEmpty()) {
                            rows = doc.select("table[class*=tData] tr");
                        }
                        if (rows.isEmpty()) break;

                        if (firstPage) {
                            firstPage = false;
                            log.info("[프로필] {} 행 수: {}", teamLabel, rows.size());
                        }

                        for (Element row : rows) {
                            try {
                                Elements cells = row.select("td");
                                if (cells.size() < 3) continue;
                                if (row.select("th").size() > 0) continue;

                                String firstCell = cells.get(0).text().trim();
                                if (firstCell.equals("순위") || firstCell.isEmpty()) continue;

                                // 선수명 셀에서 <a> 태그 href 추출
                                Element nameCell = cells.get(1);
                                Element link = nameCell.selectFirst("a[href]");
                                if (link == null) continue;

                                String playerName = nameCell.text().trim();
                                String href = link.attr("href");
                                String kboPlayerId = extractPlayerIdFromHref(href);
                                if (kboPlayerId == null || playerName.isEmpty()) continue;

                                // DB에서 선수 찾기
                                playerRepository.findByNameAndTeamId(playerName, teamId)
                                        .ifPresent(player -> {
                                            if (player.getKboPlayerId() == null || player.getKboPlayerId().isEmpty()) {
                                                player.setKboPlayerId(kboPlayerId);
                                                playerRepository.save(player);
                                            }
                                        });

                                totalExtracted++;
                            } catch (Exception e) {
                                log.debug("kboPlayerId 추출 스킵: {}", e.getMessage());
                            }
                        }

                        if (!goToNextPage(page, ++pageNum)) break;
                    }
                } catch (Exception e) {
                    log.warn("[프로필] {} kboPlayerId 추출 중 오류, 페이지 재생성: {}", teamLabel, e.getMessage());
                    try { page.close(); } catch (Exception ignored) {}
                    page = createPage(browser);
                }
            }

            log.info("[프로필] {} kboPlayerId 추출 완료: {}건", label, totalExtracted);

        } catch (Exception e) {
            log.error("[프로필] {} kboPlayerId 추출 실패", label, e);
        } finally {
            cleanup(page, browser, pw);
        }

        return totalExtracted;
    }

    /**
     * kboPlayerId가 있지만 프로필이 아직 없는 선수에 대해 상세 페이지 접근하여 프로필 수집
     */
    private int crawlDetailProfiles() {
        List<Player> players = playerRepository.findByKboPlayerIdIsNotNullAndProfileUpdatedAtIsNull();
        if (players.isEmpty()) {
            log.info("[프로필] 프로필 미수집 선수 없음");
            return 0;
        }

        log.info("[프로필] 프로필 미수집 선수 {}명 — 상세 페이지 접근", players.size());

        Playwright pw = null;
        Browser browser = null;
        Page page = null;
        int profileCount = 0;

        try {
            pw = createPlaywright();
            browser = createBrowser(pw);
            page = createPage(browser);

            for (int i = 0; i < players.size(); i++) {
                Player player = players.get(i);
                try {
                    // 타자 상세 페이지 먼저 시도, 실패 시 투수 상세 페이지 시도
                    String detailUrl = PLAYER_HITTER_DETAIL_URL + "?playerId=" + player.getKboPlayerId();
                    page.navigate(detailUrl, new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(10000));

                    Document doc = getJsoupDocument(page);

                    // 프로필 파싱
                    boolean parsed = parsePlayerProfile(doc, player);

                    if (!parsed) {
                        // 투수 상세 페이지 시도
                        detailUrl = PLAYER_PITCHER_DETAIL_URL + "?playerId=" + player.getKboPlayerId();
                        page.navigate(detailUrl, new Page.NavigateOptions()
                                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(10000));
                        doc = getJsoupDocument(page);
                        parsed = parsePlayerProfile(doc, player);
                    }

                    if (parsed) {
                        player.setProfileUpdatedAt(LocalDateTime.now());
                        playerRepository.save(player);
                        profileCount++;
                    }

                    if ((i + 1) % 50 == 0) {
                        log.info("[프로필] 진행 중: {}/{}", i + 1, players.size());
                    }
                } catch (Exception e) {
                    log.debug("[프로필] {} 프로필 수집 실패: {}", player.getName(), e.getMessage());
                    try { page.close(); } catch (Exception ignored) {}
                    page = createPage(browser);
                }
            }

            log.info("[프로필] 상세 프로필 수집 완료: {}명", profileCount);

        } catch (Exception e) {
            log.error("[프로필] 상세 프로필 크롤링 실패", e);
        } finally {
            cleanup(page, browser, pw);
        }

        return profileCount;
    }

    /**
     * 선수 상세 페이지에서 프로필 정보 파싱
     * KBO 사이트 구조: .player_info 내 li > strong(라벨) + span(값)
     */
    private boolean parsePlayerProfile(Document doc, Player player) {
        try {
            Element profileArea = doc.selectFirst(".player_info");
            if (profileArea == null) return false;

            boolean updated = false;

            // KBO 사이트 구조: li 안에 <strong>라벨: </strong><span>값</span>
            Elements lis = profileArea.select("li");
            for (Element li : lis) {
                Element strong = li.selectFirst("strong");
                Element span = li.selectFirst("span");
                if (strong == null || span == null) continue;

                String label = strong.text().replaceAll("[:\\s]+$", "").trim();
                String value = span.text().trim();
                if (value.isEmpty()) continue;

                updated |= applyProfileField(player, label, value);
            }

            return updated;
        } catch (Exception e) {
            log.debug("프로필 파싱 오류: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 프로필 필드 라벨에 따라 Player 엔티티에 값 적용
     *
     * KBO 사이트 필드 매핑:
     *  - 등번호: "25"
     *  - 생년월일: "1987년 06월 05일"
     *  - 포지션: "포수(우투우타)" → position + throwBat 분리
     *  - 신장/체중: "180cm/95kg"
     *  - 경력: "송정동초-무등중-진흥고-두산-..." → 학교 추출
     *  - 입단년도: "06두산" → debutYear=2006
     */
    private boolean applyProfileField(Player player, String label, String value) {
        if (value == null || value.isEmpty() || "-".equals(value)) return false;

        try {
            if (label.contains("등번호")) {
                player.setBackNumber(value.replaceAll("[^0-9]", ""));
                return true;
            }
            if (label.contains("포지션")) {
                // "포수(우투우타)" → position="포수", throwBat="우투우타"
                if (value.contains("(") && value.contains(")")) {
                    int pStart = value.indexOf("(");
                    int pEnd = value.indexOf(")");
                    player.setPosition(value.substring(0, pStart).trim());
                    player.setThrowBat(value.substring(pStart + 1, pEnd).trim());
                } else {
                    player.setPosition(value);
                }
                return true;
            }
            if (label.contains("생년월일")) {
                player.setBirthDate(parseBirthDate(value));
                return true;
            }
            if (label.contains("신장") || label.contains("체격") || label.contains("키/몸무게")) {
                // "180cm/95kg" 또는 "180cm, 95kg"
                parseHeightWeight(player, value);
                return true;
            }
            if (label.contains("경력")) {
                // "송정동초-무등중-진흥고-두산-경찰-두산-NC" → 마지막 학교(고) 추출
                player.setSchool(extractSchool(value));
                return true;
            }
            if (label.contains("입단년도") || label.contains("입단")) {
                // "06두산" → 2006
                player.setDebutYear(parseDebutYear(value));
                return true;
            }
        } catch (Exception e) {
            log.debug("프로필 필드 적용 오류: {}={} ({})", label, value, e.getMessage());
        }

        return false;
    }

    /**
     * 경력 문자열에서 마지막 학교(고등학교) 추출
     * "송정동초-무등중-진흥고-두산-경찰-두산-NC" → "진흥고"
     */
    private String extractSchool(String career) {
        if (career == null || career.isEmpty()) return null;
        String[] parts = career.split("-");
        // 뒤에서부터 "고", "대", "중" 으로 끝나는 항목 찾기 (가장 마지막 학교)
        String lastSchool = null;
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.endsWith("고") || trimmed.endsWith("대") || trimmed.endsWith("중")
                    || trimmed.endsWith("초") || trimmed.endsWith("학교")) {
                lastSchool = trimmed;
            }
        }
        return lastSchool != null ? lastSchool : career;
    }

    /**
     * 입단년도 파싱: "06두산" → 2006, "2015KIA" → 2015
     */
    private Integer parseDebutYear(String text) {
        if (text == null || text.isEmpty()) return null;
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        int year = Integer.parseInt(digits);
        // 2자리 연도 → 4자리 변환 (00~30 → 2000~2030, 80~99 → 1980~1999)
        if (year < 100) {
            year += (year <= 30) ? 2000 : 1900;
        }
        return year;
    }

    // ==================== 유틸 ====================

    private void selectSeasonDropdown(Page page, int season) {
        String selector = findSeasonDropdownSelector(page);
        if (selector == null) return;
        String currentValue = (String) page.evaluate(
                "(sel) => document.querySelector(sel)?.value", selector);
        if (String.valueOf(season).equals(currentValue)) return;
        selectAndWaitForPostBack(page, selector, String.valueOf(season));
    }

    private boolean goToNextPage(Page page, int pageNum) {
        try {
            String pageLinkSelector = String.format("a:text-is('%d')", pageNum);
            if (page.locator(".paging " + pageLinkSelector).count() > 0) {
                page.locator(".paging " + pageLinkSelector).click();
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private Integer resolveTeamId(String teamName) {
        Integer id = TeamMappingConstants.getTeamId(teamName);
        if (id == null) id = getTeamIdByKboName(teamName);
        return id;
    }

    /**
     * href에서 playerId 파라미터 추출
     * 예: "/Record/Player/HitterDetail/Basic.aspx?playerId=12345" → "12345"
     */
    private String extractPlayerIdFromHref(String href) {
        if (href == null) return null;
        try {
            // playerId= 파라미터 찾기
            int idx = href.indexOf("playerId=");
            if (idx == -1) return null;
            String sub = href.substring(idx + "playerId=".length());
            // & 이후 제거
            int ampIdx = sub.indexOf("&");
            if (ampIdx > 0) sub = sub.substring(0, ampIdx);
            return sub.trim().isEmpty() ? null : sub.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseBirthDate(String text) {
        try {
            // "1990.05.15", "1990-05-15", "1990년 5월 15일" 등
            text = text.replaceAll("[년월]", ".").replaceAll("일", "").replaceAll("\\s+", "").trim();
            if (text.matches("\\d{4}\\.\\d{1,2}\\.\\d{1,2}")) {
                String[] parts = text.split("\\.");
                return LocalDate.of(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]));
            }
            if (text.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(text);
            }
        } catch (Exception e) {
            log.debug("생년월일 파싱 실패: '{}'", text);
        }
        return null;
    }

    private void parseHeightWeight(Player player, String text) {
        // "183cm, 85kg" or "183/85" 형태
        try {
            String[] parts = text.replaceAll("[^0-9/,]", " ").trim().split("[/,\\s]+");
            if (parts.length >= 1) {
                player.setHeight(Integer.parseInt(parts[0].trim()));
            }
            if (parts.length >= 2) {
                player.setWeight(Integer.parseInt(parts[1].trim()));
            }
        } catch (Exception ignored) {}
    }

    private List<String[]> filterOutAllOption(List<String[]> options) {
        List<String[]> filtered = new ArrayList<>();
        for (String[] opt : options) {
            String value = opt[0];
            String text = opt[1];
            if (value.isEmpty() || "전체".equals(text)) continue;
            filtered.add(opt);
        }
        return filtered;
    }
}
