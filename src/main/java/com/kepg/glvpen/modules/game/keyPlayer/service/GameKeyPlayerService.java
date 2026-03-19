package com.kepg.glvpen.modules.game.keyPlayer.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.glvpen.modules.game.keyPlayer.domain.GameKeyPlayer;
import com.kepg.glvpen.modules.game.keyPlayer.repository.GameKeyPlayerRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GameKeyPlayerService {

    private final GameKeyPlayerRepository gameKeyPlayerRepository;

    @Transactional
    public void saveOrUpdate(GameKeyPlayer keyPlayer) {
        GameKeyPlayer existing = gameKeyPlayerRepository.findByScheduleIdAndPlayerTypeAndMetricAndRankingAndPlayerName(
                keyPlayer.getScheduleId(), keyPlayer.getPlayerType(),
                keyPlayer.getMetric(), keyPlayer.getRanking(), keyPlayer.getPlayerName());

        if (existing != null) {
            existing.setKboGameId(keyPlayer.getKboGameId());
            existing.setSeason(keyPlayer.getSeason());
            existing.setTeamId(keyPlayer.getTeamId());
            existing.setRecordInfo(keyPlayer.getRecordInfo());
            gameKeyPlayerRepository.save(existing);
        } else {
            gameKeyPlayerRepository.save(keyPlayer);
        }
    }

    public boolean existsByScheduleIdAndPlayerType(Integer scheduleId, String playerType) {
        return gameKeyPlayerRepository.existsByScheduleIdAndPlayerType(scheduleId, playerType);
    }

    public List<GameKeyPlayer> findByMetric(Integer scheduleId, String metric) {
        return gameKeyPlayerRepository.findByScheduleIdAndMetricOrderByRankingAsc(scheduleId, metric);
    }

    public List<GameKeyPlayer> findAllByScheduleId(Integer scheduleId) {
        return gameKeyPlayerRepository.findByScheduleIdOrderByMetricAscRankingAsc(scheduleId);
    }

    /**
     * scheduleId의 전체 키플레이어를 metric별로 그룹핑하여 반환
     */
    public java.util.Map<String, List<GameKeyPlayer>> findAllGroupedByMetric(Integer scheduleId) {
        List<GameKeyPlayer> all = findAllByScheduleId(scheduleId);
        return all.stream().collect(java.util.stream.Collectors.groupingBy(GameKeyPlayer::getMetric));
    }
}
