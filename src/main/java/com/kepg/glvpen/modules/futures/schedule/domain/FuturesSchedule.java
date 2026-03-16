package com.kepg.glvpen.modules.futures.schedule.domain;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
@Table(name = "futures_schedule", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"matchDate", "homeTeamId", "awayTeamId"})
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class FuturesSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private int season;

    private Timestamp matchDate;

    private Integer homeTeamId;

    private Integer awayTeamId;

    private Integer homeTeamScore;

    private Integer awayTeamScore;

    @Column(length = 50)
    private String stadium;

    @Column(length = 20)
    private String status;

    @Column(length = 20)
    private String leagueType;

    @Column(length = 200)
    private String note;

    @Column(name = "updatedAt")
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
