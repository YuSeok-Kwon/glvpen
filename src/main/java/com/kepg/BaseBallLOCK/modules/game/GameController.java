package com.kepg.BaseBallLOCK.modules.game;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import lombok.RequiredArgsConstructor;

@RequestMapping("/game")
@Controller
@RequiredArgsConstructor
public class GameController {
	
	@GetMapping("/home")
	public String home() {
		return "game/home";
	}
	
	@GetMapping("/ready")
	public String ready() {
		return "game/ready";
	}
	
	@GetMapping("/game-lineup")
	public String gameLineup() {
		return "game/lineup";
	}
	
	@GetMapping("/play")
	public String play() {
		return "game/play";
	}
	
	@GetMapping("/result")
	public String result() {
		return "game/result";
	}
	
	@GetMapping("/draw")
	public String draw() {
		return "game/draw";
	}
}
