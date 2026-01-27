package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "gameResult")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer scheduleId;
    private Integer userId;
    private Integer userScore;
    private Integer botScore;
    private boolean isWin;
    private String mvp;

    @Column(columnDefinition = "TEXT")
    private String gameLog;

    @Column(columnDefinition = "TEXT")
    private String botLineupJson;

    @Column(name = "createdAt", updatable = false, insertable = false)
    private java.time.LocalDateTime createdAt;
}