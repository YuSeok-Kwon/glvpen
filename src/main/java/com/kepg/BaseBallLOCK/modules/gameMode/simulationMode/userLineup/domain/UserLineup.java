package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.userLineup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class UserLineup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer userId;

    private Integer playerId;

    private String position; // "1B", "2B", "SS", "OF" 등

    @Column(name = "orderNum")
    private String orderNum;

    private Integer season;
}
