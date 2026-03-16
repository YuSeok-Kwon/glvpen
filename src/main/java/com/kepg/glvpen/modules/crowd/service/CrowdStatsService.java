package com.kepg.glvpen.modules.crowd.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.glvpen.modules.crowd.domain.CrowdStats;
import com.kepg.glvpen.modules.crowd.dto.CrowdStatsDTO;
import com.kepg.glvpen.modules.crowd.repository.CrowdStatsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrowdStatsService {

    private final CrowdStatsRepository crowdStatsRepository;

    @Transactional
    public void saveOrUpdate(CrowdStatsDTO dto) {
        Optional<CrowdStats> optional = crowdStatsRepository
                .findByGameDateAndHomeTeamIdAndAwayTeamId(dto.getGameDate(), dto.getHomeTeamId(), dto.getAwayTeamId());

        CrowdStats entity;
        if (optional.isPresent()) {
            entity = optional.get();
            entity.setSeason(dto.getSeason());
            entity.setDayOfWeek(dto.getDayOfWeek());
            entity.setStadium(dto.getStadium());
            entity.setCrowd(dto.getCrowd());
        } else {
            entity = CrowdStats.builder()
                    .season(dto.getSeason())
                    .gameDate(dto.getGameDate())
                    .dayOfWeek(dto.getDayOfWeek())
                    .homeTeamId(dto.getHomeTeamId())
                    .awayTeamId(dto.getAwayTeamId())
                    .stadium(dto.getStadium())
                    .crowd(dto.getCrowd())
                    .build();
        }

        crowdStatsRepository.save(entity);
    }

    public List<CrowdStats> getBySeason(int season) {
        return crowdStatsRepository.findBySeasonOrderByGameDateAsc(season);
    }
}
