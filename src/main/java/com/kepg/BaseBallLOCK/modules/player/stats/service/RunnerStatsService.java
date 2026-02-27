package com.kepg.BaseBallLOCK.modules.player.stats.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.BaseBallLOCK.modules.player.stats.domain.RunnerStats;
import com.kepg.BaseBallLOCK.modules.player.stats.repository.RunnerStatsRepository;
import com.kepg.BaseBallLOCK.modules.player.stats.statsDto.RunnerStatsDTO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class RunnerStatsService {

    private final RunnerStatsRepository runnerStatsRepository;

    public void saveRunnerStats(RunnerStatsDTO dto) {
        String series = dto.getSeries() != null ? dto.getSeries() : "0";

        Optional<RunnerStats> optional = runnerStatsRepository.findByFullKey(
                dto.getPlayerId(), dto.getSeason(), dto.getCategory(), series);

        optional.ifPresent(existing -> {
            runnerStatsRepository.delete(existing);
            runnerStatsRepository.flush();
        });

        RunnerStats entity = RunnerStats.builder()
                .playerId(dto.getPlayerId())
                .season(dto.getSeason())
                .series(series)
                .category(dto.getCategory())
                .value(dto.getValue())
                .ranking(dto.getRanking())
                .build();

        runnerStatsRepository.save(entity);
    }
}
