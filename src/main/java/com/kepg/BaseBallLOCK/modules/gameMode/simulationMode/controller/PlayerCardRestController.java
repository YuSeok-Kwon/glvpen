package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.dto.PlayerCardDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.dto.PlayerCardSaveRequest;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.service.PlayerCardService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/game")
public class PlayerCardRestController {
	
	private final PlayerCardService playerCardService;
	
	// 카드 뽑기
	@GetMapping("/draw")
	public ResponseEntity<Map<String, Object>> drawCardsByPosition(@RequestParam String position) {
		Map<String, Object> result = new HashMap<>();
		try {
			List<PlayerCardDTO> cards = playerCardService.drawCardsByPosition(position);
			result.put("result", "success");
			result.put("cards", cards);
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			result.put("result", "error");
			result.put("message", e.getMessage());
			result.put("cards", new ArrayList<>());
			return ResponseEntity.ok(result);
		}
	}
	
	// S등급 선수들 리스트
	@GetMapping("/s-list")
	public List<PlayerCardDTO> getSGradePlayers(@RequestParam String position) {
	    return playerCardService.getAllSGradePlayers(position);
	}
	
	@PostMapping("/save")
	public ResponseEntity<Void> saveCard(@RequestBody PlayerCardSaveRequest dto, HttpSession session) {
	    Integer userId = (Integer) session.getAttribute("userId"); 

	    if (userId == null) {
	        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
	    }

	    playerCardService.saveCard(dto, userId);
	    return ResponseEntity.ok().build();
	}
}
