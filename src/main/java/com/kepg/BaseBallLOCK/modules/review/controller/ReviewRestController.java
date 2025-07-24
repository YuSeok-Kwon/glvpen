package com.kepg.BaseBallLOCK.modules.review.controller;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kepg.BaseBallLOCK.modules.game.schedule.service.ScheduleService;
import com.kepg.BaseBallLOCK.modules.review.domain.ReviewSummary;
import com.kepg.BaseBallLOCK.modules.review.dto.ReviewDTO;
import com.kepg.BaseBallLOCK.modules.review.service.ReviewService;
import com.kepg.BaseBallLOCK.modules.review.summary.service.ReviewSummaryService;
import com.kepg.BaseBallLOCK.modules.user.domain.User;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RequestMapping("/review")
@RestController
public class ReviewRestController {
	
    private final ReviewService reviewService;
    private final ReviewSummaryService reviewSummaryService;
    private final ScheduleService scheduleService;

	
    @PostMapping("/save")
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
            result.put("success", false);
            result.put("message", "리뷰 저장 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    @GetMapping("/players")
    public ResponseEntity<Map<String, Object>> getPlayersBySchedule(@RequestParam int scheduleId, HttpSession session) {
    	
        int myTeamId = (Integer) session.getAttribute("favoriteTeamId");

        Map<String, Object> response = new HashMap<>();
        try {
            List<String> names = reviewService.findPlayerNamesByScheduleId(scheduleId, myTeamId);
            response.put("success", true);
            response.put("players", names != null ? names : new ArrayList<>());
        } catch (Exception e) {
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
                    System.out.println("리뷰 변경 -> 기존 요약 삭제: summaryId=" + existingSummary.getId() + ", weekStart=" + weekStart);
                    reviewSummaryService.deleteSummary(existingSummary.getId());
                }
            }
        } catch (Exception e) {
            System.err.println("요약 삭제 중 오류 발생: " + e.getMessage());
            // 요약 삭제가 실패해도 리뷰 저장은 계속 진행
        }
    }
}
