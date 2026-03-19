package com.kepg.glvpen.modules.team.teamStats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "team_stats",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_team_stats_full",
           columnNames = {"teamId", "season", "category", "series"}
       ))
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

    @Column(length = 10, columnDefinition = "VARCHAR(10) DEFAULT '0'")
    @Builder.Default
    private String series = "0";
}
