package com.kepg.BaseBallLOCK.modules.review.controller;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kepg.BaseBallLOCK.modules.game.schedule.service.ScheduleService;
import com.kepg.BaseBallLOCK.modules.review.domain.Review;
import com.kepg.BaseBallLOCK.modules.review.domain.ReviewSummary;
import com.kepg.BaseBallLOCK.modules.review.dto.CalendarResponse;
import com.kepg.BaseBallLOCK.modules.review.dto.ReviewDTO;
import com.kepg.BaseBallLOCK.modules.review.dto.ReviewResponse;
import com.kepg.BaseBallLOCK.modules.review.service.ReviewService;
import com.kepg.BaseBallLOCK.modules.review.summary.service.ReviewSummaryService;
import com.kepg.BaseBallLOCK.modules.user.domain.User;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@RestController
public class ReviewRestController {

    private final ReviewService reviewService;
    private final ReviewSummaryService reviewSummaryService;
    private final ScheduleService scheduleService;

    // ========== /api/reviews 경로의 RESTful API ==========

    /**
     * 리뷰 목록 조회
     * GET /api/reviews?year=2026&month=1
     */
    @GetMapping("/api/reviews")
    public ResponseEntity<Map<String, Object>> getReviews(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            result.put("success", false);
            result.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }

