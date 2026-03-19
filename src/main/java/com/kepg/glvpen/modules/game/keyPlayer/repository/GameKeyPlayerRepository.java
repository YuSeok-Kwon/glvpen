package com.kepg.glvpen.modules.game.keyPlayer.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.kepg.glvpen.modules.game.keyPlayer.domain.GameKeyPlayer;

@Repository
public interface GameKeyPlayerRepository extends JpaRepository<GameKeyPlayer, Integer> {

    GameKeyPlayer findByScheduleIdAndPlayerTypeAndMetricAndRankingAndPlayerName(
            Integer scheduleId, String playerType, String metric, Integer ranking, String playerName);

    boolean existsByScheduleIdAndPlayerType(Integer scheduleId, String playerType);

    List<GameKeyPlayer> findByScheduleIdAndMetricOrderByRankingAsc(Integer scheduleId, String metric);

    List<GameKeyPlayer> findByScheduleIdOrderByMetricAscRankingAsc(Integer scheduleId);

    boolean existsByScheduleId(Integer scheduleId);
}
