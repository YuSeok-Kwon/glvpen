package com.kepg.glvpen.modules.team.teamRanking.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "team_ranking",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_team_ranking_season",
           columnNames = {"season", "teamId"}
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

    private int ranking;

    private int games;

    private int wins;

    private int draws;

    private int losses;

    private double gamesBehind;

    private double winRate;
}
