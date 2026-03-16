package com.kepg.glvpen.modules.game.keyPlayer.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "kbo_game_key_player",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_game_key_v2",
           columnNames = {"scheduleId", "playerType", "metric", "ranking", "playerName"}
       ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameKeyPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer scheduleId;

    @Column(length = 20)
    private String kboGameId;

    private Integer season;

    @Column(length = 10)
    private String playerType;     // "PITCHER" / "HITTER"

    @Column(length = 30)
    private String metric;         // "GAME_WPA_RT", "KK_CN", "HR_CN" 등

    private Integer ranking;       // 1, 2, 3

    @Column(length = 30)
    private String playerName;

    private Integer teamId;

    @Column(length = 200)
    private String recordInfo;     // "14.1% (3이닝 0실점 2삼진)"
}
