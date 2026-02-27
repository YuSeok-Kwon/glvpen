package com.kepg.BaseBallLOCK.modules.player.stats.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.BaseBallLOCK.modules.player.stats.domain.DefenseStats;
import com.kepg.BaseBallLOCK.modules.player.stats.repository.DefenseStatsRepository;
import com.kepg.BaseBallLOCK.modules.player.stats.statsDto.DefenseStatsDTO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class DefenseStatsService {

    private final DefenseStatsRepository defenseStatsRepository;

    public void saveDefenseStats(DefenseStatsDTO dto) {
        String series = dto.getSeries() != null ? dto.getSeries() : "0";
        String position = dto.getPosition() != null ? dto.getPosition() : "";

        Optional<DefenseStats> optional = defenseStatsRepository.findByFullKey(
                dto.getPlayerId(), dto.getSeason(), dto.getCategory(), series, position);

        optional.ifPresent(existing -> {
            defenseStatsRepository.delete(existing);
            defenseStatsRepository.flush();
        });

        DefenseStats entity = DefenseStats.builder()
                .playerId(dto.getPlayerId())
                .season(dto.getSeason())
                .series(series)
                .position(position)
                .category(dto.getCategory())
                .value(dto.getValue())
                .ranking(dto.getRanking())
                .build();

        defenseStatsRepository.save(entity);
    }
}
