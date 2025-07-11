package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.simulation;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.card.playerCard.dto.PlayerCardOverallDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.service.SimulationGameService;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.simulation.dto.GameSimulationResponseDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.simulation.dto.GameStartRequestDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.simulation.dto.InningResultDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.simulation.dto.SimulationRequestDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.simulation.dto.SimulationResultDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.simulation.service.SimulationService;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.userLineup.service.UserLineupService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/game")
public class SimulationRestController {

    private final SimulationService simulationService;
    private final UserLineupService userLineupService;
    private final SimulationGameService simulationGameService;

    @PostMapping("/play")
    public SimulationResultDTO playGame(@RequestBody SimulationRequestDTO request) {
        return simulationService.playSimulationGame(request.getUserLineup(), request.getBotLineup(), request.getDifficulty());
    }
    
    @PostMapping("/lineup")
    public ResponseEntity<GameSimulationResponseDTO> startGame(@RequestBody GameStartRequestDTO request) {
        Integer userId = request.getUserId();
        String difficulty = request.getDifficulty();

        // 시뮬레이션 실행
        List<InningResultDTO> results = simulationService.playGame(userId, difficulty);

        // 라인업 조회
        List<PlayerCardOverallDTO> userLineup = userLineupService.getSavedLineup(userId);
        List<PlayerCardOverallDTO> botLineup = simulationGameService.getLastBotLineup(); // 마지막 봇 라인업 저장해놔야 함

        GameSimulationResponseDTO response = new GameSimulationResponseDTO();
        response.setResults(results);
        response.setUserLineup(userLineup);
        response.setBotLineup(botLineup);

        return ResponseEntity.ok(response);
    }
}
