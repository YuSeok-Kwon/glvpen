package com.kepg.glvpen.modules.analysis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kepg.glvpen.modules.analysis.domain.AnalysisResult;

/**
 * Python 배치 분석 결과 조회 Repository.
 * AiColumnGeneratorService에서 Gemini 프롬프트 구성 시 사용.
 *
 * V7 확장: sub_topic, comparison_type, anchor_value 지원
 */
public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {

    /** 특정 주제 + 시즌의 기본 분석 결과 조회 (역호환) */
    Optional<AnalysisResult> findByTopicAndSeason(Integer topic, Integer season);

    /** 특정 주제 + 서브토픽 + 시즌의 분석 결과 조회 */
    Optional<AnalysisResult> findByTopicAndSubTopicAndSeasonAndComparisonType(
            Integer topic, String subTopic, Integer season, String comparisonType);

    /** 특정 주제 + 서브토픽 + 시즌 + 비교유형 + 앵커값 조회 (완전 매칭) */
    Optional<AnalysisResult> findByTopicAndSubTopicAndSeasonAndComparisonTypeAndAnchorValue(
            Integer topic, String subTopic, Integer season,
            String comparisonType, String anchorValue);

    /** 특정 시즌의 모든 주제 분석 결과 조회 (주제 순서대로) */
    List<AnalysisResult> findBySeasonOrderByTopicAsc(Integer season);

    /** 특정 시즌의 기본(비교 아닌) 분석 결과만 조회 */
    List<AnalysisResult> findBySeasonAndComparisonTypeOrderByTopicAsc(
            Integer season, String comparisonType);

    /** 특정 주제의 시즌별 분석 결과 조회 (최신 시즌 우선) */
    List<AnalysisResult> findByTopicOrderBySeasonDesc(Integer topic);

    /** 특정 주제 + 서브토픽의 모든 비교 분석 결과 조회 */
    List<AnalysisResult> findByTopicAndSubTopicAndSeasonAndComparisonTypeNot(
            Integer topic, String subTopic, Integer season, String comparisonType);

    /** 특정 주제의 서브토픽별 결과 조회 */
    List<AnalysisResult> findByTopicAndSeasonOrderBySubTopicAsc(Integer topic, Integer season);
}
