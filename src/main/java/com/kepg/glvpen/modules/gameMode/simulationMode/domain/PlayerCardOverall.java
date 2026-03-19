package com.kepg.glvpen.modules.gameMode.simulationMode.domain;

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

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "sim_player_card_overall")
public class PlayerCardOverall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer playerId;
    private Integer season;

    private String type; // "BATTER" 또는 "PITCHER"

    // 타자용
    private Integer power;       // 파워
    private Integer contact;     // 정확
    private Integer discipline;  // 선구
    private Integer speed;       // 주루

    // 투수용
    private Integer control;     // 제구
    private Integer stuff;       // 구위
    private Integer stamina;     // 체력

    // 공통
    private Integer overall;
    private String grade;
}