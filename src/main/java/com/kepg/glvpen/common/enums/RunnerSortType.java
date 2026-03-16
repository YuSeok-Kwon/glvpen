package com.kepg.glvpen.common.enums;

/**
 * 주루 정렬 기준 Enum
 */
public enum RunnerSortType {
    G("경기 수"),
    SBA("도루 시도"),
    SB("도루 성공"),
    CS("도루 실패"),
    SB_PCT("도루 성공률", "SB_PCT"),
    OOB("주루사"),
    PKO("견제사");

    private final String description;
    private final String code;

    RunnerSortType(String description) {
        this.description = description;
        this.code = name();
    }

    RunnerSortType(String description, String code) {
        this.description = description;
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public String getCode() {
        return code;
    }

    public static RunnerSortType fromString(String value) {
        if (value == null) {
            return SB;
        }

        String normalized = value.trim().toUpperCase();

        for (RunnerSortType type : values()) {
            if (type.name().equals(normalized) || type.code.equals(normalized)) {
                return type;
            }
        }

        return SB;
    }
}
