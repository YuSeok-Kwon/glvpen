package com.kepg.glvpen.modules.game.summaryRecord.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "kbo_game_summary_record",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_summary",
           columnNames = {"scheduleId", "category"}
       ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameSummaryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer scheduleId;

    @Column(length = 20)
    private String category;       // "결승타", "홈런", "2루타", "도루", "심판" 등

    @Column(columnDefinition = "TEXT")
    private String content;        // "케이브(3회 무사 만루서 좌전 안타)"
}
