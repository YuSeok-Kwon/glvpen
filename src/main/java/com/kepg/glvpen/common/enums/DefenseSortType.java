package com.kepg.glvpen.common.enums;

/**
 * 수비 정렬 기준 Enum
 */
public enum DefenseSortType {
    G("경기 수"),
    GS("선발 출장"),
    IP("이닝"),
    E("실책"),
    PKO("견제사"),
    PO("자살"),
    A("보살"),
    DP("병살"),
    FPCT("수비율"),
    PB("포일"),
    SB("도루 허용"),
    CS("도루 저지"),
    CS_PCT("도루 저지율", "CS_PCT");

    private final String description;
    private final String code;

    DefenseSortType(String description) {
        this.description = description;
        this.code = name();
    }

    DefenseSortType(String description, String code) {
        this.description = description;
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public String getCode() {
        return code;
    }

    public static DefenseSortType fromString(String value) {
        if (value == null) {
            return G;
        }

        String normalized = value.trim().toUpperCase();

        for (DefenseSortType type : values()) {
            if (type.name().equals(normalized) || type.code.equals(normalized)) {
                return type;
            }
        }

        return G;
    }
}
