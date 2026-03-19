package com.kepg.glvpen.modules.game.summaryRecord.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.kepg.glvpen.modules.game.summaryRecord.domain.GameSummaryRecord;

@Repository
public interface GameSummaryRecordRepository extends JpaRepository<GameSummaryRecord, Integer> {

    GameSummaryRecord findByScheduleIdAndCategory(Integer scheduleId, String category);

    List<GameSummaryRecord> findByScheduleId(Integer scheduleId);

    boolean existsByScheduleId(Integer scheduleId);

    // 시즌별 이벤트(카테고리) 발생 건수
    @Query(value = """
            SELECT
                YEAR(s.matchDate) AS season,
                gsr.category AS category,
                COUNT(*) AS eventCount
            FROM kbo_game_summary_record gsr
            JOIN kbo_schedule s ON gsr.scheduleId = s.id
            WHERE YEAR(s.matchDate) BETWEEN :startYear AND :endYear
            GROUP BY YEAR(s.matchDate), gsr.category
            ORDER BY YEAR(s.matchDate), gsr.category
            """, nativeQuery = true)
    List<Object[]> findEventTrendBySeason(@Param("startYear") int startYear, @Param("endYear") int endYear);
}
