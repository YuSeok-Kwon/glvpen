package com.kepg.glvpen.modules.crowd.domain;

import java.time.LocalDate;
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
@Table(name = "kbo_crowd_stats", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"gameDate", "homeTeamId", "awayTeamId"})
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrowdStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private int season;

    @Column(nullable = false)
    private LocalDate gameDate;

    @Column(length = 5)
    private String dayOfWeek;

    @Column(nullable = false)
    private int homeTeamId;

    @Column(nullable = false)
    private int awayTeamId;

    @Column(length = 30)
    private String stadium;

    private int crowd;

    @Column(name = "updatedAt")
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
