package com.kepg.BaseBallLOCK.modules.gameMode.quickGameMode.dto;

import com.kepg.BaseBallLOCK.modules.gameMode.quickGameMode.domain.ClassicGameResult;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassicGameResultDTO {
    
    private Integer gameId;
    private Integer userId;
    private String difficulty;
    
    // 경기 결과
    private Integer userScore;
    private Integer botScore;
    private boolean isWin;
    
    // 하이라이트 정보
    private String highlights;  // JSON 형태로 저장된 하이라이트
    private String mvpPlayerName;
    private Double mvpScore;
    
    // 게임 통계
    private Integer totalHits;
    private Integer totalHomeRuns;
    private Integer totalStrikeouts;
    private String gameDuration;  // 경기 소요 시간
    
    // 난이도별 보상
    private Integer rewardPoints;
    private Integer experienceGained;
    
    public static ClassicGameResultDTO fromEntity(ClassicGameResult entity) {
        return ClassicGameResultDTO.builder()
                .gameId(entity.getId() != null ? entity.getId().intValue() : null)
                .userId(entity.getUserId())
                .difficulty(entity.getDifficulty())
                .userScore(entity.getUserScore())
                .botScore(entity.getBotScore())
                .isWin(entity.getIsWin() != null ? entity.getIsWin() : false)
                .highlights(entity.getHighlights())
                .mvpPlayerName(entity.getMvpPlayerName())
                .mvpScore(entity.getMvpScore())
                .totalHits(entity.getTotalHits())
                .totalHomeRuns(entity.getTotalHomeRuns())
                .totalStrikeouts(entity.getTotalStrikeouts())
                .gameDuration(entity.getGameDuration())
                .rewardPoints(entity.getRewardPoints())
                .experienceGained(entity.getExperienceGained())
                .build();
    }
}
