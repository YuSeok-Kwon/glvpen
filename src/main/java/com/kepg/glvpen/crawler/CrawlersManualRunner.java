package com.kepg.glvpen.crawler;

import java.time.LocalDate;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.kepg.glvpen.GlvpenApplication;
import com.kepg.glvpen.crawler.kbo.KboBasic2Crawler;
import com.kepg.glvpen.crawler.kbo.KboCrowdCrawler;
import com.kepg.glvpen.crawler.kbo.KboFuturesCrawler;
import com.kepg.glvpen.crawler.kbo.KboPlayerProfileCrawler;
import com.kepg.glvpen.crawler.kbo.KboPlayerStatsCrawler;
import com.kepg.glvpen.crawler.kbo.KboGameCenterCrawler;
import com.kepg.glvpen.crawler.kbo.KboScheduleCrawler;
import com.kepg.glvpen.crawler.kbo.KboTeamStatsCrawler;
import com.kepg.glvpen.modules.player.stats.service.SabermetricsBatchService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CrawlersManualRunner {

public static void main(String[] args) {
ConfigurableApplicationContext context = SpringApplication.run(GlvpenApplication.class, args);

try {
    // ===== 크롤링 설정 =====

    // 크롤링 옵션 — 2020~2024 다시즌 수집 (일정/팀 수집 완료)
    // 예: -Dcrawl.schedule=true
    boolean crawlSchedule = Boolean.parseBoolean(System.getProperty("crawl.schedule", "false"));
    boolean crawlTeam = Boolean.parseBoolean(System.getProperty("crawl.team", "false"));
    boolean crawlPlayer = Boolean.parseBoolean(System.getProperty("crawl.player", "false"));

    // 크롤링 옵션 — 투수/수비/주루 전용 재크롤링 (PostBack 캐스케이드 수정 후)
    // 예: -Dcrawl.pitcherOnly=true -Dcrawl.defenseOnly=true -Dcrawl.runnerOnly=true
    boolean crawlPitcherOnly = Boolean.parseBoolean(System.getProperty("crawl.pitcherOnly", "false"));
    boolean crawlDefenseOnly = Boolean.parseBoolean(System.getProperty("crawl.defenseOnly", "false"));
    boolean crawlRunnerOnly = Boolean.parseBoolean(System.getProperty("crawl.runnerOnly", "false"));
    boolean crawlBatterOnly = Boolean.parseBoolean(System.getProperty("crawl.batterOnly", "false"));
    boolean crawlBatterBasic2 = Boolean.parseBoolean(System.getProperty("crawl.batterBasic2", "false"));

    // 크롤링 옵션 — Phase 2 추가
    boolean crawlCrowd = false;
    boolean crawlPlayerProfile = false;

    // 크롤링 옵션 — Phase 3 추가
    // 예: -Dcrawl.futures=true
    boolean crawlFutures = Boolean.parseBoolean(System.getProperty("crawl.futures", "false"));
    boolean crawlFuturesDebug = false;

    // 크롤링 옵션 — Phase 4 추가
    // 예: -Dcrawl.gameCenter=true
    boolean crawlGameCenter = Boolean.parseBoolean(System.getProperty("crawl.gameCenter", "false"));
    boolean forceRecrawl = Boolean.parseBoolean(System.getProperty("crawl.forceRecrawl", "false"));

    // 키플레이어 누락 재크롤링
    // 예: -Dcrawl.recrawlKeyPlayers=true
    boolean recrawlKeyPlayers = Boolean.parseBoolean(System.getProperty("crawl.recrawlKeyPlayers", "false"));

    // 라인업 누락 재크롤링
    // 예: -Dcrawl.lineup=true
    boolean crawlLineup = Boolean.parseBoolean(System.getProperty("crawl.lineup", "false"));

    // 시범경기 게임센터 크롤링
    // 예: -Dcrawl.exhibition=true
    boolean crawlExhibition = Boolean.parseBoolean(System.getProperty("crawl.exhibition", "false"));

    // 상황별 데이터 재수집 (정규시즌 상황 옵션만)
    // 예: -Dcrawl.situationOnly=true
    boolean crawlSituationOnly = Boolean.parseBoolean(System.getProperty("crawl.situationOnly", "false"));

    // 세이버메트릭스 계산 — 시스템 프로퍼티로 비활성화 가능
    // 예: -Dcrawl.sabermetrics=false
    boolean calculateSabermetrics = Boolean.parseBoolean(System.getProperty("crawl.sabermetrics", "true"));

    // 크롤링 대상 시즌 (3월 이전이면 전년도, 3월 이후면 현재 연도)
    int targetSeason = LocalDate.now().getMonthValue() < 3
            ? LocalDate.now().getYear() - 1
            : LocalDate.now().getYear();

    // 다시즌 배치 크롤링 — 시스템 프로퍼티로 시즌 범위 오버라이드 가능
    // 예: -Dcrawl.seasonStart=2022 -Dcrawl.seasonEnd=2023
    boolean crawlMultiSeason = true;
    int multiSeasonStart = Integer.parseInt(System.getProperty("crawl.seasonStart", "2020"));
    int multiSeasonEnd = Integer.parseInt(System.getProperty("crawl.seasonEnd", String.valueOf(targetSeason)));

    // 단일 기간 스케줄 크롤링
    LocalDate startDate = LocalDate.of(targetSeason, 3, 1);
    LocalDate endDate = LocalDate.of(targetSeason, 3, 31);

    log.info("=== KBO 데이터 수집 시작 ===");
    log.info("대상 시즌: {}", targetSeason);
    log.info("일정 크롤링: {}", crawlSchedule ? "예" : "아니오");
    log.info("팀 데이터 크롤링: {}", crawlTeam ? "예" : "아니오");
    log.info("선수 데이터 크롤링: {}", crawlPlayer ? "예" : "아니오");
    log.info("투수 전용 재크롤링: {}", crawlPitcherOnly ? "예" : "아니오");
    log.info("수비 전용 재크롤링: {}", crawlDefenseOnly ? "예" : "아니오");
    log.info("주루 전용 재크롤링: {}", crawlRunnerOnly ? "예" : "아니오");
    log.info("타자 전용 재크롤링: {}", crawlBatterOnly ? "예" : "아니오");
    log.info("타자 Basic2 크롤링: {}", crawlBatterBasic2 ? "예" : "아니오");
    log.info("관중현황 크롤링: {}", crawlCrowd ? "예" : "아니오");
    log.info("선수프로필 크롤링: {}", crawlPlayerProfile ? "예" : "아니오");
    log.info("퓨처스 리그 크롤링: {}", crawlFutures ? "예" : "아니오");
    log.info("퓨처스 셀렉터 디버그: {}", crawlFuturesDebug ? "예" : "아니오");
    log.info("게임센터 크롤링: {}", crawlGameCenter ? "예" : "아니오");
    log.info("강제 재크롤링: {}", forceRecrawl ? "예" : "아니오");
    log.info("키플레이어 누락 재크롤링: {}", recrawlKeyPlayers ? "예" : "아니오");
    log.info("라인업 누락 재크롤링: {}", crawlLineup ? "예" : "아니오");
    log.info("시범경기 게임센터: {}", crawlExhibition ? "예" : "아니오");
    log.info("상황별 데이터 재수집: {}", crawlSituationOnly ? "예" : "아니오");
    log.info("세이버메트릭스 계산: {}", calculateSabermetrics ? "예" : "아니오");
    log.info("다시즌 배치 크롤링: {}", crawlMultiSeason ? multiSeasonEnd + "→" + multiSeasonStart + " (역순)" : "아니오");

    // KBO 크롤러 인스턴스
    KboScheduleCrawler scheduleCrawler = context.getBean(KboScheduleCrawler.class);
    KboTeamStatsCrawler teamCrawler = context.getBean(KboTeamStatsCrawler.class);
    KboPlayerStatsCrawler playerCrawler = context.getBean(KboPlayerStatsCrawler.class);
    KboCrowdCrawler crowdCrawler = context.getBean(KboCrowdCrawler.class);
    KboPlayerProfileCrawler profileCrawler = context.getBean(KboPlayerProfileCrawler.class);
    KboFuturesCrawler futuresCrawler = context.getBean(KboFuturesCrawler.class);
    KboGameCenterCrawler gameCenterCrawler = context.getBean(KboGameCenterCrawler.class);
    KboBasic2Crawler basic2Crawler = context.getBean(KboBasic2Crawler.class);

    if (crawlMultiSeason) {
        // 다시즌 배치 크롤링 (2025 → 2024 → ... → 2020)

        if (crawlSchedule) {
            log.info("\n[1단계] 다시즌 일정 크롤링: {}→{}", multiSeasonEnd, multiSeasonStart);
            for (int year = multiSeasonEnd; year >= multiSeasonStart; year--) {
                LocalDate seasonStart = LocalDate.of(year, 2, 1);
                LocalDate seasonEnd = LocalDate.of(year, 11, 30);
                log.info("--- {}시즌 일정 크롤링: {} ~ {} ---", year, seasonStart, seasonEnd);
                scheduleCrawler.crawlGameRange(seasonStart, seasonEnd);
            }
        }

        if (crawlTeam) {
            log.info("\n[2단계] 다시즌 팀 데이터 크롤링: {}→{}", multiSeasonEnd, multiSeasonStart);
            teamCrawler.crawlMultiSeasons(multiSeasonStart, multiSeasonEnd);
        }

        if (crawlPlayer) {
            log.info("\n[3단계] 다시즌 선수 데이터 크롤링: {}→{}", multiSeasonEnd, multiSeasonStart);
            playerCrawler.crawlMultiSeasons(multiSeasonStart, multiSeasonEnd);
        }

        if (crawlPitcherOnly) {
            log.info("\n[3-1단계] 다시즌 투수 전용 재크롤링: {}→{} (PostBack 캐스케이드 수정)", multiSeasonEnd, multiSeasonStart);
            for (int year = multiSeasonEnd; year >= multiSeasonStart; year--) {
                log.info("--- {}시즌 투수 재크롤링 ---", year);
                playerCrawler.crawlPitcherStats(year);
            }
        }

        if (crawlDefenseOnly) {
            log.info("\n[3-2단계] 다시즌 수비 전용 재크롤링: {}→{} (PostBack 캐스케이드 수정)", multiSeasonEnd, multiSeasonStart);
            for (int year = multiSeasonEnd; year >= multiSeasonStart; year--) {
                log.info("--- {}시즌 수비 재크롤링 ---", year);
                playerCrawler.crawlDefenseStats(year);
            }
        }

        if (crawlRunnerOnly) {
            log.info("\n[3-3단계] 다시즌 주루 전용 재크롤링: {}→{} (PostBack 캐스케이드 수정)", multiSeasonEnd, multiSeasonStart);
            for (int year = multiSeasonEnd; year >= multiSeasonStart; year--) {
                log.info("--- {}시즌 주루 재크롤링 ---", year);
                playerCrawler.crawlRunnerStats(year);
            }
        }

        if (crawlBatterOnly) {
            log.info("\n[3-4단계] 다시즌 타자 전용 재크롤링: {}→{} (시즌 PostBack 검증 수정)", multiSeasonEnd, multiSeasonStart);
            for (int year = multiSeasonEnd; year >= multiSeasonStart; year--) {
                log.info("--- {}시즌 타자 재크롤링 ---", year);
                playerCrawler.crawlBatterStats(year);
            }
        }

        if (crawlBatterBasic2) {
            // 인라인 Jsoup PostBack 테스트 (Spring Boot 컨텍스트 내 동작 검증)
            log.info("\n[Basic2 진단] Spring Boot 내 Jsoup PostBack 테스트...");
            try {
                // Spring Boot이 설정한 글로벌 핸들러 확인/초기화
                java.net.CookieHandler globalCh = java.net.CookieHandler.getDefault();
                log.info("[Basic2 진단] CookieHandler: {}", globalCh);
                if (globalCh != null) {
                    java.net.CookieHandler.setDefault(null);
                    log.info("[Basic2 진단] CookieHandler 초기화됨");
                }

                // SSL 컨텍스트 재설정
                javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
                sc.init(null, new javax.net.ssl.TrustManager[]{new javax.net.ssl.X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                }}, new java.security.SecureRandom());
                javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
                javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier((h, s) -> true);
                log.info("[Basic2 진단] SSL 컨텍스트 재설정됨");
                String testUrl = "https://www.koreabaseball.com/Record/Player/HitterBasic/Basic2.aspx";
                org.jsoup.Connection.Response testInit = org.jsoup.Jsoup.connect(testUrl)
                        .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                        .timeout(30000).execute();
                org.jsoup.nodes.Document testDoc = testInit.parse();
                log.info("[Basic2 진단] 초기 GET: 타이틀={} 헤더={}", testDoc.title(), testDoc.select("table thead th").size());

                // PostBack 데이터 빌드
                java.util.Map<String, String> testData = new java.util.HashMap<>();
                for (org.jsoup.nodes.Element h : testDoc.select("input[type=hidden]")) {
                    testData.put(h.attr("name"), h.val());
                }
                String testSeasonName = null;
                for (org.jsoup.nodes.Element s : testDoc.select("select[name]")) {
                    if (s.attr("name").contains("ddlSeason")) { testSeasonName = s.attr("name"); break; }
                }
                testData.put("__EVENTTARGET", testSeasonName);
                testData.put("__EVENTARGUMENT", "");
                for (org.jsoup.nodes.Element s : testDoc.select("select[name]")) {
                    org.jsoup.nodes.Element sel = s.selectFirst("option[selected]");
                    testData.put(s.attr("name"), sel != null ? sel.val() : "");
                }
                testData.put(testSeasonName, "2024");

                // HTTP keep-alive 비활성화 테스트
                System.setProperty("http.keepAlive", "false");
                log.info("[Basic2 진단] http.keepAlive=false 설정 후 테스트...");
                org.jsoup.Connection.Response freshInit = org.jsoup.Jsoup.connect(testUrl)
                        .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                        .timeout(30000).execute();
                org.jsoup.nodes.Document freshDoc = freshInit.parse();
                java.util.Map<String, String> freshData = Basic2DiagnosticTest.buildPostData(freshDoc, testSeasonName, "2024");
                org.jsoup.Connection.Response freshResp = Basic2DiagnosticTest.doPost(testUrl, freshData, new java.util.HashMap<>(freshInit.cookies()));
                org.jsoup.nodes.Document freshPostDoc = freshResp.parse();
                log.info("[Basic2 진단] keepAlive=false: 타이틀={} 행={}", freshPostDoc.title(), freshPostDoc.select("table tbody tr").size());
                System.setProperty("http.keepAlive", "true");

                // Basic2DiagnosticTest.main() 호출 (비교용)
                log.info("[Basic2 진단] Basic2DiagnosticTest.main() 호출 (비교)...");
                Basic2DiagnosticTest.main(new String[]{});
            } catch (Exception e) {
                log.error("[Basic2 진단] 테스트 실패: {}", e.getMessage());
            }

            log.info("\n[3-5단계] 다시즌 타자 Basic2 크롤링: {}→{} (Jsoup HTTP POST)", multiSeasonEnd, multiSeasonStart);
            basic2Crawler.crawlMultiSeasons(multiSeasonStart, multiSeasonEnd);
        }

        if (crawlCrowd) {
            log.info("\n[4단계] 다시즌 관중현황 크롤링: {}→{}", multiSeasonEnd, multiSeasonStart);
            crowdCrawler.crawlMultiSeasons(multiSeasonStart, multiSeasonEnd);
        }

        if (crawlPlayerProfile) {
            log.info("\n[5단계] 다시즌 선수프로필 크롤링: {}→{}", multiSeasonEnd, multiSeasonStart);
            for (int year = multiSeasonEnd; year >= multiSeasonStart; year--) {
                profileCrawler.crawlPlayerProfiles(year);
            }
        }

        if (crawlFutures) {
            log.info("\n[6단계] 다시즌 퓨처스 리그 크롤링: {}→{}", multiSeasonEnd, multiSeasonStart);
            futuresCrawler.crawlMultiSeasons(multiSeasonStart, multiSeasonEnd);
        }

        if (crawlGameCenter) {
            log.info("\n[7단계] 다시즌 게임센터 크롤링: {}→{} (force={})", multiSeasonEnd, multiSeasonStart, forceRecrawl);
            gameCenterCrawler.crawlMultiSeasons(multiSeasonStart, multiSeasonEnd, forceRecrawl);
        }

        if (recrawlKeyPlayers) {
            log.info("\n[8단계] 키플레이어 누락 재크롤링: {}→{}", multiSeasonStart, multiSeasonEnd);
            gameCenterCrawler.recrawlMissingKeyPlayers(multiSeasonStart, multiSeasonEnd);
        }

        if (crawlLineup) {
            log.info("\n[9단계] 라인업 누락 재크롤링: {}→{}", multiSeasonStart, multiSeasonEnd);
            gameCenterCrawler.recrawlMissingLineups(multiSeasonStart, multiSeasonEnd);
        }

        if (crawlExhibition) {
            log.info("\n[10단계] 다시즌 시범경기 게임센터 크롤링: {}→{} (force={})", multiSeasonEnd, multiSeasonStart, forceRecrawl);
            for (int year = multiSeasonEnd; year >= multiSeasonStart; year--) {
                gameCenterCrawler.crawlExhibitionGameCenters(year, forceRecrawl);
            }
        }

        if (crawlSituationOnly) {
            log.info("\n[11단계] 다시즌 상황별 데이터 재수집: {}→{}", multiSeasonEnd, multiSeasonStart);
            for (int year = multiSeasonEnd; year >= multiSeasonStart; year--) {
                log.info("--- {}시즌 상황별 데이터 재수집 ---", year);
                playerCrawler.crawlSituationOnly(year);
            }
        }

    } else {
        // 단일 시즌 크롤링

        if (crawlSchedule) {
            log.info("\n[1단계] 일정 크롤링: {} ~ {}", startDate, endDate);
            scheduleCrawler.crawlGameRange(startDate, endDate);
        }

        if (crawlTeam) {
            log.info("\n[2단계] 팀 데이터 크롤링 ({}시즌)", targetSeason);
            teamCrawler.crawlAllTeamData(targetSeason);
        }

        if (crawlPlayer) {
            log.info("\n[3단계] 선수 데이터 크롤링 ({}시즌)", targetSeason);
            playerCrawler.crawlAllPlayerData(targetSeason);
        }

        if (crawlPitcherOnly) {
            log.info("\n[3-1단계] 투수 전용 재크롤링 ({}시즌, PostBack 캐스케이드 수정)", targetSeason);
            playerCrawler.crawlPitcherStats(targetSeason);
        }

        if (crawlBatterOnly) {
            log.info("\n[3-4단계] 타자 전용 재크롤링 ({}시즌, 시즌 PostBack 검증 수정)", targetSeason);
            playerCrawler.crawlBatterStats(targetSeason);
        }

        if (crawlBatterBasic2) {
            log.info("\n[3-5단계] 타자 Basic2 크롤링 ({}시즌, Jsoup HTTP POST)", targetSeason);
            basic2Crawler.crawlBasic2(targetSeason);
        }

        if (crawlCrowd) {
            log.info("\n[4단계] 관중현황 크롤링 ({}시즌)", targetSeason);
            crowdCrawler.crawlCrowdData(targetSeason);
        }

        if (crawlPlayerProfile) {
            log.info("\n[5단계] 선수프로필 크롤링 ({}시즌)", targetSeason);
            profileCrawler.crawlPlayerProfiles(targetSeason);
        }

        if (crawlFuturesDebug) {
            log.info("\n[디버그] 퓨처스 리그 셀렉터 덤프");
            futuresCrawler.debugDumpAllSelectors();
        }

        if (crawlFutures) {
            log.info("\n[6단계] 퓨처스 리그 크롤링 ({}시즌)", targetSeason);
            futuresCrawler.crawlAllFuturesData(targetSeason);
        }

        if (crawlGameCenter) {
            log.info("\n[7단계] 게임센터 크롤링 ({}시즌, force={})", targetSeason, forceRecrawl);
            gameCenterCrawler.crawlAllGameCenters(targetSeason, forceRecrawl);
        }

        if (recrawlKeyPlayers) {
            log.info("\n[8단계] 키플레이어 누락 재크롤링 ({}시즌)", targetSeason);
            gameCenterCrawler.recrawlMissingKeyPlayers(targetSeason, targetSeason);
        }

        if (crawlLineup) {
            log.info("\n[9단계] 라인업 누락 재크롤링 ({}시즌)", targetSeason);
            gameCenterCrawler.recrawlMissingLineups(targetSeason, targetSeason);
        }

        if (crawlExhibition) {
            log.info("\n[10단계] 시범경기 게임센터 크롤링 ({}시즌, force={})", targetSeason, forceRecrawl);
            gameCenterCrawler.crawlExhibitionGameCenters(targetSeason, forceRecrawl);
        }

        if (crawlSituationOnly) {
            log.info("\n[11단계] 상황별 데이터 재수집 ({}시즌)", targetSeason);
            playerCrawler.crawlSituationOnly(targetSeason);
        }
    }

    // 세이버메트릭스 계산 (크롤링 완료 후)
    if (calculateSabermetrics) {
        SabermetricsBatchService saberService = context.getBean(SabermetricsBatchService.class);
        int saberStart = crawlMultiSeason ? multiSeasonStart : targetSeason;
        int saberEnd = crawlMultiSeason ? multiSeasonEnd : targetSeason;
        log.info("\n[세이버메트릭스] {}~{} 시즌 계산 시작", saberStart, saberEnd);
        saberService.calculateAllSabermetrics(saberStart, saberEnd);
        log.info("[세이버메트릭스] 계산 완료");
    }

    log.info("\n=== KBO 데이터 수집 완료 ===");

} catch (Exception e) {
    log.error("크롤링 중 오류 발생", e);
} finally {
    context.close();
    System.exit(0);
}
}
}
