package com.kepg.glvpen.modules.gameMode.simulationMode.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/game")
public class PlayerCardController {

    @GetMapping("/draw-view")
    public String showDrawCardPage() {
        return "game/draw";
    }

    @GetMapping("/lineup-view")
    public String showLineupPage() {
        return "game/lineup";
    }
}
