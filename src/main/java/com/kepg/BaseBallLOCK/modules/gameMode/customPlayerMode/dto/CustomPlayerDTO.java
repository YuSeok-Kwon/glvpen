package com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomPlayerDTO {
    private String name;
    private int age;
    private String gender;
    private String skin;
    private String hair;
    private String batting;
    private int height;
    private int weight;
    private String position;
    private String avatarUrl;
    
    // 개별 능력치 필드 (JavaScript에서 사용)
    private int power;
    private int contact;
    private int speed;
    private int defense;
    
    // 기존 stats 객체도 유지 (호환성을 위해)
    private StatsDTO stats;

    @Getter
    @Setter
    public static class StatsDTO {
        private int power;
        private int contact;
        private int speed;
        private int defense;
    }
}
