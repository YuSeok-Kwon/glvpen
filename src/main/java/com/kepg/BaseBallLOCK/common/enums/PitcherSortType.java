package com.kepg.BaseBallLOCK.common.enums;

/**
 * 투수 정렬 기준 Enum
 */
public enum PitcherSortType {
    ERA("ERA"),
    WHIP("WHIP"),
    IP("이닝"),
    W("승"),
    L("패"),
    SV("세이브"),
    HLD("홀드"),
    SO("삼진"),
    BB("볼넷"),
    H("피안타"),
    HR("피홈런"),
    WAR("WAR");

    private final String description;

    PitcherSortType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 문자열을 PitcherSortType으로 변환
     * @param value 변환할 문자열
     * @return PitcherSortType (기본값: ERA)
     */
    public static PitcherSortType fromString(String value) {
        if (value == null) {
            return ERA;
        }

        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ERA;
        }
    }
}
