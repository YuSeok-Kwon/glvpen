package com.kepg.glvpen.modules.player.stats.domain;

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
@Table(name = "player_runner_stats",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_runner_stats_full",
           columnNames = {"playerId", "season", "series", "category"}
       ))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunnerStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer playerId;

    private Integer season;

    @Column(length = 10, columnDefinition = "VARCHAR(10) DEFAULT '0'")
    private String series;

    private String category;

    private Double value;

    private Integer ranking;

    @Column(name = "updatedAt")
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
