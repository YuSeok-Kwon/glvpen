package com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.repository;

import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.domain.CustomPlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * CustomPlayer 레포지토리
 */
@Repository
public interface CustomPlayerRepository extends JpaRepository<CustomPlayer, Long> {
    
    /**
     * 사용자별 커스텀 선수 목록 조회
     */
    List<CustomPlayer> findByUserIdOrderByLevelDescCreatedAtDesc(Integer userId);
    
    /**
     * 활성화된 커스텀 선수 목록 조회
     */
    @Query("SELECT c FROM CustomPlayer c WHERE c.userId = :userId AND " +
           "(c.specialTraits IS NULL OR c.specialTraits NOT LIKE '%\"active\":false%')")
    List<CustomPlayer> findActiveCustomPlayersByUserId(@Param("userId") Integer userId);
    
    /**
     * 사용자의 즐겨찾기 선수 목록
     */
    @Query("SELECT c FROM CustomPlayer c WHERE c.userId = :userId AND " +
           "c.specialTraits LIKE '%\"favorite\":true%'")
    List<CustomPlayer> findFavoritePlayersByUserId(@Param("userId") Integer userId);
    
    /**
     * 특정 레벨 이상의 선수 조회
     */
    List<CustomPlayer> findByUserIdAndLevelGreaterThanEqualOrderByLevelDesc(Integer userId, Integer minLevel);
    
    /**
     * 사용자의 총 커스텀 선수 수
     */
    Long countByUserId(Integer userId);
    
    /**
     * 사용자의 최고 레벨 선수
     */
    Optional<CustomPlayer> findTopByUserIdOrderByLevelDescExperienceDesc(Integer userId);
    
    /**
     * 포지션별 선수 조회
     */
    @Query("SELECT c FROM CustomPlayer c WHERE c.userId = :userId AND " +
           "c.specialTraits LIKE %:position%")
    List<CustomPlayer> findByUserIdAndPosition(@Param("userId") Integer userId, 
                                             @Param("position") String position);
    
    /**
     * 총 능력치 상위 선수들
     */
    @Query("SELECT c FROM CustomPlayer c WHERE c.userId = :userId " +
           "ORDER BY (c.power + c.contact + c.speed + c.fielding + c.arm) DESC")
    List<CustomPlayer> findTopPlayersByTotalStats(@Param("userId") Integer userId);
    
    /**
     * 특정 능력치가 높은 선수들
     */
    @Query("SELECT c FROM CustomPlayer c WHERE c.userId = :userId AND c.power >= :minPower")
    List<CustomPlayer> findByUserIdAndPowerGreaterThanEqual(@Param("userId") Integer userId, 
                                                           @Param("minPower") Integer minPower);
    
    @Query("SELECT c FROM CustomPlayer c WHERE c.userId = :userId AND c.contact >= :minContact")
    List<CustomPlayer> findByUserIdAndContactGreaterThanEqual(@Param("userId") Integer userId, 
                                                             @Param("minContact") Integer minContact);
    
    @Query("SELECT c FROM CustomPlayer c WHERE c.userId = :userId AND c.speed >= :minSpeed")
    List<CustomPlayer> findByUserIdAndSpeedGreaterThanEqual(@Param("userId") Integer userId, 
                                                           @Param("minSpeed") Integer minSpeed);
    
    /**
     * 레벨별 통계
     */
    @Query("SELECT c.level, COUNT(c) FROM CustomPlayer c WHERE c.userId = :userId GROUP BY c.level")
    List<Object[]> getLevelDistributionByUserId(@Param("userId") Integer userId);
    
    /**
     * 평균 능력치 조회
     */
    @Query("SELECT AVG(c.power), AVG(c.contact), AVG(c.speed), AVG(c.fielding), AVG(c.arm) " +
           "FROM CustomPlayer c WHERE c.userId = :userId")
    Object[] getAverageStatsByUserId(@Param("userId") Integer userId);
}
