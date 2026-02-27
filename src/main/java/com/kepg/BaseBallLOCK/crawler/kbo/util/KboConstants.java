package com.kepg.BaseBallLOCK.crawler.kbo.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * KBO 공식 사이트 크롤링 상수
 * URL, CSS 셀렉터, 팀 약어 매핑, 시리즈/상황 코드, 카테고리 배열 등
 */
public final class KboConstants {

    private KboConstants() {
        throw new AssertionError("유틸리티 클래스는 인스턴스화할 수 없습니다");
    }

    // ==================== URL ====================

    private static final String BASE_URL = "https://www.koreabaseball.com";

    // 일정
    public static final String SCHEDULE_URL = BASE_URL + "/Schedule/Schedule.aspx";

    // 선수 기록 - 타자
    public static final String BATTER_STATS_URL = BASE_URL + "/Record/Player/HitterBasic/Basic1.aspx";
    public static final String BATTER_STATS_URL2 = BASE_URL + "/Record/Player/HitterBasic/Basic2.aspx";
    public static final String BATTER_DETAIL1_URL = BASE_URL + "/Record/Player/HitterBasic/Detail1.aspx";

    // 선수 기록 - 투수
    public static final String PITCHER_STATS_URL = BASE_URL + "/Record/Player/PitcherBasic/Basic1.aspx";
    public static final String PITCHER_STATS_URL2 = BASE_URL + "/Record/Player/PitcherBasic/Basic2.aspx";
    public static final String PITCHER_DETAIL1_URL = BASE_URL + "/Record/Player/PitcherBasic/Detail1.aspx";
    public static final String PITCHER_DETAIL2_URL = BASE_URL + "/Record/Player/PitcherBasic/Detail2.aspx";

    // 선수 기록 - 수비/주루
    public static final String PLAYER_DEFENSE_URL = BASE_URL + "/Record/Player/Defense/Basic.aspx";
    public static final String PLAYER_RUNNER_URL = BASE_URL + "/Record/Player/Runner/Basic.aspx";

    // 팀 기록
    public static final String TEAM_BATTER_URL = BASE_URL + "/Record/Team/Hitter/Basic1.aspx";
    public static final String TEAM_BATTER_URL2 = BASE_URL + "/Record/Team/Hitter/Basic2.aspx";
    public static final String TEAM_PITCHER_URL = BASE_URL + "/Record/Team/Pitcher/Basic1.aspx";
    public static final String TEAM_PITCHER_URL2 = BASE_URL + "/Record/Team/Pitcher/Basic2.aspx";
    public static final String TEAM_DEFENSE_URL = BASE_URL + "/Record/Team/Defense/Basic.aspx";
    public static final String TEAM_RUNNER_URL = BASE_URL + "/Record/Team/Runner/Basic.aspx";
    public static final String TEAM_RANKING_URL = BASE_URL + "/Record/TeamRank/TeamRankDaily.aspx";

    // ==================== CSS 셀렉터 ====================

    // 일정 페이지
    public static final String SEL_SCHEDULE_TABLE = "#tblScheduleList";
    public static final String SEL_SCHEDULE_ROWS = "#tblScheduleList tbody tr";
    public static final String SEL_DAY_CELL = "td.day";
    public static final String SEL_PLAY_CELL = "td.play";

    // 드롭다운 (일정)
    public static final String SEL_YEAR_DROPDOWN = "#ddlYear";
    public static final String SEL_MONTH_DROPDOWN = "#ddlMonth";
    public static final String SEL_SERIES_DROPDOWN = "#ddlSeries";

    // 드롭다운 (기록)
    public static final String SEL_RECORD_YEAR_DROPDOWN = "#cphContents_cphContents_cphContents_ddlSeason_ddlSeason";

    // 드롭다운 (선수 기록 페이지 - 팀/시리즈/상황)
    public static final String SEL_TEAM_DROPDOWN = "select[name*='ddlTeam']";
    public static final String SEL_SERIES_DROPDOWN_RECORD = "select[name*='ddlSeries']";
    public static final String SEL_SITUATION_DROPDOWN = "select[name*='ddlSituation']";

    // 기록 테이블 (선수/팀 공통)
    public static final String SEL_RECORD_TABLE = "#cphContents_cphContents_cphContents_udpContent table.tData";
    public static final String SEL_RECORD_ROWS = "#cphContents_cphContents_cphContents_udpContent table.tData tbody tr";

    // 팀 순위 테이블
    public static final String SEL_RANKING_TABLE = "#cphContents_cphContents_cphContents_udpContent table.tData";
    public static final String SEL_RANKING_ROWS = "#cphContents_cphContents_cphContents_udpContent table.tData tbody tr";

    // 페이지네이션
    public static final String SEL_PAGING = ".paging";
    public static final String SEL_PAGING_NEXT = ".paging a:last-child";

    // ==================== 특수 텍스트 ====================

