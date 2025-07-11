package com.kepg.BaseBallLOCK.common.game;

import org.springframework.stereotype.Component;

/**
 * 경기 결과 저장을 위한 공통 유틸리티 클래스
 * 모든 게임 모드에서 사용 가능한 결과 저장 로직
 */
@Component
public class GameResultSaver {

    /**
     * 경기 결과를 저장하기 위한 공통 메서드
     * 각 게임 모드에서 필요에 따라 확장 가능
     */
    public void saveGameResult(Object gameResult) {
        // 공통 경기 결과 저장 로직
        // 추후 구체적인 구현 예정
    }

    /**
     * 경기 통계를 업데이트하는 공통 메서드
     */
    public void updateGameStatistics(int userId, Object gameStats) {
        // 공통 통계 업데이트 로직
        // 추후 구체적인 구현 예정
    }
}
