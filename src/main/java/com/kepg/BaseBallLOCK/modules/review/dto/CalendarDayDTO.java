package com.kepg.BaseBallLOCK.modules.review.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CalendarDayDTO {

	private int dayOfMonth;    
    private List<GameInfoDTO> games; 
    private Integer scheduleId;
    private LocalDate date;
    private boolean hasReview;
    private Integer reviewId;
}