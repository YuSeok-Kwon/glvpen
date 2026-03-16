package com.kepg.glvpen.modules.futures.stats.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.glvpen.modules.futures.stats.domain.FuturesBatterStats;
import com.kepg.glvpen.modules.futures.stats.dto.FuturesBatterStatsDTO;
import com.kepg.glvpen.modules.futures.stats.repository.FuturesBatterStatsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class FuturesBatterStatsService {

    private final FuturesBatterStatsRepository repository;

    public void saveBatterStats(FuturesBatterStatsDTO dto) {
        Optional<FuturesBatterStats> optional = repository.findByFullKey(
                dto.getPlayerId(), dto.getSeason(), dto.getCategory(), dto.getLeague());

        optional.ifPresent(existing -> {
            repository.delete(existing);
            repository.flush();
        });

        FuturesBatterStats entity = FuturesBatterStats.builder()
                .playerId(dto.getPlayerId())
                .season(dto.getSeason())
                .league(dto.getLeague())
                .category(dto.getCategory())
                .value(dto.getValue())
                .ranking(dto.getRanking())
                .build();

        repository.save(entity);
    }

    public void saveBatch(List<FuturesBatterStatsDTO> dtos) {
        for (FuturesBatterStatsDTO dto : dtos) {
            saveBatterStats(dto);
        }
    }
}
