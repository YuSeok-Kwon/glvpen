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
@Table(name = "batterStats")
@EntityListeners(AuditingEntityListener.class)
@Getter 
@Setter 
@Builder
@NoArgsConstructor 
@AllArgsConstructor
public class BatterStats {

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