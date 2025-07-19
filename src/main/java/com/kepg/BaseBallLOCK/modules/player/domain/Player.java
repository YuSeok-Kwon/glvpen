package com.kepg.BaseBallLOCK.modules.player.domain;

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

}