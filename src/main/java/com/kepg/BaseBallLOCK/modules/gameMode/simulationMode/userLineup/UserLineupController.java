package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.userLineup;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/game")
public class UserLineupController {
	
	@GetMapping("/lineup-view")
	public String lineupView() {
		return "game/lineup";
	}
}
