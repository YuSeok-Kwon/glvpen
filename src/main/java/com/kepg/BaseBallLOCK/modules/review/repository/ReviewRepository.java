package com.kepg.BaseBallLOCK.modules.review.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kepg.BaseBallLOCK.modules.review.domain.Review;

public interface ReviewRepository extends JpaRepository<Review, Integer> {
    
	// createdAt 기준으로 유저의 리뷰 목록 조회 (특정 기간)
	List<Review> findByUserIdAndCreatedAtBetween(int userId, LocalDateTime start, LocalDateTime end);

	// matchDate 기준으로 유저의 리뷰 목록 조회 (특정 기간)
	@Query(value = """
		SELECT r.* FROM review r
		JOIN schedule s ON r.scheduleId = s.id
		WHERE r.userId = :userId
		AND s.matchDate BETWEEN :start AND :end
	""", nativeQuery = true)
	List<Review> findByUserIdAndScheduleMatchDateBetween(
	    @Param("userId") int userId,
	    @Param("start") LocalDateTime start,
	    @Param("end") LocalDateTime end
	);
	
	// 특정 유저의 matchDate에 해당하는 리뷰 1개 조회
	@Query(value = """
		SELECT r.* FROM review r
		JOIN schedule s ON r.scheduleId = s.id
		WHERE r.userId = :userId
		AND DATE(s.matchDate) = :matchDate
	""", nativeQuery = true)
	Optional<Review> findByUserIdAndMatchDate(
	    @Param("userId") int userId,
	    @Param("matchDate") LocalDate matchDate
	);
	
	// 유저가 특정 경기에 대해 작성한 모든 리뷰 조회
	List<Review> findAllByUserIdAndScheduleId(int userId, int scheduleId);
	
}
