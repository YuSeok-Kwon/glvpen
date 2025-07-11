package com.kepg.BaseBallLOCK.modules.gameMode.quickGameMode.repository;

import com.kepg.BaseBallLOCK.modules.gameMode.quickGameMode.domain.ClassicGameResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClassicGameResultRepository extends JpaRepository<ClassicGameResult, Long> {

    // 사용자별 게임 결과 조회 (최신순)
    List<ClassicGameResult> findByUserIdOrderByCreatedAtDesc(Integer userId);

    // 사용자별 난이도별 게임 결과 조회
    List<ClassicGameResult> findByUserIdAndDifficultyOrderByCreatedAtDesc(Integer userId, String difficulty);

    // 사용자별 승률 계산
    @Query("SELECT COUNT(c) FROM ClassicGameResult c WHERE c.userId = :userId AND c.isWin = true")
    Integer countWinsByUserId(@Param("userId") Integer userId);

    @Query("SELECT COUNT(c) FROM ClassicGameResult c WHERE c.userId = :userId")
    Integer countTotalGamesByUserId(@Param("userId") Integer userId);

    // 사용자별 난이도별 통계
    @Query("SELECT c.difficulty, COUNT(c), SUM(CASE WHEN c.isWin = true THEN 1 ELSE 0 END) " +
           "FROM ClassicGameResult c WHERE c.userId = :userId GROUP BY c.difficulty")
    List<Object[]> getStatsByUserIdAndDifficulty(@Param("userId") Integer userId);

    // 최고 점수 기록
    @Query("SELECT MAX(c.userScore) FROM ClassicGameResult c WHERE c.userId = :userId AND c.difficulty = :difficulty")
    Integer getHighestScoreByUserIdAndDifficulty(@Param("userId") Integer userId, @Param("difficulty") String difficulty);

    // 총 획득 경험치
    @Query("SELECT SUM(c.experienceGained) FROM ClassicGameResult c WHERE c.userId = :userId")
    Integer getTotalExperienceByUserId(@Param("userId") Integer userId);
}
