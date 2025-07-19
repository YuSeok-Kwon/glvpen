package com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.repository;

import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.domain.CustomPlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("customPlayerModeRepository")
public interface CustomPlayerRepository extends JpaRepository<CustomPlayer, Long> {
    
    /**
     * 히터 모드 플레이어 조회
     */
    @Query("SELECT p FROM CustomPlayer p WHERE p.mode = 'HITTER'")
    List<CustomPlayer> findHitterPlayers();
    
    /**
     * 포지션별 히터 모드 플레이어 조회
     */
    @Query("SELECT p FROM CustomPlayer p WHERE p.mode = 'HITTER' AND p.position = :position")
    List<CustomPlayer> findHitterPlayersByPosition(@Param("position") String position);
    
    /**
     * RPG 모드 플레이어 조회
     */
    @Query("SELECT p FROM CustomPlayer p WHERE p.mode = 'RPG'")
    List<CustomPlayer> findRpgPlayers();
    
    /**
     * 사용자별 RPG 모드 플레이어 조회
     */
    @Query("SELECT p FROM CustomPlayer p WHERE p.mode = 'RPG' AND p.userId = :userId")
    List<CustomPlayer> findRpgPlayersByUserId(@Param("userId") Integer userId);
    
    /**
     * 사용자별 커스텀 선수 목록 조회 (RPG 모드)
     */
    @Query("SELECT p FROM CustomPlayer p WHERE p.mode = 'RPG' AND p.userId = :userId ORDER BY p.level DESC, p.createdAt DESC")
    List<CustomPlayer> findByUserIdOrderByLevelDescCreatedAtDesc(@Param("userId") Integer userId);
}