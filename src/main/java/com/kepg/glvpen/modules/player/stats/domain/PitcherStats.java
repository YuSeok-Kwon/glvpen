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
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "player_pitcher_stats",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_pitcher_stats_full",
           columnNames = {"playerId", "season", "category", "series", "situationType", "situationValue"}
       ),
       indexes = {
           @Index(name = "idx_pitcher_player_season", columnList = "playerId, season"),
           @Index(name = "idx_pitcher_season_category", columnList = "season, category"),
           @Index(name = "idx_pitcher_season_series_sit", columnList = "season, series, situationType, situationValue")
       })
@EntityListeners(AuditingEntityListener.class)
@Getter 
@Setter 
@Builder
@NoArgsConstructor 
@AllArgsConstructor
public class PitcherStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer playerId;

    private Integer season;

    private String category;

    private Double value;

    private Integer ranking;

    private String position;

    @Column(length = 10, columnDefinition = "VARCHAR(10) DEFAULT '0'")
    private String series;

    @Column(length = 20, columnDefinition = "VARCHAR(20) DEFAULT ''")
    private String situationType;

    @Column(length = 50, columnDefinition = "VARCHAR(50) DEFAULT ''")
    private String situationValue;
    
    @Column(name = "updatedAt")
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
