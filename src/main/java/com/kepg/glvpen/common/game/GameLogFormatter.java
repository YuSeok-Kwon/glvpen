package com.kepg.glvpen.common.game;

import org.springframework.stereotype.Component;
import java.util.List;

/**
 * 경기 로그 포맷팅을 위한 공통 유틸리티 클래스
 * 모든 게임 모드에서 사용 가능한 로그 포맷팅 로직
 */
@Component
public class GameLogFormatter {

    /**
     * 경기 하이라이트 로그 포맷팅
     * @param playLogs 플레이 로그 리스트
     * @return 포맷된 하이라이트 문자열
     */
    public String formatHighlights(List<String> playLogs) {
        StringBuilder highlights = new StringBuilder();
        
        for (String log : playLogs) {
            if (isHighlightWorthy(log)) {
                highlights.append("⚾ ").append(log).append("\n");
            }
        }
        
        return highlights.toString();
    }

    /**
     * 이닝별 스코어 포맷팅
     * @param homeScore 홈팀 점수 배열
     * @param awayScore 원정팀 점수 배열
     * @return 포맷된 스코어보드 문자열
     */
    public String formatScoreBoard(int[] homeScore, int[] awayScore) {
        StringBuilder scoreBoard = new StringBuilder();
        
        scoreBoard.append("이닝\t");
        for (int i = 1; i <= 9; i++) {
            scoreBoard.append(i).append("\t");
        }
        scoreBoard.append("R\n");
        
        scoreBoard.append("원정\t");
        int awayTotal = 0;
        for (int score : awayScore) {
            scoreBoard.append(score).append("\t");
            awayTotal += score;
        }
        scoreBoard.append(awayTotal).append("\n");
        
        scoreBoard.append("홈\t");
        int homeTotal = 0;
        for (int score : homeScore) {
            scoreBoard.append(score).append("\t");
            homeTotal += score;
        }
        scoreBoard.append(homeTotal).append("\n");
        
        return scoreBoard.toString();
    }

    /**
     * 하이라이트 가치가 있는 플레이인지 판단
     * @param log 플레이 로그
     * @return 하이라이트 여부
     */
    private boolean isHighlightWorthy(String log) {
        return log.contains("홈런") || log.contains("삼진") || 
               log.contains("도루") || log.contains("실책") ||
               log.contains("승부") || log.contains("역전");
    }
}
