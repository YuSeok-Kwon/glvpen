package com.kepg.glvpen.modules.gameMode.simulationMode.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import com.kepg.glvpen.modules.gameMode.simulationMode.domain.UserCard;

public interface UserCardRepository extends JpaRepository<UserCard, Integer> {
    
    // 유저 보유 카드 전체 조회
    List<UserCard> findByUserId(Integer userId);

    // 특정 유저가 특정 시즌에 보유한 카드 중 포지션 필터
    List<UserCard> findByUserIdAndSeasonAndPosition(Integer userId, Integer season, String position);

    boolean existsByUserIdAndPlayerIdAndSeason(Integer userId, Integer playerId, Integer season);
    
    @Modifying
    void deleteByUserIdAndPlayerIdAndSeason(Integer userId, Integer playerId, Integer season);

}