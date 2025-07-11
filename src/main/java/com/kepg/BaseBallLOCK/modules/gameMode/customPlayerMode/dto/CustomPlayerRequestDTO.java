package com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * CustomPlayer 게임 요청 DTO
 * - 사용자 정의 선수 생성/편집 기반 게임
 */
@Data
@Builder
public class CustomPlayerRequestDTO {
    
    private Integer userId;
    private String gameMode;        // "CREATE", "EDIT", "TRADE" 등
    private boolean fastMode;       // 빠른 게임 모드
    
    // 커스텀 선수 정보
    private List<CustomPlayerInfo> customPlayers;
    private Integer targetTeamId;   // 대전할 팀 ID
    private String difficulty;      // 게임 난이도
    
    // 게임 옵션
    private boolean allowTrades;    // 트레이드 허용 여부
    private Integer budgetLimit;    // 예산 제한
    private String seasonType;      // "REGULAR", "POSTSEASON", "ALLSTAR"
    
    /**
     * 커스텀 선수 정보
     */
    @Data
    @Builder
    public static class CustomPlayerInfo {
        private String playerName;
        private String position;
        private Integer age;
        private String nationality;
        
        // 타격 스탯
        private Integer battingPower;
        private Integer contactAbility;
        private Integer speed;
        private Integer onBasePercentage;
        
        // 투구 스탯 (투수인 경우)
        private Integer velocity;
        private Integer control;
        private Integer stamina;
        private Integer movement;
        
        // 수비 스탯
        private Integer fielding;
        private Integer throwingPower;
        private Integer reaction;
        
        // 특수 능력
        private List<String> specialAbilities;
        private String playerType;  // "POWER", "CONTACT", "SPEED", "BALANCED"
        
        // 커스텀 설정
        private String customImage;
        private String customUniform;
        private Integer creationCost;
    }
}
