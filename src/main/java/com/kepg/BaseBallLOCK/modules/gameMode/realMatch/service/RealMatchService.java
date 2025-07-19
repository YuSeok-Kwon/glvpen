package com.kepg.BaseBallLOCK.modules.gameMode.realMatch.service;

import com.kepg.BaseBallLOCK.modules.gameMode.realMatch.domain.RealMatchResult;
import com.kepg.BaseBallLOCK.modules.gameMode.realMatch.dto.RealMatchRequestDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.realMatch.dto.RealMatchResultDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.realMatch.repository.RealMatchResultRepository;
import com.kepg.BaseBallLOCK.modules.game.schedule.domain.Schedule;
import com.kepg.BaseBallLOCK.modules.game.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 실제 경기 예측 서비스
 * 실제 KBO 경기 데이터를 기반으로 한 예측 게임 로직 처리
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RealMatchService {
    
    private final RealMatchResultRepository realMatchResultRepository;
    private final ScheduleRepository scheduleRepository;
    private final Random random = new Random();
    
    /**
     * 실제 경기 예측 게임 실행
     */
    public RealMatchResultDTO playPredictionGame(RealMatchRequestDTO request) {
        log.info("실제 경기 예측 게임 시작 - 사용자: {}, 스케줄: {}", request.getUserId(), request.getScheduleId());
        
        // 스케줄 정보 조회
        Schedule schedule = scheduleRepository.findById(request.getScheduleId().intValue())
                .orElseThrow(() -> new RuntimeException("경기 일정을 찾을 수 없습니다."));
        
        // 중복 예측 체크
        RealMatchResult existingResult = realMatchResultRepository
                .findByUserIdAndScheduleId(request.getUserId(), request.getScheduleId());
        
        if (existingResult != null) {
            log.warn("이미 예측한 경기입니다 - 사용자: {}, 스케줄: {}", request.getUserId(), request.getScheduleId());
            throw new RuntimeException("이미 예측한 경기입니다.");
        }
        
        // 예측 결과 생성 (실제로는 경기 결과와 비교)
        boolean isCorrect = calculatePredictionResult(request, schedule);
        int pointsEarned = isCorrect ? calculatePointsEarned(request.getBetAmount()) : 0;
        
        // 결과 저장
        RealMatchResult result = RealMatchResult.builder()
                .userId(request.getUserId())
                .scheduleId(request.getScheduleId())
                .predictedWinner(request.getHomeTeamPrediction())
                .predictedHomeScore(request.getHomeScorePrediction())
                .predictedAwayScore(request.getAwayScorePrediction())
                .predictedMvp(request.getMvpPrediction())
                .betAmount(request.getBetAmount())
                .winnerCorrect(isCorrect)
                .pointsEarned(pointsEarned)
                .predictionTime(LocalDateTime.now())
                .isProcessed(true)
                .build();
        
        realMatchResultRepository.save(result);
        
        // 결과 DTO 생성
        return RealMatchResultDTO.builder()
                .predictionId(result.getId())
                .userId(request.getUserId())
                .scheduleId(request.getScheduleId())
                .winnerCorrect(isCorrect)
                .pointsEarned(pointsEarned)
                .actualWinner(determineWinner(schedule))
                .predictedWinner(request.getHomeTeamPrediction())
                .predictionTime(LocalDateTime.now())
                .build();
    }
    
    /**
     * 사용자 예측 통계 조회
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getUserPredictionStats(Integer userId) {
        List<RealMatchResult> results = realMatchResultRepository
                .findByUserIdOrderByPredictionTimeDesc(userId);
        
        long totalPredictions = results.size();
        long correctPredictions = results.stream()
                .mapToLong(r -> r.getWinnerCorrect() != null && r.getWinnerCorrect() ? 1 : 0)
                .sum();
        int totalPointsEarned = results.stream()
                .mapToInt(RealMatchResult::getPointsEarned)
                .sum();
        
        double accuracy = totalPredictions > 0 ? 
                (double) correctPredictions / totalPredictions * 100 : 0.0;
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPredictions", totalPredictions);
        stats.put("correctPredictions", correctPredictions);
        stats.put("accuracy", Math.round(accuracy * 100.0) / 100.0);
        stats.put("totalPointsEarned", totalPointsEarned);
        stats.put("recentResults", results.stream().limit(10).toList());
        
        return stats;
    }
    
    /**
     * 예측 결과 계산 (실제 경기 결과와 비교)
     */
    private boolean calculatePredictionResult(RealMatchRequestDTO request, Schedule schedule) {
        // 실제 구현에서는 경기가 끝난 후 실제 결과와 비교
        // 현재는 시뮬레이션을 위해 랜덤 결과 생성
        String actualWinner = determineWinner(schedule);
        if (actualWinner != null) {
            return actualWinner.equals(request.getHomeTeamPrediction());
        }
        
        // 경기가 아직 끝나지 않은 경우 임시 결과
        return random.nextBoolean();
    }
    
    /**
     * 스케줄로부터 승자 판정
     */
    private String determineWinner(Schedule schedule) {
        if (schedule.getHomeTeamScore() != null && schedule.getAwayTeamScore() != null) {
            if (schedule.getHomeTeamScore() > schedule.getAwayTeamScore()) {
                return "home";
            } else if (schedule.getAwayTeamScore() > schedule.getHomeTeamScore()) {
                return "away";
            } else {
                return "draw";
            }
        }
        return null;
    }
    
    /**
     * 획득 포인트 계산
     */
    private int calculatePointsEarned(int betAmount) {
        // 배팅 금액의 1.5배 지급
        return (int) (betAmount * 1.5);
    }
}