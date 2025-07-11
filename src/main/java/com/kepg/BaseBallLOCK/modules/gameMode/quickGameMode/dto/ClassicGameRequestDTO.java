package com.kepg.BaseBallLOCK.modules.gameMode.quickGameMode.dto;

import com.kepg.BaseBallLOCK.modules.gameMode.quickGameMode.service.ClassicSimulationService.Difficulty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassicGameRequestDTO {
    
    private Integer userId;
    private String userLineupIds;  // 쉼표로 구분된 선수 ID들
    private Difficulty difficulty;
    private Integer teamId;        // 상대팀 ID (선택사항)
    
    // 게임 설정
    @Builder.Default
    private boolean fastMode = true;      // 하이라이트만 보여줄지 여부
    @Builder.Default
    private boolean autoGenerate = true;  // 상대팀 자동 생성 여부
}
