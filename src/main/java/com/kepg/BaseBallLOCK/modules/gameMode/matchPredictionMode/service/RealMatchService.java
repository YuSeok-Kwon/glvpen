package com.kepg.BaseBallLOCK.modules.gameMode.matchPredictionMode.service;

import com.kepg.BaseBallLOCK.modules.gameMode.matchPredictionMode.domain.RealMatchResult;
import com.kepg.BaseBallLOCK.modules.gameMode.matchPredictionMode.dto.RealMatchRequestDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.matchPredictionMode.dto.RealMatchResultDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.matchPredictionMode.repository.RealMatchResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * RealMatch 게임 서비스
 * - 실제 KBO 경기 기반 예측 게임
 * - 경기 일정 연동 및 결과 비교
 * - 포인트 베팅 시스템
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RealMatchService {
    
    private final RealMatchResultRepository realMatchResultRepository;
    // private final ScheduleService scheduleService;  // 실제 경기 일정 서비스 (추후 구현)
    // private final TeamService teamService;          // 팀 정보 서비스
    
    /**
     * 예측 게임 실행
     */
    public RealMatchResultDTO playPredictionGame(RealMatchRequestDTO request) {
        // 1. 실제 경기 정보 조회
        // KboSchedule schedule = scheduleService.getScheduleById(request.getScheduleId());
        
        // 2. 이미 예측한 경기인지 확인
        Optional<RealMatchResult> existingResult = realMatchResultRepository
            .findByUserIdAndScheduleId(request.getUserId(), request.getScheduleId());
        
        if (existingResult.isPresent()) {
            throw new IllegalStateException("이미 예측한 경기입니다.");
        }
        
        // 3. 예측 유효성 검증
        validatePrediction(request);
        
        // 4. 게임 시뮬레이션 (실제 결과가 없는 경우)
        RealMatchResultDTO result = simulateRealMatch(request);
        
        // 5. 결과 저장
        saveGameResult(result);
        
        return result;
    }
    
    /**
     * 실제 경기 결과와 예측 비교
     */
    public RealMatchResultDTO compareWithActualResult(Long scheduleId) {
        // 실제 경기 결과 조회 로직
        // TODO: 실제 KBO API 또는 크롤링 연동 필요
        return null;
    }
    
    /**
     * RealMatch 시뮬레이션
     */
    private RealMatchResultDTO simulateRealMatch(RealMatchRequestDTO request) {
        LocalDateTime now = LocalDateTime.now();
        
        // TODO: 실제 경기 데이터 기반 시뮬레이션
        // 현재는 Mock 데이터로 구현
        
        return RealMatchResultDTO.builder()
            .userId(request.getUserId())
            .scheduleId(request.getScheduleId())
            .userTeamName("사용자팀")  // TODO: 실제 팀명 조회
            .opponentTeamName("상대팀")
            .gameTime(request.getGameTime())
            .stadium("잠실야구장")
            
            // Mock 실제 결과
            .actualUserScore(5)
            .actualOpponentScore(3)
            .actualWinner("사용자팀")
            .actualMvp("김선수")
            
            // 사용자 예측
            .predictedUserScore(request.getPredictedUserScore())
            .predictedOpponentScore(request.getPredictedOpponentScore())
            .predictedWinner(request.getUserPrediction())
            .predictedMvp(request.getPredictedMvp())
            
            // 예측 정확도 계산
            .scoreExactMatch(isScoreExactMatch(request))
            .winnerCorrect(isWinnerCorrect(request))
            .mvpCorrect(isMvpCorrect(request))
            .predictionAccuracy(calculatePredictionAccuracy(request))
            
            // 포인트 계산
            .betPoints(request.getBetPoints())
            .earnedPoints(calculateEarnedPoints(request))
            .rewardType(determineRewardType(request))
            
            .gameLog("게임 로그...")
            .matchSummary("경기 요약...")
            .playedAt(now)
            .season("2024")
            .round(1)
            .matchType("정규시즌")
            
            .build();
    }
    
    /**
     * 예측 유효성 검증
     */
    private void validatePrediction(RealMatchRequestDTO request) {
        if (request.getGameTime().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("이미 종료된 경기입니다.");
        }
        
        if (request.getBetPoints() != null && request.getBetPoints() < 0) {
            throw new IllegalArgumentException("베팅 포인트는 0 이상이어야 합니다.");
        }
        
        // 추가 유효성 검증...
    }
    
    /**
     * 정확한 스코어 예측 여부
     */
    private boolean isScoreExactMatch(RealMatchRequestDTO request) {
        // Mock 로직 - 실제로는 경기 결과와 비교
        return request.getPredictedUserScore() == 5 && request.getPredictedOpponentScore() == 3;
    }
    
    /**
     * 승리팀 예측 정확도
     */
    private boolean isWinnerCorrect(RealMatchRequestDTO request) {
        // Mock 로직
        return "사용자팀".equals(request.getUserPrediction());
    }
    
    /**
     * MVP 예측 정확도
     */
    private boolean isMvpCorrect(RealMatchRequestDTO request) {
        // Mock 로직
        return "김선수".equals(request.getPredictedMvp());
    }
    
    /**
     * 전체 예측 정확도 계산
     */
    private double calculatePredictionAccuracy(RealMatchRequestDTO request) {
        double accuracy = 0.0;
        int totalPredictions = 0;
        
        // 승리팀 예측
        if (request.getUserPrediction() != null) {
            totalPredictions++;
            if (isWinnerCorrect(request)) accuracy += 1.0;
        }
        
        // 스코어 예측
        if (request.getPredictedUserScore() != null && request.getPredictedOpponentScore() != null) {
            totalPredictions++;
            if (isScoreExactMatch(request)) accuracy += 1.0;
        }
        
        // MVP 예측
        if (request.getPredictedMvp() != null) {
            totalPredictions++;
            if (isMvpCorrect(request)) accuracy += 1.0;
        }
        
        return totalPredictions > 0 ? accuracy / totalPredictions : 0.0;
    }
    
    /**
     * 획득 포인트 계산
     */
    private Integer calculateEarnedPoints(RealMatchRequestDTO request) {
        int basePoints = 100;
        int bonusMultiplier = 1;
        
        // 정확도에 따른 보너스
        double accuracy = calculatePredictionAccuracy(request);
        if (accuracy >= 1.0) bonusMultiplier = 5;      // 완벽 예측
        else if (accuracy >= 0.67) bonusMultiplier = 3; // 2/3 이상 맞춤
        else if (accuracy >= 0.33) bonusMultiplier = 2; // 1/3 이상 맞춤
        
        // 베팅 포인트에 따른 추가 보상
        int betBonus = request.getBetPoints() != null ? request.getBetPoints() / 10 : 0;
        
        return basePoints * bonusMultiplier + betBonus;
    }
    
    /**
     * 보상 타입 결정
     */
    private String determineRewardType(RealMatchRequestDTO request) {
        double accuracy = calculatePredictionAccuracy(request);
        
        if (accuracy >= 1.0) return "PERFECT_PREDICTION";
        else if (accuracy >= 0.67) return "GOOD_PREDICTION";
        else if (accuracy >= 0.33) return "FAIR_PREDICTION";
        else return "PARTICIPATION";
    }
    
    /**
     * 게임 결과 저장
     */
    private void saveGameResult(RealMatchResultDTO resultDto) {
        RealMatchResult entity = RealMatchResult.builder()
            .userId(resultDto.getUserId())
            .scheduleId(resultDto.getScheduleId())
            .userTeamId(1)  // TODO: 실제 팀 ID 설정
            .opponentTeamId(2)
            
            .actualUserScore(resultDto.getActualUserScore())
            .actualOpponentScore(resultDto.getActualOpponentScore())
            .actualWinner(resultDto.getActualWinner())
            .actualMvp(resultDto.getActualMvp())
            
            .predictedUserScore(resultDto.getPredictedUserScore())
            .predictedOpponentScore(resultDto.getPredictedOpponentScore())
            .predictedWinner(resultDto.getPredictedWinner())
            .predictedMvp(resultDto.getPredictedMvp())
            
            .scoreExactMatch(resultDto.isScoreExactMatch())
            .winnerCorrect(resultDto.isWinnerCorrect())
            .mvpCorrect(resultDto.isMvpCorrect())
            .predictionAccuracy(resultDto.getPredictionAccuracy())
            
            .betPoints(resultDto.getBetPoints())
            .earnedPoints(resultDto.getEarnedPoints())
            .betType(resultDto.getRewardType())
            
            .gameTime(resultDto.getGameTime())
            .playedAt(resultDto.getPlayedAt())
            .season(resultDto.getSeason())
            .roundNumber(resultDto.getRound())
            .matchType(resultDto.getMatchType())
            .stadium(resultDto.getStadium())
            
            .gameLog(resultDto.getGameLog())
            .matchSummary(resultDto.getMatchSummary())
            
            .build();
        
        realMatchResultRepository.save(entity);
    }
    
    /**
     * 사용자 예측 통계 조회
     */
    @Transactional(readOnly = true)
    public PredictionStatistics getUserPredictionStats(Integer userId) {
        Long totalGames = realMatchResultRepository.countTotalGamesByUserId(userId);
        Long correctPredictions = realMatchResultRepository.countCorrectPredictionsByUserId(userId);
        Long exactScorePredictions = realMatchResultRepository.countExactScorePredictionsByUserId(userId);
        Integer totalPoints = realMatchResultRepository.getTotalEarnedPointsByUserId(userId);
        Double avgAccuracy = realMatchResultRepository.getAveragePredictionAccuracyByUserId(userId);
        
        return new PredictionStatistics(
            totalGames,
            correctPredictions,
            exactScorePredictions,
            totalPoints,
            avgAccuracy
        );
    }
    
    /**
     * 예측 통계 정보
     */
    public static class PredictionStatistics {
        private final Long totalGames;
        private final Long correctPredictions;
        private final Long exactScorePredictions;
        private final Integer totalPoints;
        private final Double averageAccuracy;
        
        public PredictionStatistics(Long totalGames, Long correctPredictions, 
                                  Long exactScorePredictions, Integer totalPoints, Double averageAccuracy) {
            this.totalGames = totalGames;
            this.correctPredictions = correctPredictions;
            this.exactScorePredictions = exactScorePredictions;
            this.totalPoints = totalPoints;
            this.averageAccuracy = averageAccuracy;
        }
        
        public Long getTotalGames() { return totalGames; }
        public Long getCorrectPredictions() { return correctPredictions; }
        public Long getExactScorePredictions() { return exactScorePredictions; }
        public Integer getTotalPoints() { return totalPoints; }
        public Double getAverageAccuracy() { return averageAccuracy; }
        public Double getWinRate() { 
            return totalGames > 0 ? (double) correctPredictions / totalGames * 100 : 0.0; 
        }
    }
}
