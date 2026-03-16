package com.kepg.glvpen.modules.team.teamHeadToHead.domain;

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
@Table(name = "team_head_to_head", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"season", "teamId", "opponentTeamId"})
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamHeadToHead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private int season;

    @Column(nullable = false)
    private int teamId;

    @Column(nullable = false)
    private int opponentTeamId;

    private int wins;

    private int losses;

    private int draws;

    @Column(name = "updatedAt")
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
