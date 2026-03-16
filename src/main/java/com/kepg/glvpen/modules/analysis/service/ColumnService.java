package com.kepg.glvpen.modules.analysis.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.glvpen.modules.analysis.domain.AnalysisColumn;
import com.kepg.glvpen.modules.analysis.repository.AnalysisColumnRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ColumnService {

    private final AnalysisColumnRepository columnRepository;

    /**
     * 분석 컬럼 목록 조회 (카테고리별, 페이징)
     */
    public Page<AnalysisColumn> getColumns(String category, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (category != null && !category.isBlank()) {
            return columnRepository.findByCategoryOrderByPublishDateDesc(category, pageable);
        }
        return columnRepository.findAllByOrderByPublishDateDesc(pageable);
    }

    /**
     * 분석 컬럼 상세 조회 (조회수 증가)
     */
    @Transactional
    public AnalysisColumn getColumnDetail(Long id) {
        AnalysisColumn column = columnRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("분석 컬럼을 찾을 수 없습니다: " + id));
        column.setViewCount(column.getViewCount() + 1);
        return columnRepository.save(column);
    }

    /**
     * 분석 컬럼 저장 (수동 작성)
     */
    @Transactional
    public AnalysisColumn saveColumn(AnalysisColumn column) {
        return columnRepository.save(column);
    }

    /**
     * 분석 컬럼 삭제
     */
    @Transactional
    public void deleteColumn(Long id) {
        columnRepository.deleteById(id);
    }

    /**
     * 피처드 컬럼 반환 (없으면 최신 1건 fallback)
     */
    public AnalysisColumn getFeaturedColumn() {
        return columnRepository.findFirstByFeaturedTrueOrderByPublishDateDesc()
                .orElseGet(() -> {
                    Page<AnalysisColumn> latest = columnRepository.findAllByOrderByPublishDateDesc(PageRequest.of(0, 1));
                    return latest.hasContent() ? latest.getContent().get(0) : null;
                });
    }

    /**
     * 이전 컬럼 (상세 페이지 네비게이션)
     */
    public Optional<AnalysisColumn> getPreviousColumn(Long id) {
        return columnRepository.findFirstByIdLessThanOrderByIdDesc(id);
    }

    /**
     * 다음 컬럼 (상세 페이지 네비게이션)
     */
    public Optional<AnalysisColumn> getNextColumn(Long id) {
        return columnRepository.findFirstByIdGreaterThanOrderByIdAsc(id);
    }

    /**
     * 관련 컬럼 추천 (같은 카테고리, 최신순 3건)
     */
    public List<AnalysisColumn> getRelatedColumns(AnalysisColumn column) {
        return columnRepository.findRelatedColumns(
                column.getCategory(), column.getId(), PageRequest.of(0, 3));
    }

    /**
     * HTML 태그 제거 후 200자 요약 추출
     */
    public static String extractSummary(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String text = content.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
        return text.length() > 200 ? text.substring(0, 200) : text;
    }
}
