package com.kepg.BaseBallLOCK.modules.game.highlight.doamin;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Builder
@Getter
@Setter
@Table(name = "gameHighlight")
@NoArgsConstructor
@AllArgsConstructor
public class GameHighlight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer scheduleId;
    private Integer ranking;

    private String inning;
    private String pitcherName;
    private String batterName;
    private String pitchCount;
    private String result;
    private String beforeSituation;
    private String afterSituation;
}
