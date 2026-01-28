package com.kepg.BaseBallLOCK.modules.team.teamRanking;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 팀 랭킹 관련 공통 상수 및 유틸리티
 */
public final class TeamRankingConstants {

    private TeamRankingConstants() {
        // 유틸리티 클래스는 인스턴스화 방지
    }

    /**
     * 카테고리 코드 → 한글 이름 매핑
     */
    public static Map<String, String> getCategoryNameMap() {
        Map<String, String> map = new HashMap<>();
        map.put("TotalWAR", "종합 WAR");
        map.put("OPS", "팀 OPS");
        map.put("AVG", "팀 타율");
        map.put("HR", "팀 홈런");
        map.put("SB", "팀 도루");
        map.put("BetterWAR", "타자 WAR");
        map.put("PitcherWAR", "투수 WAR");
        map.put("SO", "팀 탈삼진");
        map.put("ERA", "팀 ERA");
        map.put("WHIP", "팀 WHIP");
        map.put("BB", "팀 볼넷");
        map.put("타격", "타격 WAA");
        map.put("주루", "주루 WAA");
        map.put("수비", "수비 WAA");
        map.put("선발", "선발 WAA");
        map.put("불펜", "불펜 WAA");
        return map;
    }

    /**
     * 팀 랭킹 테이블 헤더 목록
     */
    public static List<String> getTeamRankingHeaders() {
        return Arrays.asList(
            "TotalWAR", "BetterWAR", "OPS", "AVG", "HR", "SB",
            "PitcherWAR", "SO", "ERA", "WHIP", "BB",
            "타격", "주루", "수비", "선발", "불펜"
        );
    }
}
