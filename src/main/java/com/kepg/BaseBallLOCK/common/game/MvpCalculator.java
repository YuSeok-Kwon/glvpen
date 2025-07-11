package com.kepg.BaseBallLOCK.common.game;

import org.springframework.stereotype.Component;

/**
 * MVP 계산을 위한 공통 유틸리티 클래스
 * 모든 게임 모드에서 사용 가능한 MVP 계산 로직
 */
@Component
public class MvpCalculator {

    /**
     * 타자의 MVP 점수 계산
     * @param hits 안타 수
     * @param homeRuns 홈런 수
     * @param rbis 타점
     * @param avg 타율
     * @return MVP 점수
     */
    public double calculateBatterMvpScore(int hits, int homeRuns, int rbis, double avg) {
        return (hits * 1.0) + (homeRuns * 4.0) + (rbis * 2.0) + (avg * 10.0);
    }

    /**
     * 투수의 MVP 점수 계산
     * @param wins 승리 수
     * @param strikeouts 삼진 수
     * @param era 평균자책점
     * @param innings 이닝 수
     * @return MVP 점수
     */
    public double calculatePitcherMvpScore(int wins, int strikeouts, double era, double innings) {
        return (wins * 5.0) + (strikeouts * 0.5) + (10.0 / (era + 1)) + (innings * 0.1);
    }
}
