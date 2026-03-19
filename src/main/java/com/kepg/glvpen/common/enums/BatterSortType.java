package com.kepg.glvpen.common.enums;

/**
 * 타자 정렬 기준 Enum
 */
public enum BatterSortType {
    WOBA("wOBA"),
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
    WRCPLUS("WRC+", "WRCPLUS"),
    BABIP("BABIP"),
    ISO("ISO"),
    K_RATE("삼진율", "K_RATE"),
    BB_RATE("볼넷율", "BB_RATE"),
    AB("타수"),
    R("득점"),
    TB("루타"),
    SAC("희생번트"),
    SF("희생플라이"),
    IBB("고의사구"),
    HBP("사구"),
    GDP("병살"),
    MH("멀티히트"),
    RISP("득점권타율"),
    PH_BA("대타타율", "PH_BA");

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
     * @return BatterSortType (기본값: OPS)
     */
    public static BatterSortType fromString(String value) {
        if (value == null) {
            return OPS;
        }

        String normalized = value.trim().toUpperCase();

        for (BatterSortType type : values()) {
            if (type.name().equals(normalized) || type.code.equals(normalized)) {
                return type;
            }
        }

        return OPS;
    }
}
