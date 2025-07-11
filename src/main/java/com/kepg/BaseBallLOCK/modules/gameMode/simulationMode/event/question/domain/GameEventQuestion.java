package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.event.question.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "gameEventQuestion")
public class GameEventQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String eventType;
    private String questionText;

    private String choice1;
    private String choice2;
    private String choice3;
    private String choice4;

    private String correct1;
    private String correct2;
    private String correct3;
    private String correct4;
}
