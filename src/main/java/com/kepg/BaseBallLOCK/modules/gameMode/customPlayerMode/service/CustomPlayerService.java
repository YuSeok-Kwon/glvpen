package com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.service;

import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.domain.CustomPlayer;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.repository.CustomPlayerRepository;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto.CustomPlayerRequestDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto.CustomPlayerResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
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
    
    @Qualifier("customPlayerModeRepository")
    private final CustomPlayerRepository customPlayerRepository;
    private final Random random = new Random();

    /**
     * 커스텀 선수 생성 (RPG 모드용)
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
        boolean isWin = determineGameOutcome(teamStats, request.getDifficulty());
        
        return CustomPlayerResultDTO.builder()
                .userId(request.getUserId())
                .gameMode(request.getGameMode())
                .startTime(startTime)
                .endTime(endTime)
                .isWin(isWin)
                .difficulty(request.getDifficulty())
                .userTeamStats(teamStats)
                .playerPerformances(performances)
                .mvpPlayer(mvpPlayer)
                .build();
    }
    
    /**
     * 선수 성과 계산
     */
    private CustomPlayerResultDTO.CustomPlayerPerformance calculatePlayerPerformance(
        CustomPlayer player, String difficulty) {
        
        // 난이도에 따른 성과 변동성
        double difficultyFactor = 1.0;
        if (difficulty.equals("EASY")) {
            difficultyFactor = 1.2;
        } else if (difficulty.equals("HARD")) {
            difficultyFactor = 0.8;
        }
        
        double performanceFactor = 1.0 + (random.nextDouble() - 0.5) * difficultyFactor;
        
        // 능력치 기반 성과 계산
        int hits = (int) (player.getContact() * performanceFactor / 20);
        int homeRuns = (int) (player.getPower() * performanceFactor / 50);
        int stolenBases = (int) (player.getSpeed() * performanceFactor / 40);
         return CustomPlayerResultDTO.CustomPlayerPerformance.builder()
                .playerId(player.getId())
                .playerName(player.getPlayerName())
            .atBats(4)
            .hits(hits)
            .homeRuns(homeRuns)
            .stolenBases(stolenBases)
            .build();
    }
    
    /**
     * 선수 경험치 업데이트
     */
    private void updatePlayerExperience(List<CustomPlayer> players, CustomPlayerResultDTO result) {
        for (CustomPlayer player : players) {
            int earnedExp = calculateExperience(player, result);
            player.setExperience(player.getExperience() + earnedExp);
            
            // 레벨업 체크
            checkLevelUp(player);
        }
    }
    
    /**
     * 경험치 계산
     */
    private int calculateExperience(com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.domain.CustomPlayer player, CustomPlayerResultDTO result) {
        // 기본 경험치
        int baseExp = result.isWin() ? 100 : 50;
        
        // MVP 보너스
        if (result.getMvpPlayer() != null && result.getMvpPlayer().equals(player.getPlayerName())) {
            baseExp += 50;
        }
        
        return baseExp;
    }
    
    /**
     * 레벨업 체크
     */
    private void checkLevelUp(com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.domain.CustomPlayer player) {
        int requiredExp = player.getLevel() * 100; // 레벨업에 필요한 경험치
        
        if (player.getExperience() >= requiredExp) {
            player.setLevel(player.getLevel() + 1);
            player.setExperience(player.getExperience() - requiredExp);
            
            // 레벨업에 따른 능력치 성장
            growPlayerStats(player);
        }
    }
    
    /**
     * 선수 능력치 성장
     */
    private void growPlayerStats(com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.domain.CustomPlayer player) {
        player.setPower(player.getPower() + random.nextInt(3) + 1);
        player.setContact(player.getContact() + random.nextInt(3) + 1);
        player.setSpeed(player.getSpeed() + random.nextInt(3) + 1);
        player.setFielding(player.getFielding() + random.nextInt(3) + 1);
        player.setArm(player.getArm() + random.nextInt(3) + 1);
    }
    
    /**
     * 게임 결과 저장
     */
    private void saveCustomPlayerGameResult(CustomPlayerResultDTO result) {
        // GameResultRepository를 통해 저장 (별도 구현 필요)
        System.out.println("게임 결과 저장: " + result);
    }
    
    /**
     * 활성화된 커스텀 선수 목록 조회
     */
    public List<CustomPlayer> getActiveCustomPlayers(Integer userId) {
        // Mock 데이터 (실제로는 DB에서 조회)
        return customPlayerRepository.findAll(); // Example method
    }
    
    /**
     * 선수 능력치 검증
     */
    private void validatePlayerStats(CustomPlayerRequestDTO.CustomPlayerInfo playerInfo) {
        int totalStats = (playerInfo.getBattingPower() != null ? playerInfo.getBattingPower() : 0) +
                         (playerInfo.getContactAbility() != null ? playerInfo.getContactAbility() : 0) +
                         (playerInfo.getSpeed() != null ? playerInfo.getSpeed() : 0) +
                         (playerInfo.getFielding() != null ? playerInfo.getFielding() : 0) +
                         (playerInfo.getThrowingPower() != null ? playerInfo.getThrowingPower() : 0);
        
        if (totalStats > 250) { // 예: 초기 스탯 총합 250 제한
            throw new IllegalArgumentException("능력치 총합이 제한을 초과했습니다.");
        }
    }
    
    /**
     * 선수 특성 조합
     */
    private String buildSpecialTraits(CustomPlayerRequestDTO.CustomPlayerInfo playerInfo) {
        // 선택된 특성을 조합하여 JSON 문자열로 반환
        return "{}";
    }
    
    /**
     * 팀 통계 계산
     */
    private CustomPlayerResultDTO.CustomTeamStats calculateTeamStats(List<CustomPlayerResultDTO.CustomPlayerPerformance> performances) {
        int totalHits = performances.stream().mapToInt(CustomPlayerResultDTO.CustomPlayerPerformance::getHits).sum();
        int totalHomeRuns = performances.stream().mapToInt(CustomPlayerResultDTO.CustomPlayerPerformance::getHomeRuns).sum();
        
        return CustomPlayerResultDTO.CustomTeamStats.builder()
            .runs(totalHits + totalHomeRuns) // 간단한 득점 계산
            .hits(totalHits)
            .errors(random.nextInt(3))
            .build();
    }
    
    /**
     * MVP 선수 선정
     */
    private String selectMvpPlayer(List<CustomPlayerResultDTO.CustomPlayerPerformance> performances) {
        return performances.stream()
            .max((p1, p2) -> Integer.compare(p1.getHits() + p1.getHomeRuns() * 2, p2.getHits() + p2.getHomeRuns() * 2))
            .map(CustomPlayerResultDTO.CustomPlayerPerformance::getPlayerName)
            .orElse(null);
    }
    
    /**
     * 게임 승패 결정
     */
    private boolean determineGameOutcome(CustomPlayerResultDTO.CustomTeamStats teamStats, String difficulty) {
        // 난이도에 따른 상대팀 점수 생성
        int opponentRuns;
        switch (difficulty) {
            case "easy":
                opponentRuns = random.nextInt(teamStats.getRuns() > 0 ? teamStats.getRuns() : 1);
                break;
            case "normal":
                opponentRuns = random.nextInt(5);
                break;
            case "hard":
                opponentRuns = random.nextInt(10) + 3;
                break;
            default:
                opponentRuns = 0;
        }
        
        return teamStats.getRuns() > opponentRuns;
    }
}
