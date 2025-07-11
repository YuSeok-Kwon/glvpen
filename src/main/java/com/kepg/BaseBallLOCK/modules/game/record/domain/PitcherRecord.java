package com.kepg.BaseBallLOCK.modules.game.record.domain;

import com.kepg.BaseBallLOCK.modules.player.domain.Player;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pitcherRecord")
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
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "playerId", insertable = false, updatable = false)
    @org.hibernate.annotations.NotFound(action = org.hibernate.annotations.NotFoundAction.IGNORE)
    private Player player;
}
