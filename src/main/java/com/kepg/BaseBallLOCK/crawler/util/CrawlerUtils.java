package com.kepg.BaseBallLOCK.crawler.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.WebDriver;

import lombok.extern.slf4j.Slf4j;

/**
 * 크롤링 작업에 필요한 공통 유틸리티 메서드 모음
 */
@Slf4j
public final class CrawlerUtils {

    private CrawlerUtils() {
        throw new AssertionError("유틸리티 클래스는 인스턴스화할 수 없습니다");
    }

    // ==================== 공통 Regex 패턴 ====================

    /**
     * Statiz URL에서 s_no 파라미터를 추출하는 패턴
     */
    public static final Pattern STATIZ_ID_PATTERN = Pattern.compile("s_no=(\\d+)");

    /**
     * 날짜 패턴 (MM-dd)
     */
    public static final Pattern DATE_PATTERN = Pattern.compile("(\\d{2})-(\\d{2})");

    /**
     * 날짜+시간 패턴 (MM-dd HH:mm)
     */
    public static final Pattern DATE_TIME_PATTERN = Pattern.compile("(\\d{2})-(\\d{2})\\s+(\\d{2}):(\\d{2})");

    /**
     * 경기장 정보 추출 패턴 (괄호 안의 내용)
     */
    public static final Pattern STADIUM_PATTERN = Pattern.compile("\\(([^)]+)\\)");

    /**
     * 투수 결과 정보 추출 패턴
     */
    public static final Pattern PITCHER_RESULT_PATTERN = Pattern.compile("\\s*\\(([승패세홀]).*");

    // ==================== 페이지 로딩 ====================

    /**
     * WebDriver로 페이지를 로드하고 Jsoup Document로 파싱합니다.
     *
     * @param driver WebDriver 인스턴스
     * @param url 로드할 URL
     * @param waitMs 대기 시간 (밀리초)
     * @return 파싱된 Document
     * @throws InterruptedException 대기 중 인터럽트 발생 시
     */
    public static Document loadPage(WebDriver driver, String url, int waitMs) throws InterruptedException {
        driver.get(url);
        Thread.sleep(waitMs);

        String html = driver.getPageSource();
        return Jsoup.parse(html);
    }

    /**
     * WebDriver로 페이지를 로드하고 Jsoup Document로 파싱합니다 (기본 대기 시간: 3초).
     *
     * @param driver WebDriver 인스턴스
     * @param url 로드할 URL
     * @return 파싱된 Document
     * @throws InterruptedException 대기 중 인터럽트 발생 시
     */
    public static Document loadPage(WebDriver driver, String url) throws InterruptedException {
        return loadPage(driver, url, 3000);
    }

    // ==================== 숫자 파싱 ====================

    /**
     * Element에서 Double 값을 안전하게 파싱합니다.
     * 빈 값이나 "-"는 0.0으로 처리합니다.
     *
     * @param element 파싱할 Element
     * @return 파싱된 Double 값, 실패 시 0.0
     */
    public static double parseDouble(Element element) {
        if (element == null) {
            return 0.0;
        }

        try {
            String text = element.text().trim();
            if (text.isEmpty() || text.equals("-")) {
                return 0.0;
            }
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            log.debug("Double 파싱 실패: {}", element.text());
            return 0.0;
        }
    }

