package com.kepg.glvpen.modules.game;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.kepg.glvpen.modules.game.schedule.service.ScheduleService;
import com.kepg.glvpen.modules.team.service.TeamService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequestMapping("/game")
@Controller
@RequiredArgsConstructor
@Slf4j
public class GameController {

	private final ScheduleService scheduleService;
	private final TeamService teamService;

	@GetMapping("/home-view")
	public String home() {
		return "game/home";
	}

	@GetMapping("/classic-sim-mode")
	public String classicSimMode() {
		return "game/classic-sim";
	}

}
