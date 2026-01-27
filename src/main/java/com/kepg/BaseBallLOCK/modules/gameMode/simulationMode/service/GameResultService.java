package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.dto.PlayerCardOverallDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.domain.GameResult;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.dto.GameResultDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.dto.PlayerPerformanceDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.repository.GameResultRepository;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.dto.SimulationResultDTO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class GameResultService {
	
	private final GameResultRepository gameResultRepository;
	private final PlayerCardOverallService playerCardOverallService;

	public GameResultDTO generateGameSummary(int scheduleId, int userId, SimulationResultDTO result,
	        List<PlayerCardOverallDTO> userLineup, List<PlayerCardOverallDTO> botLineup) {

	    GameResultDTO dto = new GameResultDTO();
	    dto.setScheduleId(scheduleId);
	    dto.setUserId(userId);
	    dto.setUserScore(result.getUserTotalScore());
	    dto.setBotScore(result.getBotTotalScore());
	    dto.setWin(result.getUserTotalScore() > result.getBotTotalScore());

	    // 전체 경기 로그 수집
	    List<String> allLogs = new ArrayList<>();

	    int userSum = 0;
	    int botSum = 0;

	    for (var inning : result.getInnings()) {
	        int inningNumber = inning.getInningNumber();

	        // 유저 공격
	        for (String play : inning.getUserPlays()) {
	            String replaced = replaceBatterName(play, userLineup);
	            allLogs.add(inningNumber + "회초 🧑 " + replaced);
	        }
	        userSum += inning.getUserScore();

	        // 점수 변동 있을 때만 로그
	        if (inning.getUserScore() > 0) {
	            allLogs.add(inningNumber + "회초 종료 점수 🧑 " + userSum + " : 🤖 " + botSum);
	        }

	        // 봇 공격
	        for (String play : inning.getBotPlays()) {
	            String replaced = replaceBatterName(play, botLineup);
	            allLogs.add(inningNumber + "회말 🤖 " + replaced);
	        }
	        botSum += inning.getBotScore();

	        if (inning.getBotScore() > 0) {
	            allLogs.add(inningNumber + "회말 종료 점수 🧑 " + userSum + " : 🤖 " + botSum);
	        }
	    }

	    // 하이라이트 로그만 추출해서 설정
	    List<String> highlights = filterImportantLogs(allLogs);
	    dto.setGameLog(highlights);

	    //  라인업 정보 보강
	    for (int i = 0; i < userLineup.size(); i++) {
	        PlayerCardOverallDTO player = userLineup.get(i);
	        PlayerCardOverallDTO full = playerCardOverallService.getCardByPlayerAndSeason(player.getPlayerId(), player.getSeason());
	        if (full != null) {
	            userLineup.set(i, full);
	        }
	    }

	    List<PlayerCardOverallDTO> updatedBotLineup = new ArrayList<>();
	    for (PlayerCardOverallDTO player : botLineup) {
	        PlayerCardOverallDTO full = playerCardOverallService.getCardByPlayerAndSeason(player.getPlayerId(), player.getSeason());
	        updatedBotLineup.add(full != null ? full : player);
	    }
	    dto.setBotLineup(updatedBotLineup);

	    // MVP 선정
	    List<PlayerPerformanceDTO> performances = collectPerformances(allLogs);
	    PlayerPerformanceDTO mvp = null;

	    boolean userWon = result.getUserTotalScore() >= result.getBotTotalScore();

	    for (PlayerPerformanceDTO p : performances) {
	        boolean found = false;

	        if (userWon) {
	            for (PlayerCardOverallDTO player : userLineup) {
	                if (player.getPlayerName().equals(p.name)) {
	                    found = true;
	                    break;
	                }
	            }
	        } else {
	            for (PlayerCardOverallDTO player : botLineup) {
	                if (player.getPlayerName().equals(p.name)) {
	                    found = true;
	                    break;
	                }
	            }
	        }

	        if (found && (mvp == null || p.getScore() > mvp.getScore())) {
	            mvp = p;
	        }
	    }

	    dto.setMvp(mvp != null ? mvp.name : "없음");
	    dto.setBotLineup(botLineup);
	    
	    return dto;
	}
		
    // 타자 이름 치환
	private String replaceBatterName(String playText, List<PlayerCardOverallDTO> lineup) {
		if (!playText.matches("^[0-9]+번 타자: .*")) return playText;
		
			try {
				int batterIndex = Integer.parseInt(playText.split("번 타자")[0]) - 1;
				String playerName = (lineup.get(batterIndex) != null) ? lineup.get(batterIndex).getPlayerName() : "???";
				return playerName + ": " + playText.split(": ")[1];
				
			} catch (Exception e) {
				return playText;
		}
	}
	
	// MVP 선정
	public List<PlayerPerformanceDTO> collectPerformances(List<String> logs) {
	    Map<String, PlayerPerformanceDTO> map = new HashMap<>();

	    for (String log : logs) {
	        String[] parts = log.split("[🧑🤖]\\s?");
	        if (parts.length < 2 || !parts[1].contains(":")) continue;

	        String[] playerResult = parts[1].split(":");
	        if (playerResult.length < 2) continue;

	        String playerName = playerResult[0].trim();
	        String result = playerResult[1].trim();

	        PlayerPerformanceDTO p = map.getOrDefault(playerName, new PlayerPerformanceDTO(playerName));
	        p.recordResult(result);
	        map.put(playerName, p);
	    }

	    return new ArrayList<>(map.values());
	}
	
	// 로그 필터링
	public List<String> filterImportantLogs(List<String> logs) {
	    List<String> result = new ArrayList<>();

	    for (String log : logs) {
	        if (log.contains("홈런") || log.contains("2루타") || log.contains("3루타")) {
	            result.add(log);
	        } else if (log.matches(".*회(초|말) 종료 .*")) {
	            result.add(log);
	        }
	    }

	    return result;
	}

	// 결과 저장
	public void saveGameResult(GameResultDTO dto) {
	    // 중요 로그만 필터링해서 JSON으로 저장
	    List<String> filteredLogs = filterImportantLogs(dto.getGameLog());
	    String gameLogJson = new Gson().toJson(filteredLogs);

	    // 봇 라인업을 JSON으로 저장
	    String botLineupJson = null;
	    if (dto.getBotLineup() != null) {
	        botLineupJson = new Gson().toJson(dto.getBotLineup());
	    }

	    // Builder 패턴으로 Entity 생성
	    GameResult entity = GameResult.builder()
	            .scheduleId(dto.getScheduleId())
	            .userId(dto.getUserId())
	            .userScore(dto.getUserScore())
	            .botScore(dto.getBotScore())
	            .isWin(dto.isWin())
	            .mvp(dto.getMvp())
	            .gameLog(gameLogJson)
	            .botLineupJson(botLineupJson)
	            .build();

	    gameResultRepository.save(entity);
	}
    
	public GameResultDTO getGameResult(int scheduleId, int userId) {
	    GameResult entity = gameResultRepository.findByScheduleIdAndUserId(scheduleId, userId);

	    if (entity == null) return null;

	    GameResultDTO dto = new GameResultDTO();
	    dto.setScheduleId(entity.getScheduleId());
	    dto.setUserId(entity.getUserId());
	    dto.setUserScore(entity.getUserScore());
	    dto.setBotScore(entity.getBotScore());
	    dto.setWin(entity.isWin());
	    dto.setMvp(entity.getMvp());

	    // JSON → List<String>
	    List<String> logList = new Gson().fromJson(entity.getGameLog(), new TypeToken<List<String>>() {}.getType());
	    dto.setGameLog(logList);

	    // JSON → List<PlayerCardOverallDTO>
	    if (entity.getBotLineupJson() != null) {
	        List<PlayerCardOverallDTO> botLineup = new Gson().fromJson(
	            entity.getBotLineupJson(),
	            new TypeToken<List<PlayerCardOverallDTO>>() {}.getType()
	        );
	        dto.setBotLineup(botLineup);
	    }

	    return dto;
	}
}
