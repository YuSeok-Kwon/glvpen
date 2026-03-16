package com.kepg.glvpen.modules.player.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;

import lombok.*;

@Entity
@Table(name = "player")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer teamId;

    @Column(length = 30, nullable = false)
    private String name;

    @Column(length = 20)
    private String kboPlayerId;

    @Column(length = 5)
    private String backNumber;

    @Column(length = 20)
    private String position;

    private LocalDate birthDate;

    private Integer height;

    private Integer weight;

    private Integer debutYear;

    @Column(length = 50)
    private String school;

    @Column(length = 10)
    private String throwBat;

    private LocalDateTime profileUpdatedAt;
}
