package com.kepg.BaseBallLOCK.modules.review.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 캘린더 데이터를 클라이언트에 전달하기 위한 Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarResponse {

    private Integer year;
    private Integer month;
    private List<List<CalendarDayDTO>> calendar;

    /**
     * 캘린더 주간 데이터
     * calendar는 List<List<CalendarDayDTO>> 형태
     * - 외부 List: 주(week) 단위
     * - 내부 List: 7일(일~토)
     */
}
