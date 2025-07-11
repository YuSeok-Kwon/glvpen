package com.kepg.BaseBallLOCK.modules.player.stats.domain;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pitcherStats")
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
    
    @Column(name = "updatedAt")
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
