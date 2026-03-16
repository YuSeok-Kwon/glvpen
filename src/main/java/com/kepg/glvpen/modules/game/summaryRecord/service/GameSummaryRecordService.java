package com.kepg.glvpen.modules.game.summaryRecord.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kepg.glvpen.modules.game.summaryRecord.domain.GameSummaryRecord;
import com.kepg.glvpen.modules.game.summaryRecord.repository.GameSummaryRecordRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GameSummaryRecordService {

    private final GameSummaryRecordRepository gameSummaryRecordRepository;

    @Transactional
    public void saveOrUpdate(GameSummaryRecord record) {
        GameSummaryRecord existing = gameSummaryRecordRepository
                .findByScheduleIdAndCategory(record.getScheduleId(), record.getCategory());

        if (existing != null) {
            GameSummaryRecord updated = GameSummaryRecord.builder()
                    .id(existing.getId())
                    .scheduleId(record.getScheduleId())
                    .category(record.getCategory())
                    .content(record.getContent())
                    .build();
            gameSummaryRecordRepository.save(updated);
        } else {
            gameSummaryRecordRepository.save(record);
        }
    }

    public boolean existsByScheduleId(Integer scheduleId) {
        return gameSummaryRecordRepository.existsByScheduleId(scheduleId);
    }

    public java.util.List<GameSummaryRecord> findByScheduleId(Integer scheduleId) {
        return gameSummaryRecordRepository.findByScheduleId(scheduleId);
    }
}
