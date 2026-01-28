package com.kepg.BaseBallLOCK.modules.review.controller;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.kepg.BaseBallLOCK.modules.game.schedule.dto.GameDetailCardView;
import com.kepg.BaseBallLOCK.modules.game.schedule.dto.ScheduleCardView;
import com.kepg.BaseBallLOCK.modules.game.schedule.service.ScheduleService;
import com.kepg.BaseBallLOCK.modules.review.domain.Review;
import com.kepg.BaseBallLOCK.modules.review.domain.ReviewSummary;
import com.kepg.BaseBallLOCK.modules.review.dto.CalendarDayDTO;
import com.kepg.BaseBallLOCK.modules.review.service.ReviewService;
import com.kepg.BaseBallLOCK.modules.review.summary.service.ReviewSummaryService;
import com.kepg.BaseBallLOCK.modules.user.domain.User;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/review")
public class ReviewController {

    private final ReviewService reviewService;
    private final ScheduleService scheduleService;
    private final ReviewSummaryService reviewSummaryService;

    @GetMapping("/calendar-view")
    public String reviewCalendar(@RequestParam(value = "year", required = false) Integer year,
                                  @RequestParam(value = "month", required = false) Integer month,
                                  HttpSession session,
                                  Model model) {

        User user = (User) session.getAttribute("loginUser");
        int userId = user != null ? user.getId() : 0;
        Integer myTeamId = user != null ? user.getFavoriteTeamId() : null;
        if (myTeamId == null) {
            myTeamId = 999;
        }

        // 오늘 날짜 → 이번 주 시작일 (월요일)
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        
        int currentYear = (year != null) ? year : today.getYear();
        int currentMonth = (month != null) ? month : today.getMonthValue();

        YearMonth currentYearMonth = YearMonth.of(currentYear, currentMonth);
        YearMonth prevMonth = currentYearMonth.minusMonths(1);
        YearMonth nextMonth = currentYearMonth.plusMonths(1);

        List<List<CalendarDayDTO>> calendar = reviewService.generateCalendar(currentYear, currentMonth, userId, myTeamId);
        String teamColor = reviewService.getTeamColorByTeamId(myTeamId);

        // 요약 자동 생성 시도
        ReviewSummary summary = reviewSummaryService.getWeeklySummaryByStartDate(userId, weekStart);
        if (summary == null) {
        	reviewSummaryService.generateWeeklyReviewSummary(userId, weekStart);
            summary = reviewSummaryService.getWeeklySummaryByStartDate(userId, weekStart);
        }
        
        Map<LocalDate, Boolean> summaryExistMap = new HashMap<>();
        for (List<CalendarDayDTO> week : calendar) {
            if (week.get(0) != null) {
                LocalDate date = week.get(0).getDate();

                log.debug("달력 주차 처리 - 월요일 날짜: {}", date);

                // 먼저 요약이 존재하는지 확인
                boolean exists = reviewSummaryService.summaryExistsForWeek(userId, date);

                // 요약이 없는 경우 자동 생성 시도
                if (!exists) {
                    log.debug("요약이 없어서 생성 시도");
                    ReviewSummary weeklySummary = reviewSummaryService.generateWeeklyReviewSummary(userId, date);
                    exists = (weeklySummary != null); // 생성에 성공했으면 true
                    log.debug("요약 생성 결과: {}", (weeklySummary != null ? "성공" : "실패"));
                }

                log.debug("최종 summaryExistMap[{}] = {}", date, exists);
                summaryExistMap.put(date, exists);
            }
        }
        
        model.addAttribute("summaryExistMap", summaryExistMap);
        model.addAttribute("year", currentYear);
        model.addAttribute("month", currentMonth);
        model.addAttribute("prevYear", prevMonth.getYear());
        model.addAttribute("prevMonth", prevMonth.getMonthValue());
        model.addAttribute("nextYear", nextMonth.getYear());
        model.addAttribute("nextMonth", nextMonth.getMonthValue());
        model.addAttribute("calendar", calendar);
        model.addAttribute("myTeamId", myTeamId);
        model.addAttribute("teamColor", teamColor);
        model.addAttribute("today", today);

        return "review/home";
    }
    
