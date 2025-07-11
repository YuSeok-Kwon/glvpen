package com.kepg.BaseBallLOCK.modules.review.summary.repository;

import java.sql.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kepg.BaseBallLOCK.modules.review.domain.ReviewSummary;

public interface ReviewSummaryRepository extends JpaRepository<ReviewSummary, Integer> {
	
	// 특정 유저의 가장 최근 주간 요약(weekEndDate 기준 최신)을 조회
    Optional<ReviewSummary> findTopByUserIdOrderByWeekEndDateDesc(int userId);
    
    // 특정 유저의 특정 주(weekStartDate)에 해당하는 요약 리스트를 조회
    List<ReviewSummary> findByUserIdAndWeekStartDate(int userId, Date weekStartDate);
    
    // 특정 유저가 특정 주차에 대해 이미 요약을 작성했는지 여부를 확인
    boolean existsByUserIdAndWeekStartDate(int userId, Date weekStartDate);

}