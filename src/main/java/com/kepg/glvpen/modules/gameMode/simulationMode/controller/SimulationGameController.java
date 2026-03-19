package com.kepg.glvpen.modules.gameMode.simulationMode.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.google.gson.Gson;
import com.kepg.glvpen.modules.gameMode.simulationMode.dto.PlayerCardOverallDTO;
import com.kepg.glvpen.modules.gameMode.simulationMode.dto.GameResultDTO;
import com.kepg.glvpen.modules.gameMode.simulationMode.service.GameResultService;
import com.kepg.glvpen.modules.gameMode.simulationMode.service.SimulationGameService;
import com.kepg.glvpen.modules.gameMode.simulationMode.dto.SimulationResultDTO;
import com.kepg.glvpen.modules.gameMode.simulationMode.service.SimulationService;
import com.kepg.glvpen.modules.gameMode.simulationMode.service.UserLineupService;
import com.kepg.glvpen.modules.user.domain.User;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RequestMapping("/simulations")
@Controller("simulationModeGameController")
public class SimulationGameController {

	private final SimulationGameService simulationGameService;
	private final SimulationService simulationService;
	private final UserLineupService userLineupService;
	private final GameResultService gameResultService;

	// 헬퍼 메서드: User에서 userId 추출 (null 안전)
	private int getUserIdOrDefault(User user) {
		return user != null ? user.getId() : 0;
	}
		
	@GetMapping("/home-view")
	public String gameHomeView() {
		return "game/home";
	}
	
	@GetMapping("/ready-view")
	public String gameReadyView(HttpSession session, Model model) {
		User user = (User) session.getAttribute("loginUser");
		int userId = getUserIdOrDefault(user);
		model.addAttribute("userId", userId);
		return "game/ready";
	}
	
	@GetMapping("/play-view")
	public String showResult(@RequestParam(defaultValue = "normal") String difficulty, Model model, HttpSession session) {
	    User user = (User) session.getAttribute("loginUser");
	    int userId = getUserIdOrDefault(user);

	    List<PlayerCardOverallDTO> userLineup = userLineupService.getSavedLineup(userId);
	    if (userLineup == null || userLineup.isEmpty()) {
	        return "redirect:/game/lineup-view";
	    }

	    @SuppressWarnings("unchecked")
	    List<PlayerCardOverallDTO> botLineup = (List<PlayerCardOverallDTO>) session.getAttribute("botLineup");
	    if (botLineup == null || botLineup.isEmpty()) {
	        return "redirect:/simulations/ready-view";
	    }

	    int scheduleId = simulationGameService.createSimulationSchedule(userId, difficulty);
	    SimulationResultDTO result = simulationService.playSimulationGame(userLineup, botLineup, difficulty);
	    GameResultDTO summary = gameResultService.generateGameSummary(scheduleId, userId, result, userLineup, botLineup);
	    gameResultService.saveGameResult(summary);

	    model.addAttribute("userId", userId);
	    model.addAttribute("scheduleId", scheduleId);
	    model.addAttribute("difficulty", difficulty);
	    model.addAttribute("botLineup", botLineup);
	    
	    Gson gson = new Gson();

	    model.addAttribute("botLineup", gson.toJson(botLineup));
	    model.addAttribute("userLineup", gson.toJson(userLineup));
	    model.addAttribute("result", gson.toJson(result.getInnings()));

	    return "game/play";
	}
}
