package com.kepg.BaseBallLOCK.modules.gameMode.realMatch.repository;

import com.kepg.BaseBallLOCK.modules.gameMode.realMatch.domain.RealMatchResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 실제 경기 예측 결과 리포지토리
 */
@Repository
public interface RealMatchResultRepository extends JpaRepository<RealMatchResult, Long> {
    
    /**
     * 사용자별 예측 기록 조회
     */
    List<RealMatchResult> findByUserIdOrderByPredictionTimeDesc(Integer userId);
    
    /**
     * 특정 경기의 예측 기록 조회
     */
    List<RealMatchResult> findByScheduleId(Long scheduleId);
    
    /**
     * 사용자의 특정 경기 예측 기록 조회
     */
    RealMatchResult findByUserIdAndScheduleId(Integer userId, Long scheduleId);
    
    /**
     * 사용자의 총 예측 횟수 조회
     */
    @Query("SELECT COUNT(r) FROM RealMatchResult r WHERE r.userId = :userId")
    Long countByUserId(@Param("userId") Integer userId);
    
    /**
     * 사용자의 정확한 예측 횟수 조회
     */
    @Query("SELECT COUNT(r) FROM RealMatchResult r WHERE r.userId = :userId AND r.accuracy >= :minAccuracy")
    Long countCorrectPredictions(@Param("userId") Integer userId, @Param("minAccuracy") Integer minAccuracy);
    
    /**
     * 기간별 예측 기록 조회
     */
    @Query("SELECT r FROM RealMatchResult r WHERE r.userId = :userId AND r.predictionTime BETWEEN :startDate AND :endDate ORDER BY r.predictionTime DESC")
    List<RealMatchResult> findByUserIdAndPredictionTimeBetween(
            @Param("userId") Integer userId, 
            @Param("startDate") LocalDateTime startDate, 
            @Param("endDate") LocalDateTime endDate);
    
    /**
     * 상위 예측자 랭킹 조회
     */
    @Query("SELECT r.userId, AVG(r.accuracy) as avgAccuracy FROM RealMatchResult r GROUP BY r.userId ORDER BY avgAccuracy DESC")
    List<Object[]> findTopPredictors();
    
    /**
     * 처리되지 않은 예측 결과 조회 (배치 처리용)
     */
    List<RealMatchResult> findByIsProcessedFalse();
}
