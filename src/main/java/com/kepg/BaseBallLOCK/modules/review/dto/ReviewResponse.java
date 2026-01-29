package com.kepg.BaseBallLOCK.modules.review.dto;

import java.time.LocalDateTime;

import com.kepg.BaseBallLOCK.modules.review.domain.Review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Review 엔티티를 클라이언트에 전달하기 위한 Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {

    private Integer id;
    private Integer userId;
    private Integer scheduleId;
    private String summary;
    private String bestPlayer;
    private String worstPlayer;
    private String feelings;
    private Integer rating;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 추가 정보 (조인 데이터)
    private String homeTeamName;
    private String awayTeamName;
    private Integer homeTeamScore;
    private Integer awayTeamScore;
    private LocalDateTime matchDate;

    /**
     * Review Entity를 ReviewResponse DTO로 변환
     */
    public static ReviewResponse from(Review review) {
        return ReviewResponse.builder()
            .id(review.getId())
            .userId(review.getUserId())
            .scheduleId(review.getScheduleId())
            .summary(review.getSummary())
            .bestPlayer(review.getBestPlayer())
            .worstPlayer(review.getWorstPlayer())
            .feelings(review.getFeelings())
            .rating(review.getRating())
            .createdAt(review.getCreatedAt() != null ?
                review.getCreatedAt().toLocalDateTime() : null)
            .updatedAt(review.getUpdatedAt() != null ?
                review.getUpdatedAt().toLocalDateTime() : null)
            .build();
    }
}