    public static final String TEXT_TRAVEL_DAY = "이동일";
    public static final String TEXT_NO_DATA = "데이터가 없습니다";
    public static final String TEXT_RAIN_CANCEL = "우천취소";
    public static final String TEXT_GAME_CANCEL = "경기취소";

    // ==================== KBO 팀 약어 → 팀 ID 매핑 ====================

    private static final Map<String, Integer> KBO_ABBR_TO_TEAM_ID = new HashMap<>();

    static {
        KBO_ABBR_TO_TEAM_ID.put("HT", 1);  // KIA
        KBO_ABBR_TO_TEAM_ID.put("OB", 2);  // 두산
        KBO_ABBR_TO_TEAM_ID.put("SS", 3);  // 삼성
        KBO_ABBR_TO_TEAM_ID.put("SK", 4);  // SSG (전신 SK)
        KBO_ABBR_TO_TEAM_ID.put("LG", 5);  // LG
        KBO_ABBR_TO_TEAM_ID.put("HH", 6);  // 한화
        KBO_ABBR_TO_TEAM_ID.put("NC", 7);  // NC
        KBO_ABBR_TO_TEAM_ID.put("KT", 8);  // KT
        KBO_ABBR_TO_TEAM_ID.put("LT", 9);  // 롯데
        KBO_ABBR_TO_TEAM_ID.put("WO", 10); // 키움
    }

    public static Integer getTeamIdByKboAbbr(String kboAbbr) {
        return KBO_ABBR_TO_TEAM_ID.get(kboAbbr);
    }

    // ==================== KBO 팀 이름 → 팀 ID ====================

    private static final Map<String, Integer> KBO_TEAM_NAME_TO_ID = new HashMap<>();

    static {
        KBO_TEAM_NAME_TO_ID.put("KIA", 1);
        KBO_TEAM_NAME_TO_ID.put("두산", 2);
        KBO_TEAM_NAME_TO_ID.put("삼성", 3);
        KBO_TEAM_NAME_TO_ID.put("SSG", 4);
        KBO_TEAM_NAME_TO_ID.put("LG", 5);
        KBO_TEAM_NAME_TO_ID.put("한화", 6);
        KBO_TEAM_NAME_TO_ID.put("NC", 7);
        KBO_TEAM_NAME_TO_ID.put("KT", 8);
        KBO_TEAM_NAME_TO_ID.put("롯데", 9);
        KBO_TEAM_NAME_TO_ID.put("키움", 10);
    }

    public static Integer getTeamIdByKboName(String teamName) {
        return KBO_TEAM_NAME_TO_ID.get(teamName);
    }

    // ==================== 팀 드롭다운 value → 팀 이름 ====================

    /** KBO 사이트 팀 드롭다운 value 목록 (전체 제외, 10개 팀) */
    public static final String[][] TEAM_DROPDOWN_VALUES = {
        {"HT", "KIA"},
        {"OB", "두산"},
        {"SS", "삼성"},
        {"SK", "SSG"},
        {"LG", "LG"},
        {"HH", "한화"},
        {"NC", "NC"},
        {"KT", "KT"},
        {"LT", "롯데"},
        {"WO", "키움"}
    };

    // ==================== 시리즈 코드 ====================

    /** 정규시즌 시리즈 코드 (드롭다운 value) */
    public static final String SERIES_REGULAR = "0,9,6";
    /** 포스트시즌 시리즈 코드 */
    public static final String SERIES_POSTSEASON = "3,4,5,7";

    /**
     * 시리즈 목록: [label, value]
     * 정규시즌=0, 시범경기=1, 와일드카드=4, 준플레이오프=3, 플레이오프=5, 한국시리즈=7
     */
    public static final String[][] SERIES_LIST = {
        {"정규시즌", "0,9,6"},
        {"시범경기", "1"},
        {"와일드카드", "4"},
        {"준플레이오프", "3"},
        {"플레이오프", "5"},
        {"한국시리즈", "7"}
    };

    /** 시리즈 코드 → DB 저장용 단순 코드 */
    public static String seriesValueToCode(String dropdownValue) {
        if (dropdownValue == null) return "0";
        return switch (dropdownValue) {
            case "0,9,6" -> "0";
            case "1" -> "1";
            case "4" -> "4";
            case "3" -> "3";
            case "5" -> "5";
            case "7" -> "7";
            default -> "0";
        };
    }

    // ==================== 상황별 코드 (타자/투수 정규시즌 전용) ====================

    /**
     * 경기상황별 코드: [label, value]
     * 정규시즌에서만 사용 가능, 비정규시즌에는 드롭다운 없음
     */
    public static final String[][] SITUATION_CODES = {
        {"전체", ""},
        {"월별", "MONTH_SC"},
        {"요일별", "WEEK_SC"},
        {"구장별", "STADIUM_SC"},
        {"홈/원정", "HOMEAYAY_SC"},
        {"상대팀별", "OPPTEAM_SC"},
        {"주/야간", "DAYNIGHT_SC"},
        {"전/후반기", "HALF_SC"},
        {"주자없는상황", "41"},
        {"득점권", "42"},
        {"만루", "43"},
        {"주자 1루", "44"},
        {"주자 2루", "45"},
        {"주자 3루", "46"},
        {"주자 1,2루", "47"}
    };

