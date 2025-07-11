package com.kepg.BaseBallLOCK.modules.team.teamRanking.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "teamRanking")
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
