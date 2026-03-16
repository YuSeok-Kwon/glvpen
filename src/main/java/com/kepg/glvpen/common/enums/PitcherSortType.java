package com.kepg.glvpen.common.enums;

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
    WAR("WAR"),
    FIP("FIP"),
    XFIP("xFIP"),
    K9("K/9", "K9"),
    BB9("BB/9", "BB9"),
    G("경기 수"),
    WPCT("승률"),
    HBP("사구"),
    R("실점"),
    ER("자책점"),
    CG("완투"),
    SHO("완봉"),
    QS("QS"),
    BSV("블론세이브"),
    TBF("타자상대수"),
    NP("투구수"),
    AVG("피안타율"),
    TWO_B("피2루타", "2B"),
    THREE_B("피3루타", "3B"),
    SAC("희생번트"),
    SF("희생플라이"),
    IBB("고의사구"),
    WP("폭투"),
    BK("보크");

    private final String description;
    private final String code;

    PitcherSortType(String description) {
        this.description = description;
        this.code = name();
    }

    PitcherSortType(String description, String code) {
        this.description = description;
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public String getCode() {
        return code;
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

        String normalized = value.trim().toUpperCase();

        for (PitcherSortType type : values()) {
            if (type.name().equals(normalized) || type.code.equals(normalized)) {
                return type;
            }
        }

        return ERA;
    }
}
