package com.kepg.glvpen.modules.gameMode.simulationMode.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/game")
public class GameResultController {

	@GetMapping("/result-view")
	public String gameResult(@RequestParam int scheduleId, @RequestParam int userId, Model model) {
	    model.addAttribute("scheduleId", scheduleId);
	    model.addAttribute("userId", userId);
	    return "game/result";
	}
}
