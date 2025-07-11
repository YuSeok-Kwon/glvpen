package com.kepg.BaseBallLOCK.modules.gameMode.quickGameMode.service;

import org.springframework.stereotype.Service;

import com.kepg.BaseBallLOCK.common.game.MvpCalculator;
import com.kepg.BaseBallLOCK.common.game.GameLogFormatter;
import com.kepg.BaseBallLOCK.modules.gameMode.quickGameMode.domain.ClassicGameResult;
import com.kepg.BaseBallLOCK.modules.gameMode.quickGameMode.dto.ClassicGameRequestDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.quickGameMode.dto.ClassicGameResultDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.quickGameMode.repository.ClassicGameResultRepository;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;

/**
 * Classic Simulation Mode 서비스
 * 기존 시뮬레이션에 난이도 시스템을 추가한 개선된 버전
 */
@Service
@RequiredArgsConstructor
public class ClassicSimulationService {
    
    private final ClassicGameResultRepository gameResultRepository;
    private final MvpCalculator mvpCalculator;
    private final GameLogFormatter gameLogFormatter;
    private final Random random = new Random();
    
    /**
     * 게임 난이도 열거형
     */
    public enum Difficulty {
        EASY("쉬움", 1.0, 100),
        NORMAL("보통", 1.5, 150), 
        HARD("어려움", 2.0, 200);
        
        private final String description;
        private final double scoreMultiplier;    // 보상 점수 배수
        private final int baseExperience;        // 기본 경험치
        
        Difficulty(String description, double scoreMultiplier, int baseExperience) {
            this.description = description;
            this.scoreMultiplier = scoreMultiplier;
            this.baseExperience = baseExperience;
        }
        
        public String getDescription() { return description; }
        public double getScoreMultiplier() { return scoreMultiplier; }
        public int getBaseExperience() { return baseExperience; }
    }
    
    /**
     * Classic Mode 게임 실행
     */
    public ClassicGameResultDTO playClassicGame(ClassicGameRequestDTO request) {
        LocalDateTime startTime = LocalDateTime.now();
        
        // 1. 봇 팀 생성 (난이도에 따라 다른 전략)
        // String botLineup = generateBotLineup(request.getDifficulty(), request.getTeamId());
        
        // 2. 기존 시뮬레이션 서비스 활용해서 게임 실행
        // TODO: 실제 SimulationService 연동 필요
        
        // 3. 하이라이트 추출 (fastMode일 때)
        // String highlights = extractGameHighlights(request.isFastMode());
        
        // 4. 결과 계산
        ClassicGameResultDTO result = calculateGameResult(request, startTime);
        
        // 5. 데이터베이스 저장
        saveGameResult(result);
        
        return result;
    }
    
    /**
     * 난이도에 따른 봇 라인업 생성
     */
    private String generateBotLineup(Difficulty difficulty, Integer teamId) {
        // 난이도별 봇 전략
        switch (difficulty) {
            case EASY:
                return generateEasyBotLineup(teamId);
            case NORMAL:
                return generateNormalBotLineup(teamId);
            case HARD:
                return generateHardBotLineup(teamId);
            default:
                return generateNormalBotLineup(teamId);
        }
    }
    
    private String generateEasyBotLineup(Integer teamId) {
        // 낮은 스탯의 선수들로 구성
        // TODO: 실제 선수 데이터 기반 라인업 생성
        return "easy_lineup_placeholder";
    }
    
    private String generateNormalBotLineup(Integer teamId) {
        // 평균적인 스탯의 선수들로 구성
        return "normal_lineup_placeholder";
    }
    
    private String generateHardBotLineup(Integer teamId) {
        // 높은 스탯의 선수들로 구성
        return "hard_lineup_placeholder";
    }
    
    /**
     * 게임 하이라이트 추출
     */
    private String extractGameHighlights(boolean fastMode) {
        if (!fastMode) {
            return "full_game_log";
        }
        
        // 주요 장면만 추출
        List<String> highlights = List.of(
            "1회초 홈런으로 선제점!",
            "3회말 역전 안타!",
            "7회초 결승 홈런!",
            "9회말 극적인 마무리!"
        );
        
        return gameLogFormatter.formatHighlights(highlights);
    }
    
