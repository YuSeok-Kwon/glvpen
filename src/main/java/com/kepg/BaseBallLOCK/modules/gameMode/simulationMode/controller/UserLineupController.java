package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/user-lineups")
public class UserLineupController {
	
	@GetMapping("")
	public String lineupView() {
		return "game/lineup";
	}
}
