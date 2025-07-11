package com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.service;

import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.domain.CustomPlayer;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto.CustomPlayerRequestDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto.CustomPlayerResultDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.repository.CustomPlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * CustomPlayer 게임 서비스
 * - 커스텀 선수 생성, 편집, 관리
 * - 커스텀 선수 기반 게임 시뮬레이션
 * - 선수 성장 및 경험치 시스템
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CustomPlayerService {
    
    private final CustomPlayerRepository customPlayerRepository;
    private final Random random = new Random();
    
    /**
     * 커스텀 선수 생성
     */
    public CustomPlayer createCustomPlayer(Integer userId, CustomPlayerRequestDTO.CustomPlayerInfo playerInfo) {
        // 능력치 검증 (총합 제한)
        validatePlayerStats(playerInfo);
        
        CustomPlayer player = CustomPlayer.builder()
            .userId(userId)
            .playerName(playerInfo.getPlayerName())
            .level(1)
            .experience(0)
            .power(playerInfo.getBattingPower() != null ? playerInfo.getBattingPower() : 50)
            .contact(playerInfo.getContactAbility() != null ? playerInfo.getContactAbility() : 50)
            .speed(playerInfo.getSpeed() != null ? playerInfo.getSpeed() : 50)
            .fielding(playerInfo.getFielding() != null ? playerInfo.getFielding() : 50)
            .arm(playerInfo.getThrowingPower() != null ? playerInfo.getThrowingPower() : 50)
            .specialTraits(buildSpecialTraits(playerInfo))
            .build();
        
        CustomPlayer savedPlayer = customPlayerRepository.save(player);
        return savedPlayer;
    }
    
    /**
     * 커스텀 선수 게임 실행
     */
    public CustomPlayerResultDTO playCustomPlayerGame(CustomPlayerRequestDTO request) {
        LocalDateTime startTime = LocalDateTime.now();
        
        // 1. 사용자의 커스텀 선수들 조회
        List<CustomPlayer> userPlayers = getActiveCustomPlayers(request.getUserId());
        
        // 2. 게임 시뮬레이션 실행
        CustomPlayerResultDTO result = simulateCustomPlayerGame(request, userPlayers, startTime);
        
        // 3. 선수 경험치 및 성장 처리
        updatePlayerExperience(userPlayers, result);
        
        // 4. 게임 결과 저장
        saveCustomPlayerGameResult(result);
        
        return result;
    }
    
    /**
     * 커스텀 선수 기반 게임 시뮬레이션
     */
    private CustomPlayerResultDTO simulateCustomPlayerGame(CustomPlayerRequestDTO request, 
                                                         List<CustomPlayer> userPlayers, 
                                                         LocalDateTime startTime) {
        
        // Mock 시뮬레이션 로직 (실제로는 SimulationService와 연동)
        LocalDateTime endTime = LocalDateTime.now();
        
        List<CustomPlayerResultDTO.CustomPlayerPerformance> performances = new ArrayList<>();
        
        // 각 커스텀 선수의 성과 계산
        for (CustomPlayer player : userPlayers) {
            CustomPlayerResultDTO.CustomPlayerPerformance performance = 
                calculatePlayerPerformance(player, request.getDifficulty());
            performances.add(performance);
        }
        
        // MVP 선정
        String mvpPlayer = selectMvpPlayer(performances);
        
        // 팀 통계 계산
        CustomPlayerResultDTO.CustomTeamStats teamStats = calculateTeamStats(performances);
        
        // 게임 결과 결정
        boolean isWin = determineGameResult(teamStats, request.getDifficulty());
        
        return CustomPlayerResultDTO.builder()
            .userId(request.getUserId())
            .gameMode(request.getGameMode())
            .userTeamName("Custom Team")
            .opponentTeamName("CPU Team")
            .playedAt(endTime)
            
            .userScore(isWin ? random.nextInt(5) + 5 : random.nextInt(3) + 1)
            .opponentScore(isWin ? random.nextInt(3) + 1 : random.nextInt(5) + 5)
            .isWin(isWin)
            .winner(isWin ? "Custom Team" : "CPU Team")
            
            .playerPerformances(performances)
            .mvpPlayer(mvpPlayer)
            .userTeamStats(teamStats)
            
            .earnedExperience(calculateEarnedExperience(isWin, performances))
            .earnedCoins(calculateEarnedCoins(isWin, performances))
            .achievements(generateAchievements(performances))
            
            .gameLog(generateGameLog(performances))
            .gameSummary(generateGameSummary(isWin, mvpPlayer))
            .gameStartTime(startTime)
            .gameEndTime(endTime)
            
            .build();
    }
    
    /**
     * 개별 선수 성과 계산
     */
    private CustomPlayerResultDTO.CustomPlayerPerformance calculatePlayerPerformance(
        CustomPlayer player, String difficulty) {
        
        // 난이도에 따른 성과 조정
        double difficultyMultiplier = getDifficultyMultiplier(difficulty);
        
        // 능력치 기반 성과 계산
        int totalStats = player.getPower() + player.getContact() + player.getSpeed() + 
                        player.getFielding() + player.getArm();
        
        double performanceBase = (totalStats / 250.0) * difficultyMultiplier;
        
        // 랜덤 요소 추가
        performanceBase += (random.nextDouble() - 0.5) * 0.3;
        
        int atBats = 4 + random.nextInt(2);
        int hits = (int) (atBats * Math.max(0.1, Math.min(0.8, performanceBase)));
        int homeRuns = hits > 2 && random.nextDouble() < (player.getPower() / 100.0) ? 1 : 0;
        int rbis = hits + (homeRuns * 2) + random.nextInt(2);
        
        return CustomPlayerResultDTO.CustomPlayerPerformance.builder()
            .playerName(player.getPlayerName())
            .position(extractPosition(player.getSpecialTraits()))
            .atBats(atBats)
            .hits(hits)
            .homeRuns(homeRuns)
            .rbis(rbis)
            .runs(hits + random.nextInt(2))
            .battingAverage(atBats > 0 ? (double) hits / atBats : 0.0)
            .performanceRating((int) (performanceBase * 10))
            .performanceDescription(generatePerformanceDescription(hits, homeRuns, rbis))
            .specialPlays(generateSpecialPlays(player))
            .build();
    }
    
    /**
     * 선수 경험치 업데이트
     */
    private void updatePlayerExperience(List<CustomPlayer> players, CustomPlayerResultDTO result) {
        int baseExp = result.isWin() ? 50 : 25;
        
        for (int i = 0; i < players.size() && i < result.getPlayerPerformances().size(); i++) {
            CustomPlayer player = players.get(i);
            CustomPlayerResultDTO.CustomPlayerPerformance performance = 
                result.getPlayerPerformances().get(i);
            
            // 성과에 따른 추가 경험치
            int bonusExp = performance.getPerformanceRating() * 5;
            int totalExp = baseExp + bonusExp;
            
            player.setExperience(player.getExperience() + totalExp);
            
            // 레벨업 처리
            checkLevelUp(player);
            
            customPlayerRepository.save(player);
        }
    }
    
    /**
     * 레벨업 처리
     */
    private void checkLevelUp(CustomPlayer player) {
        int requiredExp = player.getLevel() * 100; // 레벨당 100 경험치 필요
        
        while (player.getExperience() >= requiredExp) {
            player.setExperience(player.getExperience() - requiredExp);
            player.setLevel(player.getLevel() + 1);
            
            // 레벨업 시 능력치 향상
            improveStats(player);
            
            requiredExp = player.getLevel() * 100;
        }
    }
    
    /**
     * 능력치 향상
     */
    private void improveStats(CustomPlayer player) {
        // 랜덤하게 2-3개 능력치 향상
        int improvements = 2 + random.nextInt(2);
        
        for (int i = 0; i < improvements; i++) {
            int statChoice = random.nextInt(5);
            switch (statChoice) {
                case 0: player.setPower(Math.min(99, player.getPower() + 1)); break;
                case 1: player.setContact(Math.min(99, player.getContact() + 1)); break;
                case 2: player.setSpeed(Math.min(99, player.getSpeed() + 1)); break;
                case 3: player.setFielding(Math.min(99, player.getFielding() + 1)); break;
                case 4: player.setArm(Math.min(99, player.getArm() + 1)); break;
            }
        }
    }
    
    /**
     * 사용자의 활성 커스텀 선수 조회
     */
    @Transactional(readOnly = true)
    public List<CustomPlayer> getActiveCustomPlayers(Integer userId) {
        return customPlayerRepository.findActiveCustomPlayersByUserId(userId);
    }
    
    /**
     * 커스텀 선수 통계 조회
     */
    @Transactional(readOnly = true)
    public CustomPlayerStatistics getCustomPlayerStatistics(Integer userId) {
        Long totalPlayers = customPlayerRepository.countByUserId(userId);
        CustomPlayer topPlayer = customPlayerRepository.findTopByUserIdOrderByLevelDescExperienceDesc(userId)
                                    .orElse(null);
        
        Object[] avgStats = customPlayerRepository.getAverageStatsByUserId(userId);
        List<Object[]> levelDistribution = customPlayerRepository.getLevelDistributionByUserId(userId);
        
        return new CustomPlayerStatistics(
            totalPlayers,
            topPlayer != null ? topPlayer.getLevel() : 0,
            topPlayer != null ? topPlayer.getPlayerName() : "없음",
            avgStats,
            levelDistribution
        );
    }
    
    // Helper methods
    private void validatePlayerStats(CustomPlayerRequestDTO.CustomPlayerInfo playerInfo) {
        // 총 능력치 제한 (예: 최대 400)
        int totalStats = 0;
        if (playerInfo.getBattingPower() != null) totalStats += playerInfo.getBattingPower();
        if (playerInfo.getContactAbility() != null) totalStats += playerInfo.getContactAbility();
        if (playerInfo.getSpeed() != null) totalStats += playerInfo.getSpeed();
        if (playerInfo.getFielding() != null) totalStats += playerInfo.getFielding();
        if (playerInfo.getThrowingPower() != null) totalStats += playerInfo.getThrowingPower();
        
        if (totalStats > 400) {
            throw new IllegalArgumentException("총 능력치가 제한을 초과했습니다.");
        }
    }
    
    private String buildSpecialTraits(CustomPlayerRequestDTO.CustomPlayerInfo playerInfo) {
        // JSON 형태로 특성 정보 구성
        StringBuilder traits = new StringBuilder("{");
        if (playerInfo.getPosition() != null) {
            traits.append("\"position\":\"").append(playerInfo.getPosition()).append("\",");
        }
        if (playerInfo.getPlayerType() != null) {
            traits.append("\"type\":\"").append(playerInfo.getPlayerType()).append("\",");
        }
        traits.append("\"active\":true,\"favorite\":false}");
        return traits.toString();
    }
    
    private double getDifficultyMultiplier(String difficulty) {
        if ("EASY".equals(difficulty)) return 1.2;
        if ("HARD".equals(difficulty)) return 0.8;
        return 1.0; // NORMAL
    }
    
    private String extractPosition(String specialTraits) {
        if (specialTraits != null && specialTraits.contains("position")) {
            // JSON 파싱하여 포지션 추출 (간단 구현)
            return "1B"; // 기본값
        }
        return "OF";
    }
    
    private String selectMvpPlayer(List<CustomPlayerResultDTO.CustomPlayerPerformance> performances) {
        return performances.stream()
            .max((p1, p2) -> Integer.compare(p1.getPerformanceRating(), p2.getPerformanceRating()))
            .map(CustomPlayerResultDTO.CustomPlayerPerformance::getPlayerName)
            .orElse("없음");
    }
    
    private CustomPlayerResultDTO.CustomTeamStats calculateTeamStats(
        List<CustomPlayerResultDTO.CustomPlayerPerformance> performances) {
        
        int totalHits = performances.stream().mapToInt(p -> p.getHits()).sum();
        int totalRuns = performances.stream().mapToInt(p -> p.getRuns()).sum();
        int totalAtBats = performances.stream().mapToInt(p -> p.getAtBats()).sum();
        
        return CustomPlayerResultDTO.CustomTeamStats.builder()
            .totalHits(totalHits)
            .totalRuns(totalRuns)
            .totalErrors(random.nextInt(3))
            .teamBattingAverage(totalAtBats > 0 ? (double) totalHits / totalAtBats : 0.0)
            .teamEra(3.0 + random.nextDouble() * 2.0)
            .leftOnBase(totalHits + random.nextInt(5))
            .teamMvp(selectMvpPlayer(performances))
            .build();
    }
    
    private boolean determineGameResult(CustomPlayerResultDTO.CustomTeamStats teamStats, String difficulty) {
        double winProbability = 0.5; // 기본 50%
        
        // 팀 성과에 따른 승률 조정
        if (teamStats.getTeamBattingAverage() > 0.3) winProbability += 0.2;
        if (teamStats.getTotalRuns() > 5) winProbability += 0.1;
        
        // 난이도에 따른 조정
        if ("EASY".equals(difficulty)) winProbability += 0.2;
        else if ("HARD".equals(difficulty)) winProbability -= 0.2;
        
        return random.nextDouble() < winProbability;
    }
    
    private Integer calculateEarnedExperience(boolean isWin, 
        List<CustomPlayerResultDTO.CustomPlayerPerformance> performances) {
        int baseExp = isWin ? 100 : 50;
        int bonusExp = performances.stream().mapToInt(p -> p.getPerformanceRating()).sum();
        return baseExp + bonusExp;
    }
    
    private Integer calculateEarnedCoins(boolean isWin, 
        List<CustomPlayerResultDTO.CustomPlayerPerformance> performances) {
        int baseCoins = isWin ? 200 : 100;
        int bonusCoins = performances.stream().mapToInt(p -> p.getHits() * 10).sum();
        return baseCoins + bonusCoins;
    }
    
    private List<String> generateAchievements(List<CustomPlayerResultDTO.CustomPlayerPerformance> performances) {
        List<String> achievements = new ArrayList<>();
        
        for (CustomPlayerResultDTO.CustomPlayerPerformance p : performances) {
            if (p.getHomeRuns() > 0) achievements.add("홈런왕");
            if (p.getHits() >= 3) achievements.add("멀티히트");
            if (p.getBattingAverage() >= 0.5) achievements.add("완벽한 경기");
        }
        
        return achievements;
    }
    
    private List<String> generateGameLog(List<CustomPlayerResultDTO.CustomPlayerPerformance> performances) {
        List<String> log = new ArrayList<>();
        log.add("경기 시작");
        
        for (CustomPlayerResultDTO.CustomPlayerPerformance p : performances) {
            if (p.getHits() > 0) {
                log.add(p.getPlayerName() + "이(가) " + p.getHits() + "안타를 기록했습니다.");
            }
            if (p.getHomeRuns() > 0) {
                log.add(p.getPlayerName() + "이(가) 홈런을 터뜨렸습니다!");
            }
        }
        
        log.add("경기 종료");
        return log;
    }
    
    private String generateGameSummary(boolean isWin, String mvpPlayer) {
        return (isWin ? "승리! " : "패배! ") + "MVP: " + mvpPlayer;
    }
    
    private String generatePerformanceDescription(int hits, int homeRuns, int rbis) {
        if (homeRuns > 0) return "홈런 포함 " + hits + "안타, " + rbis + "타점의 활약";
        if (hits >= 3) return hits + "안타 멀티히트 활약";
        if (hits >= 1) return hits + "안타로 공격에 기여";
        return "아쉬운 타석 결과";
    }
    
    private List<String> generateSpecialPlays(CustomPlayer player) {
        List<String> plays = new ArrayList<>();
        
        if (player.getPower() > 80 && random.nextBoolean()) {
            plays.add("파워풀한 스윙");
        }
        if (player.getSpeed() > 80 && random.nextBoolean()) {
            plays.add("도루 성공");
        }
        if (player.getFielding() > 80 && random.nextBoolean()) {
            plays.add("환상적인 수비");
        }
        
        return plays;
    }
    
    private void saveCustomPlayerGameResult(CustomPlayerResultDTO result) {
        // TODO: 게임 결과를 별도 테이블에 저장하는 로직
        // CustomPlayerGameResult 엔티티 필요
    }
    
    /**
     * 커스텀 선수 통계 정보
     */
    public static class CustomPlayerStatistics {
        private final Long totalPlayers;
        private final Integer maxLevel;
        private final String topPlayerName;
        private final Object[] averageStats;
        private final List<Object[]> levelDistribution;
        
        public CustomPlayerStatistics(Long totalPlayers, Integer maxLevel, String topPlayerName,
                                    Object[] averageStats, List<Object[]> levelDistribution) {
            this.totalPlayers = totalPlayers;
            this.maxLevel = maxLevel;
            this.topPlayerName = topPlayerName;
            this.averageStats = averageStats;
            this.levelDistribution = levelDistribution;
        }
        
        public Long getTotalPlayers() { return totalPlayers; }
        public Integer getMaxLevel() { return maxLevel; }
        public String getTopPlayerName() { return topPlayerName; }
        public Object[] getAverageStats() { return averageStats; }
        public List<Object[]> getLevelDistribution() { return levelDistribution; }
    }
}
