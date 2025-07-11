package com.kepg.BaseBallLOCK.modules.game.highlight.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.BaseBallLOCK.modules.game.highlight.doamin.GameHighlight;
import com.kepg.BaseBallLOCK.modules.game.highlight.dto.GameHighlightDTO;
import com.kepg.BaseBallLOCK.modules.game.highlight.repository.GameHighlightRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GameHighlightService {

    private final GameHighlightRepository gameHighlightRepository;

	 // 주어진 scheduleId의 하이라이트 존재 여부 확인 후 있으면 업데이트, 없으면 새로 저장
    @Transactional
    public void saveOrUpdate(GameHighlightDTO dto) {
        GameHighlight existing = gameHighlightRepository.findByScheduleIdAndRanking(dto.getScheduleId(), dto.getRanking());

        if (existing != null) {
            GameHighlight updated = GameHighlight.builder()
                    .id(existing.getId()) // 기존 ID 유지
                    .scheduleId(dto.getScheduleId())
                    .ranking(dto.getRanking())
                    .inning(dto.getInning())
                    .pitcherName(dto.getPitcherName())
                    .batterName(dto.getBatterName())
                    .pitchCount(dto.getPitchCount())
                    .result(dto.getResult())
                    .beforeSituation(dto.getBeforeSituation())
                    .afterSituation(dto.getAfterSituation())
                    .build();

            gameHighlightRepository.save(updated); // update
        } else {
            GameHighlight newEntity = GameHighlight.builder()
                    .scheduleId(dto.getScheduleId())
                    .ranking(dto.getRanking())
                    .inning(dto.getInning())
                    .pitcherName(dto.getPitcherName())
                    .batterName(dto.getBatterName())
                    .pitchCount(dto.getPitchCount())
                    .result(dto.getResult())
                    .beforeSituation(dto.getBeforeSituation())
                    .afterSituation(dto.getAfterSituation())
                    .build();

            gameHighlightRepository.save(newEntity); // insert
        }
    }
    
	 // 특정 scheduleId에 해당하는 하이라이트 목록을 조회해서 DTO 리스트로 변환
    public List<GameHighlightDTO> findByScheduleId(Integer scheduleId) {
        List<GameHighlight> highlights = gameHighlightRepository.findByScheduleIdOrderByRankingAsc(scheduleId);

        List<GameHighlightDTO> result = new ArrayList<>();
        for (GameHighlight h : highlights) {
            GameHighlightDTO dto = GameHighlightDTO.builder()
                    .scheduleId(h.getScheduleId())
                    .ranking(h.getRanking())
                    .inning(h.getInning())
                    .pitcherName(h.getPitcherName())
                    .batterName(h.getBatterName())
                    .pitchCount(h.getPitchCount())
                    .result(h.getResult())
                    .beforeSituation(h.getBeforeSituation())
                    .afterSituation(h.getAfterSituation())
                    .build();
            result.add(dto);
        }

        return result;
    }
}
