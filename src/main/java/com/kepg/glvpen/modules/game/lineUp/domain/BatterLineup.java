package com.kepg.glvpen.modules.game.lineUp.domain;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "kbo_batter_lineup")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatterLineup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer scheduleId;

    private Integer teamId;

    private Integer playerId;

    @Column(name = "`order`")
    private Integer order;

    @Column(length = 10)
    private String position;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "playerId", insertable = false, updatable = false)
    @org.hibernate.annotations.NotFound(action = org.hibernate.annotations.NotFoundAction.IGNORE)
    private Player player;
}
