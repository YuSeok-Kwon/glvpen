package com.kepg.BaseBallLOCK.modules.gameMode.matchPredictionMode.repository;

import com.kepg.BaseBallLOCK.modules.gameMode.matchPredictionMode.domain.RealMatchResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * RealMatch 게임 결과 레포지토리
 */
@Repository
public interface RealMatchResultRepository extends JpaRepository<RealMatchResult, Long> {
    
    /**
     * 사용자별 게임 결과 조회
     */
    List<RealMatchResult> findByUserIdOrderByPlayedAtDesc(Integer userId);
    
    /**
     * 특정 경기 일정의 모든 예측 결과 조회
     */
    List<RealMatchResult> findByScheduleIdOrderByPredictionAccuracyDesc(Long scheduleId);
    
    /**
     * 사용자의 특정 경기 예측 결과 조회
     */
    Optional<RealMatchResult> findByUserIdAndScheduleId(Integer userId, Long scheduleId);
    
    /**
     * 사용자의 총 게임 수 조회
     */
    @Query("SELECT COUNT(r) FROM RealMatchResult r WHERE r.userId = :userId")
    Long countTotalGamesByUserId(@Param("userId") Integer userId);
    
    /**
     * 사용자의 정확한 예측 수 조회 (승리팀 맞춤)
     */
    @Query("SELECT COUNT(r) FROM RealMatchResult r WHERE r.userId = :userId AND r.winnerCorrect = true")
    Long countCorrectPredictionsByUserId(@Param("userId") Integer userId);
    
    /**
     * 사용자의 정확한 스코어 예측 수 조회
     */
    @Query("SELECT COUNT(r) FROM RealMatchResult r WHERE r.userId = :userId AND r.scoreExactMatch = true")
    Long countExactScorePredictionsByUserId(@Param("userId") Integer userId);
    
    /**
     * 사용자의 총 획득 포인트 조회
     */
    @Query("SELECT COALESCE(SUM(r.earnedPoints), 0) FROM RealMatchResult r WHERE r.userId = :userId")
    Integer getTotalEarnedPointsByUserId(@Param("userId") Integer userId);
    
    /**
     * 사용자의 평균 예측 정확도 조회
     */
    @Query("SELECT COALESCE(AVG(r.predictionAccuracy), 0.0) FROM RealMatchResult r WHERE r.userId = :userId")
    Double getAveragePredictionAccuracyByUserId(@Param("userId") Integer userId);
    
    /**
     * 특정 기간 내 게임 결과 조회
     */
    List<RealMatchResult> findByUserIdAndPlayedAtBetweenOrderByPlayedAtDesc(
        Integer userId, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * 시즌별 게임 결과 조회
     */
    List<RealMatchResult> findByUserIdAndSeasonOrderByPlayedAtDesc(Integer userId, String season);
    
    /**
     * 팀별 예측 성과 조회
     */
    @Query("SELECT r FROM RealMatchResult r WHERE r.userId = :userId AND r.userTeamId = :teamId ORDER BY r.playedAt DESC")
    List<RealMatchResult> findByUserIdAndTeamId(@Param("userId") Integer userId, @Param("teamId") Integer teamId);
    
    /**
     * 최고 예측 정확도 순 리더보드
     */
    @Query("SELECT r.userId, AVG(r.predictionAccuracy) as avgAccuracy FROM RealMatchResult r " +
           "GROUP BY r.userId ORDER BY avgAccuracy DESC")
    List<Object[]> getLeaderboardByAccuracy();
    
    /**
     * 총 포인트 순 리더보드
     */
    @Query("SELECT r.userId, SUM(r.earnedPoints) as totalPoints FROM RealMatchResult r " +
           "GROUP BY r.userId ORDER BY totalPoints DESC")
    List<Object[]> getLeaderboardByPoints();
}
