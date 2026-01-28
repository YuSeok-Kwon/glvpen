package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.domain.GameEventAnswer;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.repository.GameEventAnswerRepository;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.domain.GameEventQuestion;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.repository.GameEventQuestionRepository;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.domain.GameResult;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.repository.GameResultRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GameEventService {

    private final GameEventQuestionRepository gameEventQuestionRepository;
    private final GameEventAnswerRepository gameEventAnswerRepository;
    private final GameResultRepository gameResultRepository;

    public List<GameEventQuestion> getRandomFiveQuestions() {
        return gameEventQuestionRepository.getRandomQuestions();
    }

    public void saveUserAnswer(int scheduleId, int questionId, String answerText) {
        GameEventAnswer answer = new GameEventAnswer();
        answer.setScheduleId(scheduleId);
        answer.setQuestionId(questionId);
        answer.setAnswerText(answerText);
        gameEventAnswerRepository.save(answer);
    }
    
    public boolean evaluateGameResult(int scheduleId) {
        List<GameEventAnswer> answers = gameEventAnswerRepository.findByScheduleId(scheduleId);

        int correctCount = 0;
        for (GameEventAnswer answer : answers) {
            if (answer.isCorrect()) {
                correctCount++;
            }
        }

        return correctCount >= 4;
    }
    
    public void evaluateAndSaveResult(int scheduleId, int userId, List<GameEventAnswer> answers) {
        int correctCount = 0;
        for (GameEventAnswer answer : answers) {
            if (answer.isCorrect()) {
                correctCount++;
            }
        }

        boolean isWin = correctCount >= 4;

        GameResult result = GameResult.builder()
                .scheduleId(scheduleId)
                .userId(userId)
                .isWin(isWin)
                .build();

        gameResultRepository.save(result);
    }
    
    public List<GameEventAnswer> getAnswersByScheduleId(int scheduleId, int userId) {
        return gameEventAnswerRepository.findByScheduleIdAndUserId(scheduleId, userId);
    }
    
    public Optional<GameEventQuestion> getQuestionForInning(int inning, boolean isTop) {
        String type = null;

        if ((inning == 1 || inning == 3 || inning == 7) && isTop) {
            type = "batting";
        } else if ((inning == 1 || inning == 5) && !isTop) {
            type = "pitch";
        } else if (inning == 9 && !isTop) {
            // coach 또는 defense 랜덤 선택
            type = Math.random() < 0.5 ? "coach" : "defense";
        }

        if (type != null) {
            return gameEventQuestionRepository.findRandomByType(type);
        }
        return Optional.empty();
    }
}
