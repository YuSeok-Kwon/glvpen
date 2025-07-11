package com.kepg.BaseBallLOCK.modules.game.scoreBoard.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "scoreBoard")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoreBoard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer scheduleId;

    private Integer homeScore;
    private Integer awayScore;

    private String homeInningScores;  // "1,0,2,0,0,1,0,0,0"
    private String awayInningScores;

    private Integer homeR;
    private Integer homeH;
    private Integer homeE;
    private Integer homeB;

    private Integer awayR;
    private Integer awayH;
    private Integer awayE;
    private Integer awayB;

    private String winPitcher;
    private String losePitcher;
}
