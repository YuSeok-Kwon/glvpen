package com.kepg.glvpen.modules.gameMode.simulationMode.controller;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kepg.glvpen.modules.gameMode.simulationMode.dto.GameResultDTO;
import com.kepg.glvpen.modules.gameMode.simulationMode.service.GameResultService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/game-results")
public class GameResultRestController {

    private final GameResultService gameResultService;

    @GetMapping("/summary")
    public ResponseEntity<GameResultDTO> getGameResult(@RequestParam int scheduleId, @RequestParam int userId) {
    	
        GameResultDTO result = gameResultService.getGameResult(scheduleId, userId);
        
        if (result == null) {
        	return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }
}
