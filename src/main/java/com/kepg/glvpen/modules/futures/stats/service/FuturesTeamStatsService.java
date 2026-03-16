package com.kepg.glvpen.modules.futures.stats.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.glvpen.modules.futures.stats.domain.FuturesTeamStats;
import com.kepg.glvpen.modules.futures.stats.dto.FuturesTeamStatsDTO;
import com.kepg.glvpen.modules.futures.stats.repository.FuturesTeamStatsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class FuturesTeamStatsService {

    private final FuturesTeamStatsRepository repository;

    public void saveOrUpdate(FuturesTeamStatsDTO dto) {
        Optional<FuturesTeamStats> optional = repository.findByFullKey(
                dto.getTeamId(), dto.getSeason(), dto.getCategory(),
                dto.getLeague(), dto.getStatType());

        FuturesTeamStats stat = optional.orElseGet(() -> FuturesTeamStats.builder()
                .teamId(dto.getTeamId())
                .season(dto.getSeason())
                .league(dto.getLeague())
                .statType(dto.getStatType())
                .category(dto.getCategory())
                .build());

        stat.setValue(dto.getValue());
        stat.setRanking(dto.getRanking());
        repository.save(stat);
    }

    public void saveBatch(List<FuturesTeamStatsDTO> dtos) {
        for (FuturesTeamStatsDTO dto : dtos) {
            saveOrUpdate(dto);
        }
    }
}
