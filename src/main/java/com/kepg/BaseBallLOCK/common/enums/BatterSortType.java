package com.kepg.BaseBallLOCK.common.enums;

/**
 * 타자 정렬 기준 Enum
 */
public enum BatterSortType {
    WAR("WAR"),
    AVG("타율"),
    OPS("OPS"),
    HR("홈런"),
    SB("도루"),
    G("경기 수"),
    PA("타석 수"),
    H("안타"),
    TWO_B("2루타", "2B"),
    THREE_B("3루타", "3B"),
    RBI("타점"),
    BB("볼넷"),
    SO("삼진"),
    OBP("출루율"),
    SLG("장타율"),
    WRCPLUS("WRC+", "WRCPLUS");

    private final String description;
    private final String code;

    BatterSortType(String description) {
        this.description = description;
        this.code = name();
    }

    BatterSortType(String description, String code) {
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
     * 문자열을 BatterSortType으로 변환
     * @param value 변환할 문자열
     * @return BatterSortType (기본값: WAR)
     */
    public static BatterSortType fromString(String value) {
        if (value == null) {
            return WAR;
        }

        String normalized = value.trim().toUpperCase();

        for (BatterSortType type : values()) {
            if (type.name().equals(normalized) || type.code.equals(normalized)) {
                return type;
            }
        }

        return WAR;
    }
}
