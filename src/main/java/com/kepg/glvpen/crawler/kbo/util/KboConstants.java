package com.kepg.glvpen.crawler.kbo.util;

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
    // [2026.03 수정] Basic1.aspx, Basic2.aspx 모두 정상 동작 확인
    // Basic1: AVG,G,PA,AB,R,H,2B,3B,HR,TB,RBI,SAC,SF (13컬럼)
    // Basic2: AVG,BB,IBB,HBP,SO,GDP,SLG,OBP,OPS,MH,RISP,PH-BA (12컬럼)
    // BasicOld: 정규시즌→Basic1 포맷, 비정규시즌→16컬럼(BasicOld 포맷)
    // Detail1.aspx는 에러 페이지 (삭제됨)
    public static final String BATTER_STATS_URL = BASE_URL + "/Record/Player/HitterBasic/Basic1.aspx";
    public static final String BATTER_STATS_URL2 = BASE_URL + "/Record/Player/HitterBasic/Basic2.aspx";
    public static final String BATTER_BASICOLD_URL = BASE_URL + "/Record/Player/HitterBasic/BasicOld.aspx";

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

    // 팀 상대전적 (TeamRank.aspx — 순위 + 상대전적 매트릭스)
    public static final String TEAM_RANK_URL = BASE_URL + "/Record/TeamRank/TeamRank.aspx";

    // 관중현황
    public static final String CROWD_DAILY_URL = BASE_URL + "/Record/Crowd/GraphDaily.aspx";

    // 퓨처스 리그
    public static final String FUTURES_SCHEDULE_URL = BASE_URL + "/Futures/Schedule/FuturesList.aspx";
    public static final String FUTURES_BATTER_URL = BASE_URL + "/Futures/Player/Hitter.aspx";
    public static final String FUTURES_PITCHER_URL = BASE_URL + "/Futures/Player/Pitcher.aspx";
    public static final String FUTURES_TEAM_BATTER_URL = BASE_URL + "/Futures/Team/Hitter.aspx";
    public static final String FUTURES_TEAM_PITCHER_URL = BASE_URL + "/Futures/Team/Pitcher.aspx";

    // 선수 상세 (프로필)
    public static final String PLAYER_HITTER_DETAIL_URL = BASE_URL + "/Record/Player/HitterDetail/Basic.aspx";
    public static final String PLAYER_PITCHER_DETAIL_URL = BASE_URL + "/Record/Player/PitcherDetail/Basic.aspx";

    // 게임센터 API (HTTP POST)
    private static final String SCHEDULE_WS_URL = BASE_URL + "/ws/Schedule.asmx";
    public static final String GAME_CENTER_SCOREBOARD_URL = SCHEDULE_WS_URL + "/GetScoreBoardScroll";
    public static final String GAME_CENTER_BOXSCORE_URL = SCHEDULE_WS_URL + "/GetBoxScoreScroll";
    public static final String GAME_CENTER_KEY_PITCHER_URL = SCHEDULE_WS_URL + "/GetKeyPlayerPitcher";
    public static final String GAME_CENTER_KEY_HITTER_URL = SCHEDULE_WS_URL + "/GetKeyPlayerHitter";
    public static final String GAME_CENTER_REFERER = BASE_URL + "/Schedule/GameCenter/Main.aspx";

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

    // 드롭다운 (관중현황 - 월)
    public static final String SEL_MONTH_DROPDOWN_RECORD = "select[name*='ddlMonth']";

    // 드롭다운 (퓨처스 리그)
    public static final String SEL_FUTURES_LEAGUE_DROPDOWN = "select[name*='ddlGroup']";

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

        // 퓨처스 리그 전용 팀
        KBO_TEAM_NAME_TO_ID.put("고양", 11);
        KBO_TEAM_NAME_TO_ID.put("상무", 12);
        KBO_TEAM_NAME_TO_ID.put("울산", 13);
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
    public static final String SERIES_REGULAR = "0";
    /** 포스트시즌 시리즈 코드 */
    public static final String SERIES_POSTSEASON = "3,4,5,7";

    /**
     * 시리즈 목록: [label, dropdownValue]
     * KBO 사이트 선수기록 페이지의 실제 드롭다운 value 기준
     * 포스트시즌 옵션은 해당 시즌에 진행된 경우에만 드롭다운에 존재
     */
    public static final String[][] SERIES_LIST = {
        {"정규시즌", "0"},
        {"시범경기", "1"},
        {"와일드카드", "4"},
        {"준플레이오프", "3"},
        {"플레이오프", "5"},
        {"한국시리즈", "7"}
    };

    /** 시리즈 드롭다운 value → DB 저장용 코드 (현재 동일) */
    public static String seriesValueToCode(String dropdownValue) {
        if (dropdownValue == null) return "0";
        return dropdownValue;
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

    // [2025.12~] KBO 사이트 URL 변경: BasicOld.aspx 단일 페이지, 시리즈별 컬럼 변경
    //
    // 정규시즌(series=0): 순위|선수명|팀명|AVG|G|PA|AB|R|H|2B|3B|HR|TB|RBI|SAC|SF (13개)
    //   → 기존 Basic1.aspx와 동일한 포맷
    // 시범경기 등(series!=0): 순위|선수명|팀명|AVG|G|PA|AB|H|2B|3B|HR|RBI|SB|CS|BB|HBP|SO|GDP|E (16개)
    //   → BasicOld 전용 포맷
    //
    // SLG,OBP,OPS,ISO 등 파생 지표는 SabermetricsBatchService에서 계산
    public static final String[] BATTER_REGULAR_CATS = {
        "AVG", "G", "PA", "AB", "R", "H", "2B", "3B", "HR", "TB", "RBI", "SAC", "SF"
    };

    public static final String[] BATTER_BASICOLD_CATS = {
        "AVG", "G", "PA", "AB", "H", "2B", "3B", "HR", "RBI", "SB", "CS", "BB", "HBP", "SO", "GDP", "E"
    };

    // Basic1.aspx: 순위|선수명|팀명|AVG|G|PA|AB|R|H|2B|3B|HR|TB|RBI|SAC|SF
    public static final String[] BATTER_BASIC1_CATS = BATTER_REGULAR_CATS;

    // Basic2.aspx: 순위|선수명|팀명|AVG|BB|IBB|HBP|SO|GDP|SLG|OBP|OPS|MH|RISP|PH-BA
    // BB, SO, HBP → 세이버메트릭스 핵심 (K%, BB%, OBP, BABIP, wOBA)
    // SLG, OBP, OPS → 직접 제공 (계산 불필요)
    // RISP, PH-BA → 클러치 지수용
    public static final String[] BATTER_BASIC2_CATS = {
        "AVG", "BB", "IBB", "HBP", "SO", "GDP", "SLG", "OBP", "OPS", "MH", "RISP", "PH-BA"
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

    // 퓨처스 타자: 순위|선수명|팀명|AVG|G|PA|AB|R|H|2B|3B|HR|RBI|SB|BB|HBP|SO|SLG|OBP
    public static final String[] FUTURES_BATTER_CATS = {
        "AVG", "G", "PA", "AB", "R", "H", "2B", "3B", "HR", "RBI", "SB", "BB", "HBP", "SO", "SLG", "OBP"
    };

    // 퓨처스 투수: 순위|선수명|팀명|ERA|G|W|L|SV|HLD|WPCT|IP|H|HR|BB|HBP|SO|R|ER
    public static final String[] FUTURES_PITCHER_CATS = {
        "ERA", "G", "W", "L", "SV", "HLD", "WPCT", "IP", "H", "HR", "BB", "HBP", "SO", "R", "ER"
    };

    // ==================== 타자/투수 페이지 URL 배열 (순회용) ====================

    // [2026.03 수정] Basic1 + Basic2 두 페이지 크롤링
    // Basic1: 기본 타격 지표 (G, PA, AB, H, HR, RBI 등)
    // Basic2: 세이버메트릭스 입력값 (BB, SO, HBP) + 파생 지표 (OBP, SLG, OPS) + 상황별 (RISP, PH-BA)
    public static final String[] BATTER_PAGE_URLS = {
        BATTER_STATS_URL,   // Basic1
        BATTER_STATS_URL2   // Basic2
    };

    public static final String[][] BATTER_PAGE_CATS = {
        BATTER_BASIC1_CATS,
        BATTER_BASIC2_CATS
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