    /**
     * 게임 결과 계산
     */
    private ClassicGameResultDTO calculateGameResult(ClassicGameRequestDTO request, LocalDateTime startTime) {
        LocalDateTime endTime = LocalDateTime.now();
        long duration = ChronoUnit.SECONDS.between(startTime, endTime);
        
        // 임시 결과 생성 (실제로는 시뮬레이션 결과 사용)
        int userScore = random.nextInt(10) + 1;
        int botScore = random.nextInt(10) + 1;
        boolean isWin = userScore > botScore;
        
        // 난이도별 보상 계산
        Difficulty difficulty = request.getDifficulty();
        int rewardPoints = calculateRewardPoints(isWin, userScore, difficulty);
        int experienceGained = calculateExperience(isWin, difficulty);
        
        return ClassicGameResultDTO.builder()
                .userId(request.getUserId())
                .difficulty(difficulty.name())
                .userScore(userScore)
                .botScore(botScore)
                .isWin(isWin)
                .highlights(extractGameHighlights(request.isFastMode()))
                .mvpPlayerName("MVP 선수")
                .mvpScore(mvpCalculator.calculateBatterMvpScore(3, 1, 4, 0.350))
                .totalHits(random.nextInt(15) + 5)
                .totalHomeRuns(random.nextInt(3))
                .totalStrikeouts(random.nextInt(10) + 2)
                .gameDuration(duration + "초")
                .rewardPoints(rewardPoints)
                .experienceGained(experienceGained)
                .build();
    }
    
    /**
     * 보상 점수 계산
     */
    private int calculateRewardPoints(boolean isWin, int userScore, Difficulty difficulty) {
        int basePoints = userScore * 10;
        if (isWin) {
            basePoints += 100; // 승리 보너스
        }
        return (int) (basePoints * difficulty.getScoreMultiplier());
    }
    
    /**
     * 경험치 계산
     */
    private int calculateExperience(boolean isWin, Difficulty difficulty) {
        int baseExp = difficulty.getBaseExperience();
        if (isWin) {
            baseExp += 50; // 승리 보너스
        }
        return baseExp;
    }
    
    /**
     * 게임 결과 저장
     */
    private void saveGameResult(ClassicGameResultDTO resultDTO) {
        ClassicGameResult entity = ClassicGameResult.builder()
                .userId(resultDTO.getUserId())
                .difficulty(resultDTO.getDifficulty())
                .userScore(resultDTO.getUserScore())
                .botScore(resultDTO.getBotScore())
                .isWin(resultDTO.isWin())
                .highlights(resultDTO.getHighlights())
                .mvpPlayerName(resultDTO.getMvpPlayerName())
                .mvpScore(resultDTO.getMvpScore())
                .totalHits(resultDTO.getTotalHits())
                .totalHomeRuns(resultDTO.getTotalHomeRuns())
                .totalStrikeouts(resultDTO.getTotalStrikeouts())
                .gameDuration(resultDTO.getGameDuration())
                .rewardPoints(resultDTO.getRewardPoints())
                .experienceGained(resultDTO.getExperienceGained())
                .build();
        
        gameResultRepository.save(entity);
    }
    
    /**
     * 사용자 게임 기록 조회
     */
    public List<ClassicGameResultDTO> getUserGameHistory(Integer userId) {
        List<ClassicGameResult> results = gameResultRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return results.stream()
                .map(ClassicGameResultDTO::fromEntity)
                .toList();
    }
    
    /**
     * 사용자 통계 조회
     */
    public PlayerStatistics getUserStats(Integer userId) {
        Integer totalGames = gameResultRepository.countTotalGamesByUserId(userId);
        Integer totalWins = gameResultRepository.countWinsByUserId(userId);
        Integer totalExperience = gameResultRepository.getTotalExperienceByUserId(userId);
        
        double winRate = totalGames != null && totalGames > 0 ? (double) totalWins / totalGames * 100 : 0.0;
        
        return new PlayerStatistics(
            totalGames != null ? totalGames : 0,
            totalWins != null ? totalWins : 0,
            winRate,
            totalExperience != null ? totalExperience : 0
        );
    }
    
    /**
     * 플레이어 통계 정보
     */
    public static class PlayerStatistics {
        private final Integer totalGames;
        private final Integer totalWins;
        private final Double winRate;
        private final Integer totalExperience;
        
        public PlayerStatistics(Integer totalGames, Integer totalWins, Double winRate, Integer totalExperience) {
            this.totalGames = totalGames;
            this.totalWins = totalWins;
            this.winRate = winRate;
            this.totalExperience = totalExperience;
        }
        
        public Integer getTotalGames() { return totalGames; }
        public Integer getTotalWins() { return totalWins; }
        public Double getWinRate() { return winRate; }
        public Integer getTotalExperience() { return totalExperience; }
    }
}
