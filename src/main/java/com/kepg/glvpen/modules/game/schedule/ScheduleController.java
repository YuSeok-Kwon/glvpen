package com.kepg.glvpen.modules.game.schedule;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.kepg.glvpen.common.validator.SeasonValidator;
import com.kepg.glvpen.modules.futures.schedule.service.FuturesScheduleService;
import com.kepg.glvpen.modules.game.schedule.dto.GameDetailCardView;
import com.kepg.glvpen.modules.game.schedule.dto.ScheduleCardView;
import com.kepg.glvpen.modules.game.schedule.service.ScheduleService;

import lombok.RequiredArgsConstructor;

@RequestMapping("/schedule")
@Controller
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final FuturesScheduleService futuresScheduleService;

    private static final int MIN_SCHEDULE_YEAR = 2022;

    @GetMapping("/result-view")
    public String resultView(@RequestParam(required = false) Integer year,
                              @RequestParam(required = false) Integer month,
                              @RequestParam(required = false) Integer day,
                              @RequestParam(required = false, defaultValue = "kbo") String league,
                              Model model) {
    	LocalDate today = LocalDate.now();

	    int currentSeason = SeasonValidator.currentSeason();
	    int selectedYear = (year != null) ? year : SeasonValidator.fallbackSeason();
	    int selectedMonth = (month != null) ? month : today.getMonthValue();

        Map<LocalDate, List<ScheduleCardView>> groupedSchedule;

        if ("futures".equals(league)) {
            groupedSchedule = futuresScheduleService.getGroupedScheduleByMonth(selectedYear, selectedMonth);
        } else {
            groupedSchedule = scheduleService.getGroupedScheduleByMonth(selectedYear, selectedMonth);
            groupedSchedule = scheduleService.sortScheduleWithTodayFirst(groupedSchedule, today);
        }

        // 연도 경계를 넘어 월 이동 허용 (2022 ~ 현재 연도 범위)
        LocalDate current = LocalDate.of(selectedYear, selectedMonth, 1);
	    LocalDate prev = current.minusMonths(1);
	    LocalDate next = current.plusMonths(1);

	    model.addAttribute("year", selectedYear);
	    model.addAttribute("month", selectedMonth);
	    model.addAttribute("league", league);
	    model.addAttribute("prevYear", prev.getYear() >= MIN_SCHEDULE_YEAR ? prev.getYear() : null);
	    model.addAttribute("prevMonth", prev.getYear() >= MIN_SCHEDULE_YEAR ? prev.getMonthValue() : null);
	    model.addAttribute("nextYear", next.getYear() <= currentSeason ? next.getYear() : null);
	    model.addAttribute("nextMonth", next.getYear() <= currentSeason ? next.getMonthValue() : null);
	    model.addAttribute("groupedSchedule", groupedSchedule);
	    model.addAttribute("minYear", MIN_SCHEDULE_YEAR);
	    model.addAttribute("maxYear", currentSeason);
	    model.addAttribute("scrollToDay", day);

        return "schedule/result";
    }

    @GetMapping("/detail-view")
    public String gameDetail(@RequestParam int matchId, Model model) {
        GameDetailCardView detail = scheduleService.getGameDetail(matchId);
        if (detail == null) {
            return "redirect:/schedule/result-view";
        }

        LocalDate date = detail.getMatchDate().toLocalDateTime().toLocalDate();
        List<ScheduleCardView> allGames = scheduleService.getSchedulesByDate(date);

        Integer prevMatchId = scheduleService.getPrevMatchId(matchId);
        Integer nextMatchId = scheduleService.getNextMatchId(matchId);

        model.addAttribute("prevMatchId", prevMatchId);
        model.addAttribute("nextMatchId", nextMatchId);
        model.addAttribute("game", detail);
        model.addAttribute("otherGames", allGames);
        return "schedule/detail";
    }
    
    @GetMapping("/detail-redirect-view")
    public String redirectToMatchDetail(@RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<ScheduleCardView> schedules = scheduleService.getSchedulesByDate(date);
        if (!schedules.isEmpty()) {
            return "redirect:/schedule/detail-view?matchId=" + schedules.get(0).getId();
        }
        return "redirect:/schedule/result-view?year=" + date.getYear() + "&month=" + date.getMonthValue();
    }
}
