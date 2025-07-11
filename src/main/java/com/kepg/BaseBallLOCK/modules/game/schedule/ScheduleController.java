package com.kepg.BaseBallLOCK.modules.game.schedule;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.kepg.BaseBallLOCK.modules.game.schedule.dto.GameDetailCardView;
import com.kepg.BaseBallLOCK.modules.game.schedule.dto.ScheduleCardView;
import com.kepg.BaseBallLOCK.modules.game.schedule.service.ScheduleService;

import lombok.RequiredArgsConstructor;

@RequestMapping("/schedule")
@Controller
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping("/result-view")
    public String resultView(@RequestParam(required = false) Integer year,
                              @RequestParam(required = false) Integer month,
                              Model model) {
    	LocalDate today = LocalDate.now();

	    int selectedYear;
	    int selectedMonth;

	    if (year != null) {
	        selectedYear = year;
	    } else {
	        selectedYear = today.getYear();
	    }

	    if (month != null) {
	        selectedMonth = month;
	    } else {
	        selectedMonth = today.getMonthValue();
	    }
	    
        Map<LocalDate, List<ScheduleCardView>> groupedSchedule = scheduleService.getGroupedScheduleByMonth(selectedYear, selectedMonth);
        groupedSchedule = scheduleService.sortScheduleWithTodayFirst(groupedSchedule, today);
        
        LocalDate current = LocalDate.of(selectedYear, selectedMonth, 1);
	    LocalDate prev = current.minusMonths(1);
	    LocalDate next = current.plusMonths(1);

	    model.addAttribute("year", selectedYear);
	    model.addAttribute("month", selectedMonth);
	    model.addAttribute("prevYear", prev.getYear());
	    model.addAttribute("prevMonth", prev.getMonthValue());
	    model.addAttribute("nextYear", next.getYear());
	    model.addAttribute("nextMonth", next.getMonthValue());
	    model.addAttribute("groupedSchedule", groupedSchedule);
	    
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
            int firstMatchId = schedules.get(0).getId();
            return "redirect:/schedule/detail-view?matchId=" + firstMatchId;
        }
        return "redirect:/schedule/result-view?year=" + date.getYear() + "&month=" + date.getMonthValue();
    }
}
