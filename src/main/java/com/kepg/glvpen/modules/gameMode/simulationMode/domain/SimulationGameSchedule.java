package com.kepg.glvpen.modules.gameMode.simulationMode.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sim_game_schedule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SimulationGameSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private int userId;

    private String difficulty;

    @Column(name = "createdAt", updatable = false, insertable = false)
    private LocalDateTime createdAt;
}
