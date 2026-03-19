package com.kepg.glvpen.modules.analysis.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Python 배치 분석 결과 저장 엔티티.
 * Python이 기술통계 + 가설검정 + Chart.js JSON을 생성하여 저장하고,
 * Java(AiColumnGeneratorService)가 읽어서 Gemini 프롬프트에 포함시키는 중간 테이블.
 *
 * V7 마이그레이션: 서브토픽, 비교 유형, 앵커 값 추가로 다차원 분석 지원
 */
@Entity
@Table(name = "analysis_result",
       uniqueConstraints = @UniqueConstraint(columnNames = {"topic", "sub_topic", "season", "comparison_type", "anchor_value"}))
@Getter @Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 주제 인덱스 (0~14, ROTATING_TOPICS 배열 인덱스와 매핑) */
    @Column(nullable = false)
    private Integer topic;

    /** 서브토픽 (batter/pitcher/default) - V7 추가 */
    @Column(name = "sub_topic", nullable = false, length = 50)
    @Builder.Default
    private String subTopic = "default";

    /** 분석 대상 시즌 */
    @Column(nullable = false)
    private Integer season;

    /** 비교 유형 (none/yearly/monthly) - V7 추가 */
    @Column(name = "comparison_type", nullable = false, length = 20)
    @Builder.Default
    private String comparisonType = "none";

    /** 비교 앵커 값 (2025, 06 등) - V7 추가 */
    @Column(name = "anchor_value", nullable = false, length = 20)
    @Builder.Default
    private String anchorValue = "";

    /** 기술통계 결과 (JSON) - 평균, 표준편차, 분위수 등 */
    @Lob
    @Column(name = "stats_json", columnDefinition = "LONGTEXT", nullable = false)
    private String statsJson;

    /** Chart.js 호환 차트 데이터 배열 (JSON) - 다중 차트 지원 */
    @Lob
    @Column(name = "chart_json", columnDefinition = "LONGTEXT", nullable = false)
    private String chartJson;

    /** 통계적 인사이트 텍스트 - Gemini 프롬프트에 직접 삽입 */
    @Lob
    @Column(name = "insight_text", columnDefinition = "LONGTEXT", nullable = false)
    private String insightText;

    /** 가설검정 결과 (JSON) - t-test, ANOVA, 카이제곱 등 검정 결과 */
    @Lob
    @Column(name = "hypothesis_results", columnDefinition = "LONGTEXT")
    private String hypothesisResults;

    /** 생성 시각 */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** 마지막 수정 시각 */
    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
