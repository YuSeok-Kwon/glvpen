package com.kepg.BaseBallLOCK.modules.review.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.sql.Date;
import java.sql.Timestamp;

@Entity
@Getter
@Setter
@Table(name = "reviewSummary")
public class ReviewSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private int userId;

    private int season;

    private Date weekStartDate;

    private Date weekEndDate;

    private String recordSummary;  // 예: "1승 6패"

    @Lob
    private String feelingSummary;  // 감정 흐름 요약 텍스트

    @Lob
    private String bestMoment;      // Best Player: "오명진 (2회), 김택연 (1회)"

    @Lob
    private String worstMoment;     // Worst Player

    @Lob
    private String reviewText;      // 전체 요약 멘트

    private String keywords;        // "#멘탈탈탈 #해체 #감독교체"

    private int totalReviewCount;

    private double averageRating;

    private Timestamp createdAt;

    private Timestamp updatedAt;
}