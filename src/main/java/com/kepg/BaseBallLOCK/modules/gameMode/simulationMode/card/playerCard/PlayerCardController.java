package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.card.playerCard;

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
}
