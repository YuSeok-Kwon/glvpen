package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.card.userCard.domain;

import jakarta.persistence.*;
import lombok.*;

import java.sql.Timestamp;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "userCard")
public class UserCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer userId;

    private Integer playerId;

    private Integer season;

    @Column(length = 1, nullable = false)
    private String grade;

    @Column(length = 5, nullable = false)
    private String position;
    
    
    @Column(nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Timestamp createdAt;
}