    // ==================== 페이지별 카테고리 배열 ====================

    // 타자 Basic1: 순위|선수명|팀명|AVG|G|PA|AB|R|H|2B|3B|HR|TB|RBI|SAC|SF
    public static final String[] BATTER_BASIC1_CATS = {
        "AVG", "G", "PA", "AB", "R", "H", "2B", "3B", "HR", "TB", "RBI", "SAC", "SF"
    };

    // 타자 Basic2: 순위|선수명|팀명|AVG|BB|IBB|HBP|SO|GDP|SLG|OBP|OPS|MH|RISP|PH-BA
    public static final String[] BATTER_BASIC2_CATS = {
        "AVG", "BB", "IBB", "HBP", "SO", "GDP", "SLG", "OBP", "OPS", "MH", "RISP", "PH-BA"
    };

    // 타자 Detail1: 순위|선수명|팀명|AVG|XBH|GO|AO|GO/AO|GW RBI|BB/K|P/PA|ISOP|XR|GPA
    public static final String[] BATTER_DETAIL1_CATS = {
        "AVG", "XBH", "GO", "AO", "GO/AO", "GW RBI", "BB/K", "P/PA", "ISOP", "XR", "GPA"
    };

    // 투수 Basic1: 순위|선수명|팀명|ERA|G|W|L|SV|HLD|WPCT|IP|H|HR|BB|HBP|SO|R|ER|WHIP
    public static final String[] PITCHER_BASIC1_CATS = {
        "ERA", "G", "W", "L", "SV", "HLD", "WPCT", "IP", "H", "HR", "BB", "HBP", "SO", "R", "ER", "WHIP"
    };

    // 투수 Basic2: 순위|선수명|팀명|ERA|CG|SHO|QS|BS|TBF|NP|AVG|2B|3B|SAC|SF|IBB|WP|BK
    public static final String[] PITCHER_BASIC2_CATS = {
        "ERA", "CG", "SHO", "QS", "BS", "TBF", "NP", "AVG", "2B", "3B", "SAC", "SF", "IBB", "WP", "BK"
    };

    // 투수 Detail1: 순위|선수명|팀명|ERA|GS|Wgs|Wgr|GF|SVO|TS|GDP|GO|AO|GO/AO
    public static final String[] PITCHER_DETAIL1_CATS = {
        "ERA", "GS", "Wgs", "Wgr", "GF", "SVO", "TS", "GDP", "GO", "AO", "GO/AO"
    };

    // 투수 Detail2: 순위|선수명|팀명|ERA|BABIP|P/G|P/IP|K/9|BB/9|K/BB|OBP|SLG|OPS
    public static final String[] PITCHER_DETAIL2_CATS = {
        "ERA", "BABIP", "P/G", "P/IP", "K/9", "BB/9", "K/BB", "OBP", "SLG", "OPS"
    };

    // 수비: 순위|선수명|팀명|POS|G|GS|IP|E|PKO|PO|A|DP|FPCT|PB|SB|CS|CS%
    public static final String[] DEFENSE_CATS = {
        "POS", "G", "GS", "IP", "E", "PKO", "PO", "A", "DP", "FPCT", "PB", "SB", "CS", "CS%"
    };

    // 주루: 순위|선수명|팀명|G|SBA|SB|CS|SB%|OOB|PKO
    public static final String[] RUNNER_CATS = {
        "G", "SBA", "SB", "CS", "SB%", "OOB", "PKO"
    };

    // ==================== 타자/투수 페이지 URL 배열 (순회용) ====================

    public static final String[] BATTER_PAGE_URLS = {
        BATTER_STATS_URL,   // Basic1
        BATTER_STATS_URL2,  // Basic2
        BATTER_DETAIL1_URL  // Detail1
    };

    public static final String[][] BATTER_PAGE_CATS = {
        BATTER_BASIC1_CATS,
        BATTER_BASIC2_CATS,
        BATTER_DETAIL1_CATS
    };

    public static final String[] PITCHER_PAGE_URLS = {
        PITCHER_STATS_URL,   // Basic1
        PITCHER_STATS_URL2,  // Basic2
        PITCHER_DETAIL1_URL, // Detail1
        PITCHER_DETAIL2_URL  // Detail2
    };

    public static final String[][] PITCHER_PAGE_CATS = {
        PITCHER_BASIC1_CATS,
        PITCHER_BASIC2_CATS,
        PITCHER_DETAIL1_CATS,
        PITCHER_DETAIL2_CATS
    };
}
