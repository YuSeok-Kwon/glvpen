package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.userLineup;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.card.userCard.dto.UserCardViewDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.card.userCard.service.UserCardService;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.userLineup.domain.UserLineup;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.userLineup.dto.UserLineupDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.userLineup.dto.UserLineupResponseDTO;
import com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.userLineup.service.UserLineupService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/game/lineup")
@RequiredArgsConstructor
public class UserLineupRestController {

    private final UserLineupService userLineupService;
    private final UserCardService userCardService;

    @GetMapping
    public List<UserLineupResponseDTO> getLineup(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        List<UserLineup> lineups = userLineupService.getLineupByUserId(userId);
        return lineups.stream()
                .map(UserLineupResponseDTO::fromEntity)
                .toList();
    }

    @PostMapping("/save-all")
    public ResponseEntity<Void> saveAllLineup(@RequestBody List<UserLineupDTO> lineupList, HttpSession session) {
    	
        Integer userId = (Integer) session.getAttribute("userId");

    	userLineupService.clearLineup(userId); // 기존 라인업 삭제
        for (UserLineupDTO dto : lineupList) {
        	userLineupService.save(dto, userId);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/clear")
    public ResponseEntity<?> clearLineup(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        userLineupService.clearLineup(userId);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/my-cards")
    public List<UserCardViewDTO> getMyCards(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        return userCardService.getUserCardViewList(userId);
    }
    
    @GetMapping("/my-lineup")
    public ResponseEntity<List<UserLineupDTO>> getMyLineup(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        List<UserLineupDTO> lineup = userLineupService.getUserLineup(userId);
        return ResponseEntity.ok(lineup);
    }
}
