package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.service.PlayerCardOverallService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/player-card-overall")
@RequiredArgsConstructor
public class PlayerCardOverallRestController {

    private final PlayerCardOverallService playerCardOverallService;

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
}