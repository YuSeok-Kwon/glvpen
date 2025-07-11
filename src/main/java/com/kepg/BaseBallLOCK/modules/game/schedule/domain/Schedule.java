package com.kepg.BaseBallLOCK.modules.game.schedule.domain;

import java.sql.Timestamp;

import jakarta.persistence.Column;
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
@Table(name = "schedule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Timestamp matchDate;

    private Integer homeTeamId;

    private Integer homeTeamScore;
    
    private Integer awayTeamId;
    
    private Integer awayTeamScore;


    @Column(length = 30)
    private String stadium;

    @Column(length = 20)
    private String status;
    
    private Integer statizId;
}
