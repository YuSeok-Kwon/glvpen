package com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.service;

import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.domain.CustomPlayer;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.repository.CustomPlayerRepository;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto.CustomPlayerRequestDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto.CustomPlayerResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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

        // 레벨업 시 훈련 포인트 5개 부여
        player.setTrainingPoints(player.getTrainingPoints() + 5);
        log.info("레벨업! 선수: {}, 레벨: {}, 훈련 포인트 +5", player.getPlayerName(), player.getLevel());
    }
    
    /**
     * 게임 결과 저장
     */
    private void saveCustomPlayerGameResult(CustomPlayerResultDTO result) {
        // GameResultRepository를 통해 저장 (별도 구현 필요)
        log.info("게임 결과 저장: {}", result);
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

    /**
     * 모드별 커스텀 선수 목록 조회
     */
    public List<CustomPlayer> getCustomPlayersByMode(Integer userId, String mode) {
        return customPlayerRepository.findByUserIdAndMode(userId, mode);
    }

    /**
     * ID로 커스텀 선수 조회
     */
    public CustomPlayer getCustomPlayerById(Long playerId) {
        return customPlayerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("선수를 찾을 수 없습니다. ID: " + playerId));
    }

    /**
     * 커스텀 선수 정보 수정
     */
    public CustomPlayer updateCustomPlayer(Long playerId, CustomPlayerRequestDTO.CustomPlayerInfo playerInfo) {
        CustomPlayer player = getCustomPlayerById(playerId);

        // 능력치 검증
        validatePlayerStats(playerInfo);

        // 정보 업데이트
        if (playerInfo.getPlayerName() != null) {
            player.setPlayerName(playerInfo.getPlayerName());
        }
        if (playerInfo.getBattingPower() != null) {
            player.setPower(playerInfo.getBattingPower());
        }
        if (playerInfo.getContactAbility() != null) {
            player.setContact(playerInfo.getContactAbility());
        }
        if (playerInfo.getSpeed() != null) {
            player.setSpeed(playerInfo.getSpeed());
        }
        if (playerInfo.getFielding() != null) {
            player.setFielding(playerInfo.getFielding());
        }
        if (playerInfo.getThrowingPower() != null) {
            player.setArm(playerInfo.getThrowingPower());
        }

        player.setUpdatedAt(LocalDateTime.now());

        return customPlayerRepository.save(player);
    }

    /**
     * 커스텀 선수 삭제
     */
    public void deleteCustomPlayer(Long playerId) {
        CustomPlayer player = getCustomPlayerById(playerId);
        customPlayerRepository.delete(player);
    }

    /**
     * 능력치 할당 (레벨업 시)
     */
    public CustomPlayer allocateStats(Long playerId, CustomPlayerRequestDTO.StatAllocation statAllocation) {
        CustomPlayer player = getCustomPlayerById(playerId);

        // RPG 모드에서만 가능
        if (!player.isRpgMode()) {
            throw new IllegalArgumentException("HITTER 모드에서는 능력치 할당이 불가능합니다.");
        }

        // 할당 가능 포인트 확인 (레벨당 5포인트)
        int totalAllocated = statAllocation.getPower() + statAllocation.getContact() +
                           statAllocation.getSpeed() + statAllocation.getFielding() +
                           statAllocation.getArm();

        int availablePoints = player.getLevel() * 5; // 예: 레벨당 5 포인트
        int currentTotal = player.getPower() + player.getContact() + player.getSpeed() +
                          player.getFielding() + player.getArm();

        if (currentTotal + totalAllocated > 250 + availablePoints) {
            throw new IllegalArgumentException("할당 가능한 포인트를 초과했습니다.");
        }

        // 능력치 적용
        player.setPower(player.getPower() + statAllocation.getPower());
        player.setContact(player.getContact() + statAllocation.getContact());
        player.setSpeed(player.getSpeed() + statAllocation.getSpeed());
        player.setFielding(player.getFielding() + statAllocation.getFielding());
        player.setArm(player.getArm() + statAllocation.getArm());

        player.setUpdatedAt(LocalDateTime.now());

        return customPlayerRepository.save(player);
    }

    /**
     * 경기 결과 반영 (경험치/레벨업)
     */
    public CustomPlayer applyMatchResult(Long playerId, CustomPlayerResultDTO matchResult) {
        CustomPlayer player = getCustomPlayerById(playerId);

        // RPG 모드에서만 경험치 시스템 작동
        if (!player.isRpgMode()) {
            return player;
        }

        // 경험치 계산 (승리 시 보너스)
        int baseExp = 50;
        int winBonus = matchResult.isWin() ? 30 : 0;
        int totalExp = baseExp + winBonus;

        player.setExperience(player.getExperience() + totalExp);

        // 레벨업 체크 (100 경험치당 1레벨)
        int requiredExp = player.getLevel() * 100;
        while (player.getExperience() >= requiredExp) {
            player.setLevel(player.getLevel() + 1);
            player.setExperience(player.getExperience() - requiredExp);
            requiredExp = player.getLevel() * 100;

            log.info("게임 결과 저장: {}", matchResult);
        }

        player.setUpdatedAt(LocalDateTime.now());

        return customPlayerRepository.save(player);
    }

    /**
     * 선수 훈련 실행
     */
    public com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto.CustomPlayerResponseDTO.TrainingResult trainPlayer(
            CustomPlayerRequestDTO.TrainingRequest request) {

        CustomPlayer player = getCustomPlayerById(request.getPlayerId());

        // RPG 모드 확인
        if (!player.isRpgMode()) {
            throw new IllegalArgumentException("HITTER 모드에서는 훈련이 불가능합니다.");
        }

        // 훈련 포인트 확인
        int requiredPoints = request.getIntensity() != null ? request.getIntensity() : 1;
        if (player.getTrainingPoints() < requiredPoints) {
            throw new IllegalArgumentException("훈련 포인트가 부족합니다. (필요: " + requiredPoints + ", 보유: " + player.getTrainingPoints() + ")");
        }

        // 훈련 전 능력치 저장
        com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto.CustomPlayerResponseDTO.Stats statsBefore =
            com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto.CustomPlayerResponseDTO.Stats.builder()
                .power(player.getPower())
                .contact(player.getContact())
                .speed(player.getSpeed())
                .fielding(player.getFielding())
                .arm(player.getArm())
                .defense(player.getDefense())
                .build();

        // 훈련 실행
        boolean success = executeTraining(player, request.getTrainingType(), requiredPoints);

        // 훈련 포인트 차감
        player.setTrainingPoints(player.getTrainingPoints() - requiredPoints);

        // 훈련 후 능력치
        com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto.CustomPlayerResponseDTO.Stats statsAfter =
            com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto.CustomPlayerResponseDTO.Stats.builder()
                .power(player.getPower())
                .contact(player.getContact())
                .speed(player.getSpeed())
                .fielding(player.getFielding())
                .arm(player.getArm())
                .defense(player.getDefense())
                .build();

        player.setUpdatedAt(LocalDateTime.now());
        customPlayerRepository.save(player);

        // 결과 메시지 생성
        String message = generateTrainingMessage(request.getTrainingType(), success);

        // 다음 레벨까지 필요한 경험치
        int expToNextLevel = player.getLevel() * 100 - player.getExperience();

        return com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto.CustomPlayerResponseDTO.TrainingResult.builder()
                .playerId(player.getId())
                .playerName(player.getPlayerName())
                .trainingType(request.getTrainingType())
                .success(success)
                .message(message)
                .statsBefore(statsBefore)
                .statsAfter(statsAfter)
                .leveledUp(false)
                .currentLevel(player.getLevel())
                .currentExperience(player.getExperience())
                .experienceToNextLevel(expToNextLevel)
                .remainingTrainingPoints(player.getTrainingPoints())
                .build();
    }

    /**
     * 훈련 실행 로직
     */
    private boolean executeTraining(CustomPlayer player, String trainingType, int intensity) {
        // 성공률 계산 (강도가 높을수록 성공률 감소, 능력치가 높을수록 성공률 감소)
        double baseSuccessRate = 0.8; // 80% 기본 성공률
        double intensityPenalty = (intensity - 1) * 0.1; // 강도 1당 -10%

        int currentStat = getCurrentStatByType(player, trainingType);
        double statPenalty = Math.min(currentStat / 100.0 * 0.3, 0.3); // 능력치가 높을수록 최대 -30%

        double successRate = Math.max(baseSuccessRate - intensityPenalty - statPenalty, 0.3); // 최소 30%

        boolean success = random.nextDouble() < successRate;

        if (success) {
            // 성공 시 능력치 증가 (강도에 비례)
            int statIncrease = intensity + random.nextInt(intensity);
            applyStatIncrease(player, trainingType, statIncrease);
            log.info("훈련 성공! 선수: {}, 타입: {}, 증가: +{}", player.getPlayerName(), trainingType, statIncrease);
        } else {
            log.info("훈련 실패. 선수: {}, 타입: {}", player.getPlayerName(), trainingType);
        }

        return success;
    }

    /**
     * 훈련 타입에 따른 현재 능력치 조회
     */
    private int getCurrentStatByType(CustomPlayer player, String trainingType) {
        switch (trainingType) {
            case "BATTING":
                return (player.getPower() + player.getContact()) / 2;
            case "RUNNING":
                return player.getSpeed();
            case "FIELDING":
                return player.getFielding();
            case "STAMINA":
                return (player.getPower() + player.getContact() + player.getSpeed() + player.getFielding() + player.getArm()) / 5;
            default:
                return 50;
        }
    }

    /**
     * 훈련 타입에 따른 능력치 증가 적용
     */
    private void applyStatIncrease(CustomPlayer player, String trainingType, int increase) {
        switch (trainingType) {
            case "BATTING":
                // 타격 훈련: 파워 + 컨택트 증가
                player.setPower(Math.min(player.getPower() + increase, 99));
                player.setContact(Math.min(player.getContact() + increase, 99));
                break;
            case "RUNNING":
                // 주루 훈련: 스피드 증가
                player.setSpeed(Math.min(player.getSpeed() + increase * 2, 99));
                break;
            case "FIELDING":
                // 수비 훈련: 수비력 + 어깨 증가
                player.setFielding(Math.min(player.getFielding() + increase, 99));
                player.setArm(Math.min(player.getArm() + increase, 99));
                break;
            case "STAMINA":
                // 체력 훈련: 모든 능력치 소폭 증가
                player.setPower(Math.min(player.getPower() + 1, 99));
                player.setContact(Math.min(player.getContact() + 1, 99));
                player.setSpeed(Math.min(player.getSpeed() + 1, 99));
                player.setFielding(Math.min(player.getFielding() + 1, 99));
                player.setArm(Math.min(player.getArm() + 1, 99));
                break;
        }
    }

    /**
     * 훈련 결과 메시지 생성
     */
    private String generateTrainingMessage(String trainingType, boolean success) {
        String typeName;
        switch (trainingType) {
            case "BATTING":
                typeName = "타격";
                break;
            case "RUNNING":
                typeName = "주루";
                break;
            case "FIELDING":
                typeName = "수비";
                break;
            case "STAMINA":
                typeName = "체력";
                break;
            default:
                typeName = "훈련";
        }

        if (success) {
            return typeName + " 훈련에 성공했습니다! 능력치가 상승했습니다.";
        } else {
            return typeName + " 훈련에 실패했습니다. 다음 기회에 도전하세요.";
        }
    }

    /**
     * 사용자의 모든 선수 조회 (훈련 가능 선수 목록)
     */
    public List<CustomPlayer> getTrainablePlayers(Integer userId) {
        return customPlayerRepository.findByUserIdAndMode(userId, "RPG");
    }
}
