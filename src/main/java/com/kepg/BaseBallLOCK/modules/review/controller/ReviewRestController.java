package com.kepg.BaseBallLOCK.modules.review.controller;

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

import com.kepg.BaseBallLOCK.modules.review.dto.ReviewDTO;
import com.kepg.BaseBallLOCK.modules.review.service.ReviewService;
import com.kepg.BaseBallLOCK.modules.user.domain.User;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RequestMapping("/review")
@RestController
public class ReviewRestController {
	
    private final ReviewService reviewService;

	
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
}
