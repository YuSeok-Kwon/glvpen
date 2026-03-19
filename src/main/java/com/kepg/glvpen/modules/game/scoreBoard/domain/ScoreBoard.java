package com.kepg.glvpen.modules.game.scoreBoard.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "kbo_score_board",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_scoreboard_schedule",
           columnNames = {"scheduleId"}
       ))
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

    @Column(length = 20)
    private String crowd;          // 관중 수 ("12,141")

    @Column(length = 10)
    private String startTime;      // 개시 시간 ("18:30")

    @Column(length = 10)
    private String endTime;        // 종료 시간 ("21:19")

    @Column(length = 10)
    private String gameTime;       // 경기 시간 ("1:51")
}