    @GetMapping("/write-view")
    public String writeReviewPage(@RequestParam(value = "scheduleId", required = false) Integer scheduleId,
            @RequestParam(value = "reviewId", required = false) Integer reviewId,
    		HttpSession session,
    		Model model) {

        User user = (User) session.getAttribute("loginUser");
        Integer myTeamId = user != null ? user.getFavoriteTeamId() : null;
        if (myTeamId == null) {
            myTeamId = 999;
        }
        int userId = user != null ? user.getId() : 0;
        
        if (scheduleId == null) {
            return "redirect:/review/calendar-view";
        }
        
        // 경기 상세 정보 가져오기
        ScheduleCardView scheduleInfo = scheduleService.getScheduleDetailById(scheduleId);
        
        // teamColor 가져오기
        String teamColor = reviewService.getTeamColorByTeamId(myTeamId);
        
        // game 정보 가져오기 (detail 화면에서 쓰는 구조 그대로)
        GameDetailCardView game = scheduleService.getGameDetail(scheduleId);
        
        // 기존 리뷰 있으면 수정용
        if (reviewId != null) {
            Optional<Review> reviewOpt = reviewService.findById(reviewId);
            if (reviewOpt.isPresent()) {
                Review review = reviewOpt.get();
                model.addAttribute("review", review);
            } else {
                Review emptyReview = new Review();
                emptyReview.setScheduleId(scheduleId);
                emptyReview.setUserId(userId);
                model.addAttribute("review", emptyReview);
            }
        } else {
            Review newReview = new Review();
            newReview.setScheduleId(scheduleId);
            newReview.setUserId(userId);
            model.addAttribute("review", newReview);
        }
        model.addAttribute("teamColor", teamColor);
        model.addAttribute("scheduleInfo", scheduleInfo);
        model.addAttribute("game", game);
        
        
        return "review/write";
    }
    
    @GetMapping("/summary-view")
    public String reviewSummaryView(@RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                    HttpSession session, Model model) {

        log.info("=== 요약 페이지 접속 ===");
        log.info("요청된 startDate: {}", startDate);

        LocalDate weekStart = startDate.with(DayOfWeek.MONDAY);
        log.info("계산된 weekStart: {}", weekStart);

        User user = (User) session.getAttribute("loginUser");
        int userId = user != null ? user.getId() : 0;
        Integer teamId = user != null ? user.getFavoriteTeamId() : null;
        if (teamId == null) teamId = 999;

        log.info("userId: {}, teamId: {}", userId, teamId);

        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();

        List<List<CalendarDayDTO>> calendar = reviewService.generateCalendar(year, month, userId, teamId);

        // 주간 요약 조회 시도
        log.debug("기존 요약 조회 시도");
        ReviewSummary summary = reviewSummaryService.getWeeklySummaryByStartDate(userId, weekStart);

        if (summary == null) {
            log.debug("기존 요약이 없어서 새로 생성 시도");
            reviewSummaryService.generateWeeklyReviewSummary(userId, weekStart);
            summary = reviewSummaryService.getWeeklySummaryByStartDate(userId, weekStart);
        } else if (summary.getFeelingSummary() != null && summary.getFeelingSummary().contains("Gemini 응답을 불러오지 못했습니다")) {
            log.debug("기존 요약이 실패한 상태이므로 삭제하고 새로 생성");
            reviewSummaryService.deleteSummary(summary.getId());
            reviewSummaryService.generateWeeklyReviewSummary(userId, weekStart);
            summary = reviewSummaryService.getWeeklySummaryByStartDate(userId, weekStart);
        }

        log.debug("최종 요약 결과: {}", (summary != null ? "존재함" : "null"));
        if (summary != null) {
            log.debug("요약 ID: {}", summary.getId());
            log.debug("감정 요약: {}", summary.getFeelingSummary());
            log.debug("전체 요약: {}", summary.getReviewText());
        }

        Map<LocalDate, Boolean> summaryExistMap = new HashMap<>();
        for (List<CalendarDayDTO> week : calendar) {
            if (week.get(0) != null) {
                LocalDate date = week.get(0).getDate();
                boolean exists = reviewSummaryService.summaryExistsForWeek(userId, date);
                summaryExistMap.put(date, exists);
            }
        }

        model.addAttribute("calendar", calendar);
        model.addAttribute("summaryExistMap", summaryExistMap);
        model.addAttribute("summary", summary);
        model.addAttribute("teamColor", reviewService.getTeamColorByTeamId(teamId));
        model.addAttribute("teamId", teamId);

        return "review/summary";
    }

}