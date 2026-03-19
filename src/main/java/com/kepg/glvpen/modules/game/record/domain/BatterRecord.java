package com.kepg.glvpen.modules.game.record.domain;

import com.kepg.glvpen.modules.player.domain.Player;

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
@Table(name = "kbo_batter_record",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_batter_record_game",
           columnNames = {"scheduleId", "playerId"}
       ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatterRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer playerId;

    private Integer scheduleId;

    private Integer teamId;

    private Integer pa;

    private Integer ab;

    private Integer hits;

    private Integer rbi;

    private Integer hr;

    private Integer sb;

    private Integer so;

    private Integer bb;

    private Integer runs;          // 득점

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "playerId", insertable = false, updatable = false)
    @org.hibernate.annotations.NotFound(action = org.hibernate.annotations.NotFoundAction.IGNORE)
    private Player player;
}