    /**
     * 문자열에서 Double 값을 안전하게 파싱합니다.
     * 빈 값이나 "-"는 0.0으로 처리합니다.
     *
     * @param text 파싱할 문자열
     * @return 파싱된 Double 값, 실패 시 0.0
     */
    public static double parseDouble(String text) {
        if (text == null || text.trim().isEmpty() || text.trim().equals("-")) {
            return 0.0;
        }

        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            log.debug("Double 파싱 실패: {}", text);
            return 0.0;
        }
    }

    /**
     * Element에서 Integer 값을 안전하게 파싱합니다.
     * 빈 값이나 "-"는 0으로 처리합니다.
     *
     * @param element 파싱할 Element
     * @return 파싱된 Integer 값, 실패 시 0
     */
    public static int parseInt(Element element) {
        if (element == null) {
            return 0;
        }

        try {
            String text = element.text().trim();
            if (text.isEmpty() || text.equals("-")) {
                return 0;
            }
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            log.debug("Integer 파싱 실패: {}", element.text());
            return 0;
        }
    }

    /**
     * 문자열에서 Integer 값을 안전하게 파싱합니다.
     * 빈 값이나 "-"는 0으로 처리합니다.
     *
     * @param text 파싱할 문자열
     * @return 파싱된 Integer 값, 실패 시 0
     */
    public static int parseInt(String text) {
        if (text == null || text.trim().isEmpty() || text.trim().equals("-")) {
            return 0;
        }

        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            log.debug("Integer 파싱 실패: {}", text);
            return 0;
        }
    }

    // ==================== Regex 유틸리티 ====================

    /**
     * URL에서 Statiz ID를 추출합니다.
     *
     * @param url URL 문자열
     * @return Statiz ID, 없으면 null
     */
    public static Integer extractStatizId(String url) {
        if (url == null || !url.contains("s_no=")) {
            return null;
        }

        Matcher matcher = STATIZ_ID_PATTERN.matcher(url);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                log.warn("Statiz ID 파싱 실패: {}", url);
            }
        }

        return null;
    }

    /**
     * 텍스트에서 날짜 패턴 (MM-dd)을 추출합니다.
     *
     * @param text 검색할 텍스트
     * @return Matcher 객체
     */
    public static Matcher matchDate(String text) {
        return DATE_PATTERN.matcher(text);
    }

    /**
     * 텍스트에서 날짜+시간 패턴 (MM-dd HH:mm)을 추출합니다.
     *
     * @param text 검색할 텍스트
     * @return Matcher 객체
     */
    public static Matcher matchDateTime(String text) {
        return DATE_TIME_PATTERN.matcher(text);
    }

    /**
     * 텍스트에서 경기장 이름을 추출합니다 (괄호 안의 내용).
     *
     * @param text 검색할 텍스트
     * @return 경기장 이름, 없으면 null
     */
    public static String extractStadiumName(String text) {
        if (text == null) {
            return null;
        }

        Matcher matcher = STADIUM_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * 투수 정보에서 결과(승/패/세/홀)를 추출합니다.
     *
     * @param pitcherInfo 투수 정보 텍스트 (예: "김태훈 (승, 2-2)")
     * @return 결과 문자, 없으면 빈 문자열
     */
    public static String extractPitcherDecision(String pitcherInfo) {
        if (pitcherInfo == null) {
            return "";
        }

        if (pitcherInfo.contains("(승")) return "W";
        if (pitcherInfo.contains("(패")) return "L";
        if (pitcherInfo.contains("(세")) return "SV";
        if (pitcherInfo.contains("(홀")) return "HLD";

        return "";
    }

    /**
     * 투수 정보에서 이름만 추출합니다 (괄호 안의 내용 제거).
     *
     * @param pitcherInfo 투수 정보 텍스트 (예: "김태훈 (승, 2-2)")
     * @return 투수 이름 (예: "김태훈")
     */
    public static String extractPitcherName(String pitcherInfo) {
        if (pitcherInfo == null) {
            return "";
        }

        return pitcherInfo.replaceAll("\\s*\\([^)]*\\)", "").trim();
    }

    // ==================== 기타 유틸리티 ====================

    /**
     * Element가 null이 아니고 텍스트가 있는지 확인합니다.
     *
     * @param element 확인할 Element
     * @return 유효하면 true
     */
    public static boolean isValidElement(Element element) {
        return element != null && !element.text().trim().isEmpty();
    }

    /**
     * 문자열이 유효한지 확인합니다 (null이 아니고 공백이 아님).
     *
     * @param text 확인할 문자열
     * @return 유효하면 true
     */
    public static boolean isValidText(String text) {
        return text != null && !text.trim().isEmpty();
    }
}
