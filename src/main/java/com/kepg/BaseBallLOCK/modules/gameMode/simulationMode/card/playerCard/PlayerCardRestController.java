package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.card.playerCard;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.card.playerCard.dto.PlayerCardDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.card.playerCard.dto.PlayerCardSaveRequest;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.card.playerCard.service.PlayerCardService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/game")
public class PlayerCardRestController {
	
	private final PlayerCardService playerCardService;
	
	// 카드 뽑기
	@GetMapping("/draw")
	public List<PlayerCardDTO> drawCardsByPosition(@RequestParam String position) {
	    return playerCardService.drawCardsByPosition(position);
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
