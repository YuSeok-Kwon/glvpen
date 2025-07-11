package com.kepg.BaseBallLOCK.modules.review.summary.service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.kepg.BaseBallLOCK.modules.game.schedule.service.ScheduleService;
import com.kepg.BaseBallLOCK.modules.review.domain.Review;
import com.kepg.BaseBallLOCK.modules.review.domain.ReviewSummary;
import com.kepg.BaseBallLOCK.modules.review.repository.ReviewRepository;
import com.kepg.BaseBallLOCK.modules.review.summary.repository.ReviewSummaryRepository;
import com.kepg.BaseBallLOCK.modules.user.service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReviewSummaryService {
	
	private final UserService userService;
	private final ReviewRepository reviewRepository;
	private final ReviewSummaryRepository reviewSummaryRepository;
	private final AiSummaryService aiSummaryService;
	private final ScheduleService scheduleService;
	
	// 특정 주차의 ReviewSummary를 userId와 weekStartDate 기준으로 조회
	public ReviewSummary getWeeklySummaryByStartDate(int userId, LocalDate weekStartDate) {
        // LocalDate → java.sql.Date 변환
		Date sqlStartDate = Date.valueOf(weekStartDate);
	    List<ReviewSummary> list = reviewSummaryRepository.findByUserIdAndWeekStartDate(userId, sqlStartDate);
	    return list.isEmpty() ? null : list.get(0);
    }
	
	// 유저 특정 주차(weekStart~weekEnd) 동안 작성된 리뷰들을 기반으로 주간 요약 생성 또는 수정
	public ReviewSummary generateWeeklyReviewSummary(Integer userId, LocalDate weekStart) {
	    LocalDate weekEnd = weekStart.plusDays(6);

	    Date weekStartDate = Date.valueOf(weekStart);
	    Date weekEndDate = Date.valueOf(weekEnd);
	    
	    LocalDateTime startDateTime = weekStart.atStartOfDay();
	    LocalDateTime endDateTime = weekEnd.atTime(LocalTime.MAX);

	    List<Review> weeklyReviews = reviewRepository.findByUserIdAndScheduleMatchDateBetween(
	        userId, startDateTime, endDateTime
	    );

	    if (weeklyReviews == null || weeklyReviews.size() < 2) {
	        return null;
	    }

	    int win = 0;
	    int totalRating = 0;
	    int reviewCount = weeklyReviews.size();
	    Map<String, Integer> bestPlayerCount = new HashMap<>();
	    Map<String, Integer> worstPlayerCount = new HashMap<>();
	    Map<LocalDate, List<String>> dailyFeelings = new TreeMap<>();

	    for (Review review : weeklyReviews) {
	        int rating = review.getRating();
	        totalRating += rating;
	        if (rating >= 7) win++;

	        String best = review.getBestPlayer();
	        if (best != null && !best.isEmpty()) {
	            bestPlayerCount.put(best, bestPlayerCount.getOrDefault(best, 0) + 1);
	        }

	        String worst = review.getWorstPlayer();
	        if (worst != null && !worst.isEmpty()) {
	            worstPlayerCount.put(worst, worstPlayerCount.getOrDefault(worst, 0) + 1);
	        }

	        Timestamp matchTimestamp = scheduleService.findMatchDateById(review.getScheduleId());
	        LocalDate matchDate = matchTimestamp.toLocalDateTime().toLocalDate();
	        dailyFeelings.computeIfAbsent(matchDate, k -> new ArrayList<>());

	        String feeling = review.getFeelings();
	        if (feeling != null && !feeling.trim().isEmpty()) {
	            dailyFeelings.get(matchDate).add(feeling.trim());
	        }
	    }

	    int loss = reviewCount - win;
	    double averageRating = (double) totalRating / reviewCount;
	    String bestPlayers = formatPlayerCount(bestPlayerCount);
	    String worstPlayers = formatPlayerCount(worstPlayerCount);

	    String recordSummary = win + "승 " + loss + "패";
	    String feelingSummary = aiSummaryService.summarizeFeelings(dailyFeelings);
	    String reviewText = aiSummaryService.generateFullSummary(weeklyReviews);
	    String keywords = extractKeywords(weeklyReviews);

	    // 기존 summary가 있으면 UPDATE
	    ReviewSummary existing = getWeeklySummaryByStartDate(userId, weekStart);
	    Timestamp now = Timestamp.valueOf(LocalDateTime.now());

	    if (existing != null) {
	        existing.setRecordSummary(recordSummary);
	        existing.setFeelingSummary(feelingSummary);
	        existing.setBestMoment(bestPlayers);
	        existing.setWorstMoment(worstPlayers);
	        existing.setReviewText(reviewText);
	        existing.setKeywords(keywords);
	        existing.setTotalReviewCount(reviewCount);
	        existing.setAverageRating(averageRating);
	        existing.setUpdatedAt(now);
	        return reviewSummaryRepository.save(existing);  // UPDATE
	    }

	    // 없으면 새로 생성
	    ReviewSummary summary = new ReviewSummary();
	    summary.setUserId(userId);
	    summary.setSeason(2025);
	    summary.setWeekStartDate(weekStartDate);
	    summary.setWeekEndDate(weekEndDate);
	    summary.setRecordSummary(recordSummary);
	    summary.setFeelingSummary(feelingSummary);
	    summary.setBestMoment(bestPlayers);
	    summary.setWorstMoment(worstPlayers);
	    summary.setReviewText(reviewText);
	    summary.setKeywords(keywords);
	    summary.setTotalReviewCount(reviewCount);
	    summary.setAverageRating(averageRating);
	    summary.setCreatedAt(now);
	    summary.setUpdatedAt(now);

	    return reviewSummaryRepository.save(summary);  // INSERT
	}
	
	// 특정 주차(weekStartDate)에 대한 요약이 이미 존재하는지 여부 확인
	public boolean summaryExistsForWeek(int userId, LocalDate weekStartDate) {
	    Date sqlDate = Date.valueOf(weekStartDate);
	    return reviewSummaryRepository.existsByUserIdAndWeekStartDate(userId, sqlDate);
	}
	
	// "이름 (횟수회)" 형식으로 BEST/WORST 선수 통계를 문자열로 정리
	private String formatPlayerCount(Map<String, Integer> playerMap) {
	    Map<String, Integer> resolved = new HashMap<>();

	    for (Map.Entry<String, Integer> entry : playerMap.entrySet()) {
	        String raw = entry.getKey();
	        int count = entry.getValue();

	        // 쉼표로 분리된 이름을 각각 개별 선수로 집계
	        String[] names = raw.split(",");
	        for (String name : names) {
	            String trimmed = name.trim();
	            if (!trimmed.isEmpty()) {
	                resolved.put(trimmed, resolved.getOrDefault(trimmed, 0) + count);
	            }
	        }
	    }

	    List<Map.Entry<String, Integer>> entryList = new ArrayList<>(resolved.entrySet());

	    // 내림차순 정렬 (횟수가 많은 순)
	    for (int i = 0; i < entryList.size() - 1; i++) {
	        for (int j = i + 1; j < entryList.size(); j++) {
	            if (entryList.get(i).getValue() < entryList.get(j).getValue()) {
	                Map.Entry<String, Integer> temp = entryList.get(i);
	                entryList.set(i, entryList.get(j));
	                entryList.set(j, temp);
	            }
	        }
	    }

	    // "이름 (횟수회)" 형태로 변환
	    StringBuilder result = new StringBuilder();
	    for (int i = 0; i < entryList.size(); i++) {
	        Map.Entry<String, Integer> entry = entryList.get(i);
	        result.append(entry.getKey())
	              .append(" (")
	              .append(entry.getValue())
	              .append("회)");

	        if (i != entryList.size() - 1) {
	            result.append(", ");
	        }
	    }

	    return result.toString();
	}
	
	// 리뷰의 summary/feelings 내용을 바탕으로 키워드를 추출하여 "#키워드" 형식으로 반환
	public String extractKeywords(List<Review> reviews) {
	    // 전체 텍스트 결합
	    StringBuilder text = new StringBuilder();
	    for (Review review : reviews) {
	        if (review.getSummary() != null) {
	            text.append(review.getSummary()).append(" ");
	        }
	        if (review.getFeelings() != null) {
	            text.append(review.getFeelings()).append(" ");
	        }
	    }

	    // 명사 기반 키워드 추출 (예: 가장 많이 나온 단어)
	    Map<String, Integer> wordCount = new HashMap<>();
	    String[] words = text.toString().split("[^가-힣a-zA-Z0-9]");

	    for (String word : words) {
	        if (word.length() <= 1) continue; // 1글자 단어 제외

	        if (!wordCount.containsKey(word)) {
	            wordCount.put(word, 1);
	        } else {
	            wordCount.put(word, wordCount.get(word) + 1);
	        }
	    }

	    // 내림차순 정렬
	    List<Map.Entry<String, Integer>> sorted = new ArrayList<>(wordCount.entrySet());
	    for (int i = 0; i < sorted.size() - 1; i++) {
	        for (int j = i + 1; j < sorted.size(); j++) {
	            if (sorted.get(i).getValue() < sorted.get(j).getValue()) {
	                Map.Entry<String, Integer> temp = sorted.get(i);
	                sorted.set(i, sorted.get(j));
	                sorted.set(j, temp);
	            }
	        }
	    }

	    // 상위 키워드 5~10개 뽑기
	    int limit = Math.min(8, sorted.size());
	    StringBuilder keywords = new StringBuilder();
	    for (int i = 0; i < limit; i++) {
	        keywords.append("#").append(sorted.get(i).getKey());
	        if (i != limit - 1) {
	            keywords.append(" ");
	        }
	    }

	    return keywords.toString();
	}
	
	// 전체 유저의 주간 요약을 한 번에 생성 또는 갱신 (AI 요약 포함)
	public void generateWeeklyReviewSummaryForAllUsers(LocalDate weekStart) {
	    List<Integer> userIds = userService.findAllUserIds();

	    for (Integer userId : userIds) {
	        generateWeeklyReviewSummary(userId, weekStart);
	    }
	}
}
