package com.kepg.glvpen.modules.analysis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kepg.glvpen.modules.analysis.domain.AnalysisColumn;

public interface AnalysisColumnRepository extends JpaRepository<AnalysisColumn, Long> {

    Page<AnalysisColumn> findByCategoryOrderByPublishDateDesc(String category, Pageable pageable);

    Page<AnalysisColumn> findAllByOrderByPublishDateDesc(Pageable pageable);

    List<AnalysisColumn> findTop5ByOrderByPublishDateDesc();

    List<AnalysisColumn> findBySeasonOrderByPublishDateDesc(Integer season);

    List<AnalysisColumn> findByRelatedPlayerIdOrderByPublishDateDesc(Integer playerId);

    List<AnalysisColumn> findByRelatedTeamIdOrderByPublishDateDesc(Integer teamId);

    // 피처드 컬럼 조회 (가장 최근 featured=true인 컬럼 1건)
    Optional<AnalysisColumn> findFirstByFeaturedTrueOrderByPublishDateDesc();

    // 이전 컬럼 (현재 ID보다 작은 것 중 가장 큰 것)
    Optional<AnalysisColumn> findFirstByIdLessThanOrderByIdDesc(Long id);

    // 다음 컬럼 (현재 ID보다 큰 것 중 가장 작은 것)
    Optional<AnalysisColumn> findFirstByIdGreaterThanOrderByIdAsc(Long id);

    // 같은 카테고리의 관련 컬럼 (현재 컬럼 제외, 최신순 상위 3건)
    @Query("SELECT c FROM AnalysisColumn c WHERE c.category = :category AND c.id <> :excludeId ORDER BY c.publishDate DESC")
    List<AnalysisColumn> findRelatedColumns(@Param("category") String category, @Param("excludeId") Long excludeId, Pageable pageable);
}
