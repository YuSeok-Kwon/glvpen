package com.kepg.BaseBallLOCK.modules.team.teamStats.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "teamStats")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private int teamId;
    private int season;
    private String category;
    private double value;
    private Integer ranking;
}
