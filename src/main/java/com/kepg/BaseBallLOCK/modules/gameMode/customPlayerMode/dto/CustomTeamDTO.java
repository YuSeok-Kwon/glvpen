package com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto;

import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.domain.CustomTeam;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 커스텀 팀 DTO
 */
@Data
@Builder
public class CustomTeamDTO {

    private Long id;
    private Integer userId;
    private String teamName;
    private String description;
    private String emblemUrl;
    private List<LineupPosition> lineup;
    private TeamStats teamStats;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 라인업 포지션 정보
     */
    @Data
    @Builder
    public static class LineupPosition {
        private Integer battingOrder;  // 타순 (1-9)
        private String position;        // 포지션 (P, C, 1B, 2B, 3B, SS, LF, CF, RF)
        private Long playerId;
        private String playerName;
        private Integer level;
        private Integer power;
        private Integer contact;
        private Integer speed;
        private Integer fielding;
        private Integer arm;
    }

    /**
     * 팀 전체 능력치 통계
     */
    @Data
    @Builder
    public static class TeamStats {
        private Double avgPower;
        private Double avgContact;
        private Double avgSpeed;
        private Double avgFielding;
        private Double avgArm;
        private Double totalRating;
        private Integer playerCount;
    }

    /**
     * 팀 생성/수정 요청
     */
    @Data
    @Builder
    public static class CreateRequest {
        private String teamName;
        private String description;
        private List<LineupSlot> lineup;
    }

    /**
     * 라인업 슬롯 (간단한 형태)
     */
    @Data
    @Builder
    public static class LineupSlot {
        private Integer battingOrder;
        private String position;
        private Long playerId;
    }

    /**
     * Entity에서 DTO로 변환
     */
    public static CustomTeamDTO fromEntity(CustomTeam entity, List<LineupPosition> lineup) {
        TeamStats stats = calculateTeamStats(lineup);

        return CustomTeamDTO.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .teamName(entity.getTeamName())
                .description(entity.getDescription())
                .emblemUrl(entity.getEmblemUrl())
                .lineup(lineup)
                .teamStats(stats)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * 팀 전체 능력치 계산
     */
    private static TeamStats calculateTeamStats(List<LineupPosition> lineup) {
        if (lineup == null || lineup.isEmpty()) {
            return TeamStats.builder()
                    .avgPower(0.0)
                    .avgContact(0.0)
                    .avgSpeed(0.0)
                    .avgFielding(0.0)
                    .avgArm(0.0)
                    .totalRating(0.0)
                    .playerCount(0)
                    .build();
        }

        double totalPower = 0;
        double totalContact = 0;
        double totalSpeed = 0;
        double totalFielding = 0;
        double totalArm = 0;
        int count = lineup.size();

        for (LineupPosition pos : lineup) {
            totalPower += pos.getPower() != null ? pos.getPower() : 0;
            totalContact += pos.getContact() != null ? pos.getContact() : 0;
            totalSpeed += pos.getSpeed() != null ? pos.getSpeed() : 0;
            totalFielding += pos.getFielding() != null ? pos.getFielding() : 0;
            totalArm += pos.getArm() != null ? pos.getArm() : 0;
        }

        double avgPower = totalPower / count;
        double avgContact = totalContact / count;
        double avgSpeed = totalSpeed / count;
        double avgFielding = totalFielding / count;
        double avgArm = totalArm / count;
        double totalRating = (avgPower + avgContact + avgSpeed + avgFielding + avgArm) / 5;

        return TeamStats.builder()
                .avgPower(Math.round(avgPower * 10) / 10.0)
                .avgContact(Math.round(avgContact * 10) / 10.0)
                .avgSpeed(Math.round(avgSpeed * 10) / 10.0)
                .avgFielding(Math.round(avgFielding * 10) / 10.0)
                .avgArm(Math.round(avgArm * 10) / 10.0)
                .totalRating(Math.round(totalRating * 10) / 10.0)
                .playerCount(count)
                .build();
    }
}
