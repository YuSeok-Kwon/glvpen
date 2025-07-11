package com.kepg.BaseBallLOCK.modules.main;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainController {
    
    @GetMapping("/")
    public String index() {
        return "redirect:/home";  // 홈페이지로 리다이렉트
    }
    
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
    
    @GetMapping("/game-draw")
    public String gameDraw() {
        return "game/draw";
    }
}