        try {
            // year, month가 없으면 현재 년월 사용
            LocalDate now = LocalDate.now();
            int targetYear = (year != null) ? year : now.getYear();
            int targetMonth = (month != null) ? month : now.getMonthValue();

            // 해당 월의 모든 리뷰 조회
            LocalDateTime startDateTime = LocalDate.of(targetYear, targetMonth, 1).atStartOfDay();
            LocalDateTime endDateTime = startDateTime.plusMonths(1);

            List<Review> reviews = reviewService.getReviewsByUserIdAndPeriod(
                user.getId(), startDateTime, endDateTime);

            List<ReviewResponse> reviewResponses = new ArrayList<>();
            for (Review review : reviews) {
                reviewResponses.add(ReviewResponse.from(review));
            }

            result.put("success", true);
            result.put("reviews", reviewResponses);
            result.put("year", targetYear);
            result.put("month", targetMonth);
            result.put("count", reviewResponses.size());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("리뷰 목록 조회 실패 - userId: {}, error: {}",
                user.getId(), e.getMessage(), e);
            result.put("success", false);
            result.put("message", "리뷰 목록 조회 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 특정 리뷰 조회
     * GET /api/reviews/{reviewId}
     */
    @GetMapping("/api/reviews/{reviewId}")
    public ResponseEntity<Map<String, Object>> getReview(
            @PathVariable Integer reviewId,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            result.put("success", false);
            result.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }

        try {
            Review review = reviewService.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("리뷰를 찾을 수 없습니다."));

            // 권한 확인 (본인의 리뷰만 조회 가능)
            if (review.getUserId() != user.getId()) {
                result.put("success", false);
                result.put("message", "권한이 없습니다.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
            }

            ReviewResponse reviewResponse = ReviewResponse.from(review);

            result.put("success", true);
            result.put("review", reviewResponse);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("리뷰 조회 실패 - reviewId: {}, error: {}",
                reviewId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 캘린더 데이터 조회
     * GET /api/reviews/calendar?year=2026&month=1
     */
    @GetMapping("/api/reviews/calendar")
    public ResponseEntity<Map<String, Object>> getCalendar(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            result.put("success", false);
            result.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }

        try {
            // year, month가 없으면 현재 년월 사용
            LocalDate now = LocalDate.now();
            int targetYear = (year != null) ? year : now.getYear();
            int targetMonth = (month != null) ? month : now.getMonthValue();

            int myTeamId = user.getFavoriteTeamId() != null ? user.getFavoriteTeamId() : 0;

            CalendarResponse calendarResponse = CalendarResponse.builder()
                .year(targetYear)
                .month(targetMonth)
                .calendar(reviewService.generateCalendar(targetYear, targetMonth, user.getId(), myTeamId))
                .build();

            result.put("success", true);
            result.put("data", calendarResponse);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("캘린더 조회 실패 - userId: {}, error: {}",
                user.getId(), e.getMessage(), e);
            result.put("success", false);
            result.put("message", "캘린더 조회 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 리뷰 삭제
     * DELETE /api/reviews/{reviewId}
     */
    @DeleteMapping("/api/reviews/{reviewId}")
    public ResponseEntity<Map<String, Object>> deleteReview(
            @PathVariable Integer reviewId,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("loginUser");
        if (user == null) {
            result.put("success", false);
            result.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }

        try {
            Review review = reviewService.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("리뷰를 찾을 수 없습니다."));

            // 권한 확인
            if (review.getUserId() != user.getId()) {
                result.put("success", false);
                result.put("message", "권한이 없습니다.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
            }

            reviewService.deleteReview(reviewId);

            result.put("success", true);
            result.put("message", "리뷰가 삭제되었습니다.");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("리뷰 삭제 실패 - reviewId: {}, error: {}",
                reviewId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    // ========== 기존 /review 경로의 API ==========

    @PostMapping("/review/save")
    public ResponseEntity<Map<String, Object>> saveReview(@RequestBody ReviewDTO reviewDTO, HttpSession session) {
        Map<String, Object> result = new HashMap<>();

        User user = (User) session.getAttribute("loginUser");

        if (user == null) {
            result.put("success", false);
            result.put("message", "로그인이 필요합니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }

        try {
            // 리뷰 저장/수정 전에 해당 주차의 기존 요약 삭제
            deleteExistingSummaryForReview(user.getId(), reviewDTO.getScheduleId());
            
            reviewService.saveOrUpdateReview(user.getId(), reviewDTO);

            result.put("success", true);
            result.put("message", "리뷰 저장 성공");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("리뷰 저장 실패 - userId: {}, scheduleId: {}, error: {}",
                user.getId(), reviewDTO.getScheduleId(), e.getMessage(), e);
            result.put("success", false);
            result.put("message", "리뷰 저장 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    @GetMapping("/review/players")
    public ResponseEntity<Map<String, Object>> getPlayersBySchedule(@RequestParam int scheduleId, HttpSession session) {

        User user = (User) session.getAttribute("loginUser");
        int myTeamId = user != null ? user.getFavoriteTeamId() : 0;

        Map<String, Object> response = new HashMap<>();
        try {
            List<String> names = reviewService.findPlayerNamesByScheduleId(scheduleId, myTeamId);
            response.put("success", true);
            response.put("players", names != null ? names : new ArrayList<>());
        } catch (Exception e) {
            log.error("선수 목록 조회 실패 - scheduleId: {}, myTeamId: {}, error: {}",
                scheduleId, myTeamId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "선수 목록을 불러오는 데 실패했습니다.");
        }
        return ResponseEntity.ok(response);
    }
    
    /**
     * 리뷰 저장/수정 시 해당 주차의 기존 요약을 삭제하는 메서드
     */
    private void deleteExistingSummaryForReview(int userId, int scheduleId) {
        try {
            // 스케줄 ID로 경기 날짜 조회 (Timestamp를 LocalDate로 변환)
            java.sql.Timestamp matchDateTime = scheduleService.findMatchDateById(scheduleId);
            if (matchDateTime != null) {
                LocalDate gameDate = matchDateTime.toLocalDateTime().toLocalDate();
                
                // 해당 주의 월요일 날짜 계산
                LocalDate weekStart = gameDate.with(DayOfWeek.MONDAY);
                
                // 기존 요약 조회
                ReviewSummary existingSummary = reviewSummaryService.getWeeklySummaryByStartDate(userId, weekStart);
                if (existingSummary != null) {
                    log.info("리뷰 변경 -> 기존 요약 삭제: summaryId={}, weekStart={}", existingSummary.getId(), weekStart);
                    reviewSummaryService.deleteSummary(existingSummary.getId());
                }
            }
        } catch (Exception e) {
            log.error("요약 삭제 중 오류 발생: {}", e.getMessage(), e);
            // 요약 삭제가 실패해도 리뷰 저장은 계속 진행
        }
    }
}
