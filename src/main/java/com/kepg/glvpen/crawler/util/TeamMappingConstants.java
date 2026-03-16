package com.kepg.glvpen.crawler.util;

import java.util.HashMap;
import java.util.Map;

/**
 * KBO 팀 및 경기장 매핑 상수 클래스
 * 모든 크롤러에서 공통으로 사용하는 팀 이름/ID, 경기장 이름 매핑을 제공합니다.
 */
public final class TeamMappingConstants {

    private TeamMappingConstants() {
        throw new AssertionError("유틸리티 클래스는 인스턴스화할 수 없습니다");
    }

    // ==================== 팀 이름 → ID 매핑 ====================

    /**
     * 팀 이름을 팀 ID로 변환하는 맵
     */
    private static final Map<String, Integer> TEAM_NAME_TO_ID = new HashMap<>();

    static {
        TEAM_NAME_TO_ID.put("KIA", 1);
        TEAM_NAME_TO_ID.put("두산", 2);
        TEAM_NAME_TO_ID.put("삼성", 3);
        TEAM_NAME_TO_ID.put("SSG", 4);
        TEAM_NAME_TO_ID.put("LG", 5);
        TEAM_NAME_TO_ID.put("한화", 6);
        TEAM_NAME_TO_ID.put("NC", 7);
        TEAM_NAME_TO_ID.put("KT", 8);
        TEAM_NAME_TO_ID.put("롯데", 9);
        TEAM_NAME_TO_ID.put("키움", 10);

        // 과거 팀명 (2021년 이전)
        TEAM_NAME_TO_ID.put("SK", 4);   // SK 와이번스 → SSG 랜더스 (2022~)

        // 퓨처스 리그 전용 팀
        TEAM_NAME_TO_ID.put("고양", 11);
        TEAM_NAME_TO_ID.put("상무", 12);
        TEAM_NAME_TO_ID.put("울산", 13);
    }

    /**
     * 팀 이름으로 팀 ID를 조회합니다.
     *
     * @param teamName 팀 이름
     * @return 팀 ID, 없으면 null
     */
    public static Integer getTeamId(String teamName) {
        return TEAM_NAME_TO_ID.get(teamName);
    }

    /**
     * 팀 이름으로 팀 ID를 조회하고, 없으면 기본값을 반환합니다.
     *
     * @param teamName 팀 이름
     * @param defaultValue 기본값
     * @return 팀 ID 또는 기본값
     */
    public static Integer getTeamIdOrDefault(String teamName, Integer defaultValue) {
        return TEAM_NAME_TO_ID.getOrDefault(teamName, defaultValue);
    }

    /**
     * 팀 이름이 유효한지 확인합니다.
     *
     * @param teamName 팀 이름
     * @return 유효하면 true
     */
    public static boolean isValidTeamName(String teamName) {
        return TEAM_NAME_TO_ID.containsKey(teamName);
    }

    /**
     * 모든 팀 이름 목록을 반환합니다.
     *
     * @return 팀 이름 집합
     */
    public static java.util.Set<String> getAllTeamNames() {
        return TEAM_NAME_TO_ID.keySet();
    }

    // ==================== 팀 ID → Statiz teId 매핑 ====================

    /**
     * 팀 ID를 Statiz teId로 변환하는 맵
     */
    private static final Map<Integer, String> TEAM_ID_TO_TE_ID = new HashMap<>();

    static {
        TEAM_ID_TO_TE_ID.put(1, "2002");   // KIA
        TEAM_ID_TO_TE_ID.put(2, "6002");   // 두산
        TEAM_ID_TO_TE_ID.put(3, "1001");   // 삼성
        TEAM_ID_TO_TE_ID.put(4, "5002");   // LG
        TEAM_ID_TO_TE_ID.put(5, "12001");  // KT
        TEAM_ID_TO_TE_ID.put(6, "9002");   // SSG
        TEAM_ID_TO_TE_ID.put(7, "3001");   // 롯데
        TEAM_ID_TO_TE_ID.put(8, "7002");   // 한화
        TEAM_ID_TO_TE_ID.put(9, "11001");  // NC
        TEAM_ID_TO_TE_ID.put(10, "10001"); // 키움
    }

    /**
     * 팀 ID로 Statiz teId를 조회합니다.
     *
     * @param teamId 팀 ID
     * @return Statiz teId, 없으면 null
     */
    public static String getTeIdByTeamId(Integer teamId) {
        return TEAM_ID_TO_TE_ID.get(teamId);
    }

    // ==================== KBO 2글자 약어 → 팀 ID 매핑 ====================

    /**
     * KBO gameId에 사용되는 2글자 약어를 팀 ID로 변환하는 맵
     * 예: "HT" → 1 (KIA)
     */
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

    /**
     * KBO 2글자 약어로 팀 ID를 조회합니다.
     *
     * @param kboAbbr KBO 2글자 약어 (예: "HT")
     * @return 팀 ID, 없으면 null
     */
    public static Integer getTeamIdByKboAbbr(String kboAbbr) {
        return KBO_ABBR_TO_TEAM_ID.get(kboAbbr);
    }

    // ==================== 경기장 이름 매핑 ====================

    /**
     * 경기장 약칭을 정식 명칭으로 변환하는 맵
     */
    private static final Map<String, String> STADIUM_NAME_MAP = new HashMap<>();

    static {
        STADIUM_NAME_MAP.put("고척", "서울 고척스카이돔");
        STADIUM_NAME_MAP.put("잠실", "서울 잠실종합운동장");
        STADIUM_NAME_MAP.put("대구", "대구 삼성라이온즈파크");
        STADIUM_NAME_MAP.put("문학", "인천 SSG랜더스필드");
        STADIUM_NAME_MAP.put("수원", "수원 KT위즈파크");
        STADIUM_NAME_MAP.put("창원", "창원 NC파크");
        STADIUM_NAME_MAP.put("광주", "광주 기아챔피언스필드");
        STADIUM_NAME_MAP.put("대전", "대전 한화생명이글스파크");
        STADIUM_NAME_MAP.put("사직", "부산 사직야구장");
        STADIUM_NAME_MAP.put("포항", "포항야구장");
        STADIUM_NAME_MAP.put("울산", "울산 문수야구장");
        STADIUM_NAME_MAP.put("청주", "청주야구장");
    }

    /**
     * 경기장 약칭으로 정식 명칭을 조회합니다.
     *
     * @param shortName 경기장 약칭
     * @return 정식 명칭, 없으면 null
     */
    public static String getStadiumFullName(String shortName) {
        return STADIUM_NAME_MAP.get(shortName);
    }

    /**
     * 경기장 약칭으로 정식 명칭을 조회하고, 없으면 기본값을 반환합니다.
     *
     * @param shortName 경기장 약칭
     * @param defaultValue 기본값
     * @return 정식 명칭 또는 기본값
     */
    public static String getStadiumFullNameOrDefault(String shortName, String defaultValue) {
        return STADIUM_NAME_MAP.getOrDefault(shortName, defaultValue);
    }

    /**
     * 경기장 약칭이 유효한지 확인합니다.
     *
     * @param shortName 경기장 약칭
     * @return 유효하면 true
     */
    public static boolean isValidStadiumName(String shortName) {
        return STADIUM_NAME_MAP.containsKey(shortName);
    }
}
