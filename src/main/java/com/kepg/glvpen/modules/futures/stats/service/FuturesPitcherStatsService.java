package com.kepg.glvpen.modules.futures.stats.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.glvpen.modules.futures.stats.domain.FuturesPitcherStats;
import com.kepg.glvpen.modules.futures.stats.dto.FuturesPitcherStatsDTO;
import com.kepg.glvpen.modules.futures.stats.repository.FuturesPitcherStatsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class FuturesPitcherStatsService {

    private final FuturesPitcherStatsRepository repository;

    public void savePitcherStats(FuturesPitcherStatsDTO dto) {
        Optional<FuturesPitcherStats> optional = repository.findByFullKey(
                dto.getPlayerId(), dto.getSeason(), dto.getCategory(), dto.getLeague());

        optional.ifPresent(existing -> {
            repository.delete(existing);
            repository.flush();
        });

        FuturesPitcherStats entity = FuturesPitcherStats.builder()
                .playerId(dto.getPlayerId())
                .season(dto.getSeason())
                .league(dto.getLeague())
                .category(dto.getCategory())
                .value(dto.getValue())
                .ranking(dto.getRanking())
                .build();

        repository.save(entity);
    }

    public void saveBatch(List<FuturesPitcherStatsDTO> dtos) {
        for (FuturesPitcherStatsDTO dto : dtos) {
            savePitcherStats(dto);
        }
    }
}
