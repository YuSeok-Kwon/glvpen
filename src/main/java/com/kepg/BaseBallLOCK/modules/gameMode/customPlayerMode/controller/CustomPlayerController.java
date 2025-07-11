package com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.domain.CustomPlayer;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto.CustomPlayerRequestDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto.CustomPlayerResponseDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.dto.CustomPlayerResultDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.customPlayerMode.service.CustomPlayerService;

import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * CustomPlayer Mode 컨트롤러
 * - 커스텀 선수 생성, 편집, 관리
 * - RPG 스타일 선수 성장 시스템
 */
@Controller
@RequestMapping("/customplayer")
@RequiredArgsConstructor
public class CustomPlayerController {
    
    private final CustomPlayerService customPlayerService;
    
    /**
     * CustomPlayer Mode 메인 페이지
     */
    @GetMapping
    public String customPlayerHome(Model model) {
        model.addAttribute("pageTitle", "Custom Player Mode");
        model.addAttribute("gameMode", "customplayer");
        return "customplayer/home";
    }
    
    /**
     * 커스텀 선수 생성 페이지
     */
    @GetMapping("/create")
    public String createPlayerPage(Model model) {
        model.addAttribute("pageTitle", "Create Custom Player");
        return "customplayer/create";
    }
    
    /**
     * 커스텀 선수 생성 API
     */
    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<CustomPlayerResponseDTO> createCustomPlayer(
        @RequestParam Integer userId,
        @RequestBody CustomPlayerRequestDTO.CustomPlayerInfo playerInfo
    ) {
        try {
            CustomPlayer player = customPlayerService.createCustomPlayer(userId, playerInfo);
            CustomPlayerResponseDTO response = CustomPlayerResponseDTO.fromEntity(player);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 커스텀 선수 목록 페이지
     */
    @GetMapping("/players/{userId}")
    public String playerList(@PathVariable Integer userId, Model model) {
        List<CustomPlayer> players = customPlayerService.getActiveCustomPlayers(userId);
        model.addAttribute("players", players);
        model.addAttribute("userId", userId);
        return "customplayer/players";
    }
    
    /**
     * 커스텀 선수 목록 조회 API
     */
    @GetMapping("/api/players/{userId}")
    @ResponseBody
    public ResponseEntity<List<CustomPlayerResponseDTO>> getCustomPlayers(@PathVariable Integer userId) {
        try {
            List<CustomPlayer> players = customPlayerService.getActiveCustomPlayers(userId);
            List<CustomPlayerResponseDTO> response = players.stream()
                    .map(CustomPlayerResponseDTO::fromEntity)
                    .toList();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 커스텀 선수 게임 실행 페이지
     */
    @GetMapping("/game")
    public String gamePage(
        @RequestParam Integer userId,
        @RequestParam(required = false) String difficulty,
        Model model
    ) {
        List<CustomPlayer> players = customPlayerService.getActiveCustomPlayers(userId);
        model.addAttribute("players", players);
        model.addAttribute("userId", userId);
        model.addAttribute("difficulty", difficulty != null ? difficulty : "NORMAL");
        return "customplayer/game";
    }
    
    /**
     * 커스텀 선수 게임 실행 API
     */
    @PostMapping("/play")
    @ResponseBody
    public ResponseEntity<CustomPlayerResultDTO> playCustomPlayerGame(@RequestBody CustomPlayerRequestDTO request) {
        try {
            CustomPlayerResultDTO result = customPlayerService.playCustomPlayerGame(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 커스텀 선수 편집 페이지
     */
    @GetMapping("/edit/{playerId}")
    public String editPlayerPage(@PathVariable Long playerId, Model model) {
        // TODO: 선수 정보 조회 후 편집 페이지로
        model.addAttribute("playerId", playerId);
        return "customplayer/edit";
    }
    
    /**
     * 커스텀 선수 통계 페이지
     */
    @GetMapping("/stats/{userId}")
    public String playerStatistics(@PathVariable Integer userId, Model model) {
        CustomPlayerService.CustomPlayerStatistics stats = 
            customPlayerService.getCustomPlayerStatistics(userId);
        
        model.addAttribute("stats", stats);
        model.addAttribute("userId", userId);
        return "customplayer/stats";
    }
    
    /**
     * 커스텀 선수 통계 조회 API
     */
    @GetMapping("/api/stats/{userId}")
    @ResponseBody
    public ResponseEntity<CustomPlayerService.CustomPlayerStatistics> getPlayerStatistics(@PathVariable Integer userId) {
        try {
            CustomPlayerService.CustomPlayerStatistics stats = 
                customPlayerService.getCustomPlayerStatistics(userId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 선수 성장/훈련 페이지
     */
    @GetMapping("/training/{playerId}")
    public String playerTraining(@PathVariable Long playerId, Model model) {
        // TODO: 선수 훈련/성장 시스템
        model.addAttribute("playerId", playerId);
        return "customplayer/training";
    }
    
    /**
     * 선수 컬렉션 페이지
     */
    @GetMapping("/collection/{userId}")
    public String playerCollection(@PathVariable Integer userId, Model model) {
        List<CustomPlayer> players = customPlayerService.getActiveCustomPlayers(userId);
        model.addAttribute("players", players);
        model.addAttribute("userId", userId);
        return "customplayer/collection";
    }
    
    /**
     * 팀 빌더 페이지
     */
    @GetMapping("/teambuilder/{userId}")
    public String teamBuilder(@PathVariable Integer userId, Model model) {
        List<CustomPlayer> players = customPlayerService.getActiveCustomPlayers(userId);
        model.addAttribute("players", players);
        model.addAttribute("userId", userId);
        return "customplayer/teambuilder";
    }
}
