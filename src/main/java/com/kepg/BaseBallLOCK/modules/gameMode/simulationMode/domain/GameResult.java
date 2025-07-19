package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "gameResult")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GameResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private int scheduleId;
    private int userId;
    private int userScore;
    private int botScore;
    private boolean isWin;
    private String mvp;

    @Column(columnDefinition = "TEXT")
    private String gameLog;
    
    @Column(columnDefinition = "TEXT")
    private String botLineupJson;
    
    @Column(name = "createdAt", updatable = false, insertable = false)
    private java.time.LocalDateTime createdAt;
}