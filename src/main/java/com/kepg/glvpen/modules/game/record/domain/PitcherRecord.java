package com.kepg.glvpen.modules.game.record.domain;

import com.kepg.glvpen.modules.player.domain.Player;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "kbo_pitcher_record",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_pitcher_record_game",
           columnNames = {"scheduleId", "playerId"}
       ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PitcherRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer playerId;

    private Integer scheduleId;

    private Integer teamId;

    private Double innings;

    private Integer strikeouts;

    private Integer bb;

    private Integer hbp;

    private Integer runs;

    private Integer earnedRuns;

    private Integer hits;

    private Integer hr;

    @Column(length = 10)
    private String decision;

    @Column(length = 10)
    private String entryType;      // 등판 유형 ("선발", "3.8" 등)

    private Integer battersFaced;  // 상대한 타자 수

    private Integer pitchCount;    // 투구수

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "playerId", insertable = false, updatable = false)
    @org.hibernate.annotations.NotFound(action = org.hibernate.annotations.NotFoundAction.IGNORE)
    private Player player;
}
