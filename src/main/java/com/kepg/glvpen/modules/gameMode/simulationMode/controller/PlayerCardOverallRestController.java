package com.kepg.glvpen.modules.gameMode.simulationMode.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kepg.glvpen.modules.gameMode.simulationMode.service.PlayerCardOverallService;
import com.kepg.glvpen.modules.player.stats.service.SabermetricsBatchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/player-card-overall")
@RequiredArgsConstructor
@Slf4j
public class PlayerCardOverallRestController {

    private final PlayerCardOverallService playerCardOverallService;
    private final SabermetricsBatchService sabermetricsBatchService;

    @Scheduled(cron = "0 0 3 * * *") 
    public void scheduled2025OverallCalculation() {
        playerCardOverallService.calculateOnly2025();
        playerCardOverallService.assignGradesForAll(); 
    }
    
    @GetMapping("/generate-overall/current")
    public ResponseEntity<String> generateCurrnetOveralls() {
        playerCardOverallService.calculateOnly2025();
        playerCardOverallService.assignGradesForAll(); 
        return ResponseEntity.ok("2025 시즌 선수 능력치 및 등급 계산 완료");
    }
    
    @GetMapping("/generate-overall/legacy")
    public ResponseEntity<String> generateLegacyOveralls() {
        playerCardOverallService.calculateAllBatterAndPitcher();
        playerCardOverallService.assignGradesForAll(); // 등급까지
        return ResponseEntity.ok("2020~2024 시즌 선수 능력치 및 등급 계산 완료");
    }
    
    @GetMapping("/generate-grade")
    public ResponseEntity<String> generateGrades() {
        playerCardOverallService.assignGradesForAll();
        return ResponseEntity.ok("타자, 투수 등급 부여 완료");
    }

    @GetMapping("/recalculate-all")
    public ResponseEntity<String> recalculateAll() {
        log.info("=== 전체 재계산 시작 (세이버메트릭스 + OVERALL + 등급) ===");

        // 1단계: 세이버메트릭스 재계산 (포지션 세분화 포함)
        sabermetricsBatchService.calculateAllSabermetrics(2020, 2025);

        // 2단계: OVERALL 재계산 (2020~2024 + 2025)
        playerCardOverallService.calculateAllBatterAndPitcher();
        playerCardOverallService.calculateOnly2025();

        // 3단계: 등급 부여
        playerCardOverallService.assignGradesForAll();

        log.info("=== 전체 재계산 완료 ===");
        return ResponseEntity.ok("세이버메트릭스 + OVERALL + 등급 전체 재계산 완료 (2020~2025)");
    }
}