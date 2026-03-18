package com.kepg.glvpen.modules.team.teamRanking;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 팀 랭킹 관련 공통 상수 및 유틸리티
 */
public final class TeamRankingConstants {

    private TeamRankingConstants() {
    }

    /**
     * 카테고리 코드 → 한글 이름 매핑
     */
    public static Map<String, String> getCategoryNameMap() {
        Map<String, String> map = new HashMap<>();
        // 타자
        map.put("OPS", "팀 OPS");
        map.put("AVG", "팀 타율");
        map.put("HR", "팀 홈런");
        map.put("RBI", "팀 타점");
        map.put("SB", "팀 도루");
        map.put("H", "팀 안타");
        // 투수
        map.put("ERA", "팀 ERA");
        map.put("WHIP", "팀 WHIP");
        map.put("W", "팀 승리");
        map.put("SV", "팀 세이브");
        map.put("SO", "팀 탈삼진");
        map.put("HLD", "팀 홀드");
        return map;
    }

    /**
     * 팀 랭킹 테이블 헤더 목록
     */
    public static List<String> getTeamRankingHeaders() {
        return Arrays.asList(
            "OPS", "AVG", "HR", "RBI", "SB", "H",
            "ERA", "WHIP", "W", "SV", "SO", "HLD"
        );
    }
}
