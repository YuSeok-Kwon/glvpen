package com.kepg.glvpen.common.validator;

import java.time.LocalDate;

/**
 * 시즌(연도) 유효성 검증 유틸리티
 */
public class SeasonValidator {

    /**
     * 현재 연도를 시즌으로 반환
     */
    public static int currentSeason() {
        return LocalDate.now().getYear();
    }

    /**
     * 데이터가 존재할 가능성이 높은 최신 시즌 반환
     * 현재 연도에 데이터가 없을 수 있으므로 전년도를 기본값으로 제공
     */
    public static int fallbackSeason() {
        return currentSeason() - 1;
    }

    /**
     * 주어진 시즌이 현재 시즌인지 확인
     */
    public static boolean isCurrentSeason(int season) {
        return season == currentSeason();
    }

    private static final int MIN_SEASON = 2000;
    private static final int MAX_SEASON = 2100;

    /**
     * 시즌 유효성 검증
     * @param season 검증할 시즌
     * @throws IllegalArgumentException 유효하지 않은 시즌인 경우
     */
    public static void validate(int season) {
        if (season < MIN_SEASON || season > MAX_SEASON) {
            throw new IllegalArgumentException(
                String.format("시즌은 %d ~ %d 사이여야 합니다. 입력값: %d", MIN_SEASON, MAX_SEASON, season)
            );
        }
    }

    /**
     * 시즌 유효성 검증 (기본값 반환)
     * @param season 검증할 시즌
     * @param defaultSeason 기본값
     * @return 유효한 시즌 또는 기본값
     */
    public static int validateOrDefault(Integer season, int defaultSeason) {
        if (season == null) {
            return defaultSeason;
        }

        if (season < MIN_SEASON || season > MAX_SEASON) {
            return defaultSeason;
        }

        return season;
    }
}
