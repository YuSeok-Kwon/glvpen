package com.kepg.glvpen.modules.game.schedule.domain;

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
@Table(name = "kbo_schedule")
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
    
    private Integer externalId;

    @Column(length = 20)
    private String kboGameId;

    @Builder.Default
    @Column(length = 10)
    private String seriesType = "0";

    @Column(length = 5)
    private String kboSeriesCode;
}