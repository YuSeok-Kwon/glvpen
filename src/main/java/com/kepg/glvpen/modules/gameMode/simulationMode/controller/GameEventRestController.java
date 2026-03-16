package com.kepg.glvpen.modules.gameMode.simulationMode.controller;

import java.util.List;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kepg.glvpen.modules.gameMode.simulationMode.domain.GameEventAnswer;
import com.kepg.glvpen.modules.gameMode.simulationMode.domain.GameEventQuestion;
import com.kepg.glvpen.modules.gameMode.simulationMode.dto.GameResultRequestDTO;
import com.kepg.glvpen.modules.gameMode.simulationMode.service.GameEventService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/game-events")
public class GameEventRestController {

    private final GameEventService gameEventService;

    @GetMapping("/questions")
    // TODO: GameEventQuestionDTO 생성 후 entity 대신 DTO 반환하도록 수정 필요
    public List<GameEventQuestion> getQuestions() {
        return gameEventService.getRandomFiveQuestions();
    }

    @PostMapping("/answer")
    public ResponseEntity<?> saveAnswer(@RequestBody GameEventAnswer answer) {
        gameEventService.saveUserAnswer(
            answer.getScheduleId(), answer.getQuestionId(), answer.getAnswerText()
        );
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/result")
    public ResponseEntity<?> getResult(@RequestParam int scheduleId) {
        boolean isWin = gameEventService.evaluateGameResult(scheduleId);
        return ResponseEntity.ok().body(Map.of("isWin", isWin));
    }
    
    @PostMapping("/submit")
    public ResponseEntity<?> submitResult(@RequestBody GameResultRequestDTO request) {
        List<GameEventAnswer> answers = gameEventService.getAnswersByScheduleId(request.getScheduleId(), request.getUserId());
        gameEventService.evaluateAndSaveResult(request.getScheduleId(), request.getUserId(), answers);
        return ResponseEntity.ok().build();
    }
}
