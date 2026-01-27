package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.dto.PlayerCardOverallDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.dto.GameReadyCardView;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.service.SimulationGameService;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.dto.UserLineupDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.service.UserLineupService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RequestMapping("/api/games")
@RestController
public class SimulationGameRestController {
	
	private final SimulationGameService simulationGameService;
	private final UserLineupService userLineupService;

	@PostMapping("/bot/lineup")
	public ResponseEntity<List<PlayerCardOverallDTO>> getBotLineup(@RequestParam String difficulty, Model model) {
		
	    List<PlayerCardOverallDTO> botCards = simulationGameService.generateBotLineupWithStats(difficulty);
	    
	    model.addAttribute("difficulty", difficulty);
	    return ResponseEntity.ok(botCards);
	}
	
	@GetMapping("/user/lineup")
	public ResponseEntity<List<UserLineupDTO>> getUserLineup(@RequestParam Integer userId) {
	    List<UserLineupDTO> lineup = simulationGameService.getUserLineup(userId);
	    return ResponseEntity.ok(lineup);
	}
	
	@GetMapping("/user/lineup-list")
	public ResponseEntity<List<PlayerCardOverallDTO>> getUserLineupList(@RequestParam Integer userId) {
	    List<PlayerCardOverallDTO> lineup = userLineupService.getSavedLineup(userId);
	    return ResponseEntity.ok(lineup);
	}
	
	@PostMapping("/ready")
	public ResponseEntity<Map<String, List<GameReadyCardView>>> getReadyView(
	        @RequestBody List<UserLineupDTO> userLineup,
	        @RequestParam String difficulty,
	        HttpSession session) {

	    List<GameReadyCardView> userCards = simulationGameService.mergeCardInfo(userLineup);
	    List<PlayerCardOverallDTO> botLineup = simulationGameService.generateBotLineupWithStats(difficulty);
	    session.setAttribute("botLineup", botLineup);
	    
	    List<GameReadyCardView> botCards = new ArrayList<>();

	    for (int i = 0; i < botLineup.size(); i++) {
	        PlayerCardOverallDTO botCard = botLineup.get(i);

	        UserLineupDTO lineup = UserLineupDTO.builder()
	            .playerId(botCard.getPlayerId())
	            .position(botCard.getPosition())
	            .orderNum(i + 1)
	            .season(botCard.getSeason())
	            .build();

	        GameReadyCardView view = GameReadyCardView.builder()
	            .lineup(lineup)
	            .card(botCard)
	            .build();

	        botCards.add(view);
	    }

	    Map<String, List<GameReadyCardView>> result = new HashMap<>();
	    result.put("user", userCards);
	    result.put("bot", botCards);
	    
	    
	    return ResponseEntity.ok(result);
	}
}
