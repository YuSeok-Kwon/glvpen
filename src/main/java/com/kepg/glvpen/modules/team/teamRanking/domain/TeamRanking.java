package com.kepg.glvpen.modules.team.teamRanking.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "team_ranking",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_team_ranking_full",
           columnNames = {"season", "teamId", "series"}
       ))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamRanking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private int season;

    private int teamId;

    @Column(length = 10, columnDefinition = "VARCHAR(10) DEFAULT '0'")
    @Builder.Default
    private String series = "0";

    private int ranking;

    private int games;

    private int wins;

    private int draws;

    private int losses;

    private double gamesBehind;

    private double winRate;
}
