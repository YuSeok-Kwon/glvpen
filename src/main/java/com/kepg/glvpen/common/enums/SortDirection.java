package com.kepg.glvpen.common.enums;

/**
 * 정렬 방향 Enum
 */
public enum SortDirection {
    ASC("오름차순"),
    DESC("내림차순");

    private final String description;

    SortDirection(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 문자열을 SortDirection으로 변환 (대소문자 무시)
     * @param value 변환할 문자열
     * @return SortDirection (기본값: DESC)
     */
    public static SortDirection fromString(String value) {
        if (value == null) {
            return DESC;
        }

        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DESC;
        }
    }
}
